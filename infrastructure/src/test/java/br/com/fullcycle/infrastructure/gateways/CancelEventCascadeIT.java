package br.com.fullcycle.infrastructure.gateways;

import br.com.fullcycle.IntegrationTest;
import br.com.fullcycle.application.event.CancelEventUseCase;
import br.com.fullcycle.domain.customer.Customer;
import br.com.fullcycle.domain.customer.CustomerId;
import br.com.fullcycle.domain.customer.CustomerRepository;
import br.com.fullcycle.domain.event.Event;
import br.com.fullcycle.domain.event.EventCancelled;
import br.com.fullcycle.domain.event.EventId;
import br.com.fullcycle.domain.event.EventRepository;
import br.com.fullcycle.domain.event.ticket.Ticket;
import br.com.fullcycle.domain.event.ticket.TicketRepository;
import br.com.fullcycle.domain.event.ticket.TicketStatus;
import br.com.fullcycle.domain.partner.Partner;
import br.com.fullcycle.domain.partner.PartnerRepository;
import br.com.fullcycle.infrastructure.job.OutboxRelay;
import br.com.fullcycle.infrastructure.jpa.repositories.OutboxJpaRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

public class CancelEventCascadeIT extends IntegrationTest {

    @Autowired
    private ConsumerQueueGateway consumerQueueGateway;

    @Autowired
    private OutboxRelay outboxRelay;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private PartnerRepository partnerRepository;

    @Autowired
    private OutboxJpaRepository outboxJpaRepository;

    @Autowired
    private CancelEventUseCase cancelEventUseCase;

    @Autowired
    private ObjectMapper mapper;

    private Partner partner;
    private Customer customer1;
    private Customer customer2;

    @BeforeEach
    void setUp() {
        ticketRepository.deleteAll();
        eventRepository.deleteAll();
        customerRepository.deleteAll();
        partnerRepository.deleteAll();
        outboxJpaRepository.deleteAll();

        partner = partnerRepository.create(Partner.newPartner("Disney", "45.123.123/0001-12", "disney@gmail.com"));
        customer1 = customerRepository.create(Customer.newCustomer("John Doe", "123.456.789-01", "john@gmail.com"));
        customer2 = customerRepository.create(Customer.newCustomer("Jane Doe", "987.654.321-02", "jane@gmail.com"));
    }

    @Test
    @DisplayName("Deve rotear EventCancelled via ConsumerQueueGateway e cancelar todos os ingressos do evento de forma assíncrona e idempotente")
    public void testCancelEventCascadeViaConsumerQueueGateway() throws Exception {
        // given: cria evento alvo e evento diferente
        final var targetEvent = eventRepository.create(Event.newEvent("Disney on Ice", "2021-01-01", 100, partner));
        final var otherEvent = eventRepository.create(Event.newEvent("Cirque du Soleil", "2021-02-01", 50, partner));

        // cria 2 ingressos para o evento alvo e 1 ingresso para o outro evento
        final var ticket1 = ticketRepository.create(Ticket.newTicket(customer1.customerId(), targetEvent.eventId()));
        final var ticket2 = ticketRepository.create(Ticket.newTicket(customer2.customerId(), targetEvent.eventId()));
        final var otherTicket = ticketRepository.create(Ticket.newTicket(customer1.customerId(), otherEvent.eventId()));

        Assertions.assertEquals(TicketStatus.PENDING, ticket1.status());
        Assertions.assertEquals(TicketStatus.PENDING, ticket2.status());
        Assertions.assertEquals(TicketStatus.PENDING, otherTicket.status());

        // when: dispara EventCancelled publicando o JSON no ConsumerQueueGateway (caminho real da fila)
        final var eventCancelled = new EventCancelled(targetEvent.eventId());
        final var jsonContent = mapper.writeValueAsString(eventCancelled);

        consumerQueueGateway.publish(jsonContent);

        // then: aguarda e comprova que os ingressos do evento alvo terminam CANCELLED e o outro não
        waitUntilAsserted(() -> {
            final var tickets = ticketRepository.ticketsByEventId(targetEvent.eventId());
            Assertions.assertEquals(2, tickets.size());
            Assertions.assertTrue(tickets.stream().allMatch(t -> t.status() == TicketStatus.CANCELLED));

            final var untouchedTicket = ticketRepository.ticketOfId(otherTicket.ticketId()).orElseThrow();
            Assertions.assertEquals(TicketStatus.PENDING, untouchedTicket.status());
        });

        // reprocessa o evento para comprovar idempotência
        consumerQueueGateway.publish(jsonContent);

        waitUntilAsserted(() -> {
            final var tickets = ticketRepository.ticketsByEventId(targetEvent.eventId());
            Assertions.assertEquals(2, tickets.size());
            Assertions.assertTrue(tickets.stream().allMatch(t -> t.status() == TicketStatus.CANCELLED));
        });
    }

    @Test
    @DisplayName("Deve executar o fluxo completo: cancelamento grava outbox -> relay publica -> ConsumerQueueGateway roteia -> ingressos cancelados")
    public void testCancelEventCascadeViaOutboxRelayEndToEnd() throws Exception {
        // given
        final var event = eventRepository.create(Event.newEvent("Disney on Ice", "2021-01-01", 100, partner));
        final var ticket = ticketRepository.create(Ticket.newTicket(customer1.customerId(), event.eventId()));
        Assertions.assertEquals(TicketStatus.PENDING, ticket.status());

        // when: cancela o evento pelo caso de uso (grava EventCancelled na outbox do banco)
        cancelEventUseCase.execute(new CancelEventUseCase.Input(event.eventId().value()));

        // executa o relay para ler a outbox e publicar na fila
        outboxRelay.execute();

        // then: comprova que o ingresso termina CANCELLED
        waitUntilAsserted(() -> {
            final var updatedTicket = ticketRepository.ticketOfId(ticket.ticketId()).orElseThrow();
            Assertions.assertEquals(TicketStatus.CANCELLED, updatedTicket.status());
        });
    }

    private void waitUntilAsserted(final Runnable assertion) throws InterruptedException {
        final long deadline = System.currentTimeMillis() + 5000;
        AssertionError lastError = null;
        while (System.currentTimeMillis() < deadline) {
            try {
                assertion.run();
                return;
            } catch (AssertionError e) {
                lastError = e;
                Thread.sleep(100);
            }
        }
        if (lastError != null) {
            throw lastError;
        }
    }
}
