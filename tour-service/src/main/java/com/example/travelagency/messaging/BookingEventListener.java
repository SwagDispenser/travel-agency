package com.example.travelagency.messaging;

import com.example.travelagency.contract.BookingEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "travel.messaging.enabled", havingValue = "true", matchIfMissing = true)
public class BookingEventListener {
    private static final Logger log = LoggerFactory.getLogger(BookingEventListener.class);
    private final BookingEventInbox inbox;

    public BookingEventListener(BookingEventInbox inbox) {
        this.inbox = inbox;
    }

    @RabbitListener(queues = BookingMessagingConfig.QUEUE)
    public void receive(BookingEvent event) {
        boolean added = inbox.record(event);
        log.info("{} {} event {} for booking {}", added ? "Stored" : "Ignored duplicate", event.type(), event.eventId(), event.bookingId());
        // AUTO acknowledgement happens after the proxied transaction has committed.
    }
}
