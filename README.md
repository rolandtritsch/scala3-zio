# scala3-zio-template

A production-ready HTTP service template built with Scala 3 and ZIO. Features comprehensive health monitoring, database integration, AWS S3 support, and containerized deployment.

> **For Contributors**: See [CLAUDE.md][] for developer documentation, code style guidelines, and contribution workflow.

## What This Template Provides

This template solves the problem of building production-ready, cloud-native Scala services by providing:

- **HTTP Server**: RESTful API with multiple endpoints
- **Health Monitoring**: Basic and deep health checks for dependencies
- **Database Integration**: PostgreSQL support using Quill
- **Cloud Integration**: AWS S3 connectivity and health verification
- **Observability**: Structured JSON logging with request tracking
- **Containerization**: Docker and docker-compose ready
- **Graceful Shutdown**: Proper cleanup on termination
- **Code Quality**: Automated formatting, linting, and test coverage enforcement

## Prerequisites

- JVM (Java 21 or higher)
- SBT 1.11.7 or higher ([installation instructions][sbt-download])
- Docker and docker-compose (for containerized deployment)
- PostgreSQL (for database features)
- AWS account with S3 access (for AWS features)

[sbt-download]: https://www.scala-sbt.org/download.html

## Technology Stack

- **Scala 3.7.4** - Modern Scala with improved syntax and type system
- **ZIO 2.1.13** - Functional effect system for type-safe, composable programs
- **ZIO HTTP 3.6.0** - High-performance HTTP server
- **ZIO AWS S3** - AWS S3 client integration
- **Quill 4.8.6** - Compile-time query generation for PostgreSQL
- **Logback** - Structured JSON logging
- **SBT 1.11.7** - Scala build tool

## API Endpoints

The service provides the following endpoints:

- `GET /` - Root endpoint returning welcome message
- `POST /echo` - Echo endpoint that returns the request body
- `GET /health` - Basic health check (always returns 200)
- `GET /health-deep` - Comprehensive health check validating:
  - External URL connectivity (https://tedn.life)
  - AWS S3 access (list-buckets operation)
  - PostgreSQL database connection
- `POST /shutdown` - Gracefully shutdown the server

## Quick Start

### Initial Setup

1. Clone the repository
2. Set up environment variables:

```bash
cp .env.example .env
# Edit .env with your AWS and database credentials
```

3. Set up git hooks for code quality:

```bash
./scripts/git-hooks-setup.sh
```

### Running Locally

Run the application:

```bash
make run
```

The server will start on [http://localhost:8080][]

[http://localhost:8080]: http://localhost:8080

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

Create a `.env` file based on `.env.example`:

```bash
# AWS Configuration (required for /health-deep endpoint)
AWS_ACCESS_KEY_ID=<your-key-id>
AWS_SECRET_ACCESS_KEY=<your-secret-key>
AWS_DEFAULT_REGION=us-east-1

# Database Configuration (REQUIRED - app will not start without these)
DATABASE_HOST=postgres
DATABASE_PORT=5432
DATABASE_NAME=postgres
DATABASE_USER=postgres
DATABASE_PASSWORD=postgres123
```

**Note**: The application requires valid database credentials to start. The health-deep endpoint will fail gracefully if AWS credentials are missing, but database connectivity is mandatory.

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
