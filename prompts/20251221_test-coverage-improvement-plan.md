# Test Coverage Improvement Plan

## Executive Summary

**Current Coverage**: 33.23% (112/337 statements)
**Target Coverage**: 70%
**Gap**: 37 percentage points (~125 additional statements)
**Build Threshold**: 50% minimum (currently failing)

## Problem Analysis

### Why Coverage is Below 50%

1. **Health Check Implementations**: 108 untested statements (0% coverage)
   - DatabaseHealthCheckImpl: 27 statements
   - S3HealthCheckImpl: 47 statements
   - UrlHealthCheckImpl: 31 statements
   - Supporting classes: 3 statements

2. **Configuration Loading**: 11 untested statements (0% coverage)
   - AwsConfig: 7 statements
   - HealthCheckConfig: 4 statements

3. **Service Layer**: 39 untested statements
   - DatabaseService.live implementation: 0% coverage

4. **Application Lifecycle**: Partial coverage
   - Main.scala: 54% (10 untested statements)
   - HealthCheckRegistry: 27% (27 untested statements)

### What's Working Well

- HTTP endpoints: 100% coverage across all endpoints
- Test infrastructure: Solid ZIO Test setup with test layers
- Endpoint trait: 100% coverage with comprehensive specs

## Implementation Plan

### Phase 1: Health Check Implementation Tests (Priority: Critical)

**Impact**: Adds ~15-18% coverage
**Files to Create**: 3 new test files

#### 1.1 DatabaseHealthCheckSpec.scala

**Location**: `src/test/scala/http/DatabaseHealthCheckSpec.scala`

**Test Coverage**:

- Successful health check when database is available
- Health check failure when database is unavailable
- Timeout handling (should fail fast)
- Error message formatting in failures
- Resource cleanup after checks

**Mock Strategy**:

- Create TestDatabaseService that extends DatabaseService
- Use ZLayer.succeed for success cases
- Use ZLayer.fail for failure cases
- Test both healthy and unhealthy states

**Example Test Structure**:

```scala
suite("DatabaseHealthCheck")(
  test("returns healthy status when database is accessible"),
  test("returns unhealthy status when database connection fails"),
  test("handles timeout gracefully"),
  test("includes error details in unhealthy response"),
  test("cleans up resources after check")
)
```

#### 1.2 S3HealthCheckSpec.scala

**Location**: `src/test/scala/http/S3HealthCheckSpec.scala`

**Test Coverage**:

- Successful S3 connection and bucket listing
- AWS credential errors (invalid access key/secret)
- Network timeout handling
- Region configuration validation
- ListBuckets permission errors
- Error message formatting

**Mock Strategy**:

- Create TestS3AsyncClient using ZIO Test mocks
- Mock listBuckets() to return CompletableFuture
- Test various error scenarios (credentials, network, permissions)
- Verify proper S3 client lifecycle management

**Example Test Structure**:

```scala
suite("S3HealthCheck")(
  test("returns healthy when S3 is accessible"),
  test("returns unhealthy on credential errors"),
  test("returns unhealthy on network timeout"),
  test("handles region configuration correctly"),
  test("formats error messages properly")
)
```

#### 1.3 UrlHealthCheckSpec.scala

**Location**: `src/test/scala/http/UrlHealthCheckSpec.scala`

**Test Coverage**:

- Successful HTTP 200 response
- HTTP error status codes (404, 500, etc.)
- Connection timeout handling
- Invalid URL handling
- Network errors (connection refused, DNS failure)
- HTTPS vs HTTP handling

**Mock Strategy**:

- Create TestClient for ZIO HTTP client
- Mock HTTP responses with various status codes
- Simulate network failures and timeouts
- Test URL validation edge cases

**Example Test Structure**:

```scala
suite("UrlHealthCheck")(
  test("returns healthy on HTTP 200"),
  test("returns unhealthy on HTTP 404"),
  test("returns unhealthy on HTTP 500"),
  test("handles connection timeout"),
  test("handles invalid URLs"),
  test("handles connection refused")
)
```

### Phase 2: Configuration Layer Tests (Priority: High)

**Impact**: Adds ~3-5% coverage
**Files to Create**: 1 new test file

#### 2.1 AppConfigSpec.scala

**Location**: `src/test/scala/config/AppConfigSpec.scala`

**Test Coverage**:

##### ServerConfig Tests

- Loads default port (8080) when SERVER_PORT not set
- Loads custom port from SERVER_PORT environment variable
- Validates port number range (must be positive)
- ZLayer construction succeeds

##### DatabaseConfig Tests

