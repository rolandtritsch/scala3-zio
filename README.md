# scala3-zio
My first ZIO app/service

## Prerequisites

- JVM (Java 8 or higher)
- The `./mill` script handles all other dependencies automatically

## Project Structure

```
.
├── build.sc           # Mill build configuration
├── app/
│   ├── src/           # Application source code
│   └── test/src/      # Test source code
└── out/               # Build output (generated)
```

## Technology Stack

- **Scala 3.3.4** - Modern Scala with improved syntax and features
- **ZIO 2.1.13** - Functional effect system for type-safe, composable programs
- **Mill** - Fast, simple build tool

## Quick Start

See all available commands:

```bash
make help
```

## Building

Compile the project:

```bash
make compile
```

## Running

Run the application:

```bash
make run
```

## Testing

Run tests:

```bash
make test
```

## Other Useful Commands

- **Start a REPL with dependencies loaded:**
  ```bash
  make console
  ```

- **Build an executable JAR:**
  ```bash
  make assembly
  ```
  The JAR will be created at `out/assembly.dest/out.jar`

- **Clean build artifacts:**
  ```bash
  make clean
  ```

- **Show all available tasks:**
  ```bash
  make resolve
  ```

- **Continuously compile on file changes:**
  ```bash
  make watch
  ```

## IDE Support

Mill works with:
- **IntelliJ IDEA** - Install the Scala plugin and import the project
- **VS Code** - Install the Metals extension for Scala support
