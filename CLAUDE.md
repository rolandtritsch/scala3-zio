# CLAUDE.md

This document explains the implementation details and architectural decisions of this codebase. For user-facing documentation, see [README.md][]. For contribution guidelines, see [CONTRIBUTING.md][].

## Technology Stack

- **Scala 3.7.4** - Modern Scala with improved syntax and type system
- **ZIO 2.1.13** - Functional effect system for type-safe, composable programs
- **ZIO HTTP 3.6.0** - High-performance HTTP server and client
- **ZIO Config 4.0.2** - Type-safe configuration with automatic derivation
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
│   │   ├── config/                       # Configuration definitions
│   │   │   └── AppConfig.scala           # ZIO Config case classes and layers
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

1. **Configuration Layer** (`config/` package):
   - Type-safe configuration using ZIO Config
   - `ServerConfig`: HTTP server settings (port, timeouts)
   - `DatabaseConfig`: PostgreSQL connection parameters
   - `AwsConfig`: AWS credentials and region
   - Automatic validation and fail-fast error handling
   - Loaded from environment variables with sensible defaults

2. **HTTP Layer** (`http/` package):
   - RESTful endpoints using ZIO HTTP Routes
   - Each endpoint is a self-contained module with route definition and handler
   - Request/response logging for observability
   - Graceful shutdown coordination via Promise

3. **Service Layer** (`service/` package):
   - `DatabaseService`: PostgreSQL interaction using Quill
   - Connection pooling via JDBC DataSource
   - Health check capabilities
   - Configuration injected via ZIO ZLayers

4. **Infrastructure**:
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

This application uses **ZIO Config** for type-safe configuration with automatic validation and fail-fast error handling. Configuration is loaded from environment variables and validated at application startup.

### Configuration Architecture

Configuration is organized into three case classes defined in `config/AppConfig.scala`:

- `ServerConfig`: HTTP server settings
- `DatabaseConfig`: PostgreSQL connection parameters
- `AwsConfig`: AWS credentials and region

Each config has a corresponding `ZLayer` that loads from environment variables with sensible defaults:

```scala
import org.roland.scala3_zio_template.config.{ServerConfig, DatabaseConfig, AwsConfig}

// Load individual configs
ServerConfig.layer    // Loads server configuration
DatabaseConfig.layer  // Loads database configuration
AwsConfig.layer       // Loads AWS configuration
```

Services depend only on the configuration they need via ZIO's dependency injection:

```scala
val live: ZLayer[Any, Throwable, DatabaseService] =
  ZLayer.make[DatabaseService](
    DatabaseConfig.layer.mapError(e => new RuntimeException(e.getMessage)),
    dataSourceLayer,
    Quill.Postgres.fromNamingStrategy(SnakeCase),
    serviceLayer
  )
```

### Environment Variables

See `.env.example` for a complete configuration template.

#### Server Configuration

- `SERVER_PORT` - HTTP server port (default: 8080)

#### Database Configuration (Required)

- `DATABASE_HOST` - PostgreSQL host (default: localhost)
- `DATABASE_PORT` - PostgreSQL port (default: 5432)
- `DATABASE_NAME` - Database name (default: postgres)
- `DATABASE_USER` - Database username (**required**, no default)
- `DATABASE_PASSWORD` - Database password (**required**, no default)

#### AWS Configuration (Required)

- `AWS_ACCESS_KEY_ID` - AWS access key for S3 operations (**required**, no default)
- `AWS_SECRET_ACCESS_KEY` - AWS secret key for S3 operations (**required**, no default)
- `AWS_REGION` - AWS region (default: us-east-1)

**Fail-Fast Behavior**: The application refuses to start if any required configuration is missing. You'll see a clear error message indicating which variable is missing. Optional variables with defaults will use their default values if not specified.

### Application Configuration Defaults

When all required variables are provided, the following defaults apply:

