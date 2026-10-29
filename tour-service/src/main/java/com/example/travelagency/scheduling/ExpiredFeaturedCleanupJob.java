package com.example.travelagency.scheduling;

import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@DisallowConcurrentExecution
public class ExpiredFeaturedCleanupJob implements Job {
    private static final Logger log = LoggerFactory.getLogger(ExpiredFeaturedCleanupJob.class);
    private final TourMaintenanceService service;

    public ExpiredFeaturedCleanupJob(TourMaintenanceService service) {
        this.service = service;
    }

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        try {
            int graceDays = JobParameters.nonNegativeInt(context.getMergedJobDataMap(), "graceDays");
            service.clearExpiredFeatured(graceDays);
        } catch (RuntimeException exception) {
            log.error("Quartz job {} failed; waiting for the next scheduled run", context.getJobDetail().getKey(), exception);
            throw new JobExecutionException("Expired featured cleanup failed", exception, false);
        }
    }
}
