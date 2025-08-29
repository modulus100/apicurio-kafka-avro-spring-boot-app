# Spring Boot + Kafka + Apicurio + Avro + CloudEvents (Azure Entra ID) — Developer Guide

This is a demonstration project showing how Apicurio can be used with Kafka, Confluent, Avro, and a Spring Boot service.

This repository demonstrates producing and consuming Avro messages with Spring Boot and Confluent serializers against Apicurio Registry (ccompat API), with OAuth2 protected Schema Registry using Azure Entra ID (formerly Azure AD). It also shows CloudEvents integration and Avro-to-Java code generation.

Kafka runs unauthenticated (PLAINTEXT) for local development; only Apicurio Schema Registry is secured.

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

- `migrator/`
  - One-shot Spring Boot job to pre-register Avro schemas in Apicurio
  - Main class: `org.example.migrator.MigratorApplication`

- Infra
  - `docker-compose.yml` spins up: `kafka` and `registry` (Apicurio). A `keycloak` service exists for legacy setups; for Azure Entra ID, ignore it and configure Apicurio to use Azure OIDC as described below.

## Prerequisites

- Java 21+
- Docker 24+ and Docker Compose
- Gradle Wrapper (provided): `./gradlew`
- Azure tenant with permissions to create App registrations

## Azure Entra ID setup (replace any Keycloak references)

You will create two App registrations in Azure Entra ID:

1) Apicurio Registry app (resource server)
- App registration (Web)
- Expose an API: Application ID URI like `api://<APICURIO_APP_ID>`
- Create a client secret; note the values

2) SR client app (producer/consumer/migrator)
- App registration (confidential client)
- Create a client secret; note the values
- API permissions: add application permission to the Apicurio API you exposed above (or use default if none). Admin-consent as needed.

Important URLs and values (replace placeholders):
- Tenant ID: `<TENANT_ID>`
- Token endpoint (v2): `https://login.microsoftonline.com/<TENANT_ID>/oauth2/v2.0/token`
- Issuer/authority (OIDC discovery): `https://login.microsoftonline.com/<TENANT_ID>/v2.0`
- OAuth2 scope for client credentials: `api://<APICURIO_APP_ID>/.default`

### Configure Apicurio to trust Azure Entra ID

Set these environment variables for the `registry` container (for example via `docker-compose.yml` overrides or an `.env` file):

```
REGISTRY_AUTH_ENABLED=true
REGISTRY_AUTH_AUTHORIZATION_ROLES_ENABLED=false
QUARKUS_OIDC_AUTH_SERVER_URL=https://login.microsoftonline.com/<TENANT_ID>/v2.0
QUARKUS_OIDC_CLIENT_ID=<APICURIO_APP_CLIENT_ID>
QUARKUS_OIDC_CREDENTIALS_SECRET=<APICURIO_APP_CLIENT_SECRET>
```

Notes:
- `REGISTRY_AUTH_AUTHORIZATION_ROLES_ENABLED=false` is convenient for local dev. Enable and map roles later if needed.
- The Apicurio image already exposes Confluent-compatible (ccompat) endpoints at `/apis/ccompat/v7`.

### Configure apps (Confluent SR client) to obtain tokens from Azure

Both `app-producer` and `app-consumer` use Confluent SR client bearer token support. Set the following environment variables before running apps or the migrator:

```
export SR_OIDC_CLIENT_ID='<SR_CLIENT_APP_ID>'
export SR_OIDC_CLIENT_SECRET='<SR_CLIENT_SECRET>'
export SR_OIDC_SCOPE='api://<APICURIO_APP_ID>/.default'
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
  bearer.auth.issuer.endpoint.url: https://login.microsoftonline.com/<TENANT_ID>/oauth2/v2.0/token
  bearer.auth.client.id: ${SR_OIDC_CLIENT_ID}
  bearer.auth.client.secret: ${SR_OIDC_CLIENT_SECRET}
  bearer.auth.scope: ${SR_OIDC_SCOPE}
```

Important: the current `application.yml` files in `app-producer/` and `app-consumer/` point to a Keycloak token URL by default. Replace the value of `bearer.auth.issuer.endpoint.url` with the Azure token endpoint shown above.

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
# Start only the needed services (ignore Keycloak)
docker compose up -d kafka registry
```

Ensure `registry` is configured with the Azure variables shown earlier. After startup:

```bash
curl -f http://localhost:8081/apis/ccompat/v7/subjects || echo "Registry not ready yet"
```

2) Export Azure SR client credentials (for apps and migrator)

```bash
export SR_OIDC_CLIENT_ID='<SR_CLIENT_APP_ID>'
export SR_OIDC_CLIENT_SECRET='<SR_CLIENT_SECRET>'
export SR_OIDC_SCOPE='api://<APICURIO_APP_ID>/.default'
```

3) Register the Avro schema (migrator)

```bash
./gradlew :migrator:bootRun
# or
./gradlew :migrator:bootJar && java -jar migrator/build/libs/migrator-*.jar
```

Expected output includes either:
- `Schema already exists for subject=demo-topic-value, id=...`
- `Schema registered subject=demo-topic-value, id=...`

4) Run producer and consumer

```bash
./gradlew :app-consumer:bootRun
./gradlew :app-producer:bootRun
```

## Configuration highlights

- Schema Registry URL (ccompat): `http://localhost:8081/apis/ccompat/v7`
- Default subject naming: `TopicNameStrategy` → `demo-topic-value`
- Kafka bootstrap: `localhost:9092`
- Auto register schemas disabled: managed by `migrator`

## Troubleshooting

- 401/403 from Apicurio
  - Verify Azure OIDC variables on the `registry` container
  - Check tenant ID and token/issuer URLs
  - For local dev set `REGISTRY_AUTH_AUTHORIZATION_ROLES_ENABLED=false`

- Token errors (invalid_client / invalid_scope)
  - Ensure the SR app has a client secret and uses the v2 token endpoint
  - Use `scope=api://<APICURIO_APP_ID>/.default` for client credentials

- Subject mismatch
  - Subject is `demo-topic-value` by default. If you change the subject name strategy, align the migrator and apps accordingly.

## Project structure

- `app-producer/` Spring Boot producer (Avro + CloudEvents)
- `app-consumer/` Spring Boot consumer (Avro SpecificRecord)
- `migrator/` One-shot schema registration job
- `docker-compose.yml` Local infra

## Notes

- Kafka is left open (PLAINTEXT) for simplicity. Secure Kafka separately if needed.
- Azure Entra ID fully replaces any Keycloak references in this guide. The compose file contains a `keycloak` service for legacy scenarios—ignore it when using Azure.
