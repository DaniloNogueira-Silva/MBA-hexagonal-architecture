package br.com.fullcycle.infrastructure.repositories;

import br.com.fullcycle.IntegrationTest;
import br.com.fullcycle.domain.event.Event;
import br.com.fullcycle.domain.event.EventRepository;
import br.com.fullcycle.domain.event.EventStatus;
import br.com.fullcycle.domain.partner.Partner;
import br.com.fullcycle.domain.partner.PartnerRepository;
import br.com.fullcycle.infrastructure.jpa.repositories.OutboxJpaRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.stream.StreamSupport;

class EventDatabaseRepositoryIT extends IntegrationTest {

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private PartnerRepository partnerRepository;

    @Autowired
    private OutboxJpaRepository outboxJpaRepository;

    private Partner partner;

    @BeforeEach
    void setUp() {
        eventRepository.deleteAll();
        partnerRepository.deleteAll();
        outboxJpaRepository.deleteAll();

        partner = partnerRepository.create(Partner.newPartner("John Doe", "41.536.538/0001-00", "john.doe@gmail.com"));
    }

    @Test
    @DisplayName("Deve persistir um evento ativo e restaurá-lo com status ACTIVE")
    public void testPersistAndRestoreActiveEvent() {
        // given
        final var event = Event.newEvent("Disney on Ice", "2021-01-01", 100, partner);

        // when
        final var createdEvent = eventRepository.create(event);

        // then
        final var restored = eventRepository.eventOfId(createdEvent.eventId()).orElseThrow();
        Assertions.assertEquals(EventStatus.ACTIVE, restored.status());
        Assertions.assertFalse(restored.isCancelled());
    }

    @Test
    @DisplayName("Deve persistir um evento cancelado e gravar o EventCancelled na outbox")
    public void testPersistCancelledEventAndVerifyOutbox() {
        // given
        final var event = Event.newEvent("Disney on Ice", "2021-01-01", 100, partner);
        eventRepository.create(event);

        // when
        event.cancel();
        eventRepository.update(event);

        // then
        final var restored = eventRepository.eventOfId(event.eventId()).orElseThrow();
        Assertions.assertEquals(EventStatus.CANCELLED, restored.status());
        Assertions.assertTrue(restored.isCancelled());

        // Verifica que o evento de domínio EventCancelled foi gravado na outbox
        final var outboxList = outboxJpaRepository.findAll();
        final var eventCancelledOutbox = StreamSupport.stream(outboxList.spliterator(), false)
                .filter(it -> it.content().contains("event.cancelled"))
                .findFirst();

        Assertions.assertTrue(eventCancelledOutbox.isPresent());
        Assertions.assertTrue(eventCancelledOutbox.get().content().contains(event.eventId().value()));
    }
}
