# Desafio: Cancelamento de Evento com Arquitetura Hexagonal & Clean

Este repositório contém a implementação completa do desafio de **Cancelamento de Evento** sobre a plataforma de venda de ingressos desenvolvida no curso de MBA Full Cycle, partindo do estado final de Clean Architecture (`clean-arch`).

---

## 🏛️ Onde a Cascata de Cancelamento Acontece

O cancelamento dos ingressos ocorre de forma totalmente desacoplada e orientada a eventos de domínio, garantindo que o agregado `Event` jamais manipule diretamente o agregado `Ticket`:

1. **Emissão do Evento de Domínio:**
   Quando a solicitação de cancelamento de evento é executada pelo `CancelEventUseCase`, o método de domínio `event.cancel()` valida se o evento já está cancelado (lançando `ValidationException("Event already cancelled")` caso esteja), altera o estado interno para `CANCELLED` e registra o evento de domínio `EventCancelled` (tipo `event.cancelled`).
2. **Persistência Transacional (Outbox Pattern):**
   Ao persistir o agregado através do `EventDatabaseRepository`, todos os eventos de domínio registrados no agregado são serializados e salvos atomicamente na tabela `outbox` no banco de dados relacional.
3. **Publicação Assíncrona via Fila:**
   O componente agendado `OutboxRelay` busca periodicamente os registros não publicados da outbox, envia seu conteúdo via `QueueGateway` e marca a mensagem como publicada.
4. **Roteamento e Reação no Caso de Uso:**
   O adapter `ConsumerQueueGateway` recebe a mensagem de forma assíncrona (executada no pool `queueExecutor`), identifica o tipo `event.cancelled`, desserializa o payload em `EventCancelled` e aciona o caso de uso de aplicação `CancelEventTicketsUseCase`.
5. **Cascata Idempotente nos Ingressos:**
   O `CancelEventTicketsUseCase` busca todos os ingressos associados ao evento (`ticketRepository.ticketsByEventId(...)`), invoca `ticket.cancel()` (que transiciona o status do ingresso para `TicketStatus.CANCELLED` de forma idempotente, sem quebrar em reprocessamentos) e persiste cada ingresso atualizado através da porta `TicketRepository`.

---

## 🚀 Como Executar o Projeto

### Pré-requisitos
- **Java 17** (LTS)
- **Docker e Docker Compose** (para o banco MySQL em tempo de execução)

### 1. Inicializando o Banco de Dados
Para subir a instância do MySQL configurada para a aplicação:
```bash
docker compose up -d
```

### 2. Executando a Aplicação
Inicie a aplicação Spring Boot utilizando o Gradle Wrapper:
```bash
# Linux/macOS
./gradlew :infrastructure:bootRun

# Windows (PowerShell)
.\gradlew.bat :infrastructure:bootRun
```
A API estará disponível em `http://localhost:8080`.

---

## 🧪 Como Executar a Suíte de Testes

Os testes automatizados utilizam banco de dados H2 em memória e cobrem:
- Testes unitários de domínio (`EventTest`, `TicketTest`)
- Testes unitários e de integração de casos de uso com repositórios in-memory (`CancelEventUseCaseTest`, `GetEventByIdUseCaseTest`, `CancelEventTicketsUseCaseTest`, etc.)
- Testes de integração de infraestrutura com banco de dados real e consultas JPA (`EventDatabaseRepositoryIT`, `TicketDatabaseRepositoryIT`)
- Testes de API REST (`EventControllerTest`, testando cancelamento e ambos os presenters)
- Testes de GraphQL (`EventResolverTest`, testando mutation e query)
- Teste assíncrono de ponta a ponta (`CancelEventCascadeIT`, provando a cascata real outbox/relay/consumer -> ingressos cancelados)

Para rodar todos os testes:
```bash
# Linux/macOS
./gradlew test

# Windows (PowerShell)
.\gradlew.bat test
```

Para rodar a verificação completa com relatórios de cobertura JaCoCo:
```bash
# Linux/macOS
./gradlew check

# Windows (PowerShell)
.\gradlew.bat check
```

---

## 📡 Endpoints e Contratos

### REST (`EventController`)

#### 1. Cancelar Evento
- **Rota:** `POST /events/{id}/cancel`
- **Sucesso (200 OK):**
  ```json
  {
    "id": "f3b6...",
    "status": "CANCELLED"
  }
  ```
- **Erro (422 Unprocessable Entity):**
  - Se evento inexistente: `Event not found`
  - Se já cancelado: `Event already cancelled`

#### 2. Consultar Evento por ID
- **Representação Completa (sem header `X-Public`):**
  - **Rota:** `GET /events/{id}`
  - **Sucesso (200 OK):**
    ```json
    {
      "id": "f3b6...",
      "name": "Disney on Ice",
      "date": "2021-01-01",
      "totalSpots": 100,
      "status": "ACTIVE"
    }
    ```
- **Representação Pública Reduzida (com header `X-Public: true`):**
  - **Rota:** `GET /events/{id}` (Header: `X-Public: true`)
  - **Sucesso (200 OK):**
    ```json
    {
      "id": "f3b6...",
      "status": "ACTIVE"
    }
    ```
- **Não Encontrado (404 Not Found):**
  - Ambos os presenters retornam HTTP 404 sem corpo quando o evento não existe.

### GraphQL (`EventResolver` + `schema.gqls`)

- **Query `eventOfId`:**
  ```graphql
  query {
    eventOfId(id: "f3b6...") {
      id
      name
      date
      totalSpots
      status
    }
  }
  ```

- **Mutation `cancelEvent`:**
  ```graphql
  mutation {
    cancelEvent(id: "f3b6...") {
      id
      status
    }
  }
  ```
