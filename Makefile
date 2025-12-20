SCALA_VERSION := $(shell sbt -Dsbt.log.noformat=true -error 'print scalaVersion')

.DEFAULT_GOAL := help

.PHONY: help
help: ## Show help for all targets
	@echo "Available targets:"
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | awk 'BEGIN {FS = ":.*?## "}; {printf "\033[36m%-20s\033[0m %s\n", $$1, $$2}'

.PHONY: assembly
assembly: ## Build an executable JAR
	sbt assembly

.PHONY: clean
clean: ## Clean build artifacts
	sbt clean

.PHONY: compile
compile: ## Compile the project
	sbt compile

.PHONY: console
console: ## Start a REPL with dependencies loaded
	sbt console

.PHONY: coverage
coverage: ## Run tests with coverage
	sbt clean coverage test coverageReport

.PHONY: coverage-doc
coverage-doc: coverage ## Run tests with coverage and generate report to docs/coverage
	rm -rf docs/coverage && mkdir -p docs/coverage
	cp -r target/scala-$(SCALA_VERSION)/scoverage-report/* docs/coverage

.PHONY: docker-build
docker-build: ## Build Docker image
	docker build -t scala3-zio-template:latest .

.PHONY: docker-build-no-cache
docker-build-no-cache: ## Build Docker image without cache
	docker build --no-cache -t scala3-zio-template:latest .

.PHONY: docker-clean
docker-clean: docker-down ## Clean Docker resources (containers, images)
	docker rmi scala3-zio-template:latest || true

.PHONY: docker-down
docker-down: ## Stop and remove docker-compose services
	docker compose down

.PHONY: docker-health
docker-health: ## Check container health status
	docker inspect --format='{{json .State.Health}}' scala3-zio-template | python3 -m json.tool

.PHONY: docker-logs
docker-logs: ## Show Docker container logs (follow mode)
	docker logs -f scala3-zio-template

.PHONY: docker-logs-compose
docker-logs-compose: ## Show docker-compose logs (follow mode)
	docker compose logs -f

.PHONY: docker-logs-static
docker-logs-static: ## Show Docker container logs (static)
	docker logs scala3-zio-template

.PHONY: docker-ps
docker-ps: ## Show docker-compose service status
	docker compose ps

.PHONY: docker-restart
docker-restart: ## Restart docker-compose services
	docker compose restart

.PHONY: docker-run
docker-run: ## Run Docker container directly (without compose)
	docker run -d \
		--name scala3-zio-template \
		--env-file .env \
		-p 8080:8080 \
		--memory="2g" \
		--cpus="2.0" \
		scala3-zio-template:latest

.PHONY: docker-shell
docker-shell: ## Open shell in running container
	docker exec -it scala3-zio-template sh

.PHONY: docker-stop
docker-stop: ## Stop and remove Docker container
	docker stop scala3-zio-template || true
	docker rm scala3-zio-template || true

.PHONY: docker-up
docker-up: ## Start services with docker-compose
	docker compose up -d

.PHONY: format
format: ## Format all source code
	sbt scalafmtAll

.PHONY: format-check
format-check: ## Check if code is formatted correctly
	sbt scalafmtCheckAll

.PHONY: lint
lint: ## Auto-fix linting issues
	sbt scalafixAll

.PHONY: lint-check
lint-check: ## Check for linting issues (CI mode)
	sbt "scalafix --check"

.PHONY: markdownlint
markdownlint: ## Format and fix all markdown files
	markdownlint-cli2 --fix '**/*.md'

.PHONY: markdownlint-check
markdownlint-check: ## Check if markdown is formatted correctly
	markdownlint-cli2 '**/*.md'

.PHONY: resolve
resolve: ## Show all available tasks
	sbt tasks

.PHONY: run
run: ## Run the application
	sbt run

.PHONY: scala-doc
scala-doc: ## Generate Scaladoc API documentation to docs/
	sbt doc
	rm -rf docs/ && mkdir -p docs/
	cp -r target/scala-$(SCALA_VERSION)/api/* docs/

.PHONY: test
test: ## Run tests
	sbt test

.PHONY: watch
watch: ## Continuously compile on file changes
	sbt ~compile

.PHONY: watch-test
watch-test: ## Continuously run tests on file changes
	sbt ~test
