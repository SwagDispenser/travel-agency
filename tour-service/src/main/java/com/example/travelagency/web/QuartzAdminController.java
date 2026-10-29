package com.example.travelagency.web;

import com.example.travelagency.config.QuartzConfig;
import com.example.travelagency.scheduling.TourMaintenanceService;
import org.quartz.JobDataMap;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.impl.matchers.GroupMatcher;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/quartz")
public class QuartzAdminController {
    private final Scheduler scheduler;
    private final TourMaintenanceService service;

    public QuartzAdminController(Scheduler scheduler, TourMaintenanceService service) {
        this.scheduler = scheduler;
        this.service = service;
    }

    @GetMapping
    public List<String> jobs() throws SchedulerException {
        return scheduler.getJobKeys(GroupMatcher.jobGroupEquals(QuartzConfig.GROUP))
                .stream().map(JobKey::toString).sorted().toList();
    }

    @PostMapping("/run/{name}")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, String> run(@PathVariable String name,
                                   @RequestParam(required = false) String country) throws SchedulerException {
        JobKey key = new JobKey(name, QuartzConfig.GROUP);
        if (!scheduler.checkExists(key)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Quartz job not found");
        }
        JobDataMap data = new JobDataMap();
        if (country != null) {
            data.put("country", country);
        }
        scheduler.triggerJob(key, data);
        return Map.of("status", "QUEUED", "job", key.toString());
    }

    @GetMapping("/reports")
    public List<Map<String, Object>> reports() {
        return service.reports();
    }
}
