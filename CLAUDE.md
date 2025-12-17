# CLAUDE.md

This document provides guidance for developers working on this codebase. For user-facing documentation, see [README.md][].

## Technology Stack

- **Scala 3.7.4** - Modern Scala with improved syntax and type system
- **ZIO 2.1.13** - Functional effect system for type-safe, composable programs
- **ZIO Test** - Testing framework integrated with ZIO
- **SBT 1.11.7** - Scala build tool
- **Scoverage** - Code coverage measurement (minimum 60% required)

## Code Organization

### File Size Limits

- Source files should not exceed **400 lines of code**
- Test files can be up to **800 lines of code**
- If a file exceeds these limits, refactor into smaller, focused modules

### Repository Structure

```text
.
├── src/
│   ├── main/scala/       # Application code
│   └── test/scala/       # Test code
├── project/              # SBT configuration
├── scripts/              # Development scripts
├── git-hooks/            # Git hook implementations
├── .github/workflows/    # CI/CD pipelines
└── target/               # Build artifacts (ignored by git)
```

### Architecture Overview

This is a ZIO-based application following these patterns:

- **ZIOAppDefault**: Entry point extending `ZIOAppDefault` for automatic runtime setup
- **Effect System**: All side effects wrapped in ZIO effects for type safety and composability
- **Error Handling**: Using `.catchAll()` for explicit error recovery
- **Testing**: Using `ZIOSpecDefault` with `TestConsole` for testable I/O operations

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

- **Minimum statement coverage**: 60%
- **Build fails** if coverage drops below minimum
- Coverage report generated at: `target/scala-3.7.4/scoverage-report/`

## Making Sure Changes Work

Before submitting changes:

1. ✅ All tests pass (`make test`)
2. ✅ Code coverage is ≥60% (`make coverage`)
3. ✅ Code is formatted (`make format-check`)
4. ✅ No linting issues (`make lint-check`)
5. ✅ Pre-commit hook passes
6. ✅ CI/CD pipeline passes on GitHub

## Documentation

- **README.md**: User-facing documentation (what, why, how to use)
- **CLAUDE.md**: Developer documentation (how to contribute, architecture, workflow)
- Keep these files mutually exclusive and reference each other as appropriate

[README.md]: ./README.md
