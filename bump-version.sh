#!/usr/bin/env bash
set -euo pipefail

if [ $# -ne 2 ]; then
  echo "Usage: $0 <old-version> <new-version>"
  echo "Example: $0 0.2.6 0.2.7"
  exit 1
fi

OLD="$1"
NEW="$2"

# Escape dots for sed
OLD_ESC="${OLD//./\\.}"

FILES=(
  gradle.properties
  pom.xml
  README.md
)

for f in "${FILES[@]}"; do
  if [ -f "$f" ]; then
    sed -i '' "s/${OLD_ESC}/${NEW}/g" "$f"
    echo "Updated $f"
  else
    echo "SKIP: $f not found"
  fi
done

echo "Done. Review changes with: git diff"
