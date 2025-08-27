# Spring Boot + Kafka + Apicurio (Keycloak OAuth2) Demo

This repo runs Kafka and Apicurio Schema Registry locally, secures the Registry with Keycloak (OIDC), and contains:
- app: Main Spring Boot service using Confluent serializers against Apicurio.
- migrator: One-shot Spring Boot job that registers the Avro schema and exits.

Kafka is unauthenticated. Only Apicurio requires OAuth2.

## Components

- `docker-compose.yml`
  - `kafka` at `localhost:9092`
  - `keycloak` at `http://localhost:8080`
  - `registry` (Apicurio) at `http://localhost:8081` (ccompat API: `/apis/ccompat/v7`)
- `app/src/main/resources/application.yml`
  - Confluent serializers and SR client set to OAuth2 client-credentials
  - `auto.register.schemas: false` (relies on migrator)
- `migrator`
  - Registers `avro/greeting.avsc` under subject `demo-topic-value` and exits

## One-time Keycloak setup

1) Start stack (first time)
```bash
docker compose up -d keycloak kafka
```

2) Open Keycloak admin UI: http://localhost:8080 (admin / admin)

3) Create realm: `demo`

4) Create confidential client for Apicurio: `apicurio-registry`
- Client authentication: ON (confidential)
- Valid redirect URIs: `http://localhost:8081/*`
- Copy the generated client secret

5) Export the Apicurio OIDC client secret and start Registry
```bash
export APICURIO_OIDC_CLIENT_SECRET='<apicurio-registry-secret>'
docker compose up -d registry
```

6) Create confidential client for apps: `sr-client`
- Client authentication: ON (confidential)
- Copy its client secret

7) Export app client credentials (used by both `app` and `migrator`)
```bash
export SR_OIDC_CLIENT_ID='sr-client'
export SR_OIDC_CLIENT_SECRET='<sr-client-secret>'
# optional if your realm uses scopes
export SR_OIDC_SCOPE=''
```

Tip: If initial testing fails with 403 due to role mapping, set in `docker-compose.yml`:
- `REGISTRY_AUTH_AUTHORIZATION_ROLES_ENABLED: "false"` (then restart `registry`). You can re-enable later and configure roles.

## Run migrator (register schema)

The migrator registers `app/src/main/resources/avro/greeting.avsc` (copied into its own resources) under the subject `demo-topic-value` (from `app.kafka.topic=demo-topic` and default `TopicNameStrategy`).

```bash
./gradlew :migrator:bootRun
# or
./gradlew :migrator:bootJar
java -jar migrator/build/libs/migrator-*.jar
```

Expected output includes either:
- `Schema already exists for subject=demo-topic-value, id=...` or
- `Schema registered subject=demo-topic-value, id=...`

## Run main app

```bash
./gradlew :app:bootRun
```

The app uses Confluent serializers with:
- `schema.registry.url: http://localhost:8081/apis/ccompat/v7`
- OAuth2 bearer: `bearer.auth.*` properties (issuer endpoint, client id/secret)
- `auto.register.schemas: false` to rely on the migrator deployment

## Troubleshooting

- 401/403 from Apicurio
  - Ensure `APICURIO_OIDC_CLIENT_SECRET` is exported and `registry` restarted
  - Check realm name (`demo`) and issuer URL in configs
  - Temporarily set `REGISTRY_AUTH_AUTHORIZATION_ROLES_ENABLED=false`

- Token/issuer URL
  - Migrator/app use: `http://localhost:8080/realms/demo/protocol/openid-connect/token`

- Subject mismatch
  - Subject is `demo-topic-value`. If you use a different subject name strategy, adjust migrator accordingly.

## Project structure

- `app/` main service
- `migrator/` one-shot schema registration job
- `utilities/` shared utilities (if needed)
- `docker-compose.yml` infra services

## Notes

- This setup keeps Kafka open (PLAINTEXT) and only protects the Schema Registry with OAuth2.
- For production parity, you can switch Keycloak to Azure Entra ID; the client-credentials flow remains the same.
