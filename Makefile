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

.PHONY: dev dev-build dev-down dev-clean prod demo prod-down demo-down prod-clean demo-clean prod-logs demo-logs help

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
