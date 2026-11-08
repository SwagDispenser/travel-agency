package com.example.travelagency.booking;

import com.example.travelagency.booking.messaging.OutboxPublisher;
import com.example.travelagency.booking.messaging.OutboxRepository;
import com.example.travelagency.booking.messaging.RabbitBookingEventPublisher;
import com.example.travelagency.contract.BookingEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {"spring.datasource.url=jdbc:h2:mem:booking-tests;DB_CLOSE_DELAY=-1", "travel.messaging.enabled=false"})
class BookingServiceTest {
    @Autowired BookingService service;
    @Autowired OutboxRepository outbox;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;
    RabbitBookingEventPublisher sender;
    OutboxPublisher dispatcher;

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM booking_outbox");
        jdbc.update("DELETE FROM bookings");
        sender = mock(RabbitBookingEventPublisher.class);
        dispatcher = new OutboxPublisher(outbox, sender);
    }

    @Test
    void threeActionsProduceExactlyOneEventEach() throws Exception {
        BookingResponse created = service.create(new BookingRequest("Dmytro", 1, 2));
        assertThat(created.status()).isEqualTo(BookingEvent.Status.NEW);
        assertThat(service.confirm(created.id()).status()).isEqualTo(BookingEvent.Status.CONFIRMED);
        assertThat(service.cancel(created.id()).status()).isEqualTo(BookingEvent.Status.CANCELLED);
        var events = outbox.pending();
        assertThat(events).extracting(BookingEvent::type).containsExactly(
                BookingEvent.Type.CREATED, BookingEvent.Type.CONFIRMED, BookingEvent.Type.CANCELLED);
        assertThat(events).extracting(BookingEvent::eventId).doesNotHaveDuplicates();
        verifyNoInteractions(sender);

        dispatcher.publishPending();
        dispatcher.publishPending();

        for (var event : events) {
            verify(sender, times(1)).publish(event);
        }
        assertThat(outbox.pending()).isEmpty();
    }

    @Test
    void invalidTransitionDoesNotAddEvent() {
        BookingResponse created = service.create(new BookingRequest("Dmytro", 1, 2));
        service.cancel(created.id());
        assertThatThrownBy(() -> service.confirm(created.id())).isInstanceOf(ResponseStatusException.class);
        assertThat(outbox.pending()).hasSize(2);
        assertThat(service.findAll().get(0).status()).isEqualTo(BookingEvent.Status.CANCELLED);
    }

    @Test
    void rollbackRemovesBothBusinessRowAndOutboxRowAndNothingIsPublished() {
        new TransactionTemplate(transactionManager).executeWithoutResult(transaction -> {
            service.create(new BookingRequest("Rollback", 1, 2));
            assertThat(outbox.pending()).hasSize(1);
            transaction.setRollbackOnly();
        });

        assertThat(service.findAll()).isEmpty();
        assertThat(outbox.pending()).isEmpty();
        dispatcher.publishPending();
        verifyNoInteractions(sender);
    }

    @Test
    void publisherOnAnotherThreadCannotSeeUncommittedEvent() throws Exception {
        new TransactionTemplate(transactionManager).executeWithoutResult(transaction -> {
            service.create(new BookingRequest("After commit", 1, 1));
            assertThatCode(() -> CompletableFuture.runAsync(dispatcher::publishPending).get(3, TimeUnit.SECONDS))
                    .doesNotThrowAnyException();
            verifyNoInteractions(sender);
        });

        var committed = outbox.pending().get(0);
        dispatcher.publishPending();
        verify(sender).publish(committed);
        assertThat(outbox.pending()).isEmpty();
    }
}
