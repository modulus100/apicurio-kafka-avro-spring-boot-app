# Spring Boot + Kafka + Apicurio + Avro + CloudEvents — Developer Guide

This is a demonstration project showing how Apicurio can be used with Kafka, Confluent, Avro, and a Spring Boot service.

This repository demonstrates producing and consuming Avro messages with Spring Boot and Confluent serializers against Apicurio Registry (ccompat API), with an OAuth2-protected Schema Registry. It also shows CloudEvents integration and Avro-to-Java code generation.

Kafka runs unauthenticated (PLAINTEXT) for local development; only Apicurio Schema Registry is secured.

> Authentication provider: For local development, use Keycloak (documented below). Azure Entra ID guidance will be provided in a separate document.

## Modules and key files

- `app-producer/`
  - Spring Boot app that publishes Avro events, optionally enriched as CloudEvents
  - Avro schemas: `app-producer/src/main/avro/`
  - Config: `app-producer/src/main/resources/application.yml`
  - Main class: `org.example.app.ProducerApp`

- `app-consumer/`
  - Spring Boot app that consumes Avro (SpecificRecord) messages
  - Config: `app-consumer/src/main/resources/application.yml`
  - Main class: `org.example.app.ConsumerApp`

- `avro-schema-registry/`
  - One-shot Spring Boot job to pre-register Avro schemas in Apicurio
  - Main class: `org.example.migrator.MigratorApplication`

- Infra
  - `docker-compose.yml` spins up: `kafka` and `registry` (Apicurio). A `keycloak` service exists for legacy setups; for Azure Entra ID, ignore it and configure Apicurio to use Azure OIDC as described below.

## Prerequisites

- Java 21+
- Docker 24+ and Docker Compose
- Gradle Wrapper (provided): `./gradlew`
- Azure tenant with permissions to create App registrations

## Identity and clients

### Keycloak (local development)

If you prefer Keycloak locally, you can use the included `keycloak` service in `docker-compose.yml`.

1) Start Keycloak and Kafka, then open `http://localhost:8080` (admin/admin by default in compose):

```bash
docker compose up -d keycloak kafka
```

2) In Keycloak:
- Create realm: `demo`
- Create confidential client: `apicurio-registry` (Client authentication ON)
- Create confidential client: `sr-client` (Client authentication ON)
- For `apicurio-registry`, create a client secret and note it
- For `sr-client`, create a client secret and note it

3) Configure Apicurio Registry to use Keycloak (set env in `registry`):

```
REGISTRY_AUTH_ENABLED=true
REGISTRY_AUTH_AUTHORIZATION_ROLES_ENABLED=false
QUARKUS_OIDC_AUTH_SERVER_URL=http://keycloak:8080/realms/demo
QUARKUS_OIDC_CLIENT_ID=apicurio-registry
QUARKUS_OIDC_CREDENTIALS_SECRET=<KEYCLOAK_APICURIO_CLIENT_SECRET>
```

4) Configure apps to use Keycloak token endpoint:

```
export SR_OIDC_CLIENT_ID=sr-client
export SR_OIDC_CLIENT_SECRET=<KEYCLOAK_SR_CLIENT_SECRET>
export SR_OIDC_SCOPE=""    # often empty for client credentials in Keycloak
```

And set in `application.yml` (or via env):

```
spring.kafka.properties.bearer.auth.issuer.endpoint.url: http://localhost:8080/realms/demo/protocol/openid-connect/token
```

Both `app-producer` and `app-consumer` use Confluent SR client bearer token support. For local development with Keycloak, set:

```
export SR_OIDC_CLIENT_ID=sr-client
export SR_OIDC_CLIENT_SECRET=<KEYCLOAK_SR_CLIENT_SECRET>
export SR_OIDC_SCOPE=""    # often empty for client credentials in Keycloak
```

In the apps, the relevant properties are in:
- `app-producer/src/main/resources/application.yml`
- `app-consumer/src/main/resources/application.yml`

Key properties (already present in the YML; override defaults via env):

```
spring.kafka.properties:
  schema.registry.url: http://localhost:8081/apis/ccompat/v7
  auto.register.schemas: false
  bearer.auth.credentials.source: OAUTHBEARER
  bearer.auth.issuer.endpoint.url: http://localhost:8080/realms/demo/protocol/openid-connect/token
  bearer.auth.client.id: ${SR_OIDC_CLIENT_ID}
  bearer.auth.client.secret: ${SR_OIDC_CLIENT_SECRET}
  bearer.auth.scope: ${SR_OIDC_SCOPE}
```

