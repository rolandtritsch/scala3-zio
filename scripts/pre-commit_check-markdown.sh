#!/bin/bash
set -e

# Check if there are any staged Markdown files
STAGED_MD_FILES=$(git diff --cached --name-only --diff-filter=ACM | grep '\.md$' || true)

if [ -z "$STAGED_MD_FILES" ]; then
  exit 0
fi

echo "Checking markdown format..."
if ! make markdownlint-check; then
  echo "❌ Markdown files are not properly formatted."
  echo "Run 'make markdownlint' to fix formatting issues."
  exit 1
fi

echo "✅ Markdown checks passed!"
exit 0
