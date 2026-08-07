# SOLUTION.md - Design & Trade-offs

XML-to-JSON Content Transformation Service for legal documents (French Content Systems assignment).

## What it does

Ingests legal XML judgments, validates them against the provided XSD, transforms valid documents
to a normalized JSON record plus a plain-text artifact (for AI/RAG) using XSLT 3.0 on Saxon-HE,
and publishes artifacts keyed by `content_id` so repeated submissions never produce duplicate outputs.

```
POST /api/documents --> XSD validation --> XSLT 3.0 (Saxon-HE) --> publish artifacts
        |                     |                                        |
POST /api/batches         diagnostics recorded,             {content_id}/normalized.json
(local dir or s3://      REJECTED, never published          {content_id}/fulltext.txt
prefix, concurrent)                                         (batch runs grouped under bulk/)
```

Artifacts are written through an `ArtifactRepository` seam with two implementations:
local filesystem (default) and S3 (`app.storage.type=s3`) - same layout either way.

## Architecture

Layered Spring Boot 3 / Java 17 service:

- `controller` - REST endpoints (documents, batches). Thin; delegates to services.
- `service` - one class per pipeline stage: `XmlValidationService`, `XmlTransformationService`,
  `ContentIdExtractor` (StAX, streaming), `DocumentProcessingService` (orchestrator),
  `BatchProcessingService` (concurrent folder processing).
- `repository` - `ArtifactRepository` and `ProcessingRecordRepository` interfaces.
  Artifacts: filesystem (local default) or S3 (`S3ArtifactRepository`). This seam is what
  let the S3 implementation drop in with zero business-logic changes.
- `constant`, `dto`, `entity`, `mapper`, `config`, `exception` - supporting layers.

### Key design decisions and trade-offs

| Decision | Why | Trade-off / production path |
|---|---|---|
| Filesystem artifact store (local default) | Artifacts survive restarts and are inspectable by reviewers | S3 implementation exists behind the same interface and is what the AWS deployment runs |
| Pluggable status registry | In-memory `ConcurrentHashMap` by default (no DB dependency to run locally); `app.registry.type=dynamodb` switches to a DynamoDB table keyed by `content_id` (Implemented — the live deployment uses it, so duplicate detection survives restarts and would be shared across instances) | In-memory mode is per-instance and lost on restart; it exists purely as the zero-dependency default |
| Synchronous single-doc API | Simple request/response contract; a doc processes in milliseconds | At volume, switch to queue-driven ingestion (see AWS section) |
| Async batch API (202 + poll) | Batches can be large; the client should not block | Job state is in memory; production would persist job state |
| Idempotency = content_id + SHA-256 | Same content resubmitted -> no-op; changed content, same id -> republish (latest wins) | Alternative "first wins/version everything" is a product decision; hash comparison makes either trivial |
| XSLT 3.0 `xsl:output method="json"` producing XDM maps | The entire normalization lives in one declarative stylesheet, compiled once at startup | JSON key order is non-deterministic (spec-compliant); Saxon-HE has no streaming XSLT (see scaling notes) |
| Whole-document string transform | Legal judgments are typically KB-MB; a 10 MB configurable cap guards memory | Truly huge inputs would need Saxon-EE streaming or a chunking pre-pass |
| Validation collects *all* errors | Operators get complete diagnostics in one submission instead of fix-one-resubmit loops | Slightly slower than fail-fast for pathological inputs (bounded by size cap) |

### Security / robustness

- XXE hardened: DTDs and external entities disabled on every parser (validator, StAX, Saxon).
- Path traversal guard on `content_id`-derived output paths.
- Configurable max document size (413 on breach).
- Malformed XML, wrong namespace, missing ids -> structured 4xx with diagnostics, never a 500.
- API authentication: when `app.security.api-key` is set, `/api/**` requires an
  `X-API-Key` header (constant-time comparison, 401 otherwise). The key lives in SSM
  Parameter Store, injected as an env var - never in the repo. Health probes and the
  OpenAPI docs stay public; metrics are public here for demo purposes but in production
  would sit on a separate management port scraped over a private network.
- Evolution path: a shared key is proportionate for a service-to-service ingestion API
  behind TLS. At scale I would move to OAuth2 client-credentials with an identity
  provider - AWS Cognito, Keycloak, or Azure AD/Entra - validated as JWTs by Spring
  Security's resource-server support, and mTLS between services if a mesh is in play.

### Operability

- `/actuator/health` with liveness/readiness probes (container-orchestrator ready).
- Micrometer metrics: `documents.received/published/rejected/duplicate` counters,
  `documents.validation.duration` and `documents.transformation.duration` timers,
  exposed via `/actuator/metrics` and `/actuator/prometheus`.
- Concurrency externalized: `app.pipeline.concurrency` (or `APP_PIPELINE_CONCURRENCY` env var).
- Profiles: `application-local.yml`, `application-docker.yml`, `application-aws.yml` -
  config via environment, not code.
