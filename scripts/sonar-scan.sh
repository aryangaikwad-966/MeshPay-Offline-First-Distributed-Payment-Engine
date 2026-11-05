#!/usr/bin/env bash
# Run a SonarQube scan against the local repository using the official
# sonar-scanner-cli Docker image. Requires the sonarqube compose service
# (profile "sonar") to be running and a user token.
#
# Usage:
#   SONAR_TOKEN=<token> ./scripts/sonar-scan.sh
#   SONAR_HOST_URL=http://localhost:9000 SONAR_TOKEN=<token> ./scripts/sonar-scan.sh
set -euo pipefail

cd "$(dirname "$0")/.."

SONAR_HOST_URL="${SONAR_HOST_URL:-http://host.docker.internal:9000}"
SONAR_TOKEN="${SONAR_TOKEN:?Set SONAR_TOKEN to a SonarQube user token}"

# Start the SonarQube server if it is not already running.
if ! curl -sf "http://localhost:9000/api/system/status" >/dev/null 2>&1; then
  echo "SonarQube not reachable; starting compose profile 'sonar'..."
  docker compose --profile sonar up -d sonarqube
fi

docker run --rm \
  --add-host=host.docker.internal:host-gateway \
  -e SONAR_HOST_URL="${SONAR_HOST_URL}" \
  -e SONAR_TOKEN="${SONAR_TOKEN}" \
  -v "$(pwd):/usr/src" \
  sonarsource/sonar-scanner-cli
