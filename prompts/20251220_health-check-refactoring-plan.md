# HealthDeepEndpoint Refactoring Plan

## Overview

Refactor HealthDeepEndpoint to support arbitrary, pluggable health checks that run in parallel. Extract existing checks into a new `health_checks` sub-package with dynamic registration via ZLayer.

## Requirements

- **Parallel Execution**: All health checks run concurrently using ZIO fibers
- **Report All Results**: Continue running all checks even if some fail
- **Dynamic Registration**: Health checks register via ZLayer (pluggable architecture)
- **Maintain Functionality**: All existing checks and tests continue working

## Architecture

### Core Abstractions

**HealthCheck Trait** (`src/main/scala/http/health_checks/HealthCheck.scala`)

```scala
trait HealthCheck:
  def name: String                  // Must be kebab-case (e.g., "url", "s3", "database")
  def description: String           // Human-readable description
  def check: ZIO[Any, Nothing, HealthCheckResult]  // Never fails

case class HealthCheckResult(
  name: String,
  status: HealthStatus,
  message: String,
  durationMs: Long
)

enum HealthStatus:
  case Healthy, Unhealthy

object HealthCheck:
  // Validate check name is kebab-case
  private val kebabCaseRegex = "^[a-z][a-z0-9]*(-[a-z0-9]+)*$".r

  def validateName(name: String): Either[String, String] =
    if kebabCaseRegex.matches(name) then Right(name)
    else Left(s"Health check name '$name' must be kebab-case (lowercase, hyphens only)")
```

**JSON Encoding** (use ZIO JSON with auto-derivation):

```scala
import zio.json.*

given JsonEncoder[HealthStatus] =
  JsonEncoder.string.contramap(_.toString.toLowerCase)

given JsonEncoder[HealthCheckResult] = DeriveJsonEncoder.gen
```

Key principles:

- Return type is `ZIO[Any, Nothing, HealthCheckResult]` (never fails)
- Each check handles timeout and error recovery internally
- Results include timing for observability
- Status is strongly typed (enum vs string)
- **Names must be kebab-case** (validated at startup via `validateName`)
- **Error messages are user-friendly** (stack traces logged to server only)

### Dynamic Registration Pattern

**HealthCheckRegistry** (`src/main/scala/http/health_checks/HealthCheckRegistry.scala`)

```scala
case class HealthCheckRegistry(checks: List[HealthCheck])

object HealthCheckRegistry:
  val layer: ZLayer[UrlHealthCheck & S3HealthCheck & DatabaseHealthCheck, Nothing, HealthCheckRegistry] =
    ZLayer.fromZIO(
      for
        urlCheck <- ZIO.service[UrlHealthCheck]
        s3Check <- ZIO.service[S3HealthCheck]
        dbCheck <- ZIO.service[DatabaseHealthCheck]
        // Validate all names are unique
        allChecks = List(urlCheck, s3Check, dbCheck)
        names = allChecks.map(_.name)
        _ <- ZIO.when(names.distinct.size != names.size)(
          ZIO.fail(new IllegalStateException(s"Duplicate health check names: ${names.groupBy(identity).filter(_._2.size > 1).keys.mkString(", ")}"))
        )
        // Validate all names are kebab-case
        _ <- ZIO.foreachDiscard(allChecks)(check =>
          ZIO.fromEither(HealthCheck.validateName(check.name))
            .mapError(msg => new IllegalStateException(msg))
        )
      yield HealthCheckRegistry(allChecks)
    )

  /**
   * Run all health checks in parallel with global timeout protection.
   * Returns empty list if no checks registered (endpoint returns 200 with overallStatus: healthy).
   * Global timeout ensures endpoint doesn't hang if individual check timeouts fail.
   */
  def runAllChecks(checks: List[HealthCheck]): ZIO[Any, Nothing, List[HealthCheckResult]] =
    ZIO.collectAllPar(checks.map(_.check))
      .timeout(15.seconds)
      .map(_.getOrElse(List.empty))
```

Benefits:

- Compile-time verification of dependencies
- Type-safe dependency injection
- Easy to add/remove checks (modify registry layer only)
- Checks constructed lazily at startup
- **Validates unique names and kebab-case at startup** (fails fast)
- **Global 15s timeout** prevents endpoint from hanging

**Critical Edge Cases**:

1. **Duplicate Names**: Registry validates uniqueness at startup
2. **ZLayer Dependency Cycles**: Health checks MUST only depend on leaf services (config, external clients), never on other health checks or the registry
3. **Empty Registry**: Returns empty list; endpoint returns 200 with `overallStatus: healthy`

### Response Format

**New Structure** (arrays support arbitrary number of checks):