- In AWS: container logs stream to CloudWatch Logs (`awslogs` driver), pipeline counters
  publish to CloudWatch metrics (namespace `xmltojson`), with alarms on error spikes and
  instance health (see below).

## Cloud-ready packaging and evolution plan

How I would run this in AWS - **and, scaled to free-tier services, how it is actually
running today**: the service is deployed on a free-tier AWS account (ECR image, EC2 micro
instance with an IAM instance role, S3 input/output buckets, CloudWatch logs + metrics +
alarms, GitHub Actions pushing images via OIDC) and working end to end. The design below
is the same architecture at production scale.

### Where inputs and outputs live

What I did:
- Inputs: S3 bucket for raw XML, one object per document; the batch API accepts an
  `s3://bucket/prefix` and processes every XML object under it.
- Outputs: S3 bucket with the same layout as local: `{content_id}/normalized.json` and
  `.../fulltext.txt`, batches grouped under `bulk/`.
- Status/diagnostics: DynamoDB table keyed by `content_id` (on-demand billing).

As an alternative at scale, I would:
- Have producers upload straight to the input bucket and S3 event notifications drive
  processing instead of API calls; lifecycle rules tier old inputs to infrequent access.

### Triggering processing at volume

What I did:
- The batch endpoint pulls an `s3://` prefix and fans out across a configurable worker
  pool (`app.pipeline.concurrency`) - right-sized for a single free-tier instance.

As an alternative at scale, I would:
- Wire S3 `ObjectCreated` -> SQS and have the service consume the queue (long polling).
  The REST API stays for ad-hoc submissions and retrieval; the queue becomes the
  ingestion path. SQS adds buffering, retry with backoff, and a dead-letter queue for poison messages
  (today's REJECTED-with-diagnostics, but with replay).
- Run on ECS Fargate with auto-scaling on queue depth, or EKS if the team already runs
  Kubernetes - same container, HPA on queue depth, IRSA instead of task roles.

### Monitoring

What I did:
- CloudWatch Logs via the `awslogs` driver (7-day retention), pipeline counters in the
  `xmltojson` CloudWatch namespace, an ERROR-log metric filter, and alarms on error rate
  and instance status checks. Locally the same metrics are on `/actuator/prometheus`.

As an alternative at scale, I would:
- Add Container Insights for CPU/memory; scrape duration percentiles from the Prometheus
  endpoint into Grafana/AMP.
- Alarm on DLQ depth > 0, rejection-rate spikes, p99 transform duration, ALB 5xx.
- Switch to structured JSON logs with `content_id` as a correlation field.

### Preventing duplicate publishing

What I did:
- SHA-256 content hash per submission; records live in DynamoDB, so duplicate detection
  survives restarts and is shared across instances - verified by restarting the live
  container and resubmitting the same document (`duplicate: true`, artifacts untouched).

As an alternative at scale, I would:
- Make the check race-proof with a DynamoDB conditional write
  (`attribute_not_exists(content_id) OR content_hash <> :new_hash`): condition fails ->
  idempotent no-op; succeeds -> transform, write artifacts, mark PUBLISHED.
- Rely on S3 keys being per `content_id`, so even racing workers converge on one artifact set;
  the conditional write just decides who does the work.

### Infrastructure as code

The AWS resources for this exercise (ECR, EC2 + instance role, S3 buckets, DynamoDB table,
CloudWatch log group/alarms, GitHub OIDC role) were created with AWS CLI commands to keep
the focus on the service itself. In a real environment the standard would be Terraform (or
CDK/CloudFormation): each of these resources maps 1:1 to a resource block, state lives in an
S3 backend with locking, and environments (dev/prod) become workspaces or separate var files.
That also makes the free-tier guardrails (lifecycle policies, retention periods, instance
sizing) reviewable in a pull request instead of living in someone's shell history.

### Evolving toward a RAG pipeline

What I did:
- The pipeline already produces RAG-ready seeds: the plain-text artifact (concatenated,
  normalized paragraphs) and a `paragraphs[]` array with ids and section types in the
  normalized JSON, all keyed by `content_id` with S3 versioning on.

Next artifacts to produce per document:

- **Chunked paragraphs with metadata** - the service already emits `paragraphs[]` with ids and section
  types; publish them as chunk records (`{content_id, paragraph_id, section, text, court,
  decision_date, jurisdiction}`) sized for embedding windows. Legal citations and party roles
  make excellent retrieval filters.
- **Embeddings** - a downstream consumer (Lambda or Fargate task) subscribes to a
  "published" SNS/EventBridge event, embeds chunks (e.g., Bedrock Titan), and upserts into a
  vector store (OpenSearch k-NN / pgvector) keyed by `content_id#paragraph_id` - republishing
  a revised judgment replaces its chunks atomically.
- **Manifest per document** - a small JSON manifest listing artifact versions and hashes, so
  downstream indexers can detect changes cheaply without re-reading full artifacts.
- **Content versioning** - keep S3 object versioning on; the DynamoDB record gains a
  `version` counter so RAG indexes can invalidate stale chunks deterministically.