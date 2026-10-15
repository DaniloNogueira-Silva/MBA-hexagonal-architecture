package br.com.fullcycle.application.event;

import br.com.fullcycle.application.repository.InMemoryEventRepository;
import br.com.fullcycle.domain.event.Event;
import br.com.fullcycle.domain.event.EventId;
import br.com.fullcycle.domain.event.EventStatus;
import br.com.fullcycle.domain.partner.Partner;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GetEventByIdUseCaseTest {

    @Test
    @DisplayName("Deve obter um evento por id")
    public void testGetEventById() {
        // given
        final var aPartner = Partner.newPartner("John Doe", "41.536.538/0001-00", "john.doe@gmail.com");
        final var expectedDate = "2021-01-01";
        final var expectedName = "Disney on Ice";
        final var expectedTotalSpots = 100;

        final var anEvent = Event.newEvent(expectedName, expectedDate, expectedTotalSpots, aPartner);

        final var eventRepository = new InMemoryEventRepository();
        eventRepository.create(anEvent);

        final var useCase = new GetEventByIdUseCase(eventRepository);
        final var input = new GetEventByIdUseCase.Input(anEvent.eventId().value());

        // when
        final var output = useCase.execute(input).orElseThrow();

        // then
        Assertions.assertEquals(anEvent.eventId().value(), output.id());
        Assertions.assertEquals(expectedName, output.name());
        Assertions.assertEquals(expectedDate, output.date());
        Assertions.assertEquals(expectedTotalSpots, output.totalSpots());
        Assertions.assertEquals(EventStatus.ACTIVE.name(), output.status());
    }

    @Test
    @DisplayName("Deve obter um evento cancelado com status CANCELLED")
    public void testGetCancelledEventById() {
        // given
        final var aPartner = Partner.newPartner("John Doe", "41.536.538/0001-00", "john.doe@gmail.com");
        final var anEvent = Event.newEvent("Disney on Ice", "2021-01-01", 100, aPartner);
        anEvent.cancel();

        final var eventRepository = new InMemoryEventRepository();
        eventRepository.create(anEvent);

        final var useCase = new GetEventByIdUseCase(eventRepository);
        final var input = new GetEventByIdUseCase.Input(anEvent.eventId().value());

        // when
        final var output = useCase.execute(input).orElseThrow();

        // then
        Assertions.assertEquals(anEvent.eventId().value(), output.id());
        Assertions.assertEquals(EventStatus.CANCELLED.name(), output.status());
    }

    @Test
    @DisplayName("Deve retornar vazio ao buscar evento inexistente")
    public void testGetNonExistentEventById() {
        // given
        final var eventRepository = new InMemoryEventRepository();
        final var useCase = new GetEventByIdUseCase(eventRepository);
        final var input = new GetEventByIdUseCase.Input(EventId.unique().value());

        // when
        final var output = useCase.execute(input);

        // then
        Assertions.assertTrue(output.isEmpty());
    }
}
