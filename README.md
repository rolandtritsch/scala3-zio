# scala3-zio-template

A production-ready HTTP service template built with Scala 3 and ZIO. Features comprehensive health monitoring, database integration, AWS S3 support, and containerized deployment.

> **For Contributors**: See [CONTRIBUTING.md][] for development workflow and [CLAUDE.md][] for implementation details and architecture.

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

### Installation

1. Clone the repository:

```bash
git clone <repository-url>
cd scala3-zio-template
```

2. Set up environment variables:

```bash
cp .env.example .env
# Edit .env with your AWS and database credentials
```

3. Run the application:

```bash
make run
```

The server will start on [http://localhost:8080][]

[http://localhost:8080]: http://localhost:8080

### Using the API

Test the endpoints:

```bash
# Basic health check
curl http://localhost:8080/health

# Comprehensive health check
curl http://localhost:8080/health-deep

# Echo endpoint
curl -X POST http://localhost:8080/echo -d "Hello, World!"

# Root endpoint
curl http://localhost:8080/
```

## Configuration

This application uses a centralized, type-safe configuration system powered by ZIO Config. All configuration is loaded from environment variables and validated at startup, ensuring fail-fast behavior when required settings are missing.

### Configuration Architecture

Configuration is organized into three distinct areas:

- **Server Configuration**: HTTP server settings
- **Database Configuration**: PostgreSQL connection parameters
- **AWS Configuration**: AWS credentials and region

All configuration logic is centralized in `src/main/scala/config/AppConfig.scala`, making it easy to understand what the application requires and how it's configured.

### Environment Variables

The application requires configuration via environment variables. Create a `.env` file based on `.env.example`:

#### Required Configuration

```bash
# Database Configuration (REQUIRED - app will not start without these)
DATABASE_HOST=localhost
DATABASE_PORT=5432
DATABASE_NAME=postgres
DATABASE_USER=your_username
DATABASE_PASSWORD=your_password

# AWS Configuration (REQUIRED - app will not start without these)
AWS_ACCESS_KEY_ID=<your-key-id>
AWS_SECRET_ACCESS_KEY=<your-secret-key>
AWS_REGION=us-east-1
```

#### Optional Configuration

```bash
# Server Configuration (optional - defaults shown)
SERVER_PORT=8080
```

**Note**: The application uses a **fail-fast approach**. If any required configuration is missing, the application will refuse to start and display a clear error message indicating which variable is missing. This prevents runtime surprises and ensures proper configuration.

## Docker Deployment

### Quick Start with Docker

Start the application with docker-compose:

```bash
make docker-up
```

This will:

- Start a PostgreSQL database container
- Build and start the application container
- Expose the API on [http://localhost:8080][]

Stop the services:

```bash
make docker-down
```

### Build Your Own Image

Build the Docker image:

```bash
make docker-build
```

Run directly with Docker:

```bash
make docker-run
```

## Contributing

Contributions are welcome! Please see [CONTRIBUTING.md][] for:

- Development workflow
- Branch naming conventions
- Testing requirements
- Code quality standards
- Pull request process

## Documentation

- **[README.md][]** (this file): What the project is and how to use it
- **[CONTRIBUTING.md][]**: How to contribute and development workflow
- **[CLAUDE.md][]**: Implementation details and architectural decisions

[README.md]: ./README.md
[CONTRIBUTING.md]: ./CONTRIBUTING.md
[CLAUDE.md]: ./CLAUDE.md