- Loads all required fields (host, port, name, user, password)
- Uses defaults for optional fields (host=localhost, port=5432)
- jdbcUrl method constructs correct JDBC URL
- Fails fast when required fields (user, password) are missing
- ZLayer construction with all valid values

##### AwsConfig Tests (Currently 0% coverage)

- Loads AWS_ACCESS_KEY_ID correctly
- Loads AWS_SECRET_ACCESS_KEY correctly
- Loads AWS_REGION with default (us-east-1)
- Fails fast when credentials are missing
- ZLayer construction succeeds with valid credentials

##### HealthCheckConfig Tests (Currently 0% coverage)

- Loads HEALTH_CHECK_URL correctly
- Uses default value when not set
- Validates URL format
- ZLayer construction succeeds

**Test Strategy**:

- Use `ZLayer.succeed()` to provide test configurations (per CLAUDE.md guidelines)
- Test error cases by intentionally omitting required fields
- Verify default values for optional configuration
- Test jdbcUrl construction with various host/port/database combinations

**Example Test Structure**:

```scala
suite("AppConfig")(
  suite("ServerConfig")(
    test("uses default port when not specified"),
    test("loads custom port from environment"),
    test("constructs layer successfully")
  ),
  suite("DatabaseConfig")(
    test("loads all fields correctly"),
    test("constructs correct JDBC URL"),
    test("fails when user is missing"),
    test("fails when password is missing")
  ),
  suite("AwsConfig")(
    test("loads credentials correctly"),
    test("uses default region"),
    test("fails when credentials missing")
  ),
  suite("HealthCheckConfig")(
    test("loads URL correctly"),
    test("uses default URL when not specified")
  )
)
```

### Phase 3: Service Layer Tests (Priority: High)

**Impact**: Adds ~5-8% coverage
**Files to Create/Modify**: 1 file modification

#### 3.1 Enhanced DatabaseServiceSpec.scala

**Location**: `src/test/scala/service/DatabaseServiceSpec.scala` (existing - enhance)

**Current Coverage**: Only tests mock implementation and jdbcUrl method
**New Coverage Needed**:

##### DatabaseService.live Layer Tests

- Layer construction succeeds with valid configuration
- Connection pool initialization
- Quill context initialization with SnakeCase naming strategy
- DataSource creation from DatabaseConfig

##### Health Check Method Tests

- healthCheck() returns true when database is accessible
- healthCheck() returns false when database connection fails
- healthCheck() handles SQL exceptions gracefully
- healthCheck() doesn't leak connections

**Mock Strategy**:

- Use H2 in-memory database for integration-style tests
- Create test tables if needed for health checks
- Verify connection pooling behavior
- Test with valid and invalid configurations

**Alternative Mock Strategy** (if H2 is too heavy):

- Mock DataSource and Connection objects
- Verify that healthCheck() calls the right JDBC methods
- Test exception handling paths

**Example Test Structure**:

```scala
suite("DatabaseService")(
  suite("live layer")(
    test("constructs successfully with valid config"),
    test("initializes connection pool"),
    test("configures Quill with SnakeCase strategy")
  ),
  suite("healthCheck")(
    test("returns true when database is accessible"),
    test("returns false on connection failure"),
    test("handles SQL exceptions gracefully"),
    test("doesn't leak connections on failure")
  )
)
```

### Phase 4: HealthCheckRegistry Tests (Priority: Medium)

**Impact**: Adds ~3-5% coverage
**Files to Create**: 1 new test file

#### 4.1 HealthCheckRegistrySpec.scala

**Location**: `src/test/scala/http/HealthCheckRegistrySpec.scala`

**Current Coverage**: 27% (only tested via integration tests)
**New Coverage Needed**:

##### Registration Tests

- Successfully registers multiple health checks
- Prevents duplicate health check names
- Validates health check names (no empty, no special chars)
- Maintains insertion order

##### Execution Tests

- Runs all health checks in parallel
- Respects global timeout (30 seconds)
- Collects all results even when some checks fail
- Includes check name and status in results
- Returns all results within timeout period

##### Error Handling Tests

- Continues execution when individual checks fail
- Includes error messages in results
- Doesn't throw exceptions on check failures
- Handles checks that timeout individually

**Mock Strategy**:

- Create test health checks with known behaviors:
  - AlwaysHealthyCheck (always returns healthy)
  - AlwaysUnhealthyCheck (always returns unhealthy)
  - TimeoutCheck (sleeps longer than timeout)
  - ErrorCheck (throws exception)
- Use TestClock to control time in timeout tests

**Example Test Structure**:

```scala
suite("HealthCheckRegistry")(
  suite("registration")(
    test("registers health check successfully"),
    test("prevents duplicate names"),
    test("validates health check names"),
    test("maintains registration order")
  ),
  suite("execution")(
    test("runs all checks in parallel"),
    test("respects global timeout"),
    test("collects all results"),
    test("continues on individual failures")
  )
)
```

### Phase 5: Main Application Tests (Priority: Medium)

**Impact**: Adds ~2-3% coverage
**Files to Modify**: MainSpec.scala (existing)

#### 5.1 Enhanced MainSpec.scala

**Location**: `src/test/scala/MainSpec.scala` (existing - enhance)

**Current Coverage**: 54% (12/22 statements)
**New Coverage Needed**:

##### Application Lifecycle Tests

- Application starts successfully with valid config
- All routes are registered correctly (already tested)
- Server binds to configured port (already tested)
- Graceful shutdown works (already tested)
- Application fails fast on invalid configuration

##### Layer Construction Tests

- All ZLayers compose correctly
- Dependencies are satisfied
- Layer construction errors are caught and reported
- Service initialization happens in correct order

**Test Strategy**:

- Test with minimal valid configuration
- Test with invalid configuration (should fail)
- Verify all HTTP routes are present
- Test server lifecycle

**Example Test Structure**:

```scala
suite("Main")(
  suite("application lifecycle")(
    test("starts with valid configuration"),
    test("fails fast on invalid config"),
    test("initializes all services correctly")
  ),
  suite("route registration")(
    // Existing tests...
  )
)
```

### Phase 6: Edge Cases and Error Paths (Priority: Low)

**Impact**: Adds ~2-4% coverage to reach 70% target
**Files to Create/Modify**: Various files as needed

#### 6.1 Additional Coverage Areas

Based on coverage report analysis, add tests for:

##### HealthCheckResponse Edge Cases

- Very long error messages (truncation)
- Special characters in error messages
- Null/empty check names
- Invalid status values

##### Endpoint Error Handling

- Malformed requests (already well-tested)
- Server errors during request processing
- Timeout during request handling

##### Configuration Edge Cases

- Environment variables with whitespace
- Invalid port numbers (negative, > 65535)
- Malformed URLs in HealthCheckConfig
- Invalid AWS regions

**Test Strategy**:

- Focus on error paths not covered by happy-path tests
- Test boundary conditions (empty strings, max values)
- Test invalid input handling
- Verify error messages are helpful

## Test Implementation Guidelines

### ZIO Test Patterns

All tests should follow ZIO Test patterns as documented in CLAUDE.md:

1. **Use TestLayers for Dependencies**:

```scala
test("example") {
  for
    result <- someService.method()
  yield assertTrue(result == expected)
}.provide(
  ZLayer.succeed(TestConfig(...)),
  TestServiceLayer
)
```

1. **Use ZIO.service for Dependency Access**:

```scala
test("accesses config correctly") {
  for
    config <- ZIO.service[DatabaseConfig]
  yield assertTrue(config.host == "testhost")
}.provide(ZLayer.succeed(DatabaseConfig(...)))
```

1. **Test Error Cases with catchAll**:

```scala
test("handles errors gracefully") {
  for
    result <- failingService.method()
      .catchAll(err => ZIO.succeed(ErrorResponse(err.getMessage)))
  yield assertTrue(result.isError)
}.provide(FailingServiceLayer)
```

1. **Use TestClock for Time-Based Tests**:

```scala
test("respects timeout") {
  for
    fiber <- longRunningOp.timeout(5.seconds).fork
    _ <- TestClock.adjust(5.seconds)
    result <- fiber.join
  yield assertTrue(result.isEmpty)
}
```

### Mock Implementation Strategy

All external dependencies should be mocked using ZIO Test patterns:

#### Database Mocking

```scala
case class TestDatabaseService(healthy: Boolean) extends DatabaseService:
  def healthCheck(): Task[Boolean] = ZIO.succeed(healthy)

object TestDatabaseService:
  val healthy: ZLayer[Any, Nothing, DatabaseService] =
    ZLayer.succeed(TestDatabaseService(healthy = true))

  val unhealthy: ZLayer[Any, Nothing, DatabaseService] =
    ZLayer.succeed(TestDatabaseService(healthy = false))
```

#### S3 Client Mocking

```scala
def mockS3Client(shouldSucceed: Boolean): S3AsyncClient =
  new S3AsyncClient:
    override def listBuckets(): CompletableFuture[ListBucketsResponse] =
      if shouldSucceed then
        CompletableFuture.completedFuture(
          ListBucketsResponse.builder().build()
        )
      else
        val future = new CompletableFuture[ListBucketsResponse]()
        future.completeExceptionally(new RuntimeException("S3 connection failed"))
        future
```

