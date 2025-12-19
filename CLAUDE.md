# CLAUDE.md

This document provides guidance for developers working on this codebase. For user-facing documentation, see [README.md][].

## Technology Stack

- **Scala 3.7.4** - Modern Scala with improved syntax and type system
- **ZIO 2.1.13** - Functional effect system for type-safe, composable programs
- **ZIO HTTP 3.6.0** - High-performance HTTP server and client
- **ZIO AWS S3** - AWS S3 client integration
- **ZIO Test** - Testing framework integrated with ZIO
- **Quill 4.8.6** - Compile-time query generation for PostgreSQL
- **PostgreSQL 42.7.4** - JDBC driver for PostgreSQL
- **Logback 1.4.14** - Logging framework with JSON encoding
- **SBT 1.11.7** - Scala build tool
- **Scoverage** - Code coverage measurement (minimum 70% required)

## Code Organization

### File Size Limits

- Source files should not exceed **400 lines of code**
- Test files can be up to **800 lines of code**
- If a file exceeds these limits, refactor into smaller, focused modules

### Repository Structure

```text
.
├── src/
│   ├── main/scala/
│   │   ├── Main.scala                    # Application entry point
│   │   ├── http/                         # HTTP endpoint definitions
│   │   │   ├── EchoEndpoint.scala
│   │   │   ├── Endpoint.scala            # Base endpoint trait
│   │   │   ├── HealthDeepEndpoint.scala
│   │   │   ├── HealthEndpoint.scala
│   │   │   ├── RootEndpoint.scala
│   │   │   └── ShutdownEndpoint.scala
│   │   └── service/                      # Service layer
│   │       └── DatabaseService.scala
│   └── test/scala/                       # Test code mirroring main structure
├── project/                              # SBT configuration
├── scripts/                              # Development and deployment scripts
│   ├── docker-test.sh
│   └── git-hooks-setup.sh
├── git-hooks/                            # Git hook implementations
├── .github/workflows/                    # CI/CD pipelines
├── docker-compose.yml                    # Docker orchestration
├── Dockerfile                            # Container build definition
├── .env.example                          # Environment variable template
└── target/                               # Build artifacts (ignored by git)
```

### Architecture Overview

This is a production-ready ZIO HTTP service following these architectural patterns:

#### Core Patterns

- **ZIOAppDefault**: Entry point extending `ZIOAppDefault` for automatic runtime setup
- **Effect System**: All side effects wrapped in ZIO effects for type safety and composability
- **Error Handling**: Using `.catchAll()` for explicit error recovery and graceful degradation
- **Testing**: Using `ZIOSpecDefault` with test services for fully testable I/O operations
- **Dependency Injection**: Using ZIO's ZLayer system for compile-time-verified dependency management

#### Application Layers

1. **HTTP Layer** (`http/` package):
   - RESTful endpoints using ZIO HTTP Routes
   - Each endpoint is a self-contained module with route definition and handler
   - Request/response logging for observability
   - Graceful shutdown coordination via Promise

2. **Service Layer** (`service/` package):
   - `DatabaseService`: PostgreSQL interaction using Quill
   - Connection pooling via JDBC DataSource
   - Health check capabilities
   - Configuration loading from environment variables

3. **Infrastructure**:
   - SLF4J logging with structured JSON output
   - AWS S3 client configuration with credential management
   - PostgreSQL connection management with startup validation
   - Graceful shutdown handling with configurable timeout (30 seconds)

#### Key Features

- **Health Monitoring**: Two-tier health checks (basic and deep)
  - Basic: Always returns 200 (liveness probe)
  - Deep: Validates external URL, S3 connectivity, and database connection
- **Graceful Shutdown**: Server waits for in-flight requests before terminating
- **Request Logging**: All endpoints log request start and completion
- **Error Recovery**: Failed health checks return detailed error messages without crashing

## Configuration

### Environment Variables

The application uses environment variables for configuration. See `.env.example` for a complete template.

#### Required Variables

- `DATABASE_HOST` - PostgreSQL host (default: localhost)
- `DATABASE_PORT` - PostgreSQL port (default: 5432)
- `DATABASE_NAME` - Database name (default: postgres)
- `DATABASE_USER` - Database username (required, no default)
- `DATABASE_PASSWORD` - Database password (required, no default)

#### Optional Variables

- `AWS_ACCESS_KEY_ID` - AWS access key for S3 operations
- `AWS_SECRET_ACCESS_KEY` - AWS secret key for S3 operations
- `AWS_REGION` - AWS region (default: us-east-1)

**Note**: Database credentials are mandatory. The application will fail to start without them. AWS credentials are optional; the `/health-deep` endpoint will report S3 as unhealthy if they're missing, but the application will continue running.

