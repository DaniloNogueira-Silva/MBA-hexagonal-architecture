package br.com.fullcycle.infrastructure.graphql;

import br.com.fullcycle.domain.event.EventId;
import br.com.fullcycle.domain.event.EventRepository;
import br.com.fullcycle.domain.partner.Partner;
import br.com.fullcycle.domain.partner.PartnerRepository;
import br.com.fullcycle.infrastructure.dtos.NewEventDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.graphql.tester.AutoConfigureGraphQlTester;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.graphql.test.tester.GraphQlTester;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

@ActiveProfiles("test")
@AutoConfigureGraphQlTester
@SpringBootTest
public class EventResolverTest {

    @Autowired
    private GraphQlTester tester;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private PartnerRepository partnerRepository;

    private Partner disney;

    @BeforeEach
    void setUp() {
        eventRepository.deleteAll();
        partnerRepository.deleteAll();

        disney = partnerRepository.create(Partner.newPartner("Disney", "45.123.123/0001-12", "disney@gmail.com"));
    }

    @Test
    @DisplayName("Deve cancelar um evento via GraphQL mutation cancelEvent")
    public void testCancelEventMutation() {
        // Cria um evento primeiro
        final var createEventMutation = """
                mutation CreateEvent($input: EventInput) {
                    createEvent(input: $input) {
                        id
                        name
                        date
                        totalSpots
                        status
                    }
                }
                """;

        final var input = Map.of(
                "name", "Disney on Ice",
                "date", "2021-01-01",
                "totalSpots", 100,
                "partnerId", disney.partnerId().value()
        );

        final var eventId = this.tester.document(createEventMutation)
                .variable("input", input)
                .execute()
                .path("createEvent.id")
                .entity(String.class)
                .get();

        // Cancela o evento
        final var cancelEventMutation = """
                mutation CancelEvent($id: ID!) {
                    cancelEvent(id: $id) {
                        id
                        status
                    }
                }
                """;

        this.tester.document(cancelEventMutation)
                .variable("id", eventId)
                .execute()
                .path("cancelEvent.id").entity(String.class).isEqualTo(eventId)
                .path("cancelEvent.status").entity(String.class).isEqualTo("CANCELLED");
    }

    @Test
    @DisplayName("Deve consultar um evento por id via GraphQL query eventOfId")
    public void testQueryEventOfId() {
        final var createEventMutation = """
                mutation CreateEvent($input: EventInput) {
                    createEvent(input: $input) {
                        id
                        name
                        date
                        totalSpots
                    }
                }
                """;

        final var input = Map.of(
                "name", "Disney on Ice",
                "date", "2021-01-01",
                "totalSpots", 100,
                "partnerId", disney.partnerId().value()
        );

        final var eventId = this.tester.document(createEventMutation)
                .variable("input", input)
                .execute()
                .path("createEvent.id")
                .entity(String.class)
                .get();

        final var eventOfIdQuery = """
                query EventOfId($id: ID!) {
                    eventOfId(id: $id) {
                        id
                        name
                        date
                        totalSpots
                        status
                    }
                }
                """;

        this.tester.document(eventOfIdQuery)
                .variable("id", eventId)
                .execute()
                .path("eventOfId.id").entity(String.class).isEqualTo(eventId)
                .path("eventOfId.name").entity(String.class).isEqualTo("Disney on Ice")
                .path("eventOfId.date").entity(String.class).isEqualTo("2021-01-01")
                .path("eventOfId.totalSpots").entity(Integer.class).isEqualTo(100)
                .path("eventOfId.status").entity(String.class).isEqualTo("ACTIVE");
    }
}
