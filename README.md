# scala3-zio - My first ZIO app/service

> **For Contributors**: See [CLAUDE.md][] for developer documentation, code style guidelines, and contribution workflow.

## Prerequisites

- JVM (Java 21 or higher)
- SBT 1.10.5 (see installation instructions at [https://www.scala-sbt.org/download.html][])

## Project Structure

```text
.
├── build.sbt          # SBT build configuration
├── project/           # SBT project configuration
│   ├── build.properties
│   └── plugins.sbt
├── src/
│   ├── main/scala/    # Application source code
│   └── test/scala/    # Test source code
└── target/            # Build output (generated)
```

## Technology Stack

- **Scala 3.3.4** - Modern Scala with improved syntax and features
- **ZIO 2.1.13** - Functional effect system for type-safe, composable programs
- **SBT** - Standard Scala build tool

[https://www.scala-sbt.org/download.html]: https://www.scala-sbt.org/download.html

## Quick Start

### Initial Setup

After cloning the repository, set up git hooks to ensure code quality:

```bash
./scripts/git-hooks-setup.sh
```

This installs a pre-commit hook that automatically checks formatting and linting before each commit.

### Development Commands

See all available commands:

```bash
make help
```

## Git Hooks

This project uses git hooks to maintain code quality. The hooks are stored in `git-hooks/` and symlinked to `.git/hooks/` during setup.

### Pre-commit Hook

The pre-commit hook runs automatically before each commit and:

- Checks if code is properly formatted (`make format-check`)
- Checks for linting issues (`make lint-check`)
- Blocks the commit if checks fail

If the pre-commit hook fails:

```bash
# Fix formatting issues
make format

# Fix linting issues
make lint

# Retry the commit
git commit
```

### Bypassing Hooks

In rare cases where you need to bypass hooks (not recommended):

```bash
git commit --no-verify
```

## Docker Deployment

### Building and Running

Build the Docker image:

```bash
make docker-build
```

Run with docker-compose (recommended):

```bash
make docker-up
```

View logs:

```bash
make docker-logs-compose
```

Stop services:

```bash
make docker-down
```

### Environment Variables

The Docker setup requires a `.env` file with AWS credentials:

```bash
AWS_ACCESS_KEY_ID=<your-key-id>
AWS_SECRET_ACCESS_KEY=<your-secret-key>
AWS_DEFAULT_REGION=us-east-1
```

### Container Configuration

- **Port**: 8080 (mapped to host 8080)
- **Memory**: 2GB limit, 512MB reserved
- **CPU**: 2.0 CPUs limit, 0.5 reserved
- **Health Check**: GET /health every 30s

### Available Docker Commands

```bash
make docker-build          # Build Docker image
make docker-build-no-cache # Build without cache
make docker-run            # Run container directly
make docker-stop           # Stop container
make docker-logs           # Follow container logs
make docker-up             # Start with docker-compose
make docker-down           # Stop docker-compose
make docker-ps             # Show service status
make docker-shell          # Open shell in container
make docker-health         # Check health status
make docker-clean          # Clean Docker resources
```

### Testing Docker Setup

Run the automated test suite:

```bash
./scripts/docker-test.sh
```

[CLAUDE.md]: ./CLAUDE.md