### Application Configuration

Server configuration is defined in Main.scala:288-295:

- **Port**: 8080
- **Keep-Alive**: Enabled
- **Idle Timeout**: 30 seconds
- **Max Header Size**: 16 KB
- **Graceful Shutdown Timeout**: 30 seconds

## Development Workflow

### Making Changes

1. **Ensure hooks are installed**: Run `./scripts/git-hooks-setup.sh` if you haven't already
2. **Make your changes** in appropriate source files
3. **Format code**: `make format`
4. **Run linter**: `make lint`
5. **Run tests**: `make test`
6. **Check coverage**: `make coverage`
7. **Commit**: Git hooks will automatically verify formatting and linting

### Build Commands

See `Makefile` for all available commands:

- `make build` / `make compile` - Compile the project
- `make run` - Run the application
- `make test` - Run all tests
- `make watch-test` - Continuously run tests on file changes
- `make coverage` - Generate test coverage report (requires ≥60%)
- `make format` - Format code with scalafmt
- `make format-check` - Check if code is formatted correctly
- `make lint` - Auto-fix linting issues with scalafix
- `make lint-check` - Check for linting issues (CI mode)
- `make clean` - Clean build artifacts
- `make console` - Start a REPL with dependencies loaded
- `make watch` - Continuously compile on file changes

### Running a Single Test

To run a specific test suite:

```bash
sbt "testOnly *MainSpec"
```

To run a specific test within a suite:

```bash
sbt 'testOnly *MainSpec -- -t "program should print welcome messages"'
```

### Interactive Development

#### REPL/Console

Start an interactive Scala console with project dependencies loaded:

```bash
make console
```

#### Watch Mode

Automatically recompile on file changes:

```bash
make watch
```

Automatically run tests on file changes:

```bash
make watch-test
```

### CI/CD

The project uses GitHub Actions for continuous integration:

- **On push/PR**: Runs formatting checks, linting, tests, and coverage
- **Configuration**: See `.github/workflows/` for pipeline definitions

## Code Style

### Scala

- **Formatting**: Enforced by scalafmt (configuration in `.scalafmt.conf`)
- **Linting**: Enforced by scalafix (configuration in `.scalafix.conf`)
- **Language**: American English for all identifiers, comments, and documentation
- **Scala 3 Syntax**: Use Scala 3 features (indentation-based syntax, `given`/`using`, etc.)
- **ZIO Patterns**:
  - Use `for`-comprehensions for sequential effects
  - Use `.catchAll()` for error handling
  - Extend `ZIOAppDefault` for main applications
  - Extend `ZIOSpecDefault` for test suites

### Markdown

- **Links**: Use reference-style links (`[word][]`) with references at the bottom of the file
  - ✅ Preferred: `[ZIO][]` with `[ZIO]: https://zio.dev` at bottom
  - ❌ Avoid: `[ZIO](https://zio.dev)` inline links
- **Linting**: Configured via `.markdownlint.jsonc`
  - Run: `markdownlint '**/*.md'` to check
  - Run: `markdownlint --fix '**/*.md'` to auto-fix

## Testing

### Running Tests

```bash
make test                           # Run all tests
make watch-test                     # Run tests continuously
make coverage                       # Generate coverage report
sbt "testOnly *MainSpec"           # Run specific test suite
sbt 'testOnly *MainSpec -- -t "test name"'  # Run specific test
```

### Test Organization

- Unit tests should be colocated with the code they test
- Test files follow the naming convention: `*Spec.scala`
- Use ZIO Test framework for writing tests
- Tests should extend `ZIOSpecDefault`
- Use `TestConsole` and other test services for testable I/O

### Coverage Requirements

- **Minimum statement coverage**: 70%
- **Build fails** if coverage drops below minimum
- Coverage report generated at: `target/scala-3.7.4/scoverage-report/`
- Coverage is enforced in CI/CD pipeline

## Making Sure Changes Work

Before submitting changes:

1. ✅ All tests pass (`make test`)
2. ✅ Code coverage is ≥70% (`make coverage`)
3. ✅ Code is formatted (`make format-check`)
4. ✅ No linting issues (`make lint-check`)
5. ✅ Pre-commit hook passes
6. ✅ CI/CD pipeline passes on GitHub
7. ✅ Docker build succeeds (`make docker-build`)
8. ✅ Application starts successfully in Docker (`make docker-up`)

## Docker Development

### Building Docker Images

```bash
make docker-build         # Standard build
make docker-build-no-cache # Force rebuild without cache
```

### Running Locally

Using docker-compose (recommended):

```bash
make docker-up            # Start services
make docker-down          # Stop services
make docker-restart       # Restart services
```

