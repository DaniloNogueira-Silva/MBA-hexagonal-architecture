package br.com.fullcycle.application.ticket;

import br.com.fullcycle.application.repository.InMemoryTicketRepository;
import br.com.fullcycle.domain.customer.CustomerId;
import br.com.fullcycle.domain.event.EventId;
import br.com.fullcycle.domain.event.ticket.Ticket;
import br.com.fullcycle.domain.event.ticket.TicketStatus;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

class CancelEventTicketsUseCaseTest {

    @Test
    @DisplayName("Deve cancelar todos os tickets de um determinado evento")
    public void testCancelAllTicketsForAnEvent() {
        // given
        final var targetEventId = EventId.unique();
        final var otherEventId = EventId.unique();

        final var ticket1 = Ticket.newTicket(CustomerId.unique(), targetEventId);
        final var ticket2 = Ticket.newTicket(CustomerId.unique(), targetEventId);
        final var otherTicket = Ticket.newTicket(CustomerId.unique(), otherEventId);

        final var ticketRepository = new InMemoryTicketRepository();
        ticketRepository.create(ticket1);
        ticketRepository.create(ticket2);
        ticketRepository.create(otherTicket);

        final var useCase = new CancelEventTicketsUseCase(ticketRepository);
        final var input = new CancelEventTicketsUseCase.Input(targetEventId.value());

        // when
        final var output = useCase.execute(input);

        // then
        Assertions.assertEquals(targetEventId.value(), output.eventId());
        Assertions.assertEquals(2, output.ticketIds().size());
        Assertions.assertTrue(output.ticketIds().contains(ticket1.ticketId().value()));
        Assertions.assertTrue(output.ticketIds().contains(ticket2.ticketId().value()));

        final var updatedTicket1 = ticketRepository.ticketOfId(ticket1.ticketId()).orElseThrow();
        final var updatedTicket2 = ticketRepository.ticketOfId(ticket2.ticketId()).orElseThrow();
        final var updatedOtherTicket = ticketRepository.ticketOfId(otherTicket.ticketId()).orElseThrow();

        Assertions.assertEquals(TicketStatus.CANCELLED, updatedTicket1.status());
        Assertions.assertEquals(TicketStatus.CANCELLED, updatedTicket2.status());
        Assertions.assertEquals(TicketStatus.PENDING, updatedOtherTicket.status());
    }

    @Test
    @DisplayName("Reprocessar o cancelamento de tickets deve ser idempotente")
    public void testCancelTicketsIdempotence() {
        // given
        final var eventId = EventId.unique();
        final var ticket = Ticket.newTicket(CustomerId.unique(), eventId);

        final var ticketRepository = new InMemoryTicketRepository();
        ticketRepository.create(ticket);

        final var useCase = new CancelEventTicketsUseCase(ticketRepository);
        final var input = new CancelEventTicketsUseCase.Input(eventId.value());

        // when
        useCase.execute(input);
        final var output = Assertions.assertDoesNotThrow(() -> useCase.execute(input));

        // then
        Assertions.assertEquals(1, output.ticketIds().size());
        final var updatedTicket = ticketRepository.ticketOfId(ticket.ticketId()).orElseThrow();
        Assertions.assertEquals(TicketStatus.CANCELLED, updatedTicket.status());
    }

    @Test
    @DisplayName("Deve lidar graciosamente com evento que não possui tickets")
    public void testCancelEventWithoutTickets() {
        // given
        final var eventId = EventId.unique();
        final var ticketRepository = new InMemoryTicketRepository();

        final var useCase = new CancelEventTicketsUseCase(ticketRepository);
        final var input = new CancelEventTicketsUseCase.Input(eventId.value());

        // when
        final var output = useCase.execute(input);

        // then
        Assertions.assertEquals(eventId.value(), output.eventId());
        Assertions.assertTrue(output.ticketIds().isEmpty());
    }
}
