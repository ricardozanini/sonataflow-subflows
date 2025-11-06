# SonataFlow Subflows — Local vs Remote (CNCF Serverless Workflow 0.8)

This repo demonstrates two ways to orchestrate the same **Order Processing** workflow using the **SonataFlow engine** on **Quarkus**:

1. **Local subflows** — the parent workflow calls child workflows **in‑process** via `subFlowRef`.
2. **Remote subflows** — the parent workflow calls child services **over HTTP** via `functionRef` → **OpenAPI** operations.

> Built with CNCF **Serverless Workflow DSL v0.8**, SonataFlow runtime, and Quarkus. The examples are minimal on purpose so you can copy/adapt them.

---

## Repository layout

```
sonataflow-subflows
├── local/                      # Local subflow demo (single Quarkus app)
│   └── order-flow/             # 'order' uses subFlowRef to call fraud/shipping in-process
└── remote/                     # Remote subflow demo (multi-service)
    ├── order-flow/             # 'order' app; calls fraud/shipping via OpenAPI functions
    ├── fraud-flow/             # Fraud microservice exposing workflow + OpenAPI
    └── shipping-flow/          # Shipping microservice exposing workflow + OpenAPI
```

Each service is a Quarkus app with its own `application.properties`. **Ports are already preconfigured** in the repo. Use `mvn quarkus:dev` from each module to run.

---

## What you’ll build

The **Order Processing** workflow runs two branches in parallel:

* **Fraud evaluation** — decides if the order is fraudulent based on `total`, etc.
* **Shipping type** — resolves `shipping` as `domestic` vs `international` from the `country` code.

Both branches write their results into the parent workflow’s `workflowdata`.

### Input example (Order)

```json
{
  "id": "f0643c68-609c-48aa-a820-5df423fa4fe0",
  "country": "BR",
  "total": 10000,
  "description": "iPhone 12"
}
```

### Output shape (high-level)

```json
{
  "id": "<instance-id>",
  "workflowdata": {
    "id": "f0643c68-609c-48aa-a820-5df423fa4fe0",
    "country": "BR",
    "total": 10000,
    "description": "iPhone 12",
    "fraudEvaluation": true,
    "shipping": "international" // or "domestic"
  }
}
```

> Exact fields depend on the subflow outputs. See each module’s OpenAPI and schemas for details.

---

## Side-by-side comparison

| Aspect                   | **Local Subflows** (`order-local`)                           | **Remote Subflows** (`order-remote`)                         |
| ------------------------ | ------------------------------------------------------------ | ------------------------------------------------------------ |
| How branches are invoked | `subFlowRef: fraudhandling` / `subFlowRef: shippinghandling` | `functionRef` → `operation: openapi/*.yaml#createResource_*` |
| Topology                 | Single Quarkus app (parent + subflows)                       | Three Quarkus apps (parent + two remote services)            |
| Coupling                 | Tighter (shared runtime)                                     | Looser (HTTP boundary, independent deploy/scale)             |
| Failure domain           | A crash affects everything in-process                        | Failures isolated per service; retries/timeouts via HTTP     |
| Versioning               | Update all together                                          | Independent versioning per service                           |
| Testing                  | Fast, unit-like                                              | Realistic service boundaries                                 |
| Observability            | In-process traces                                            | Cross-service tracing/headers (propagate)                    |
| Best for                 | Local dev, simple deployments                                | Teams/services autonomy, polyglot stacks                     |

---

## How it works

### Local subflows (excerpt)

```yaml
id: order
name: Order Processing
version: "1.0"
start: ProcessOrder
dataInputSchema: schemas/input/order.json
extensions:
  - extensionid: workflow-output-schema
    outputSchema: schemas/output/order.json
states:
  - name: ProcessOrder
    type: parallel
    branches:
      - name: HandleFraudEvaluation
        actions:
          - subFlowRef: fraudhandling
      - name: HandleShippingType
        actions:
          - subFlowRef: shippinghandling
    completionType: allOf
    end: true
```

### Remote subflows (excerpt)

