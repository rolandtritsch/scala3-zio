.DEFAULT_GOAL := help

SCALA_VERSION := $(shell sbt -Dsbt.log.noformat=true -error 'print scalaVersion')

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

.PHONY: watch-test
watch-test: ## Continuously run tests on file changes
	sbt ~test

.PHONY: watch
watch: ## Continuously compile on file changes
	sbt ~compile
