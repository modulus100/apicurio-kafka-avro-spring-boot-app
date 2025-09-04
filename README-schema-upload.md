# Schema upload strategy

This document describes how Avro schemas are registered in Apicurio Registry for this project.

## Overview

- Auto registration by producers is disabled.
  - In `application.yml`: `spring.kafka.properties.auto.register.schemas: false`
- Schemas are uploaded out-of-band (CI pipeline), not at runtime by services.
- Registry API is the Confluent-compatible (ccompat) API at `/apis/ccompat/v7`.

See also:
- Confluent serializer with Apicurio: `README-confluent-serializer-with-apicurio.md`
- Apicurio ccompat overview: `README-apicurio-ccompat.md`

## Current approach

- Schemas live alongside code under `src/main/avro/` in each module.
- Java classes are generated from Avro during build (see `README-avro-codegen.md`).
- Producers/consumers use Confluent SerDes pointing to Apicurio ccompat.
- Since auto register is OFF, schemas must exist in the registry before apps run.

## GitLab CI pipeline (in development)

- We plan to register/update schemas via a GitLab pipeline.
- Status: this pipeline is in development; we are evaluating the best approach.
- Possible steps the pipeline will perform:
  1) Build modules and validate Avro schemas
  2) Resolve target subject names (e.g., `TopicNameStrategy` → `<topic>-value`)
  3) Register new versions via ccompat `POST /subjects/{subject}/versions`
  4) Optionally enforce compatibility level (e.g., `PUT /config/{subject}`)

Notes:
- The pipeline will run on merge to main or on release tags.
- Environments/tenants can be handled via variables (e.g., SR URL, OAuth creds).

## Local development registration

For local workflows while the pipeline is being finalized, you can pre-register schemas using the `avro-schema-registry/` module:

```bash
./gradlew :avro-schema-registry:bootRun
# or
./gradlew :avro-schema-registry:bootJar && java -jar avro-schema-registry/build/libs/avro-schema-registry-*.jar
```

Expected output will indicate whether the subject already existed or was registered.

## Manual registration via API (ccompat)

Examples (replace values accordingly):

- List subjects:
```
curl -f http://localhost:8081/apis/ccompat/v7/subjects
```

- Register a new version under a subject:
```
curl -X POST \
  -H 'Content-Type: application/json' \
  --data '{"schema": "<escaped avro schema JSON>"}' \
  http://localhost:8081/apis/ccompat/v7/subjects/demo-topic-value/versions
```

- Get latest version for a subject:
```
curl -f http://localhost:8081/apis/ccompat/v7/subjects/demo-topic-value/versions/latest
```

If your registry is protected with OIDC, attach an Authorization header with a bearer token obtained via your IdP.

## Best practices

- Keep `auto.register.schemas=false` in services; register via CI to avoid accidental schema drift.
- Treat `.avsc` as source of truth; do not commit generated Java.
- Choose and document your subject naming strategy (default here: `TopicNameStrategy` → `demo-topic-value`).
- Enforce compatibility: set a compatibility level that matches your evolution policy.

## References

- Confluent SR API (for ccompat): https://docs.confluent.io/platform/current/schema-registry/develop/api.html
- Apicurio ccompat docs: https://www.apicur.io/registry/docs/apicurio-registry/3.0.x/getting-started/assembly-registry-using.html#registry-rest-api-compatibility_registry
- Confluent subject name strategies: https://docs.confluent.io/platform/current/schema-registry/serdes-develop/serdes-develop-serde.html#subject-name-strategy
