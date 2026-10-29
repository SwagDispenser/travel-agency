package com.example.travelagency.scheduling;

import com.example.travelagency.config.QuartzConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.quartz.*;
import org.quartz.impl.matchers.GroupMatcher;
import org.quartz.listeners.JobListenerSupport;
import org.springframework.aop.support.AopUtils;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientAutoConfiguration;
import org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.AopTestUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.quartz.TriggerBuilder.newTrigger;

class QuartzJdbcIntegrationTest {
    @TempDir Path temporary;

    @Test
    void liquibaseCreatesJdbcStore_andRestartPreservesJobsParametersAndPausedTrigger() throws Exception {
        String database = "jdbc:h2:file:" + temporary.resolve("quartz") + ";MODE=PostgreSQL";
        String schedulerName = "persistence-" + UUID.randomUUID();
        TriggerKey paused = new TriggerKey("report-poland-evening", QuartzConfig.GROUP);

        try (ConfigurableApplicationContext first = start(database, schedulerName)) {
            JdbcTemplate jdbc = first.getBean(JdbcTemplate.class);
            Scheduler scheduler = first.getBean(Scheduler.class);
            assertTrue(scheduler.getMetaData().isJobStoreSupportsPersistence());
            assertEquals("never", first.getEnvironment().getProperty("spring.quartz.jdbc.initialize-schema"));
            assertEquals(11, jdbc.queryForObject("""
                    SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES
                    WHERE TABLE_SCHEMA = 'PUBLIC' AND TABLE_NAME LIKE 'QRTZ\\_%' ESCAPE '\\'
                    """, Integer.class));
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM DATABASECHANGELOG WHERE ID = '003-create-quartz-h2' AND AUTHOR = 'dmytro'", Integer.class));
            assertEquals(2, scheduler.getJobKeys(GroupMatcher.jobGroupEquals(QuartzConfig.GROUP)).size());
            assertEquals(3, scheduler.getTriggerKeys(GroupMatcher.triggerGroupEquals(QuartzConfig.GROUP)).size());

            JobDetail changed = scheduler.getJobDetail(QuartzConfig.REPORT_JOB).getJobBuilder()
                    .usingJobData("horizonDays", "45").build();
            scheduler.addJob(changed, true);
            scheduler.pauseTrigger(paused);
        }

        try (ConfigurableApplicationContext second = start(database, schedulerName)) {
            Scheduler scheduler = second.getBean(Scheduler.class);
            JdbcTemplate jdbc = second.getBean(JdbcTemplate.class);
            assertEquals("45", scheduler.getJobDetail(QuartzConfig.REPORT_JOB).getJobDataMap().getString("horizonDays"));
            assertEquals(Trigger.TriggerState.PAUSED, scheduler.getTriggerState(paused));
            assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM QRTZ_JOB_DETAILS", Integer.class));
            assertEquals(3, jdbc.queryForObject("SELECT COUNT(*) FROM QRTZ_TRIGGERS", Integer.class));
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM DATABASECHANGELOG WHERE ID = '003-create-quartz-h2'", Integer.class));
            assertEquals("Europe/Kyiv", ((CronTrigger) scheduler.getTrigger(paused)).getTimeZone().getID());
        }
    }

    @Test
    void realDatabaseOperations_areTransactionalAndIdempotent() {
        try (ConfigurableApplicationContext context = memoryContext()) {
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            TourMaintenanceService service = context.getBean(TourMaintenanceService.class);
            seedTours(jdbc);
            assertTrue(AopUtils.isAopProxy(service));
            TourMaintenanceService target = AopTestUtils.getUltimateTargetObject(service);
            doAnswer(invocation -> {
                assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
                return invocation.callRealMethod();
            }).when(target).clearExpiredFeatured(0);
            doAnswer(invocation -> {
                assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
                return invocation.callRealMethod();
            }).when(target).createDailyReport("ALL", 30);

            assertEquals(1, service.clearExpiredFeatured(0));
            List<Map<String, Object>> afterCleanup = jdbc.queryForList("SELECT * FROM tours ORDER BY title");
            assertEquals(0, service.clearExpiredFeatured(0));
            assertEquals(afterCleanup, jdbc.queryForList("SELECT * FROM tours ORDER BY title"));
            assertEquals(3, jdbc.queryForObject("SELECT COUNT(*) FROM tours", Integer.class));
            assertFalse(jdbc.queryForObject("SELECT featured FROM tours WHERE title = 'Expired'", Boolean.class));
            assertTrue(jdbc.queryForObject("SELECT featured FROM tours WHERE title = 'Today'", Boolean.class));

            assertEquals(1, service.createDailyReport("ALL", 30));
            List<Map<String, Object>> snapshot = service.reports();
            assertEquals(0, service.createDailyReport("ALL", 30));
            assertEquals(snapshot, service.reports());
            assertEquals(2L, jdbc.queryForObject("SELECT tour_count FROM daily_tour_reports", Long.class));
            assertEquals(7L, jdbc.queryForObject("SELECT available_seats FROM daily_tour_reports", Long.class));
        }
    }

