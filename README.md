<div align="center">

# Enterprise Compliance Auditor (ECA)

**A retrieval-augmented, human-in-the-loop engine for auditing enterprise contracts against regulatory frameworks.**

Upload a contract -> an AI agent flags risky clauses *grounded in real regulations* -> the pipeline pauses for certified human sign-off -> every decision is written to an immutable audit ledger.

![Java](https://img.shields.io/badge/Java-21-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4-brightgreen)
![Spring AI](https://img.shields.io/badge/Spring%20AI-RAG-blue)
![OpenAI](https://img.shields.io/badge/OpenAI-Embeddings%20%2B%20Chat-black)
![Tests](https://img.shields.io/badge/tests-passing-success)

</div>

---

## Overview

Corporate legal, financial, and compliance teams manually audit thousands of pages of contracts and vendor agreements line-by-line. It is slow, expensive, and error-prone — and a single missed liability or data-sharing clause can mean significant regulatory exposure.

Most "AI contract tools" are thin, single-prompt wrappers: they have no state, no memory, and no grounding, so they hallucinate risk and can't be trusted on high-stakes documents.

**ECA is built differently.** It treats an audit as a *stateful, resumable process* rather than a one-shot prompt, grounds every judgment in a retrievable corpus of real regulations (so flags cite the rule they came from, not the model's general intuition), and **cannot complete an audit without a human approving the high-risk findings** — the pipeline physically freezes and waits.

---

## Key Capabilities (Implemented)

- **Retrieval-Augmented Analysis (RAG).** A curated corpus of regulations (GDPR, HIPAA, SOX, and general contract-risk rules) is embedded into a vector store. For each contract, ECA performs a semantic similarity search to retrieve the *relevant* rules, then augments the model prompt with them — so the analysis is grounded in actual regulatory text, not the model's memory.

- **Human-in-the-Loop State Machine.** A custom-built state machine drives every audit through `INGEST -> ANALYZE -> HUMAN_REVIEW -> APPLY -> LOG -> DONE`. When a **high-risk** clause is found, the machine *freezes its state* and refuses to advance until a certified human approves or rejects the flag — then resumes exactly where it paused, across separate HTTP requests.

- **Structured, Grounded Flagging.** The analysis model returns typed, structured output (risk level, plain-English reason, and the *exact verbatim clause*) mapped directly into domain objects — with prompt-level guardrails against hallucinating clauses that don't appear in the source contract.

- **Immutable Audit Ledger.** Every model inference and every human decision is written as an append-only, timestamped ledger entry, queryable in order — providing the tamper-evident trail required for regulatory review.

- **Provider-Portable AI Layer.** Built on Spring AI's abstraction, so the embedding and chat providers can be swapped (OpenAI today) without touching business logic.

- **Tested.** Unit and integration test coverage across the state machine, controller, retrieval service, and ledger persistence.

---

## Architecture

### The Audit State Machine

The heart of the system. Each audit is a single evolving context object that flows through the graph and is **frozen on pause**:

```
┌─────────────────────────────────┐
│ AuditContext (state) │
│ contract · flags · node · id │
└─────────────────────────────────┘
│
INGEST ──▶ ANALYZE ──▶ [ HUMAN_REVIEW ] ──▶ APPLY ──▶ LOG ──▶ DONE
│ ▲ │
│ │ │ ◀── machine FREEZES here on any
RAG + LLM │ │ HIGH-risk flag; will not
produces flags │ │ advance until a human decides
│ │
human approves/rejects
(separate HTTP request)
```

The split between `run()` (top freeze) and `resume()` (freeze done) *is* the human-in-the-loop: a guard makes it structurally impossible to cross the middle of the pipeline until a pending high-risk flag has a recorded human decision.

### The RAG Knowledge Layer

```
regulations corpus ──▶ chunked ──▶ OpenAI embeddings ──▶ VectorStore
│
contract text ──▶ semantic similarity search ────────────────┘
│
top-K relevant rules
│
injected into the analysis prompt
│
LLM flags clauses AGAINST those rules
```

### Request Flow

```
Client (REST / SSE)
│
▼
AuditController ──▶ AuditStateMachine ──▶ RetrievalService ──▶ VectorStore ──▶ OpenAI
│ │
│ └────────────▶ ChatClient (structured output) ──▶ OpenAI
▼
AuditRepository (persisted audits) · LedgerRepository (immutable trail) ──▶ Database
```

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language / Runtime | Java 21 |
| Framework | Spring Boot 3.4, Spring Web (MVC) |
| AI / RAG | Spring AI — `ChatClient`, `EmbeddingModel`, `VectorStore` |
| Models | OpenAI `gpt-4o-mini` (analysis) · `text-embedding-3-small` (embeddings) |
| Persistence | Spring Data JPA · Hibernate (immutable ledger) |
| Build | Maven |
| Testing | JUnit 5 · Spring Boot Test |

---

## API Reference

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/v1/audit` | Start an audit. Body: `{ "contractText": "..." }`. Runs analysis; returns the context (pauses at `HUMAN_REVIEW` if high-risk flags are found). |
| `POST` | `/api/v1/audit/{id}/decision` | Record a human decision on a flag. Body: `{ "flagId": "...", "decision": "APPROVED" }`. Writes to the ledger. |
| `POST` | `/api/v1/audit/{id}/resume` | Resume a paused audit. Guarded — refuses while any high-risk flag is still pending. Walks the pipeline to `DONE`. |
| `GET` | `/api/v1/audit/{id}/ledger` | Return the ordered, immutable audit trail for a run. |
| `GET` | `/api/v1/audit/search?q=...` | Semantic search over the regulation corpus (retrieval diagnostics). |

### Example: the audit lifecycle

```bash
# 1. Start an audit on a risky contract
curl -X POST localhost:8080/api/v1/audit -H 'Content-Type: application/json' -d '{
"contractText": "Party A shall be liable for any and all damages without limitation..."
}'
# state: HUMAN_REVIEW, flags: [ { level: HIGH, reason: "...", quotedSpan: "..." } ]

# 2. A human approves the flag (recorded to the ledger)
curl -X POST localhost:8080/api/v1/audit/{id}/decision -H 'Content-Type: application/json' -d '{
"flagId": "...", "decision": "APPROVED"
}'

# 3. Resume — the guard now passes, pipeline completes
curl -X POST localhost:8080/api/v1/audit/{id}/resume
# state: DONE

# 4. Pull the immutable trail
curl localhost:8080/api/v1/audit/{id}/ledger
# [ FLAG_RAISED (HIGH, timestamped), DECISION_MADE (APPROVED, timestamped) ]
```

---

## Getting Started

**Prerequisites:** JDK 21, Maven, an OpenAI API key.

```bash
# 1. Set your key (read from the environment — never hardcoded)
export OPENAI_API_KEY=sk-...

# 2. Run
./mvnw spring-boot:run
```

On startup, ECA embeds the regulation corpus into the vector store (you'll see `ingested N rules` in the logs) and serves on `http://localhost:8080`.

```bash
# Run the test suite
./mvnw test
```

---

## Design Decisions & Engineering Notes

Choices made deliberately — and the reasoning behind them:

- **Hand-built state machine over an off-the-shelf agent framework.** The human-in-the-loop pause/freeze/resume is implemented directly rather than imported, keeping the control flow fully transparent and testable. For a linear, auditable pipeline, an explicit state machine is easier to reason about — and to defend in a regulatory context — than a general agent graph.

- **Enums persisted as `STRING`, never ordinal.** In an audit table, storing enums by ordinal index would silently corrupt historical records if the enum is ever reordered. `@Enumerated(EnumType.STRING)` makes the trail stable over time.

- **Separate output DTO from the domain model.** The LLM fills a plain `FlagDto` (level, reason, span); the system assigns identity and lifecycle state (`PENDING APPROVED/REJECTED`). The model never sets IDs or decisions — those are the system's responsibility.

- **The vector store is behind an interface.** `SimpleVectorStore` (in-memory) is used for development; because everything depends on the `VectorStore` interface, swapping in pgvector for production is a configuration change, not a code change.

- **Append-only ledger.** Ledger entries are only ever inserted, never updated or deleted — the property that makes the trail trustworthy.

- **Prompt-level anti-hallucination guardrails.** The analysis prompt requires verbatim clause quotes and instructs the model to return an empty result when nothing is risky — because in compliance, a confident wrong answer is worse than no answer.

---

## Vision & Roadmap

ECA's current implementation is the core engine. The broader product vision — and the direction of ongoing work:

**Near-term**
- [ ] **SSE streaming** — stream the analysis reasoning token-by-token to the client in real time.
- [ ] **Durable persistence** — migrate from in-memory H2 to PostgreSQL + `pgvector`, making both the ledger and the embedded corpus persistent across restarts.
- [ ] **Authentication & authorization** — certified-reviewer identity attached to every decision.
- [ ] **Document ingestion** — PDF/DOCX upload and parsing for real contracts.
- [ ] **Web workspace** — a dashboard to upload documents, watch the live pipeline, and action flags.

**Longer-term**
- [ ] **Multi-agent auditing** — specialized cooperating agents (Legal, Financial, Regulatory) instead of a single analyzer.
- [ ] **Multi-tenant isolation** — per-tenant row-level security and client-scoped encryption for enterprise data separation.
- [ ] **Expanded regulatory corpus** — full GDPR / HIPAA / SOX / CCPA rule sets with citation-level provenance.

---

## License

This project is available under the MIT License.

---

<div align="center">
<sub>Built with Java 21 · Spring Boot · Spring AI</sub>
</div>
