COMPOSE := docker compose -f docker-compose.yml

.PHONY: dev prod demo prod-down demo-down prod-logs demo-logs help

help:
	@echo "Targets:"
	@echo "  make dev        Start local dev dependencies (Postgres + Unstructured)"
	@echo "  make prod       Build and run the full production stack"
	@echo "  make demo       Build and run the full demo stack (quotas enabled)"
	@echo "  make prod-down  Stop the production stack"
	@echo "  make demo-down  Stop the demo stack"
	@echo "  make prod-logs  Follow production stack logs"
	@echo "  make demo-logs  Follow demo stack logs"

dev:
	$(COMPOSE) -f docker-compose.dev.yml up pgvector unstructured-api -d

prod:
	$(COMPOSE) -f docker-compose.prod.yml up --build -d

demo:
	$(COMPOSE) -f docker-compose.demo.yml up --build -d

prod-down:
	$(COMPOSE) -f docker-compose.prod.yml down

demo-down:
	$(COMPOSE) -f docker-compose.demo.yml down

prod-logs:
	$(COMPOSE) -f docker-compose.prod.yml logs -f

demo-logs:
	$(COMPOSE) -f docker-compose.demo.yml logs -f
