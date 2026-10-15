package br.com.fullcycle.application.event;

import br.com.fullcycle.application.repository.InMemoryEventRepository;
import br.com.fullcycle.domain.event.Event;
import br.com.fullcycle.domain.event.EventId;
import br.com.fullcycle.domain.event.EventStatus;
import br.com.fullcycle.domain.exceptions.ValidationException;
import br.com.fullcycle.domain.partner.Partner;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CancelEventUseCaseTest {

    @Test
    @DisplayName("Deve cancelar um evento ativo com sucesso")
    public void testCancelActiveEvent() {
        // given
        final var aPartner = Partner.newPartner("John Doe", "41.536.538/0001-00", "john.doe@gmail.com");
        final var anEvent = Event.newEvent("Disney on Ice", "2021-01-01", 10, aPartner);

        final var eventRepository = new InMemoryEventRepository();
        eventRepository.create(anEvent);

        final var useCase = new CancelEventUseCase(eventRepository);
        final var input = new CancelEventUseCase.Input(anEvent.eventId().value());

        // when
        final var output = useCase.execute(input);

        // then
        Assertions.assertEquals(anEvent.eventId().value(), output.id());
        Assertions.assertEquals(EventStatus.CANCELLED.name(), output.status());

        final var persistedEvent = eventRepository.eventOfId(anEvent.eventId()).orElseThrow();
        Assertions.assertEquals(EventStatus.CANCELLED, persistedEvent.status());
    }

    @Test
    @DisplayName("Não deve cancelar um evento inexistente")
    public void testCancelNonExistentEventShouldThrow() {
        // given
        final var eventRepository = new InMemoryEventRepository();
        final var useCase = new CancelEventUseCase(eventRepository);
        final var nonExistentId = EventId.unique().value();
        final var input = new CancelEventUseCase.Input(nonExistentId);

        // when
        final var actualException = Assertions.assertThrows(
                ValidationException.class,
                () -> useCase.execute(input)
        );

        // then
        Assertions.assertEquals("Event not found", actualException.getMessage());
    }

    @Test
    @DisplayName("Não deve cancelar um evento já cancelado")
    public void testCancelAlreadyCancelledEventShouldThrow() {
        // given
        final var aPartner = Partner.newPartner("John Doe", "41.536.538/0001-00", "john.doe@gmail.com");
        final var anEvent = Event.newEvent("Disney on Ice", "2021-01-01", 10, aPartner);
        anEvent.cancel();

        final var eventRepository = new InMemoryEventRepository();
        eventRepository.create(anEvent);

        final var useCase = new CancelEventUseCase(eventRepository);
        final var input = new CancelEventUseCase.Input(anEvent.eventId().value());

        // when
        final var actualException = Assertions.assertThrows(
                ValidationException.class,
                () -> useCase.execute(input)
        );

        // then
        Assertions.assertEquals("Event already cancelled", actualException.getMessage());
    }
}
