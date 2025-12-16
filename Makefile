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

.PHONY: format
format: ## Format all source code
	sbt scalafmtAll

.PHONY: format-check
format-check: ## Check if code is formatted correctly
	sbt scalafmtCheckAll

.PHONY: lint
lint: ## Auto-fix linting issues
	sbt "scalafixAll --rules OrganizeImports"

.PHONY: lint-check
lint-check: ## Check for linting issues (CI mode)
	sbt "scalafix --check --rules OrganizeImports"

.PHONY: resolve
resolve: ## Show all available tasks
	sbt tasks

.PHONY: run
run: ## Run the application
	sbt run

.PHONY: scala-doc
scala-doc: ## Generate Scaladoc API documentation to docs/
	@echo "Generating Scaladoc..."
	@sbt doc
	@echo "Copying documentation to docs/..."
	@rm -rf docs/
	@mkdir -p docs/
	@cp -r target/scala-3.3.4/api/* docs/
	@echo "Documentation generated at docs/index.html"

.PHONY: test
test: ## Run tests
	sbt test

.PHONY: watch
watch: ## Continuously compile on file changes
	sbt ~compile