```json
{
  "checks": [
    {
      "name": "url",
      "status": "healthy",
      "message": "URL returned status 200",
      "durationMs": 245
    },
    {
      "name": "s3",
      "status": "healthy",
      "message": "Successfully listed 5 bucket(s)",
      "durationMs": 523
    },
    {
      "name": "database",
      "status": "healthy",
      "message": "Database connection successful",
      "durationMs": 87
    }
  ],
  "overallStatus": "healthy",
  "totalDurationMs": 523
}
```

## Breaking Changes

**Response Format**: The endpoint response structure changes from named fields to an array-based format. This is an intentional breaking change to enable the pluggable health check architecture.

**Old Format**:

```json
{
  "url": {"status": "healthy", "message": "..."},
  "s3": {"status": "healthy", "message": "..."},
  "database": {"status": "healthy", "message": "..."}
}
```

**New Format**:

```json
{
  "checks": [
    {"name": "url", "status": "healthy", "message": "...", "durationMs": 245},
    {"name": "s3", "status": "healthy", "message": "...", "durationMs": 523},
    {"name": "database", "status": "healthy", "message": "...", "durationMs": 87}
  ],
  "overallStatus": "healthy",
  "totalDurationMs": 523
}
```

**Migration**: Clients consuming `/health-deep` must update JSON parsing to:

1. Read from `checks[]` array instead of named fields
2. Use `checks[i].name` to identify each check
3. Read overall status from `overallStatus` field

**Benefits**:

- Supports arbitrary number of health checks without schema changes
- Includes timing information for each check
- Clearer overall status field

### Rollback Plan

If breaking changes cause issues in production:

**Option 1: Content Negotiation** - Add `Accept: application/json; version=2` header support to serve both formats:

- `Accept: application/json` or no header → New format (default)
- `Accept: application/json; version=1` → Old format (backwards compatible)

**Option 2: Separate Endpoint** - Create `/health-deep-v2` with new format, keep old endpoint for migration period

## Implementation Steps

### Phase 1: Create Core Abstractions

1. **Create `src/main/scala/http/health_checks/HealthCheck.scala`** (~100 lines)
   - Define `HealthCheck` trait
   - Define `HealthCheckResult` case class
   - Define `HealthStatus` enum
   - Add JSON encoders using ZIO JSON: `given JsonEncoder[HealthStatus] = JsonEncoder.string.contramap(_.toString.toLowerCase)`
   - Add `validateName` function for kebab-case validation
   - Include comprehensive ScalaDoc

2. **Create `src/main/scala/http/health_checks/HealthCheckRegistry.scala`** (~150 lines)
   - Define `HealthCheckRegistry` case class
   - Implement `layer` that composes all check layers
   - **Add validation**: Verify unique names and kebab-case compliance at startup
   - Add `runAllChecks` with **global 15s timeout**: `.timeout(15.seconds).map(_.getOrElse(List.empty))`
   - Add helper methods for result aggregation
   - Add JSON encoders for response format
   - Document empty registry behavior in ScalaDoc

### Phase 2: Extract Individual Health Checks

**Configuration Note**: Health checks need configuration for URLs, etc. Create:

- `HealthCheckConfig` case class with fields like `checkUrl: String`
- Load from environment: `HEALTH_CHECK_URL` (defaults to `https://tedn.life`)
- Add to `config/AppConfig.scala` following existing pattern

1. **Create `src/main/scala/http/health_checks/UrlHealthCheck.scala`** (~120 lines)
   - Extract `checkUrl` logic from HealthDeepEndpoint:132-167
   - Implement `HealthCheck` trait with `name = "url"` (kebab-case)
   - Keep 5s timeout internally
   - **Load URL from config**: Depend on `HealthCheckConfig` via layer
   - Depend on `Client & Scope` via layer
   - Log full errors to server, return user-friendly messages
   - Add comprehensive ScalaDoc
   - **Critical**: Only depend on leaf services, not other health checks

2. **Create `src/main/scala/http/health_checks/S3HealthCheck.scala`** (~150 lines)
   - Extract `checkS3` logic from HealthDeepEndpoint:190-225
   - Extract `createS3Layer` helper from HealthDeepEndpoint:227-247
   - Implement `HealthCheck` trait with `name = "s3"` (kebab-case)
   - Keep 10s timeout internally
   - Depend on `AwsConfig` via layer
   - Log full errors to server, return user-friendly messages
   - Add comprehensive ScalaDoc
   - **Critical**: Only depend on leaf services, not other health checks

