package com.example.travelagency.messaging;

import com.example.travelagency.contract.BookingEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.Instant;
import static org.assertj.core.api.Assertions.assertThat;

@JdbcTest
@Import(BookingEventInbox.class)
class BookingEventPersistenceTest {
    @Autowired BookingEventInbox inbox;
    @Autowired JdbcTemplate jdbc;

    @Test
    void duplicateAfterRecreatingConsumerLeavesExactlyTheSamePersistedResult() {
        var event = new BookingEvent("persistent-event", BookingEvent.Type.CREATED, 1, "Dmytro", 2, 3,
                BookingEvent.Status.NEW, Instant.parse("2026-11-05T10:00:00Z"));
        assertThat(inbox.record(event)).isTrue();
        var before = inbox.findAll();

        assertThat(new BookingEventInbox(jdbc).record(event)).isFalse();

        assertThat(inbox.findAll()).isEqualTo(before).hasSize(1);
        assertThat(before.get(0).event()).isEqualTo(event);
    }
}
