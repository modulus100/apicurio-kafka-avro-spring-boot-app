# Apicurio Registry and Confluent Compatibility (ccompat)

This document explains how Apicurio Registry exposes the Confluent Schema Registry–compatible API ("ccompat") and how to use it.

## What is ccompat

- __Definition__: The Confluent-compatible API that mirrors Confluent Schema Registry endpoints.
- __Purpose__: Lets you use Confluent clients (e.g., `kafka-avro-serializer`, SR REST client) against Apicurio without changing code.

Docs:
- Apicurio Registry ccompat API: https://www.apicur.io/registry/docs/apicurio-registry/3.0.x/getting-started/assembly-registry-using.html#registry-rest-api-compatibility_registry
- Apicurio Registry overview: https://www.apicur.io/registry/

## Base URL and endpoints

- Local default base for ccompat: `http://localhost:8081/apis/ccompat/v7`
- Common endpoints:
  - GET `/subjects`
  - GET `/subjects/{subject}/versions/latest`
  - POST `/subjects/{subject}/versions`
  - GET `/schemas/ids/{id}`

Reference:
- Confluent SR API (v6/v7 compatible): https://docs.confluent.io/platform/current/schema-registry/develop/api.html

## Subject naming and strategies

- Default Confluent subject strategies work the same against Apicurio:
  - `TopicNameStrategy` → `<topic>-key` or `<topic>-value`
  - `RecordNameStrategy` → `<full.avro.name>`
  - `TopicRecordNameStrategy` → `<topic>-<full.avro.name>`
- Configure via producer/consumer properties:
  - `value.subject.name.strategy`
  - `key.subject.name.strategy`

Docs:
- Confluent subject naming strategies: https://docs.confluent.io/platform/current/schema-registry/serdes-develop/serdes-develop-serde.html#subject-name-strategy

## Compatibility settings

- Apicurio supports compatibility levels when using ccompat endpoints.
- Typical values: `BACKWARD`, `BACKWARD_TRANSITIVE`, `FORWARD`, `FULL`, etc.
- Endpoint: `PUT /config` or `/config/{subject}`

Docs:
- Confluent SR compatibility: https://docs.confluent.io/platform/current/schema-registry/avro.html#compatibility-types
- Apicurio compatibility (ccompat): see ccompat section in docs above

## Authentication and authorization

- Apicurio can secure the registry via OIDC (Keycloak, Azure Entra ID, …).
- Confluent clients add bearer tokens to SR REST calls when configured with `bearer.auth.*` properties.
- For local development in this repo, Keycloak is used; Azure documentation is provided separately.

Docs:
- Apicurio security: https://www.apicur.io/registry/docs/apicurio-registry/3.0.x/getting-started/assembly-registry-using.html#registry-authentication-authorization_registry

## Using ccompat with Confluent serializers

- Point SR URL to the ccompat base: `schema.registry.url=http://<host>:8081/apis/ccompat/v7`
- Disable auto-register if you pre-register via tools/jobs: `auto.register.schemas=false`
- Use `KafkaAvroSerializer` / `KafkaAvroDeserializer` as usual.

Docs:
- Confluent serializers: https://docs.confluent.io/platform/current/schema-registry/serdes-develop/index.html

## Tooling and CLI

- You can interact with ccompat using generic HTTP clients (curl) or the Confluent SR CLI.
- Example (list subjects):
  - `GET http://localhost:8081/apis/ccompat/v7/subjects`

## Troubleshooting

- __404 for subjects__: Check base path (`/apis/ccompat/v7`).
- __401/403__: Verify OIDC configuration and client credentials.
- __Subject mismatch__: Align your subject naming strategy with how schemas were registered.
