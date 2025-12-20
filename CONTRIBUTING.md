# Contributing to scala3-zio-template

This guide explains how to contribute to this project, including the development workflow, testing requirements, and submission process.

> **Related Documentation**: See [README.md][] for project overview and [CLAUDE.md][] for architecture and implementation details.

## Roles and Responsibilities

- **Contributors**: Submit pull requests with changes and improvements
- **Committers**: Review, approve, and merge pull requests; publish new versions

## Development Workflow

### 1. Initial Setup

Clone the repository:

```bash
git clone <repository-url>
cd scala3-zio-template
```

Set up your development environment:

```bash
# Copy environment variables template
cp .env.example .env
# Edit .env with your credentials

# Install git hooks for code quality
./scripts/git-hooks-setup.sh
```

### 2. Create a Branch

Branch naming format: `roland/<ticket-id>/<3-word-description>`

```bash
# With a ticket ID
git checkout -b roland/PROJ-123/add-user-endpoint

# Without a ticket ID (ad-hoc work)
git checkout -b roland/ad-hoc/fix-logging-bug
```

### 3. Create a Pull Request

Create the PR immediately after branching:

```bash
gh pr create \
  --title "PROJ-123: add user endpoint" \
  --body ""
```

**PR Title Format**: `<ticket-id>: <3-word-description>`

- With ticket: `PROJ-123: add user endpoint`
- Without ticket: `ad-hoc: fix logging bug`

**PR Body**: Leave empty (all details should be in commits)

### 4. Make Changes

Commit frequently as you work:

```bash
# Make changes to files
git add .
git commit -m "Add user model and database schema"

# Continue making changes
git add .
git commit -m "Implement user endpoint handler"
```

**Before each commit**, the pre-commit hook will automatically:

- Check code formatting (`make format-check`)
- Check for linting issues (`make lint-check`)

If the hook fails:

```bash
# Fix formatting
make format

# Fix linting issues
make lint

# Retry commit
git commit
```

### 5. Testing Requirements

Before pushing changes, ensure:

- ✅ All tests pass: `make test`
- ✅ Code coverage ≥ 80%: `make coverage`
- ✅ Code is formatted: `make format-check`
- ✅ No linting issues: `make lint-check`
- ✅ Docker build succeeds: `make docker-build`

### 6. Push Changes

Push your branch to trigger CI/CD:

```bash
git push origin roland/<ticket-id>/<3-word-description>
```

Verify that GitHub Actions workflows pass:

- Formatting checks
- Linting checks
- All tests
- Coverage ≥ 80%

### 7. Merge and Cleanup

Once the PR is approved and all checks pass:

```bash
# Squash-merge the PR (via GitHub UI or gh CLI)
gh pr merge <pr-number> --squash --delete-branch

# Or via GitHub UI:
# 1. Click "Squash and merge"
# 2. Check "Delete branch" option
# 3. Confirm merge
```

The squash commit message will be the PR title by default.

## Build Commands

All build commands are available via the Makefile:

### Compilation

```bash
make build          # Compile the project
make compile        # Alias for build
make clean          # Clean build artifacts
```

### Running

```bash
make run            # Run the application locally
```

### Testing

```bash
make test           # Run all tests
make watch-test     # Run tests continuously on file changes
make coverage       # Generate coverage report (requires ≥ 80%)
```

Run specific tests:

```bash
sbt "testOnly *MainSpec"                           # Run specific test suite
sbt 'testOnly *MainSpec -- -t "test name"'         # Run specific test
```

### Code Quality

```bash
make format         # Format code with scalafmt
make format-check   # Check if code is formatted (CI mode)
make lint           # Auto-fix linting issues with scalafix
make lint-check     # Check for linting issues (CI mode)
```

### Interactive Development

```bash
make console        # Start Scala REPL with project dependencies
make watch          # Continuously compile on file changes
```

## Docker Development

### Building and Running

```bash
# Build Docker image
make docker-build
make docker-build-no-cache      # Force rebuild without cache

# Run with docker-compose (recommended)
make docker-up                  # Start services
make docker-down                # Stop services
make docker-restart             # Restart services

# Direct docker run
make docker-run                 # Start container
make docker-stop                # Stop container
```

### Debugging

```bash
make docker-logs                # Follow container logs
make docker-logs-compose        # Follow compose logs
make docker-shell               # Open shell in running container
make docker-health              # Check health status
make docker-ps                  # Show service status
make docker-clean               # Clean Docker resources
```

### Testing Docker Setup

Run the automated Docker test suite:

```bash
./scripts/docker-test.sh
```

Manual testing:

```bash
make docker-build
make docker-up
sleep 40
curl http://localhost:8080/health
make docker-down
```

## Code Style Requirements

### Scala