3. **Create `src/main/scala/http/health_checks/DatabaseHealthCheck.scala`** (~80 lines)
   - Extract `checkDatabase` logic from HealthDeepEndpoint:169-188
   - Implement `HealthCheck` trait with `name = "database"` (kebab-case)
   - Keep 5s timeout internally
   - Depend on `DatabaseService` via layer
   - Log full errors to server, return user-friendly messages
   - Add comprehensive ScalaDoc
   - **Critical**: Only depend on leaf services, not other health checks

### Phase 3: Refactor HealthDeepEndpoint

1. **Refactor `src/main/scala/http/HealthDeepEndpoint.scala`** (298 lines → ~150 lines)
   - Remove all inline check implementations (lines 132-247)
   - Remove `ServiceHealthCheck` and `HealthCheckResponse` data models
   - Remove `createS3Layer` helper
   - Simplify handler to use `HealthCheckRegistry.runAllChecks`
   - Update route dependencies from `DatabaseService & AwsConfig` to `HealthCheckRegistry`
   - Maintain existing logging behavior
   - Update ScalaDoc

### Phase 4: Update Dependency Wiring

1. **Modify `src/main/scala/Main.scala`** (line 82 area)

   **ZLayer Dependency Order** (prevents confusion, ZIO validates automatically):

   1. **Config layers** (no dependencies):
      - `ServerConfig.layer`
      - `DatabaseConfig.layer`
      - `AwsConfig.layer`
      - `HealthCheckConfig.layer` ← **New**

   2. **Infrastructure** (depends on config):
      - `Client.default`
      - `Scope.default`
      - `DatabaseService.live`

   3. **Health checks** (depends on infrastructure):
      - `UrlHealthCheck.layer` ← **New**
      - `S3HealthCheck.layer` ← **New**
      - `DatabaseHealthCheck.layer` ← **New**

   4. **Registry** (depends on health checks):
      - `HealthCheckRegistry.layer` ← **New**

7b. **Add Startup Validation** (in `Main.scala` after layer composition)

- Registry layer already validates at construction time
- If startup fails, clear error message will indicate: "Health check name 'BadName' must be kebab-case" or "Duplicate health check names: database"

### Phase 5: Update Tests

**Testing Strategy Note**: Use `TestClock.adjust()` for timeout tests instead of `Thread.sleep()` or real delays to avoid flakiness.

1. **Refactor `src/test/scala/http/HealthDeepEndpointSpec.scala`**
   - Update mock setup to provide `HealthCheckRegistry` instead of individual services
   - Update JSON parsing for new response format (parse `checks[]` array)
   - Add tests for parallel execution timing (use `TestClock`)
   - Ensure all 9 existing test scenarios still pass
   - Add test for empty registry behavior
   - Target ~300 lines (within 800 line limit)

2. **Create `src/test/scala/http/health_checks/UrlHealthCheckSpec.scala`** (~150 lines)
   - Test successful URL checks
   - Test timeout behavior (use `TestClock.adjust(5.seconds)`)
   - Test error handling
   - Test different HTTP status codes (200, 404, 500, etc.)
   - Use mock `Client` following existing pattern

3. **Create `src/test/scala/http/health_checks/S3HealthCheckSpec.scala`** (~150 lines)
    - Test successful S3 bucket listing
    - Test timeout behavior (use `TestClock.adjust(10.seconds)`)
    - Test AWS credential errors
    - Test network failures
    - Use mock S3 client following existing pattern

4. **Create `src/test/scala/http/health_checks/DatabaseHealthCheckSpec.scala`** (~150 lines)
    - Test successful database checks
    - Test timeout behavior (use `TestClock.adjust(5.seconds)`)
    - Test connection failures
    - Use mock `DatabaseService` following existing pattern (see HealthDeepEndpointSpec:26-38)

5. **Create `src/test/scala/http/health_checks/HealthCheckRegistrySpec.scala`** (~200 lines)
    - Test parallel execution of all checks
    - Test that one failure doesn't stop others
    - Test result aggregation
    - Test timing measurements
    - **Test duplicate name detection** at startup (should fail)
    - **Test invalid names** (not kebab-case, should fail)
    - **Test empty registry** (returns empty list)
    - **Test global timeout** (if all checks hang, registry times out at 15s)

### Phase 6: Update Documentation

1. **Update `CLAUDE.md`**
    - Update "Adding New Features" section with health check registration example
    - Add section explaining health check architecture (after "Endpoint Patterns")
    - Document how to add new health checks (with Redis example)
    - Update code organization section with new package structure
    - **Add edge case warnings**: duplicate names, dependency cycles, leaf services only

2. **Update `config/AppConfig.scala`**
    - Add `HealthCheckConfig` case class
    - Add environment variable loading for `HEALTH_CHECK_URL`
    - Update `.env.example` with new variable

## Critical Files

These files are most critical for the refactoring:

