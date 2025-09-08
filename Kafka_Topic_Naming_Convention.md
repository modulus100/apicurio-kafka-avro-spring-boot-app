# Kafka Topic Naming Convention for [Your Company Name]

This document outlines the Kafka topic naming convention for [Your Company Name]. The convention supports Change Data Capture (CDC), streaming, ETL processes, and event-driven architectures while ensuring readability, scalability, consistency, and compliance. The value stream reflects organizational departments (e.g., `core-banking`, `data-mesh`), an optional `sub-domain` provides additional categorization within the domain, and the mandatory `type` component categorizes the processing paradigm (e.g., `cdc`, `etl`, `streaming`, `events`).

## Naming Convention Structure

The topic name follows this pattern:

`<visibility>.<value-stream>.<domain>[.<sub-domain>].<type>.<action-or-event>[.<version>]`

### Components

1. **Visibility** (Mandatory):
   - Indicates the access level of the topic.
   - Options: `public` (accessible across teams), `private` (restricted to specific teams or domains).
   - Example: `public`, `private`.

2. **Value Stream** (Mandatory):
   - Represents the high-level organizational department or unit.
   - Examples: `core-banking`, `data-mesh`, `sales`, `finance`, `marketing`.

3. **Domain** (Mandatory):
   - Specifies the specific entity or context within the value stream.
   - Examples: `orders`, `customers`, `transactions`, `products`, `datawarehouse`.

4. **Sub-Domain** (Optional):
   - Provides additional categorization within the domain, such as a specific sub-entity or context.
   - Examples: `billing`, `shipping`, `profiles`, `analytics`.
   - Omit for simpler topics where no further categorization is needed.

5. **Type** (Mandatory):
   - Categorizes the processing paradigm or data handling method.
   - Options: `cdc` (Change Data Capture), `etl` (Extract, Transform, Load), `streaming` (real-time data streams), `events` (event-driven architectures).
   - Note: Restrict `type` to these four values to ensure consistency.

6. **Action or Event** (Mandatory):
   - Describes the specific action, event, or process the topic represents.
   - Examples: `created`, `updated`, `deleted`, `ingested`, `processed`, `pageviews`, `shipped`.

7. **Version** (Optional):
   - Indicates the schema version, used when schema changes occur.
   - Examples: `v1`, `v2`.
   - Omit if versioning is not needed (e.g., for stable schemas).

### Rules

- **Separator**: Use dots (`.`) to separate components.
- **Case**: Use lowercase letters to avoid ambiguity (e.g., `public.core-banking.orders.billing.cdc.updated.v1`).
- **Length**: Keep names concise but descriptive, respecting Kafka’s 249-character limit.
- **Consistency**: Apply the same pattern across all topics.
- **No Company Names**: Avoid including the company name unless necessary for multi-tenant environments.
- **No Application Names**: Use domain-driven terms (e.g., `pricing` instead of `pricingengine`) unless the application name is critical and stable.
- **Type Restriction**: Restrict `type` to `cdc`, `etl`, `streaming`, or `events`. Avoid using these terms in the `domain`, `sub-domain`, or `action-or-event` components.
- **Sub-Domain Usage**: Use `sub-domain` sparingly for specific contexts (e.g., `billing` within `orders`, `profiles` within `customers`). Omit when not needed to keep names concise.

## Use Case Examples

### Change Data Capture (CDC)

CDC topics capture database changes (inserts, updates, deletes) for replication or synchronization.

- **Example**: `private.core-banking.orders.billing.cdc.updated.v1`
  - Visibility: `private` (CDC data is often sensitive).
  - Value Stream: `core-banking` (the department).
  - Domain: `orders` (the entity).
  - Sub-Domain: `billing` (specific context within orders).
  - Type: `cdc` (Change Data Capture).
  - Action/Event: `updated` (the specific change).
  - Version: `v1` (schema version).

- **Example**: `private.data-mesh.customers.cdc.inserted.v1` (no sub-domain).

### Streaming

Streaming topics handle real-time data streams, often for analytics or processing.

- **Example**: `public.data-mesh.analytics.web.streaming.pageviews`
  - Visibility: `public` (accessible for analytics).
  - Value Stream: `data-mesh` (the department).
  - Domain: `analytics` (the data domain).
  - Sub-Domain: `web` (specific context within analytics).
  - Type: `streaming` (real-time data stream).
  - Action/Event: `pageviews` (the event type).
  - No version (stable schema).

- **Example**: `public.core-banking.transactions.streaming.processed` (no sub-domain).

### ETL (Extract, Transform, Load)

ETL topics manage data movement through extraction, transformation, and loading stages.

- **Example**: `private.data-mesh.datawarehouse.raw.etl.ingested.v1`
  - Visibility: `private` (ETL data may be internal).
  - Value Stream: `data-mesh` (the department).
  - Domain: `datawarehouse` (the target system).
  - Sub-Domain: `raw` (specific data zone).
  - Type: `etl` (Extract, Transform, Load).
  - Action/Event: `ingested` (the ETL stage).
  - Version: `v1` (schema version).

- **Example**: `private.core-banking.transactions.etl.transformed` (no sub-domain).

### Events

Event-driven topics capture specific business or system events.

- **Example**: `public.core-banking.users.profile.events.created`
  - Visibility: `public` (accessible for event consumers).
  - Value Stream: `core-banking` (the department).
  - Domain: `users` (the entity).
  - Sub-Domain: `profile` (specific context within users).
  - Type: `events` (event-driven architecture).
  - Action/Event: `created` (the event).
  - No version (stable schema).