Note: Azure Entra ID based configuration will be documented separately.

## Avro schemas and code generation

- Place `.avsc` files under:
  - `app-producer/src/main/avro/`
  - `app-consumer/src/main/avro/`

- The build uses Apache Avro Tools to generate SpecificRecord classes into `build/generated-src/avro` and automatically wires them into the `main` source set.

- Generate code explicitly:

```bash
./gradlew :app-producer:generateAvroJava :app-consumer:generateAvroJava
```

- Or let compilation trigger it:

```bash
./gradlew :app-producer:compileJava :app-consumer:compileJava
```

## CloudEvents integration

Both modules include CloudEvents SDK dependencies (`io.cloudevents:cloudevents-core` and `io.cloudevents:cloudevents-kafka`). Use binary mode over Kafka (CloudEvents context attributes go to headers; Avro payload stays as the value).

See examples in code:
- Producer service: `app-producer/src/main/java/org/example/app/kafka/specific/ProducerServiceSpecific.java`
- Consumer listener: `app-consumer/src/main/java/org/example/app/kafka/specific/ConsumerListenerSpecific.java`

## Running locally

1) Start Kafka and Apicurio Registry

```bash
# Start required services (add keycloak if you use it)
docker compose up -d kafka registry kafdrop
```

After startup, verify the registry is up:

```bash
curl -f http://localhost:8081/apis/ccompat/v7/subjects || echo "Registry not ready yet"
```

2) Export SR client credentials (for apps and migrator)

```bash
export SR_OIDC_CLIENT_ID=sr-client
export SR_OIDC_CLIENT_SECRET=<KEYCLOAK_SR_CLIENT_SECRET>
export SR_OIDC_SCOPE=""
```

3) Register the Avro schema (avro-schema-registry)

```bash
./gradlew :avro-schema-registry:bootRun
# or
./gradlew :avro-schema-registry:bootJar && java -jar avro-schema-registry/build/libs/avro-schema-registry-*.jar
```

Expected output includes either:
- `Schema already exists for subject=demo-topic-value, id=...`
- `Schema registered subject=demo-topic-value, id=...`

4) Run producer and consumer

```bash
./gradlew :app-consumer:bootRun
./gradlew :app-producer:bootRun
```

5) Inspect with Kafdrop

Kafdrop UI runs at `http://localhost:9000`.

- Browse topics (e.g., `demo-topic` configured in `app.*.application.yml`)
- View partitions, consumer groups, and lag
- View messages: Avro payloads are binary; you’ll see bytes. Headers will show CloudEvents attributes in binary mode (e.g., `ce_type`, `ce_id`, `ce_source`).
- Create topics if needed (auto-create is also enabled via Spring `admin.auto-create: true`).

## Configuration highlights

- Schema Registry URL (ccompat): `http://localhost:8081/apis/ccompat/v7`
- Default subject naming: `TopicNameStrategy` → `demo-topic-value`
- Kafka bootstrap: `localhost:9092`
- Auto register schemas disabled: managed by `migrator`

## Troubleshooting

- 401/403 from Apicurio
  - Verify OIDC variables on the `registry` container
  - Check token/issuer URL matches your provider (Keycloak realm URL for local)
  - For local dev set `REGISTRY_AUTH_AUTHORIZATION_ROLES_ENABLED=false`

- Token errors (invalid_client / invalid_scope)
  - Ensure the SR client is confidential and has a valid client secret
  - For Keycloak, `scope` may be blank; ensure the token URL matches the realm

- Subject mismatch
  - Subject is `demo-topic-value` by default. If you change the subject name strategy, align the migrator and apps accordingly.

## Project structure

- `app-producer/` Spring Boot producer (Avro + CloudEvents)
- `app-consumer/` Spring Boot consumer (Avro SpecificRecord)
- `avro-schema-registry/` One-shot schema registration job
- `docker-compose.yml` Local infra

## Notes

- Kafka is left open (PLAINTEXT) for simplicity. Secure Kafka separately if needed.
-- The compose file contains a `keycloak` service for local development. An Azure Entra ID guide will be provided separately.