#### HTTP Client Mocking

```scala
object TestHttpClient:
  def withStatus(status: Status): ZLayer[Any, Nothing, Client] =
    ZLayer.succeed(
      Client.fromHandler(
        Handler.succeed(Response.status(status))
      )
    )
```

### File Organization

Follow the existing test structure:

```markdown
src/test/scala/
├── config/
│   └── AppConfigSpec.scala          # NEW
├── http/
│   ├── DatabaseHealthCheckSpec.scala    # NEW
│   ├── EchoEndpointSpec.scala
│   ├── HealthCheckRegistrySpec.scala    # NEW
│   ├── HealthDeepEndpointSpec.scala
│   ├── HealthEndpointSpec.scala
│   ├── RootEndpointSpec.scala
│   ├── S3HealthCheckSpec.scala          # NEW
│   ├── ShutdownEndpointSpec.scala
│   └── UrlHealthCheckSpec.scala         # NEW
├── service/
│   └── DatabaseServiceSpec.scala    # ENHANCE
└── MainSpec.scala                   # ENHANCE
```

### Code Coverage Targets by Phase

| Phase | Coverage Increase | Cumulative Coverage |
| Phase | Coverage Increase | Cumulative Coverage |
| Starting Point | - | 33.23% |
| Phase 1: Health Checks | +15-18% | ~48-51% |
| Phase 2: Configuration | +3-5% | ~51-56% |
| Phase 3: Service Layer | +5-8% | ~56-64% |
| Phase 4: Registry | +3-5% | ~59-69% |
| Phase 5: Main App | +2-3% | ~61-72% |
| Phase 6: Edge Cases | +2-4% | **~70%** ✓ |

**Note**: Phases can be executed in parallel where they don't have dependencies. Phases 1-3 can be done concurrently.

## Documentation Updates

### Update CLAUDE.md

**File**: `/workspaces/scala3-zio-template/CLAUDE.md`

**Changes Needed**:

1. **Fix Coverage Threshold Discrepancy** (line ~181):

   ```markdown
   - Current: "Why 80% Coverage?"
   - New: "Why 70% Coverage?"
   ```

2. **Update Coverage Explanation**:

   ```markdown
   The 70% coverage threshold ensures:
   1. **High Confidence**: Most code paths are tested
   2. **Pragmatic**: Allows for boilerplate and trivial code
   3. **Enforceable**: Automated checks prevent coverage regression
   4. **Maintainable**: Not so high that tests become brittle
   ```

3. **Update build.sbt Configuration Documentation** (if needed):

   ```markdown
   ## Code Coverage

   Minimum coverage threshold: 70%
   Configured in build.sbt: `coverageMinimumStmtTotal := 70`
   ```

### Update build.sbt

**File**: `/workspaces/scala3-zio-template/build.sbt`

**Changes Needed** (line 43):

```scala
// Current
coverageMinimumStmtTotal := 50

// New
coverageMinimumStmtTotal := 70
```

**Important**: Only update this after tests are written and coverage reaches 70%, otherwise builds will fail.

## Implementation Sequence

### Recommended Order

1. **Phase 2 First** (Configuration Tests)
   - Quickest to implement
   - No external dependencies
   - Builds confidence in test setup
   - Adds ~3-5% coverage

2. **Phase 1 Next** (Health Check Tests)
   - Highest impact (~15-18% coverage)
   - Critical production code
   - Can be done in parallel (3 separate test files)
   - Tests most complex error handling

3. **Phase 4 After** (HealthCheckRegistry Tests)
   - Depends on understanding from Phase 1
   - Tests integration of health checks
   - Adds ~3-5% coverage

4. **Phase 3 After** (Service Layer Tests)
   - Builds on config testing from Phase 2
   - Tests database integration
   - Adds ~5-8% coverage

5. **Phase 5 After** (Main Application Tests)
   - Tests full application wiring
   - Validates all phases work together
   - Adds ~2-3% coverage

6. **Phase 6 Last** (Edge Cases)
   - Fill gaps to reach exactly 70%
   - Fine-tune based on coverage reports
   - Adds final ~2-4% coverage

7. **Documentation Updates Last**
   - Update CLAUDE.md after reaching 70%
   - Update build.sbt threshold after tests pass

### Parallel Execution Opportunities

These phases can be executed in parallel by different developers:

- **Track 1**: Phase 2 (Config) → Phase 3 (Service Layer)
- **Track 2**: Phase 1.1 (DatabaseHealthCheck) → Phase 4 (Registry)
- **Track 3**: Phase 1.2 (S3HealthCheck) + Phase 1.3 (UrlHealthCheck)
- **Track 4**: Phase 5 (Main App) independently