1. `src/main/scala/http/health_checks/HealthCheck.scala` - Core abstractions
2. `src/main/scala/http/health_checks/HealthCheckRegistry.scala` - Orchestration and validation
3. `src/main/scala/http/HealthDeepEndpoint.scala` - Main endpoint refactor
4. `src/main/scala/Main.scala` - Dependency wiring
5. `config/AppConfig.scala` - Configuration for health check URL
6. `src/test/scala/http/HealthDeepEndpointSpec.scala` - Regression prevention

## Error Handling Principles

1. **Never Fail**: All health checks return `ZIO[Any, Nothing, HealthCheckResult]`
2. **Timeout Protection**:
   - Each check implements its own timeout (5s, 10s, etc.)
   - Global 15s timeout at registry level prevents endpoint hanging
3. **Graceful Degradation**: Failed checks return `Unhealthy` status, not exceptions
4. **User-Friendly Messages**:
   - Response messages are actionable and user-friendly (e.g., "Database connection failed")
   - NO stack traces or technical internals in responses
   - Full error details logged to server logs using `ZIO.logError`
   - Example pattern:

     ```scala
     .catchAll { err =>
       ZIO.logError(s"$name health check failed: ${err.toString}") *>  // Full details to logs
       ZIO.succeed(HealthCheckResult(name, Unhealthy, "Service unavailable", duration))  // User-friendly message
     }
     ```

## Critical Edge Cases & Pitfalls

### 1. Duplicate Health Check Names

**Risk**: Two checks use same name (e.g., both "database"), causing JSON collision or overwriting.

**Mitigation**: Registry validates uniqueness at startup. Application fails immediately with clear error: "Duplicate health check names: database"

### 2. ZLayer Dependency Cycles

**Risk**: If `UrlHealthCheck` depends on `DatabaseService`, and later you add a check for the HTTP client that `UrlHealthCheck` uses, circular dependency breaks compilation.

**Mitigation**: **Health checks MUST only depend on leaf services** (config, external clients), never on:

- Other health checks
- The registry itself
- Services that might depend on health checks

Document this clearly in CLAUDE.md.

### 3. Test Timeout Flakiness

**Risk**: Tests using real timeouts (5s, 10s) are slow and flaky. Mocking them incorrectly means tests won't catch timeout bugs.

**Mitigation**: Use `TestClock.adjust()` for timeout tests:

```scala
for
  fiber <- healthCheck.check.fork
  _ <- TestClock.adjust(5.seconds)  // Simulate time passing
  result <- fiber.join
yield assertTrue(result.status == Unhealthy, result.message.contains("timeout"))
```

## Adding Future Health Checks (Example)

To add a Redis health check:

1. **Create** `src/main/scala/http/health_checks/RedisHealthCheck.scala`
2. **Implement** `HealthCheck` trait with Redis ping logic:

   ```scala
   case class RedisHealthCheckImpl(client: RedisClient) extends HealthCheck:
     override def name: String = "redis"  // kebab-case
     override def description: String = "Redis connectivity check"
     override def check: ZIO[Any, Nothing, HealthCheckResult] = ???
   ```

3. **Add to registry** in `HealthCheckRegistry.layer`:

   ```scala
   redisCheck <- ZIO.service[RedisHealthCheck]
   yield HealthCheckRegistry(List(urlCheck, s3Check, dbCheck, redisCheck))
   ```

4. **Wire in Main.scala** `.provide()` (in phase 3 of dependency order):

   ```scala
   RedisHealthCheck.layer,  // Add here
   RedisClient.layer,       // Add dependency if needed
   ```

5. **Create test** `src/test/scala/http/health_checks/RedisHealthCheckSpec.scala`

**No changes needed to HealthDeepEndpoint itself!**

## Verification

```bash
# Run all tests
make test

# Verify coverage >= 70%
make coverage

# Manual endpoint verification
make run
curl http://localhost:8080/health-deep | jq

# Expected response:
# {
#   "checks": [
#     {"name": "url", "status": "healthy", "message": "...", "durationMs": 245},
#     {"name": "s3", "status": "healthy", "message": "...", "durationMs": 523},
#     {"name": "database", "status": "healthy", "message": "...", "durationMs": 87}
#   ],
#   "overallStatus": "healthy",
#   "totalDurationMs": 523
# }
```

## File Size Compliance

All new files meet project constraints:

- Source files: < 400 lines
- Test files: < 800 lines
- HealthDeepEndpoint: 298 → ~150 lines
- HealthCheck.scala: ~100 lines
- HealthCheckRegistry.scala: ~150 lines
- Individual check implementations: 80-150 lines
- Individual test files: 150-200 lines

Total: **13 files to create/modify**
