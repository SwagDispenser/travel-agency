package com.example.travelagency.booking;

import com.example.travelagency.booking.messaging.*;
import com.example.travelagency.contract.BookingEvent;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
import static org.mockito.Mockito.*;

class OutboxPublisherTest {
    @Test
    void failureRetainsEventForRetryAndDoesNotOvertakeIt() throws Exception {
        var outbox = mock(OutboxRepository.class);
        var sender = mock(RabbitBookingEventPublisher.class);
        var first = event("first");
        var second = event("second");
        when(outbox.pending()).thenReturn(List.of(first, second));
        doThrow(new IllegalStateException("Broker unavailable")).when(sender).publish(first);

        new OutboxPublisher(outbox, sender).publishPending();

        verify(outbox, never()).markPublished(anyString());
        verify(sender, never()).publish(second);
    }

    @Test
    void retryUsesTheSameEventIdAndMarksPublishedOnlyAfterSuccess() throws Exception {
        var outbox = mock(OutboxRepository.class);
        var sender = mock(RabbitBookingEventPublisher.class);
        var event = event("stable-event-id");
        when(outbox.pending()).thenReturn(List.of(event));
        doThrow(new IllegalStateException("No confirm")).doNothing().when(sender).publish(event);
        var publisher = new OutboxPublisher(outbox, sender);

        publisher.publishPending();
        verify(outbox, never()).markPublished(anyString());
        publisher.publishPending();

        verify(sender, times(2)).publish(event);
        verify(outbox, times(1)).markPublished(event.eventId());
    }

    private BookingEvent event(String id) {
        return new BookingEvent(id, BookingEvent.Type.CREATED, 1, "Dmytro", 1, 2,
                BookingEvent.Status.NEW, Instant.parse("2026-11-05T10:00:00Z"));
    }
}