- **Server Port**: 8080 (configurable via `SERVER_PORT`)
- **Server Keep-Alive**: Enabled
- **Idle Timeout**: 30 seconds
- **Max Header Size**: 16 KB
- **Graceful Shutdown Timeout**: 30 seconds
- **Database Host**: localhost
- **Database Port**: 5432
- **Database Name**: postgres
- **AWS Region**: us-east-1

## Implementation Rationale

### Why ZIO?

This template uses ZIO as the core effect system for several reasons:

1. **Type Safety**: All side effects are tracked in the type system, making it impossible to silently fail
2. **Composability**: Effects can be combined using for-comprehensions and operators
3. **Testability**: ZIO's environment system allows full dependency injection and mocking
4. **Resource Safety**: ZIO automatically handles resource cleanup, even during failures
5. **Performance**: ZIO's fiber-based concurrency provides excellent performance

### Why ZIO HTTP?

ZIO HTTP was chosen over alternatives (http4s, Akka HTTP) because:

1. **Native ZIO Integration**: Works seamlessly with ZIO effects
2. **Performance**: Built for high-throughput scenarios
3. **Simplicity**: Minimal boilerplate for route definitions
4. **Type Safety**: Routes are fully type-safe at compile time

### Why Quill?

Quill provides compile-time query generation, which means:

1. **SQL Validation**: Queries are validated at compile time
2. **Type Safety**: Query results are fully typed
3. **Performance**: No runtime query parsing or reflection
4. **IDE Support**: Full autocomplete and refactoring support

### Why Multi-Stage Docker Builds?

The Dockerfile uses multi-stage builds to:

1. **Optimize Cache**: Dependencies are cached separately from source code
2. **Reduce Size**: Final image only contains the runtime JAR
3. **Speed Up Builds**: Most builds only rebuild changed layers
4. **Security**: Build tools and source code are not in the final image

## Design Patterns

### ZIO Patterns

**Effect Composition**: Use `for`-comprehensions for sequential effects:

```scala
for
  config <- loadConfig
  db <- initDatabase(config)
  server <- startServer(db)
yield server
```

**Error Handling**: Use `.catchAll()` for explicit error recovery:

```scala
fetchUser(id)
  .catchAll(err => ZIO.logError(s"Failed: $err") *> ZIO.succeed(None))
```

**Resource Management**: Use `ZIO.acquireRelease` for automatic cleanup:

```scala
ZIO.acquireRelease(
  acquire = openConnection
)(
  release = conn => closeConnection(conn).orDie
)
```

**Testing**: Use test services for deterministic I/O:

```scala
for
  _ <- Console.printLine("Hello")
  output <- TestConsole.output
yield assert(output)(contains("Hello"))
```

### Endpoint Patterns

All endpoints follow a consistent pattern:

1. **Handler Definition**: Wrap business logic in `Handler.fromFunctionZIO`
2. **Request Logging**: Log at the start of each request
3. **Completion Logging**: Log success or failure at the end
4. **Error Recovery**: Use `.catchAll()` to handle and log errors
5. **Route Definition**: Map HTTP method and path to handler

This pattern ensures:
- Consistent observability across all endpoints
- Graceful error handling without crashes
- Easy testing and debugging

## Testing Strategy

### Test Pyramid

This codebase follows the testing pyramid:

1. **Unit Tests** (most): Test individual functions and classes in isolation
2. **Integration Tests** (some): Test interactions between components (e.g., database queries)
3. **End-to-End Tests** (few): Test full HTTP request/response cycles

### Testing with ZIO

ZIO's testing approach uses test services to make I/O operations testable:

**Console Output Testing**:

```scala
for
  _ <- Console.printLine("Hello, World!")
  output <- TestConsole.output
yield assertTrue(output.head == "Hello, World!\n")
```

**Clock Testing** (for time-dependent code):

```scala
for
  fiber <- ZIO.sleep(1.hour).fork
  _ <- TestClock.adjust(1.hour)
  _ <- fiber.join
yield assertCompletes
```

