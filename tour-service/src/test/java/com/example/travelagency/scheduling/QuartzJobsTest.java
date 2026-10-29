package com.example.travelagency.scheduling;

import com.example.travelagency.config.QuartzConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.quartz.*;
import org.quartz.impl.JobExecutionContextImpl;
import org.quartz.spi.OperableTrigger;
import org.quartz.spi.TriggerFiredBundle;

import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.quartz.JobBuilder.newJob;
import static org.quartz.TriggerBuilder.newTrigger;

@ExtendWith(MockitoExtension.class)
class QuartzJobsTest {
    @Mock TourMaintenanceService service;
    private final QuartzConfig config = new QuartzConfig();

    @Test
    void definitions_haveStableNamesGroupsJobKeysAndTwoReportTriggers() {
        JobDetail cleanup = config.cleanupJobDetail();
        JobDetail report = config.reportJobDetail();
        List<CronTrigger> triggers = List.of(config.cleanupNightlyTrigger(cleanup),
                config.reportAllTrigger(report), config.reportPolandTrigger(report));

        assertEquals("expired-featured-cleanup", cleanup.getKey().getName());
        assertEquals("daily-tour-report", report.getKey().getName());
        assertEquals(2, triggers.stream().filter(trigger -> trigger.getJobKey().equals(report.getKey())).count());
        assertEquals(3, triggers.stream().map(Trigger::getKey).distinct().count());
        for (JobDetail job : List.of(cleanup, report)) {
            assertEquals(QuartzConfig.GROUP, job.getKey().getGroup());
            assertTrue(job.isDurable());
            assertTrue(job.isConcurrentExectionDisallowed());
            assertTrue(job.getJobDataMap().values().stream().allMatch(String.class::isInstance));
        }
        for (CronTrigger trigger : triggers) {
            assertEquals(QuartzConfig.GROUP, trigger.getKey().getGroup());
            assertTrue(List.of(cleanup.getKey(), report.getKey()).contains(trigger.getJobKey()));
        }
    }

    @Test
    void everyTrigger_hasAnExplicitMisfirePolicyAndKyivTimeZone() {
        CronTrigger cleanup = config.cleanupNightlyTrigger(config.cleanupJobDetail());
        CronTrigger all = config.reportAllTrigger(config.reportJobDetail());
        CronTrigger poland = config.reportPolandTrigger(config.reportJobDetail());

        assertEquals(CronTrigger.MISFIRE_INSTRUCTION_DO_NOTHING, cleanup.getMisfireInstruction());
        assertEquals(CronTrigger.MISFIRE_INSTRUCTION_FIRE_ONCE_NOW, all.getMisfireInstruction());
        assertEquals(CronTrigger.MISFIRE_INSTRUCTION_DO_NOTHING, poland.getMisfireInstruction());
        for (CronTrigger trigger : List.of(cleanup, all, poland)) {
            assertEquals("Europe/Kyiv", trigger.getTimeZone().getID());
            assertEquals(QuartzConfig.ZONE, trigger.getTimeZone().toZoneId());
        }
        assertEquals(QuartzConfig.ZONE, config.maintenanceClock().getZone());
    }

    @Test
    void quartzMergedData_triggerOverridesCountryAndRetainsDefaultHorizon() throws Exception {
        JobDetail job = config.reportJobDetail();
        JobExecutionContext context = context(job, config.reportPolandTrigger(job));

        new DailyTourReportJob(service).execute(context);

        assertEquals("ALL", job.getJobDataMap().getString("country"));
        assertEquals("Poland", context.getMergedJobDataMap().getString("country"));
        assertEquals("30", context.getMergedJobDataMap().getString("horizonDays"));
        verify(service).createDailyReport("Poland", 30);
        verifyNoMoreInteractions(service);
    }