## Verification Steps

After implementing each phase:

1. **Run Coverage Report**:

   ```bash
   sbt clean coverage test coverageReport
   ```

2. **Check Coverage Percentage**:

   ```bash
   cat target/scala-3.7.4/scoverage-report/index.html
   ```

3. **Identify Remaining Gaps**:

   ```bash
   # View detailed coverage by file
   open target/scala-3.7.4/scoverage-report/index.html
   ```

4. **Run Full Test Suite**:

   ```bash
   sbt test
   ```

5. **Verify Build Passes**:

   ```bash
   sbt clean compile test
   ```

## Success Criteria

- [ ] Code coverage reaches ≥70%
- [ ] All tests pass consistently
- [ ] No flaky tests (run test suite 5x to verify)
- [ ] Coverage report shows no critical gaps in health checks
- [ ] Coverage report shows no critical gaps in configuration
- [ ] Coverage report shows no critical gaps in service layer
- [ ] build.sbt threshold updated to 70%
- [ ] CLAUDE.md documentation updated
- [ ] CI pipeline passes with new coverage threshold

## Risks and Mitigations

### Risk 1: Mocking Complexity

**Risk**: S3 and HTTP client mocking may be complex
**Mitigation**: Start with simple success/failure cases, add edge cases incrementally

### Risk 2: Test Flakiness

**Risk**: Time-based tests or parallel execution may be flaky
**Mitigation**: Use TestClock for time control, run tests multiple times to verify stability

### Risk 3: Coverage Gaps

**Risk**: May not reach exactly 70% with planned tests
**Mitigation**: Phase 6 (Edge Cases) is flexible and can be adjusted based on actual coverage

### Risk 4: H2 Database Differences

**Risk**: H2 behavior may differ from PostgreSQL
**Mitigation**: Keep database tests simple, focus on connection and health check logic

### Risk 5: Test Maintenance

**Risk**: More tests mean more maintenance burden
**Mitigation**: Follow DRY principles, create reusable test fixtures and helpers

## Future Improvements

After reaching 70% coverage:

1. **Integration Tests**: Add docker-compose-based integration tests with real PostgreSQL
2. **Load Testing**: Test health check performance under load
3. **Mutation Testing**: Use mutation testing to verify test quality
4. **Contract Tests**: Add contract tests for HTTP endpoints
5. **Property-Based Tests**: Add property-based tests for data transformations

## Appendix A: Current Coverage by File

| File | Statements | Coverage |
| File | Statements | Coverage |
| **Well Tested (100%)** | | |
| EchoEndpoint.scala | 6 | 100% |
| Endpoint.scala | 18 | 100% |
| HealthDeepEndpoint.scala | 21 | 100% |
| HealthEndpoint.scala | 3 | 100% |
| RootEndpoint.scala | 3 | 100% |
| ShutdownEndpoint.scala | 23 | 100% |
| **Partially Tested** | | |
| Main.scala | 22 | 54.55% |
| HealthCheckResponse | 12 | 83.33% |
| DatabaseConfig | 11 | 27.27% |
| HealthCheckRegistry | 37 | 27.03% |
| ServerConfig | 4 | 25.00% |
| **Not Tested (0%)** | | |
| AwsConfig | 7 | 0% |
| HealthCheckConfig | 4 | 0% |
| DatabaseHealthCheck | 3 | 0% |
| DatabaseHealthCheckImpl | 27 | 0% |
| HealthCheck trait | 9 | 0% |
| S3HealthCheck | 3 | 0% |
| S3HealthCheckImpl | 47 | 0% |
| UrlHealthCheck | 5 | 0% |
| UrlHealthCheckImpl | 31 | 0% |
| DatabaseService.live | 39 | 0% |

## Appendix B: Test File Naming Convention

Follow the existing pattern:

- Production file: `src/main/scala/package/ClassName.scala`
- Test file: `src/test/scala/package/ClassNameSpec.scala`
- Suffix: Always use `Spec.scala`
- Package: Mirror the production package structure

## Appendix C: Useful SBT Commands

```bash
# Run tests with coverage
sbt clean coverage test coverageReport

# Run specific test suite
sbt "testOnly *HealthCheckSpec"

# Run tests continuously (watch mode)
sbt ~test

# Generate coverage report without running tests
sbt coverageReport

# Check coverage threshold
sbt coverageCheck

# Run tests with verbose output
sbt "testOnly *HealthCheckSpec -- -oF"
```