- **Formatting**: Enforced by [scalafmt][] (`.scalafmt.conf`)
- **Linting**: Enforced by [scalafix][] (`.scalafix.conf`)
- **Language**: American English for all code, comments, and documentation
- **Syntax**: Use Scala 3 features (indentation-based syntax, `given`/`using`, etc.)
- **File Size**: Maximum 400 lines for source files, 800 lines for test files

### Markdown

- **Links**: Use reference-style links with references at the bottom
  - ✅ Correct: `[ZIO][]` with `[ZIO]: https://zio.dev` at bottom
  - ❌ Avoid: `[ZIO](https://zio.dev)` inline
- **Linting**: Run `markdownlint '**/*.md'` to check
- **Auto-fix**: Run `markdownlint --fix '**/*.md'`

## Testing Guidelines

### Test Organization

- Test files mirror the structure of source files
- Naming convention: `*Spec.scala`
- Use ZIO Test framework
- Extend `ZIOSpecDefault` for test suites
- Use test services (`TestConsole`, etc.) for testable I/O

### Configuration in Tests

As of the config refactoring, tests provide configuration using `ZLayer.succeed` rather than environment variables. This approach ensures:

- **Test Isolation**: Each test has its own configuration
- **Determinism**: Tests don't depend on the environment
- **Simplicity**: No need to mock environment variables
- **Type Safety**: Configuration is fully typed and validated at compile time

Example:

```scala
import org.roland.scala3_zio_template.config.DatabaseConfig

test("database service connects successfully") {
  val testConfig = DatabaseConfig(
    host = "localhost",
    port = 5432,
    name = "testdb",
    user = "testuser",
    password = "testpass"
  )

  for
    service <- ZIO.service[DatabaseService]
    result <- service.healthCheck
  yield assertTrue(result.isHealthy)
}.provide(
  ZLayer.succeed(testConfig),
  DatabaseService.live
)
```

When testing code that requires multiple configurations, compose them with `ZLayer.make`:

```scala
test("health endpoint checks all services") {
  for
    response <- healthCheckEndpoint
  yield assertTrue(response.status == 200)
}.provide(
  ZLayer.succeed(DatabaseConfig(...)),
  ZLayer.succeed(AwsConfig(...)),
  DatabaseService.live,
  S3Service.live
)
```

### Coverage Requirements

- **Minimum**: 80% statement coverage
- **Enforcement**: Build fails if coverage drops below minimum
- **Report**: Generated at `target/scala-3.7.4/scoverage-report/`
- **CI/CD**: Coverage is checked in GitHub Actions

## Git Hooks

Git hooks maintain code quality automatically.

### Pre-commit Hook

Runs before each commit:

- ✅ Checks code formatting
- ✅ Checks for linting issues
- ❌ Blocks commit if checks fail

### Bypassing Hooks

Only bypass hooks in exceptional circumstances:

```bash
git commit --no-verify
```

**Warning**: Bypassing hooks may cause CI/CD to fail.

## CI/CD Pipeline

GitHub Actions runs on every push and pull request:

1. **Format Check**: Verifies code formatting
2. **Lint Check**: Verifies no linting issues
3. **Tests**: Runs all test suites
4. **Coverage**: Verifies ≥ 80% coverage

All checks must pass before merging.

## Pre-Submission Checklist

Before requesting review on your PR:

- [ ] All tests pass locally (`make test`)
- [ ] Code coverage ≥ 80% (`make coverage`)
- [ ] Code is formatted (`make format-check`)
- [ ] No linting issues (`make lint-check`)
- [ ] Pre-commit hook passes
- [ ] GitHub Actions workflows pass
- [ ] Docker build succeeds (`make docker-build`)
- [ ] Application starts successfully (`make docker-up`)
- [ ] Changes are documented if needed

## Common Issues

### Pre-commit Hook Failures

If the pre-commit hook fails:

```bash
# Fix formatting
make format

# Fix linting
make lint

# Retry commit
git commit
```

### Test Failures

If tests fail:

```bash
# Run tests to see failures
make test

# Run specific failing test
sbt "testOnly *FailingSpec"

# Run tests continuously while fixing
make watch-test
```

### Coverage Below Threshold

If coverage is below 80%:

1. Identify untested code in coverage report: `target/scala-3.7.4/scoverage-report/index.html`
2. Add tests for uncovered lines
3. Re-run coverage: `make coverage`

### Docker Build Failures

If Docker build fails:

```bash
# Try without cache
make docker-build-no-cache

# Check for assembly merge conflicts
# See CLAUDE.md "Assembly Merge Strategy" section
```

## Questions and Support

For questions about:

- **How to use the application**: See [README.md][]
- **Implementation details and architecture**: See [CLAUDE.md][]
- **Contributing and development workflow**: This document

For issues and bugs, please open a GitHub issue.

[README.md]: ./README.md
[CLAUDE.md]: ./CLAUDE.md
[scalafmt]: https://scalameta.org/scalafmt/
[scalafix]: https://scalacenter.github.io/scalafix/