```yaml
id: order
name: Order Processing
version: "1.0"
start: ProcessOrder
dataInputSchema: schemas/input-order.json
extensions:
  - extensionid: workflow-output-schema
    outputSchema: schemas/output-order.json
functions:
  - name: fraudEvaluation
    operation: openapi/fraud.yaml#createResource_fraudhandling
  - name: shippingType
    operation: openapi/shipping.yaml#createResource_shippinghandling
states:
  - name: ProcessOrder
    type: parallel
    branches:
      - name: HandleFraudEvaluation
        actions:
          - functionRef:
              refName: fraudEvaluation
              arguments:
                total: ${ .total }
            actionDataFilter:
              results: .workflowdata
      - name: HandleShippingType
        actions:
          - functionRef:
              refName: shippingType
              arguments:
                country: ${ .country }
            actionDataFilter:
              results: .workflowdata
    end: true
```

> **Note:** In remote mode the parent uses **OpenAPI client stubs** (generated by Quarkus OpenAPI Generator) to call the remote services.

---

## Prerequisites

* Java 17+
* Maven 3.9+
* Docker/Podman (optional, for container builds)

---

## Build & test everything

From the repo root:

```bash
mvn clean install
```

This runs all modules and their tests.

---

## Run the examples

### 1) Local subflows

In one terminal:

```bash
cd local/order
mvn quarkus:dev
```

Send a request to start the **order** workflow:

```bash
curl -X POST \
  -H 'Content-Type: application/json' \
  -d '{
        "id": "f0643c68-609c-48aa-a820-5df423fa4fe0",
        "country": "BR",
        "total": 10000,
        "description": "iPhone 12"
      }' \
  http://localhost:8080/order
```

### 2) Remote subflows

You’ll run **three** apps (in separate terminals):

**Terminal A — Fraud service**

```bash
cd remote/fraud-flow
mvn quarkus:dev
```

**Terminal B — Shipping service**

```bash
cd remote/shipping-flow
mvn quarkus:dev
```

**Terminal C — Parent (order-remote)**

```bash
cd remote/order-flow
mvn quarkus:dev
```

Now start the **order** workflow on the parent:

```bash
curl -X POST \
  -H 'Content-Type: application/json' \
  -d '{
        "id": "f0643c68-609c-48aa-a820-5df423fa4fe0",
        "country": "BR",
        "total": 10000,
        "description": "iPhone 12"
      }' \
  http://localhost:{{order_remote_port}}/order
```

> The parent calls the Fraud/Shipping services via generated OpenAPI clients. **Ports are already set** in each module’s `application.properties` so the parent knows where to reach them.

---

## OpenAPI

* The remote services expose OpenAPI via Quarkus Swagger/OpenAPI extension.
* Client stubs in `remote/order` are generated from `openapi/fraud.yaml` and `openapi/shipping.yaml`.
* Operations used by the parent:

    * `openapi/fraud.yaml#createResource_fraudhandling`
    * `openapi/shipping.yaml#createResource_shippinghandling`

> If you edit schemas, re-run `mvn clean install` so the OpenAPI generator re-generates the client interfaces and models.

---

## JSON Schemas

* Each workflow declares `dataInputSchema` and (optionally) an output schema via the **`workflow-output-schema`** extension.
* Schemas live under `/schemas` in each module and are referenced with relative paths in the workflow YAML.

---

## Troubleshooting

* **Model shape mismatch in OpenAPI-generated classes**

    * Symptom: nested `workflowdata` types look self-referential or missing properties (e.g., `ShippingOutPayload1 workflowdata;`).
    * Context: Using the output schema with a `title` on nested objects can sometimes confuse the model generator.
    * **Workaround**: remove `title` from the nested `workflowdata` object **in the OpenAPI schema** (keep titles at the top-level schemas). This avoids recursive/self-referential model generation.
    * Action: Track upstream fix in Kogito/SonataFlow model generation.

* **Ports**

    * If you see connection errors in remote mode, check each service’s `application.properties` for the actual port and that the parent config points to the same addresses.

* **CORS / Dev UI**

    * When testing via browser or Swagger UI, you may need to allow CORS or use `curl`/REST client.

---

## Why two approaches?

* Start with **Local Subflows** to iterate quickly on state logic and data mapping.
* Promote to **Remote Subflows** when you need independent deploy/scale/versioning, language freedom, or external ownership of subdomains (e.g., a Shipping team).

---

## Next steps

* Add retries/timeouts/circuit breakers to remote function calls.
* Extend schemas with validation and richer outputs.
* Add CI to verify OpenAPI client generation on changes.

---

## License

Apache-2.0 (follow the headers in source files).
