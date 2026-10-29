package com.example.travelagency.scheduling;

import com.example.travelagency.config.QuartzConfig;
import com.example.travelagency.web.QuartzAdminController;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.quartz.JobDataMap;
import org.quartz.Scheduler;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QuartzAdminControllerTest {
    @Mock Scheduler scheduler;
    @Mock TourMaintenanceService service;
    @InjectMocks QuartzAdminController controller;

    @Test
    void run_existingReport_submitsToQuartzWithCountryOverride() throws Exception {
        when(scheduler.checkExists(QuartzConfig.REPORT_JOB)).thenReturn(true);

        Map<String, String> response = controller.run("daily-tour-report", "Poland");

        assertEquals("QUEUED", response.get("status"));
        ArgumentCaptor<JobDataMap> data = ArgumentCaptor.forClass(JobDataMap.class);
        verify(scheduler).triggerJob(eq(QuartzConfig.REPORT_JOB), data.capture());
        assertEquals("Poland", data.getValue().getString("country"));
        assertFalse(data.getValue().containsKey("horizonDays"));
        verifyNoInteractions(service);
    }

    @Test
    void run_cleanupWithoutOverrides_preservesJobDefaults() throws Exception {
        when(scheduler.checkExists(QuartzConfig.CLEANUP_JOB)).thenReturn(true);

        controller.run("expired-featured-cleanup", null);

        ArgumentCaptor<JobDataMap> data = ArgumentCaptor.forClass(JobDataMap.class);
        verify(scheduler).triggerJob(eq(QuartzConfig.CLEANUP_JOB), data.capture());
        assertTrue(data.getValue().isEmpty());
    }

    @Test
    void run_unknownJob_returns404WithoutSchedulingAnything() throws Exception {
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> controller.run("unknown-job", null));

        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
        verify(scheduler, never()).triggerJob(any(), any(JobDataMap.class));
    }

    @Test
    void reports_returnsServiceSnapshots() {
        List<Map<String, Object>> snapshots = List.of(Map.of("country", "Poland", "tour_count", 2));
        when(service.reports()).thenReturn(snapshots);

        assertSame(snapshots, controller.reports());
        verifyNoInteractions(scheduler);
    }
}
