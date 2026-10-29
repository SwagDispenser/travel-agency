package com.example.travelagency.scheduling;

import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@DisallowConcurrentExecution
public class DailyTourReportJob implements Job {
    private static final Logger log = LoggerFactory.getLogger(DailyTourReportJob.class);
    private final TourMaintenanceService service;

    public DailyTourReportJob(TourMaintenanceService service) {
        this.service = service;
    }

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        try {
            JobDataMap data = context.getMergedJobDataMap();
            service.createDailyReport(JobParameters.requiredText(data, "country"),
                    JobParameters.nonNegativeInt(data, "horizonDays"));
        } catch (RuntimeException exception) {
            log.error("Quartz job {} failed; waiting for the next scheduled run", context.getJobDetail().getKey(), exception);
            throw new JobExecutionException("Daily tour report failed", exception, false);
        }
    }
}