Direct docker run:

```bash
make docker-run           # Start container
make docker-stop          # Stop container
```

### Debugging

```bash
make docker-logs          # Follow container logs
make docker-shell         # Open shell in running container
make docker-health        # Check health status
make docker-ps            # Show service status
```

### Docker Build Optimization

The Dockerfile uses multi-stage builds with optimized layer caching:

1. **Build configuration layer** (rarely changes): `build.sbt`, `project/`
2. **Dependencies layer** (changes when dependencies update): `sbt update`
3. **Source code layer** (changes frequently): `src/`

To maximize cache hits:
- Modify source code: Only last layer rebuilds (~45 seconds)
- Add dependencies: Last two layers rebuild (~2 minutes)
- Change build config: All layers rebuild (~3-4 minutes)

### Assembly Merge Strategy

The project uses a minimal merge strategy for sbt-assembly. If you encounter conflicts during `make assembly` or Docker builds:

1. Note the conflicting file from the error message
2. Add a specific merge rule to `build.sbt` in the `assemblyMergeStrategy` section
3. Common merge strategies:
   - `MergeStrategy.discard` - Ignore the file
   - `MergeStrategy.first` - Use first occurrence
   - `MergeStrategy.concat` - Concatenate all occurrences
   - `MergeStrategy.deduplicate` - Remove duplicates

### Testing Docker Locally

Run the automated test suite:

```bash
./scripts/docker-test.sh  # Automated test suite
```

Manual testing:

```bash
make docker-build
make docker-up
sleep 40  # Wait for startup
curl http://localhost:8080/health  # Should return 200
curl http://localhost:8080/        # Test application
make docker-logs-compose           # Check logs
make docker-down
```

## Adding New Features

### Adding a New Endpoint

To add a new HTTP endpoint:

1. Create a new file in `src/main/scala/http/` (e.g., `MyEndpoint.scala`)
2. Extend or use the pattern from existing endpoints:
   - Define the handler using `Handler.fromFunctionZIO[Request]`
   - Define the route using `Method.GET/POST/etc / "path" -> handler`
   - Add request logging at start and completion
3. Add the route to `Main.scala:288` in the `routes` method
4. Write tests in `src/test/scala/http/MyEndpointSpec.scala`
5. Ensure test coverage remains ≥70%

Example structure:

```scala
object MyEndpoint:
  protected val handler: Handler[Any, Nothing, Request, Response] =
    Handler.fromFunctionZIO[Request] { request =>
      ZIO.logInfo("MyEndpoint: Request received") *>
        // Your logic here
        ZIO.succeed(Response.text("OK"))
          .tapBoth(
            err => ZIO.logError(s"MyEndpoint: Failed - $err"),
            _ => ZIO.logInfo("MyEndpoint: Completed successfully")
          )
    }

  val route: Route[Any, Nothing] =
    Method.GET / "my-path" -> handler
```

### Adding a New Service

To add a new service (like `DatabaseService`):

1. Create the service trait and implementation in `src/main/scala/service/`
2. Define a `live` ZLayer for dependency injection
3. Load configuration from environment variables
4. Add the service layer to `Main.scala:288` in the `.provide()` call
5. Write comprehensive tests including error cases
6. Update `.env.example` with any new configuration variables

## Troubleshooting

### Common Issues

**Database connection failed on startup**:
- Verify DATABASE_* environment variables are set correctly
- Check PostgreSQL is running and accessible
- Verify credentials with: `psql -h $DATABASE_HOST -p $DATABASE_PORT -U $DATABASE_USER -d $DATABASE_NAME`

**S3 health check always fails**:
- Verify AWS_ACCESS_KEY_ID and AWS_SECRET_ACCESS_KEY are set
- Check the IAM user has `s3:ListAllMyBuckets` permission
- Note: This won't prevent the application from starting

**Docker build fails with merge conflicts**:
- See [Assembly Merge Strategy](#assembly-merge-strategy) section above
- Add specific merge rules to `build.sbt` for conflicting files

**Tests fail with "Address already in use"**:
- Another instance may be running on port 8080
- Stop with: `docker-compose down` or kill the process using the port

### Logging

Application logs are structured JSON by default (configured in `src/main/resources/logback.xml`). To view logs:

```bash
# Local development
make run

# Docker
make docker-logs          # Direct docker run
make docker-logs-compose  # Docker compose
```

Log format includes timestamp, level, thread, logger name, and message.

## Documentation

- **README.md**: User-facing documentation (what, why, how to use)
- **CLAUDE.md**: Developer documentation (how to contribute, architecture, workflow)
- Keep these files mutually exclusive and reference each other as appropriate

[README.md]: ./README.md
