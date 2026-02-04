#!/bin/bash
set -e

# Sync GitHub Repository Rulesets
# Usage: ./scripts/sync-github-rules.sh

REPO="sfkamath/jvm-hotpath"
RULESET_ID="12427676"
FILE=".github/rulesets/main-branch-protection.json"

echo "Syncing ruleset ${RULESET_ID} for ${REPO}..."

gh api --method PUT "repos/${REPO}/rulesets/${RULESET_ID}" \
  --input "${FILE}" \
  --silent

echo "✅ Success! Ruleset updated."
