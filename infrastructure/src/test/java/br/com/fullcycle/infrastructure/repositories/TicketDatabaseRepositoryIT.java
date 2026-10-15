package br.com.fullcycle.infrastructure.repositories;

import br.com.fullcycle.IntegrationTest;
import br.com.fullcycle.domain.customer.CustomerId;
import br.com.fullcycle.domain.event.EventId;
import br.com.fullcycle.domain.event.ticket.Ticket;
import br.com.fullcycle.domain.event.ticket.TicketRepository;
import br.com.fullcycle.domain.event.ticket.TicketStatus;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class TicketDatabaseRepositoryIT extends IntegrationTest {

    @Autowired
    private TicketRepository ticketRepository;

    @BeforeEach
    void setUp() {
        ticketRepository.deleteAll();
    }

    @Test
    @DisplayName("Deve persistir e buscar tickets por eventId no banco de dados real")
    public void testTicketsByEventId() {
        // given
        final var targetEventId = EventId.unique();
        final var otherEventId = EventId.unique();

        final var ticket1 = Ticket.newTicket(CustomerId.unique(), targetEventId);
        final var ticket2 = Ticket.newTicket(CustomerId.unique(), targetEventId);
        final var otherTicket = Ticket.newTicket(CustomerId.unique(), otherEventId);

        ticketRepository.create(ticket1);
        ticketRepository.create(ticket2);
        ticketRepository.create(otherTicket);

        // when
        final var actualTickets = ticketRepository.ticketsByEventId(targetEventId);

        // then
        Assertions.assertEquals(2, actualTickets.size());
        final var ticketIds = actualTickets.stream().map(t -> t.ticketId().value()).toList();
        Assertions.assertTrue(ticketIds.contains(ticket1.ticketId().value()));
        Assertions.assertTrue(ticketIds.contains(ticket2.ticketId().value()));
    }

    @Test
    @DisplayName("Deve atualizar o status do ticket para CANCELLED no banco de dados")
    public void testUpdateTicketStatusToCancelled() {
        // given
        final var eventId = EventId.unique();
        final var ticket = Ticket.newTicket(CustomerId.unique(), eventId);
        ticketRepository.create(ticket);

        // when
        ticket.cancel();
        ticketRepository.update(ticket);

        // then
        final var updatedTicket = ticketRepository.ticketOfId(ticket.ticketId()).orElseThrow();
        Assertions.assertEquals(TicketStatus.CANCELLED, updatedTicket.status());
    }
}
