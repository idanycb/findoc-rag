# FinDoc Analyzer: High-Performance General Purpose RAG

**FinDoc Analyzer** is a self-hosted application designed for high-precision financial document analysis using a Retrieval-Augmented Generation (RAG) architecture.

While the system is architected for future optimization in financial auditing (SEC filings), it currently serves as a powerful general-purpose knowledge retrieval engine capable of ingesting any PDF/Text data and providing grounded, cited answers.

## 🚀 Key Architectural Pillars

### 1. Single-Instance Document Vault
Each deployment serves one individual, household, or organization. Multiple users can share the same instance, and all authenticated users work against the same private document vault.

### 2. Reactive Ingestion Pipeline (Unstructured.io)
To handle massive documents without blocking, the ingestion engine uses Project Reactor:
- **Streaming Parser:** Binary data is streamed to an Unstructured.io container.
- **Semantic Partitioning:** Recognizes tables, narrative, and titles as reactive elements.
- **Page-by-Page Ingestion:** Vectors are stored in a streaming fashion, providing near-instant "Time-to-First-Vector."

### 3. Production-Grade RAG Pipeline
The retrieval orchestrator uses a "Coarse-to-Fine" strategy:
- **Query Expansion:** Decomposes user questions into diverse semantic variations.
- **List-wise Re-ranking:** A second-stage LLM-as-a-Judge process evaluates retrieved chunks for explicit relevance in a single batch (Rate-limit friendly).
- **Deterministic Attribution:** Every AI claim is cited with a specific source filename and page number.

## 🛠️ Tech Stack
- **Backend:** Spring Boot 4.x, Java 21, Project Reactor.
- **AI Framework:** LangChain4j (Latest) + Groq (Llama-3.1-8b).
- **Persistence:** PostgreSQL 16 + pgvector.
- **Infrastructure:** AWS S3 (Binary Storage), AWS SQS (Event Orchestration).
- **Parsing:** Unstructured.io (Containerized).

## 🗺️ Future Roadmap
- [ ] **SEC EDGAR Optimization:** Transition to authoritative SEC feeds and semantic parsing for 10-K/10-Q sections.
- [ ] **Hybrid Quantitative RAG:** Implementing SQL tools to query structured data alongside vectors.
- [ ] **Cost Analytics:** Instance-level token usage tracking and analysis cost reporting.