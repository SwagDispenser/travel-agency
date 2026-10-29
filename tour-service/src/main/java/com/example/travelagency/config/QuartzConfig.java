package com.example.travelagency.config;

import com.example.travelagency.scheduling.DailyTourReportJob;
import com.example.travelagency.scheduling.ExpiredFeaturedCleanupJob;
import org.quartz.CronTrigger;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;
import java.util.TimeZone;

import static org.quartz.CronScheduleBuilder.cronSchedule;
import static org.quartz.JobBuilder.newJob;
import static org.quartz.TriggerBuilder.newTrigger;

@Configuration(proxyBeanMethods = false)
public class QuartzConfig {
    public static final String GROUP = "travel-maintenance";
    public static final ZoneId ZONE = ZoneId.of("Europe/Kyiv");
    public static final JobKey CLEANUP_JOB = new JobKey("expired-featured-cleanup", GROUP);
    public static final JobKey REPORT_JOB = new JobKey("daily-tour-report", GROUP);

    @Bean
    public Clock maintenanceClock() {
        return Clock.system(ZONE);
    }

    @Bean
    public JobDetail cleanupJobDetail() {
        return newJob(ExpiredFeaturedCleanupJob.class).withIdentity(CLEANUP_JOB)
                .usingJobData("graceDays", "0").storeDurably().build();
    }

    @Bean
    public JobDetail reportJobDetail() {
        return newJob(DailyTourReportJob.class).withIdentity(REPORT_JOB)
                .usingJobData("country", "ALL").usingJobData("horizonDays", "30")
                .storeDurably().build();
    }

    @Bean
    public CronTrigger cleanupNightlyTrigger(@Qualifier("cleanupJobDetail") JobDetail job) {
        return newTrigger().withIdentity("cleanup-nightly", GROUP).forJob(job.getKey())
                .withSchedule(cronSchedule("0 0 2 * * ?").inTimeZone(TimeZone.getTimeZone(ZONE))
                        .withMisfireHandlingInstructionDoNothing()).build(); //misfire
    }

    @Bean
    public CronTrigger reportAllTrigger(@Qualifier("reportJobDetail") JobDetail job) {
        return newTrigger().withIdentity("report-all-morning", GROUP).forJob(job.getKey())
                .withSchedule(cronSchedule("0 0 8 * * ?").inTimeZone(TimeZone.getTimeZone(ZONE))
                        .withMisfireHandlingInstructionFireAndProceed()).build();
    }

    @Bean
    public CronTrigger reportPolandTrigger(@Qualifier("reportJobDetail") JobDetail job) {
        return newTrigger().withIdentity("report-poland-evening", GROUP).forJob(job.getKey())
                .usingJobData("country", "Poland")
                .withSchedule(cronSchedule("0 0 18 * * ?").inTimeZone(TimeZone.getTimeZone(ZONE))
                        .withMisfireHandlingInstructionDoNothing()).build();
    }
}
