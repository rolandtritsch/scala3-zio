.DEFAULT_GOAL := help

.PHONY: help
help: ## Show help for all targets
	@echo "Available targets:"
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | awk 'BEGIN {FS = ":.*?## "}; {printf "\033[36m%-20s\033[0m %s\n", $$1, $$2}'

.PHONY: compile
compile: ## Compile the project
	./mill app.compile

.PHONY: run
run: ## Run the application
	./mill app.run

.PHONY: test
test: ## Run tests
	./mill app.test

.PHONY: console
console: ## Start a REPL with dependencies loaded
	./mill app.console

.PHONY: assembly
assembly: ## Build an executable JAR
	./mill app.assembly

.PHONY: clean
clean: ## Clean build artifacts
	./mill clean

.PHONY: resolve
resolve: ## Show all available tasks
	./mill resolve app._

.PHONY: watch
watch: ## Continuously compile on file changes
	./mill --watch app.compile