    @Test
    void realScheduler_autowiresTheJobAndAppliesTriggerOverrides() throws Exception {
        try (ConfigurableApplicationContext context = memoryContext()) {
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            Scheduler scheduler = context.getBean(Scheduler.class);
            seedTours(jdbc);
            CountDownLatch completed = new CountDownLatch(1);
            AtomicReference<JobExecutionException> failure = new AtomicReference<>();
            scheduler.getListenerManager().addJobListener(listener("report-result", completed, failure));
            scheduler.pauseTriggers(GroupMatcher.triggerGroupEquals(QuartzConfig.GROUP));
            scheduler.start();
            JobDataMap overrides = new JobDataMap();
            overrides.put("country", "Poland");

            scheduler.triggerJob(QuartzConfig.REPORT_JOB, overrides);

            assertTrue(completed.await(5, TimeUnit.SECONDS));
            assertNull(failure.get());
            assertEquals("Poland", jdbc.queryForObject("SELECT country FROM daily_tour_reports", String.class));
            assertEquals(30, jdbc.queryForObject("SELECT horizon_days FROM daily_tour_reports", Integer.class));
            assertEquals(1L, jdbc.queryForObject("SELECT tour_count FROM daily_tour_reports", Long.class));
        }
    }

    @Test
    void sameJobKey_secondExecutionIsBlockedUntilFirstCompletes() throws Exception {
        try (ConfigurableApplicationContext context = memoryContext()) {
            Scheduler scheduler = context.getBean(Scheduler.class);
            TourMaintenanceService target = AopTestUtils.getUltimateTargetObject(context.getBean(TourMaintenanceService.class));
            CountDownLatch entered = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            CountDownLatch completed = new CountDownLatch(2);
            AtomicReference<JobExecutionException> failure = new AtomicReference<>();
            AtomicInteger calls = new AtomicInteger();
            AtomicInteger active = new AtomicInteger();
            AtomicInteger maxActive = new AtomicInteger();
            doAnswer(invocation -> {
                int simultaneous = active.incrementAndGet();
                maxActive.accumulateAndGet(simultaneous, Math::max);
                int call = calls.incrementAndGet();
                try {
                    if (call == 1) {
                        entered.countDown();
                        assertTrue(release.await(8, TimeUnit.SECONDS));
                    }
                    return 1;
                } finally {
                    active.decrementAndGet();
                }
            }).when(target).createDailyReport("ALL", 30);
            scheduler.getListenerManager().addJobListener(listener("serial-execution", completed, failure));
            scheduler.pauseTriggers(GroupMatcher.triggerGroupEquals(QuartzConfig.GROUP));
            scheduler.start();
            Trigger first = newTrigger().withIdentity("serial-first", "test")
                    .forJob(QuartzConfig.REPORT_JOB).startNow().build();
            Trigger second = newTrigger().withIdentity("serial-second", "test")
                    .forJob(QuartzConfig.REPORT_JOB).startNow().build();

            try {
                scheduler.scheduleJob(first);
                assertTrue(entered.await(3, TimeUnit.SECONDS));
                scheduler.scheduleJob(second);
                await().atMost(Duration.ofSeconds(3))
                        .until(() -> scheduler.getTriggerState(second.getKey()) == Trigger.TriggerState.BLOCKED);
                assertEquals(1, calls.get());
                release.countDown();
                assertTrue(completed.await(5, TimeUnit.SECONDS));
                assertNull(failure.get());
                assertEquals(2, calls.get());
                assertEquals(1, maxActive.get());
            } finally {
                release.countDown();
            }
        }
    }

    private ConfigurableApplicationContext memoryContext() {
        return start("jdbc:h2:mem:lab6-" + UUID.randomUUID() + ";MODE=PostgreSQL", "lab6-" + UUID.randomUUID());
    }

    private ConfigurableApplicationContext start(String database, String schedulerName) {
        return new SpringApplicationBuilder(TestApplication.class).web(WebApplicationType.NONE).run(
                "--spring.datasource.url=" + database,
                "--spring.quartz.scheduler-name=" + schedulerName,
                "--spring.quartz.auto-startup=false",
                "--spring.h2.console.enabled=false",
                "--spring.main.banner-mode=off");
    }

    private static JobListenerSupport listener(String name, CountDownLatch completed,
                                               AtomicReference<JobExecutionException> failure) {
        return new JobListenerSupport() {
            @Override public String getName() { return name; }
            @Override public void jobWasExecuted(JobExecutionContext context, JobExecutionException error) {
                if (error != null) {
                    failure.set(error);
                }
                completed.countDown();
            }
        };
    }

    private static void seedTours(JdbcTemplate jdbc) {
        jdbc.update("DELETE FROM tours");
        insertTour(jdbc, "Expired", "Poland", "2030-05-20", "2030-05-30", 5);
        insertTour(jdbc, "Today", "Poland", "2030-06-01", "2030-06-05", 3);
        insertTour(jdbc, "Future", "Italy", "2030-06-10", "2030-06-12", 4);
    }

    private static void insertTour(JdbcTemplate jdbc, String title, String country,
                                   String start, String end, int seats) {
        jdbc.update("""
                INSERT INTO tours (title, country, city, start_date, end_date, price, available_seats, featured)
                VALUES (?, ?, 'Demo', ?, ?, 220.00, ?, TRUE)
                """, title, country, LocalDate.parse(start), LocalDate.parse(end), seats);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration(exclude = {HibernateJpaAutoConfiguration.class, JpaRepositoriesAutoConfiguration.class, OAuth2ClientAutoConfiguration.class,
            OAuth2ResourceServerAutoConfiguration.class})
    @Import(QuartzConfig.class)
    static class TestApplication {
        @Bean @Primary Clock testClock() {
            return Clock.fixed(Instant.parse("2030-06-01T09:00:00Z"), QuartzConfig.ZONE);
        }
        @Bean TourMaintenanceService maintenanceService(JdbcTemplate jdbc, Clock clock) {
            return spy(new TourMaintenanceService(jdbc, clock));
        }
    }
}