- **Example**: `public.data-mesh.orders.events.shipped` (no sub-domain).

## Multi-Tenant Environments

For multi-tenant setups, prepend a tenant identifier to the topic name to ensure isolation and clarity.

- **Example**: `private.tenantA.core-banking.orders.billing.cdc.updated.v1`
  - Tenant ID: `tenantA` (identifies the tenant).
  - Follows the standard convention thereafter.

- **Example**: `public.tenantB.data-mesh.customers.events.created.v1` (no sub-domain).

## Security and Compliance

To support security and compliance:

- **Use Visibility**: Use `private` for sensitive or restricted data (e.g., `private.core-banking.transactions.cdc.secure.v1`).
- **Compliance Labels**: Include terms like `gdpr` or `hipaa` in the `domain` or `action-or-event` for regulated data (e.g., `private.data-mesh.customers.cdc.gdpr-profiles.v1`).
- **Access-Based Naming**: Indicate access levels (e.g., `internal.core-banking.employee.profile.events.details`).

## Best Practices

1. **Document Conventions**:
   - Maintain a centralized document (e.g., this one) to ensure team alignment.
   - Share with all Kafka users and enforce during topic creation.

2. **Enforce via Tools**:
   - Set `auto.create.topics.enable` to `false` on Kafka brokers to prevent automatic topic creation.
   - Use tools like Kadeck or Confluent Control Center to enforce naming rules and manage permissions.
   - Example: Configure Kadeck with a regex pattern like `^public\.[a-z-]+\.[a-z-]+(\.[a-z-]+)?\.(cdc|etl|streaming|events)\.[a-z-]+(\.v[0-9]+)?$` to enforce the convention.

3. **Avoid Common Pitfalls**:
   - **Ambiguous Names**: Avoid vague names like `data` or `messages`.
   - **Overuse of Abbreviations**: Use clear terms (e.g., `transactions` instead of `txn`).
   - **Inconsistent Separators**: Stick to dots (`.`) for consistency.
   - **Unnecessary Versioning**: Only use versions when schema changes occur.
   - **Incorrect Type Usage**: Restrict `type` to `cdc`, `etl`, `streaming`, or `events`.
   - **Overusing Sub-Domain**: Use `sub-domain` only when necessary to avoid overly long names.

4. **Regular Audits**:
   - Periodically review topics for compliance with conventions.
   - Use tools like Kafka Manager or Confluent Control Center for monitoring.

5. **Schema Registry**:
   - Store schema versions in a schema registry instead of relying solely on topic name versioning.
   - Example: Use Confluent Schema Registry to manage `v1`, `v2` schemas for `core-banking.orders`.

## Tools for Enforcement

- **Kadeck**: Use Kadeck (free for up to 5 users) to manage topic creation and permissions, ensuring compliance with naming conventions.
- **Confluent Control Center**: Monitor and enforce naming rules via GUI.
- **Kafka CLI**: Use `kafka-topics.sh` to manually create and verify topics (e.g., `kafka-topics.sh --create --topic private.core-banking.orders.billing.cdc.updated.v1 --bootstrap-server <broker>`).
- **Apache Atlas**: Track data lineage and ensure naming compliance in large setups.

## Example Topic Names

| Use Case | Topic Name | Description |
|----------|-----------|-------------|
| CDC | `private.core-banking.orders.billing.cdc.updated.v1` | Captures updates to billing orders in core banking. |
| Streaming | `public.data-mesh.analytics.web.streaming.clicks` | Streams real-time web click data for analytics. |
| ETL | `private.data-mesh.datawarehouse.raw.etl.transformed.v1` | Manages transformed raw data for ETL processes. |
| Events | `public.core-banking.products.catalog.events.added` | Captures product additions in the catalog. |
| Multi-Tenant | `private.tenantA.core-banking.payments.cdc.updated.v1` | Tenant-specific payment CDC data. |
| Compliance | `private.data-mesh.customers.cdc.gdpr-profiles.v1` | GDPR-compliant customer profile CDC data. |

## Notes

- **Value Stream**: Use department names like `core-banking` or `data-mesh` to reflect organizational structure.
- **Domain**: Use entity-specific terms (e.g., `orders`, `customers`).
- **Sub-Domain**: Use sparingly for specific contexts (e.g., `billing`, `web`, `raw`). Omit for simpler topics.
- **Type**: Restrict to `cdc`, `etl`, `streaming`, or `events` to categorize the processing paradigm.
- **Versioning**: Use versioning sparingly and only for schema changes. Rely on a schema registry for detailed version management.
- **Wildcards**: The dot-separated structure supports Kafka wildcard queries (e.g., `public.core-banking.*.cdc.*` for all CDC topics in core banking).
- **Scalability**: The hierarchical structure scales well for large organizations with multiple departments and domains.
- **Simplicity**: The convention is simple enough to fit on a "beer coaster" while being flexible for complex use cases.

## Conclusion

This Kafka topic naming convention ensures clarity, scalability, and compliance for [Your Company Name]’s Kafka ecosystem. By structuring topics with visibility, department-based value streams, entity-specific domains, optional sub-domains, mandatory processing type, action/event, and optional versioning, we support CDC, streaming, ETL, and event-driven use cases while maintaining readability and governance. Regular audits and tools like Kadeck or Confluent Control Center will help enforce these conventions and keep the Kafka cluster organized.

For feedback or suggestions, contact [Your Contact Info].