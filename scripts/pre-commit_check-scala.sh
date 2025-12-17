#!/bin/bash
set -e

# Check if there are any staged Scala files
STAGED_SCALA_FILES=$(git diff --cached --name-only --diff-filter=ACM | grep '\.scala$' || true)

if [ -z "$STAGED_SCALA_FILES" ]; then
  exit 0
fi

echo "Checking format..."
if ! make format-check; then
  echo "❌ Code is not properly formatted."
  echo "Run 'make format' to fix formatting issues."
  exit 1
fi

echo "Checking lint..."
if ! make lint-check; then
  echo "❌ Code has linting issues."
  echo "Run 'make lint' to fix linting issues."
  exit 1
fi

echo "✅ Scala checks passed!"
exit 0
