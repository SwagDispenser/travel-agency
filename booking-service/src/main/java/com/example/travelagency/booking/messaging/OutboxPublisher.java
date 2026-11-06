package com.example.travelagency.booking.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "travel.messaging.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxPublisher {
    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);
    private final OutboxRepository outbox;
    private final RabbitBookingEventPublisher sender;

    public OutboxPublisher(OutboxRepository outbox, RabbitBookingEventPublisher sender) {
        this.outbox = outbox;
        this.sender = sender;
    }

    @Scheduled(fixedDelayString = "${travel.messaging.publish-delay-ms:1000}")
    public void publishPending() {
        // This thread sees only committed outbox rows (the database uses READ_COMMITTED).
        for (var event : outbox.pending()) {
            try {
                sender.publish(event);
                outbox.markPublished(event.eventId());
                log.info("Published {} event {} for booking {}", event.type(), event.eventId(), event.bookingId());
            } catch (Exception failure) {
                if (failure instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                log.warn("Event {} stays in outbox for retry: {}", event.eventId(), failure.getMessage());
                return; // Preserve event order; try again on the next poll.
            }
        }
    }
}