**Configuration Testing** (using ZIO Config):

```scala
import org.roland.scala3_zio_template.config.DatabaseConfig

test("service uses provided config") {
  val testConfig = DatabaseConfig(
    host = "testhost",
    port = 5433,
    name = "testdb",
    user = "testuser",
    password = "testpass"
  )

  for
    config <- ZIO.service[DatabaseConfig]
  yield assertTrue(
    config.host == "testhost",
    config.port == 5433
  )
}.provide(ZLayer.succeed(testConfig))
```

Tests provide configuration via `ZLayer.succeed` rather than environment variables, making tests isolated and deterministic.

### Why 80% Coverage?

The 80% coverage threshold ensures:

1. **High Confidence**: Most code paths are tested
2. **Pragmatic**: Allows for boilerplate and trivial code
3. **Enforceable**: Automated checks prevent coverage regression
4. **Maintainable**: Not so high that tests become brittle

## Docker Implementation Details

### Multi-Stage Build Strategy

The Dockerfile uses three stages to optimize build performance:

**Stage 1: Build Configuration** (Dockerfile:1-8)

```dockerfile
COPY build.sbt project/ ./
RUN sbt update
```

- Copies build configuration files
- Downloads all dependencies
- **Cache Key**: `build.sbt` and `project/` contents
- **Rebuild When**: Dependencies change or build config changes

**Stage 2: Source Compilation** (Dockerfile:9-10)

```dockerfile
COPY src/ ./src/
RUN sbt assembly
```

- Copies source code
- Compiles and creates fat JAR
- **Cache Key**: `src/` contents
- **Rebuild When**: Source code changes

**Stage 3: Runtime Image** (Dockerfile:12-20)

```dockerfile
FROM eclipse-temurin:21-jre-alpine
COPY --from=builder /app/target/scala-3.7.4/*-assembly-*.jar app.jar
```

- Uses minimal JRE image (not JDK)
- Only copies final JAR
- **Size**: ~200MB vs ~800MB with JDK

### Assembly Merge Strategy

The fat JAR assembly process can encounter duplicate files from different dependencies. The strategy in `build.sbt` handles this:

```scala
assembly / assemblyMergeStrategy := {
  case PathList("META-INF", "MANIFEST.MF") => MergeStrategy.discard
  case PathList("META-INF", xs @ _*) if xs.last.endsWith(".SF") => MergeStrategy.discard
  case "application.conf" => MergeStrategy.concat
  case "reference.conf" => MergeStrategy.concat
  case x => MergeStrategy.first
}
```

**Why These Rules?**

- `MANIFEST.MF` and `.SF` files: Security signatures break when merged; discard them
- `application.conf`/`reference.conf`: Lightbend Config requires concatenation to merge settings
- Default: Use first occurrence (safer than concatenating arbitrary files)

### Graceful Shutdown Implementation

The shutdown mechanism uses a ZIO Promise to coordinate:

```scala
// Create shutdown promise
shutdownSignal <- Promise.make[Nothing, Unit]

// Trigger on /shutdown endpoint
ShutdownEndpoint.route(shutdownSignal)

// Server waits for in-flight requests
server.install(routes)
  *> shutdownSignal.await  // Block until shutdown triggered
  *> ZIO.log("Shutting down gracefully")
  *> ZIO.sleep(30.seconds)  // Grace period for cleanup
```

This ensures:
1. In-flight requests complete successfully
2. New requests are rejected after shutdown starts
3. Resources are cleaned up properly
4. No abrupt termination mid-request

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

## Documentation Structure

This repository maintains three separate documentation files:

- **[README.md][]**: What the project is, how to use it, how to install it
- **[CLAUDE.md][]** (this file): How it's implemented, why architectural decisions were made
- **[CONTRIBUTING.md][]**: How to contribute, development workflow, testing guidelines

These files reference each other but remain mutually exclusive in content.

[README.md]: ./README.md
[CONTRIBUTING.md]: ./CONTRIBUTING.md
