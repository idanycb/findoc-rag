COMPOSE := docker compose -f docker-compose.yml
DEV_BUILD_SERVICES := backend edgar-service frontend
DEV_BUILD_ARGS := $(wordlist 2,$(words $(MAKECMDGOALS)),$(MAKECMDGOALS))
DEV_BUILD_SERVICE := $(firstword $(DEV_BUILD_ARGS))

ifneq ($(filter dev-build,$(firstword $(MAKECMDGOALS))),)
ifneq ($(DEV_BUILD_SERVICE),)
ifneq ($(word 2,$(DEV_BUILD_ARGS)),)
$(error dev-build accepts at most one service: $(DEV_BUILD_SERVICES))
endif
ifeq ($(filter $(DEV_BUILD_SERVICE),$(DEV_BUILD_SERVICES)),)
$(error invalid dev service '$(DEV_BUILD_SERVICE)'; expected one of: $(DEV_BUILD_SERVICES))
endif
.PHONY: $(DEV_BUILD_SERVICE)
$(DEV_BUILD_SERVICE):
	@:
endif
endif

.PHONY: dev dev-build dev-down dev-clean prod demo prod-down demo-down prod-clean demo-clean prod-logs demo-logs eval-record-corpus eval-ingest eval-retrieval eval-production eval-prepare-generation eval-generate eval-judge eval-calibrate eval help

help:
	@echo "Targets:"
	@echo "  make dev        Start local dev dependencies (Postgres + Docling + EDGAR)"
	@echo "  make dev-build [service]"
	@echo "                  Build one dev service, or all dev images when omitted"
	@echo "                  Services: backend, edgar-service, frontend"
	@echo "  make dev-down   Stop local dev dependencies"
	@echo "  make dev-clean  Stop local dev dependencies and delete Postgres volume data"
	@echo "  make prod       Build and run the full production stack"
	@echo "  make demo       Build and run the full demo stack (quotas enabled)"
	@echo "  make prod-down  Stop the production stack"
	@echo "  make demo-down  Stop the demo stack"
	@echo "  make prod-clean Stop the production stack and delete Postgres volume data"
	@echo "  make demo-clean Stop the demo stack and delete Postgres volume data"
	@echo "  make prod-logs  Follow production stack logs"
	@echo "  make demo-logs  Follow demo stack logs"
	@echo "  make eval-record-corpus BASE_URL=... ACCESSION='a b' [TICKER=TSLA] [RAW_HTML=path]"
	@echo "                  Record local SEC section fixtures into evals/corpus with SHA256 manifest"
	@echo "  make eval-ingest      Run the opt-in ingestion coverage preflight/evaluation"
	@echo "  make eval-retrieval   Run the opt-in retrieval preflight/evaluation"
	@echo "  make eval-production  Run one production retrieval-to-Gemini canary (loads backend/.env)"
	@echo "  make eval-prepare-generation"
	@echo "                  Freeze verified contexts with the exact production prompt; no external call"
	@echo "  make eval-generate [CASE_ID=id] [RUN_DIR=path] [MODEL=name]"
	@echo "                  Call Gemini and write structured answers, scores, latency, and token usage"
	@echo "  make eval-judge [BACKEND=codex|claude_cli|ollama] [MODEL=name] [RUN_DIR=path]"
	@echo "                  Judge the latest or selected eval report with a locked-down local CLI backend"
	@echo "  make eval-calibrate"
	@echo "                  Show calibration label-set coverage for the local judge harness"
	@echo "  make eval       Run ingestion, retrieval, and local generation-input preparation"

dev:
	$(COMPOSE) -f docker-compose.dev.yml up pgvector docling-serve edgar-service -d

dev-build:
	$(COMPOSE) -f docker-compose.dev.yml build $(DEV_BUILD_SERVICE)

dev-down:
	$(COMPOSE) -f docker-compose.dev.yml down

dev-clean:
	$(COMPOSE) -f docker-compose.dev.yml down -v

prod:
	$(COMPOSE) -f docker-compose.prod.yml up --build -d

demo:
	$(COMPOSE) -f docker-compose.demo.yml up --build -d

prod-down:
	$(COMPOSE) -f docker-compose.prod.yml down

demo-down:
	$(COMPOSE) -f docker-compose.demo.yml down

prod-clean:
	$(COMPOSE) -f docker-compose.prod.yml down -v

demo-clean:
	$(COMPOSE) -f docker-compose.demo.yml down -v

prod-logs:
	$(COMPOSE) -f docker-compose.prod.yml logs -f

demo-logs:
	$(COMPOSE) -f docker-compose.demo.yml logs -f

eval-record-corpus:
	cd evals && uv run python tools/record_corpus.py --base-url "$(BASE_URL)" --ticker "$(or $(TICKER),TSLA)" $(foreach accession,$(ACCESSION),--accession "$(accession)") $(if $(RAW_HTML),--raw-html "$(RAW_HTML)")

eval-ingest:
	docker info >/dev/null
	cd backend && ./mvnw verify -P eval -Dit.test=IngestionCoverageIT

eval-retrieval:
	docker info >/dev/null
	cd backend && ./mvnw verify -P eval -Dit.test=RetrievalEvaluationIT

eval-production:
	docker info >/dev/null
	cd backend && set -a; . ./.env; set +a; ./mvnw verify -P eval -Dit.test=RetrievalEvaluationIT#productionServicePathAnswersTheCriticalAmendmentQuestionWithGemini

eval-prepare-generation:
	cd evals && uv run python run_eval.py --stage prepare-generation

eval-generate:
	cd evals && uv run --env-file ../backend/.env python run_eval.py --stage generation $(if $(CASE_ID),--case-id "$(CASE_ID)") $(if $(RUN_DIR),--run-dir "$(RUN_DIR)") $(if $(MODEL),--model "$(MODEL)")

eval-judge:
	cd evals && uv run python judge_answers.py --backend "$(or $(BACKEND),codex)" $(if $(MODEL),--model "$(MODEL)") $(if $(RUN_DIR),--run-dir "$(RUN_DIR)")

eval-calibrate:
	cd evals && uv run python judge_answers.py --calibrate

eval: eval-ingest eval-retrieval eval-prepare-generation
