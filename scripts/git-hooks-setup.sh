#!/bin/bash
set -e

echo "Setting up git hooks..."

# Get the root directory of the git repository
GIT_ROOT=$(git rev-parse --show-toplevel)

# Create symlink for pre-commit hook
ln -sf ../../git-hooks/pre-commit "$GIT_ROOT/.git/hooks/pre-commit"

echo "✅ Git hooks installed successfully!"
echo ""
echo "The following hooks are now active:"
echo "  - pre-commit: Runs format-check and lint-check on staged Scala files"
echo ""
echo "To bypass hooks (not recommended), use: git commit --no-verify"
