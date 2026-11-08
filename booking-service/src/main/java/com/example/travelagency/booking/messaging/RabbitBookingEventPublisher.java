package com.example.travelagency.booking.messaging;

import com.example.travelagency.contract.BookingEvent;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

@Component
public class RabbitBookingEventPublisher {
    private final RabbitTemplate rabbit;

    public RabbitBookingEventPublisher(RabbitTemplate rabbit) {
        this.rabbit = rabbit;
    }

    public void publish(BookingEvent event) throws Exception {
        CorrelationData correlation = new CorrelationData(event.eventId());
        rabbit.convertAndSend(BookingMessagingConfig.EXCHANGE, "booking." + event.type().name().toLowerCase(Locale.ROOT),
                event, message -> {
                    message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                    message.getMessageProperties().setMessageId(event.eventId());
                    return message;
                }, correlation);
        var confirm = correlation.getFuture().get(5, TimeUnit.SECONDS);
        if (!confirm.isAck() || correlation.getReturned() != null) {
            throw new IllegalStateException("RabbitMQ did not route and confirm event " + event.eventId());
        }
    }
}
