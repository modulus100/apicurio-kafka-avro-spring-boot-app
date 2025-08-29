# Confluent Avro Serializer with Apicurio Registry

This document explains how Confluent Avro SerDes work with Apicurio Registry when using the Confluent-compatible (ccompat) API.

## Overview

- __Serializer/Deserializer__: `io.confluent.kafka.serializers.KafkaAvroSerializer` and `KafkaAvroDeserializer`
- __Registry__: Apicurio Registry exposing ccompat at `/apis/ccompat/v7`
- __Transport__: Kafka value/key, wire format is Confluent framing with schema ID

Docs:
- Confluent SerDes overview: https://docs.confluent.io/platform/current/schema-registry/serdes-develop/index.html
- Confluent Avro Serializers config: https://docs.confluent.io/platform/current/schema-registry/serdes-develop/serdes-avro.html
- Apicurio Registry ccompat: https://www.apicur.io/registry/docs/apicurio-registry/3.0.x/getting-started/assembly-registry-using.html#registry-rest-api-compatibility_registry

## Minimal configuration

Point Confluent SerDes to Apicurio's ccompat endpoint:

```properties
# Common
schema.registry.url=http://localhost:8081/apis/ccompat/v7

# Producer
auto.register.schemas=false
value.serializer=io.confluent.kafka.serializers.KafkaAvroSerializer
key.serializer=org.apache.kafka.common.serialization.StringSerializer

# Consumer
value.deserializer=io.confluent.kafka.serializers.KafkaAvroDeserializer
key.deserializer=org.apache.kafka.common.serialization.StringDeserializer
specific.avro.reader=true
```

Notes:
- Set `auto.register.schemas=false` if you register schemas out-of-band (e.g., a migrator job). Otherwise, set to `true` to let the serializer register.
- `specific.avro.reader=true` makes the consumer return generated `SpecificRecord` classes instead of `GenericRecord`.

## Subject naming strategies

Configure how subjects are derived:

```properties
value.subject.name.strategy=io.confluent.kafka.serializers.subject.TopicNameStrategy
# key.subject.name.strategy=...
```

Docs:
- https://docs.confluent.io/platform/current/schema-registry/serdes-develop/serdes-develop-serde.html#subject-name-strategy

## Reading specific vs generic

- __Specific__: Generate Java from Avro (`SpecificRecord`). Configure consumer with `specific.avro.reader=true`.
- __Generic__: Skip codegen, work with `GenericRecord`.

Docs:
- Avro specific vs generic: https://docs.confluent.io/platform/current/schema-registry/serdes-develop/serdes-develop-avro.html#specific-and-generic-avro-records

## Version resolution

- `use.latest.version=true|false` (producer side) can instruct serializer to look up the latest schema version instead of the exact version. Use carefully with compatibility rules.

Docs:
- https://docs.confluent.io/platform/current/schema-registry/serdes-develop/serdes-develop-config.html#kafkavroserializer-config

## Authentication (OIDC bearer)

Confluent SR client can obtain OAuth2 tokens and attach as Bearer headers to SR REST calls. For local Keycloak, an example:

```properties
bearer.auth.credentials.source=OAUTHBEARER
bearer.auth.issuer.endpoint.url=http://localhost:8080/realms/demo/protocol/openid-connect/token
bearer.auth.client.id=${SR_OIDC_CLIENT_ID}
bearer.auth.client.secret=${SR_OIDC_CLIENT_SECRET}
bearer.auth.scope=${SR_OIDC_SCOPE}
```

Docs:
- Confluent Bearer auth properties: https://docs.confluent.io/platform/current/schema-registry/serdes-develop/serdes-security.html#oauth-bearer-authentication
- Apicurio auth: https://www.apicur.io/registry/docs/apicurio-registry/3.0.x/getting-started/assembly-registry-using.html#registry-authentication-authorization_registry

## Schema registration flows

- __Auto-register (producer)__: Set `auto.register.schemas=true`. Serializer registers the first time it sees a new schema for a subject.
- __Pre-register (out-of-band)__: Use a job/tool to POST to `/subjects/{subject}/versions`. Then keep producers with `auto.register.schemas=false`.

Docs:
- Confluent SR API: https://docs.confluent.io/platform/current/schema-registry/develop/api.html

## Error handling & tips

- __404 or 409 on publish__: Check subject naming strategy and compatibility level.
- __401/403 from registry__: Verify token endpoint URL, client credentials, and that Apicurio is configured to trust your IdP.
- __ClassCastException on consumer__: If using SpecificRecord, ensure `specific.avro.reader=true` and generated classes are on classpath.

## Example (Spring Boot application.yml)

```yaml
spring:
  kafka:
    bootstrap-servers: localhost:9092
    properties:
      schema.registry.url: http://localhost:8081/apis/ccompat/v7
      auto.register.schemas: false
      value.subject.name.strategy: io.confluent.kafka.serializers.subject.TopicNameStrategy
      bearer.auth.credentials.source: OAUTHBEARER
      bearer.auth.issuer.endpoint.url: http://localhost:8080/realms/demo/protocol/openid-connect/token
      bearer.auth.client.id: ${SR_OIDC_CLIENT_ID:sr-client}
      bearer.auth.client.secret: ${SR_OIDC_CLIENT_SECRET}
      bearer.auth.scope: ${SR_OIDC_SCOPE:}
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: io.confluent.kafka.serializers.KafkaAvroSerializer
    consumer:
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: io.confluent.kafka.serializers.KafkaAvroDeserializer
      properties:
        specific.avro.reader: true
```
