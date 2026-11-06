package com.example.travelagency.messaging;

import com.example.travelagency.contract.BookingEvent;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.Instant;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BookingEventInboxTest {
    private final BookingEvent event = new BookingEvent("unit-event", BookingEvent.Type.CONFIRMED,
            1, "Dmytro", 2, 3, BookingEvent.Status.CONFIRMED, Instant.parse("2026-11-05T10:00:00Z"));

    @Test
    void sameEventTwiceCausesOnlyOneWrite_unitTestForAcceptanceCriterion5() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(event.eventId()))).thenReturn(0, 1);
        BookingEventInbox inbox = new BookingEventInbox(jdbc);

        assertThat(inbox.record(event)).isTrue();
        assertThat(inbox.record(event)).isFalse();

        verify(jdbc, times(1)).update(anyString(), any(Object[].class));
    }

    @Test
    void concurrentDuplicateIsIgnoredByDatabasePrimaryKey() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(event.eventId()))).thenReturn(0);
        when(jdbc.update(anyString(), any(Object[].class))).thenThrow(new DuplicateKeyException("event_id"));
        assertThat(new BookingEventInbox(jdbc).record(event)).isFalse();
    }
}
