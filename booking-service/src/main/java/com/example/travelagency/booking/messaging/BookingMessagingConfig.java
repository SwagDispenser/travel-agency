package com.example.travelagency.booking.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "travel.messaging.enabled", havingValue = "true", matchIfMissing = true)
public class BookingMessagingConfig {
    public static final String EXCHANGE = "travel.booking.events";
    public static final String QUEUE = "travel.tour.booking-events";

    @Bean
    Declarables bookingTopology() {
        TopicExchange exchange = new TopicExchange(EXCHANGE, true, false);
        DirectExchange deadLetters = new DirectExchange(EXCHANGE + ".dead", true, false);
        Queue queue = QueueBuilder.durable(QUEUE).deadLetterExchange(deadLetters.getName())
                .deadLetterRoutingKey(QUEUE + ".failed").build();
        Queue failed = QueueBuilder.durable(QUEUE + ".failed").build();
        return new Declarables(exchange, queue, BindingBuilder.bind(queue).to(exchange).with("booking.*"),
                deadLetters, failed, BindingBuilder.bind(failed).to(deadLetters).with(failed.getName()));
    }

    @Bean
    Jackson2JsonMessageConverter bookingEventConverter(ObjectMapper mapper) {
        return new Jackson2JsonMessageConverter(mapper, "com.example.travelagency.contract");
    }
}