    @Test
    void cleanup_defaultParameters_delegatesOnceToTheService() throws Exception {
        JobDetail job = config.cleanupJobDetail();

        new ExpiredFeaturedCleanupJob(service).execute(context(job, config.cleanupNightlyTrigger(job)));

        verify(service).clearExpiredFeatured(0);
        verifyNoMoreInteractions(service);
    }

    @Test
    void misspelledParameter_failsExplicitlyWithoutCallingBusinessLogic() {
        JobDetail job = newJob(DailyTourReportJob.class).withIdentity("bad-report", QuartzConfig.GROUP)
                .usingJobData("country", "ALL").usingJobData("horizonDay", "30").build();
        Trigger trigger = newTrigger().withIdentity("bad-report-trigger", QuartzConfig.GROUP).forJob(job).build();

        JobExecutionException error = assertThrows(JobExecutionException.class,
                () -> new DailyTourReportJob(service).execute(context(job, trigger)));

        assertInstanceOf(IllegalArgumentException.class, error.getCause());
        assertTrue(error.getCause().getMessage().contains("horizonDays"));
        assertFalse(error.refireImmediately());
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"-1", "abc", "9999999999999"})
    void invalidNumericParameter_failsWithoutCallingService(String value) {
        JobDetail job = config.cleanupJobDetail();
        Trigger trigger = newTrigger().withIdentity("invalid-number", QuartzConfig.GROUP).forJob(job)
                .usingJobData("graceDays", value).build();

        JobExecutionException error = assertThrows(JobExecutionException.class,
                () -> new ExpiredFeaturedCleanupJob(service).execute(context(job, trigger)));

        assertTrue(error.getCause().getMessage().contains("nonnegative integer"));
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void blankCountry_failsExplicitly(String value) {
        JobDetail job = config.reportJobDetail();
        Trigger trigger = newTrigger().withIdentity("blank-country", QuartzConfig.GROUP).forJob(job)
                .usingJobData("country", value).build();

        JobExecutionException error = assertThrows(JobExecutionException.class,
                () -> new DailyTourReportJob(service).execute(context(job, trigger)));

        assertTrue(error.getCause().getMessage().contains("country"));
        verifyNoInteractions(service);
    }

    @Test
    void nonStringParameter_hasAnExplicitContractError() {
        JobDataMap data = new JobDataMap();
        data.put("graceDays", 1);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> JobParameters.nonNegativeInt(data, "graceDays"));

        assertTrue(error.getMessage().contains("nonblank string"));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void serviceFailure_isWrappedWithoutRefireOrUnscheduling(boolean cleanup) {
        IllegalStateException failure = new IllegalStateException("database unavailable");
        JobDetail detail = cleanup ? config.cleanupJobDetail() : config.reportJobDetail();
        Trigger trigger = cleanup ? config.cleanupNightlyTrigger(detail) : config.reportAllTrigger(detail);
        Job job;
        if (cleanup) {
            doThrow(failure).when(service).clearExpiredFeatured(0);
            job = new ExpiredFeaturedCleanupJob(service);
        } else {
            doThrow(failure).when(service).createDailyReport("ALL", 30);
            job = new DailyTourReportJob(service);
        }

        JobExecutionException error = assertThrows(JobExecutionException.class,
                () -> job.execute(context(detail, trigger)));

        assertSame(failure, error.getCause());
        assertFalse(error.refireImmediately());
        assertFalse(error.unscheduleAllTriggers());
        assertFalse(error.unscheduleFiringTrigger());
        if (cleanup) {
            verify(service, times(1)).clearExpiredFeatured(0);
        } else {
            verify(service, times(1)).createDailyReport("ALL", 30);
        }
        verifyNoMoreInteractions(service);
    }

    private static JobExecutionContext context(JobDetail detail, Trigger trigger) {
        TriggerFiredBundle bundle = new TriggerFiredBundle(detail, (OperableTrigger) trigger,
                null, false, new Date(), new Date(), null, null);
        return new JobExecutionContextImpl(mock(Scheduler.class), bundle, mock(Job.class));
    }
}
