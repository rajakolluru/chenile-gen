#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../../.." && pwd)"

IMAGE_NAME="${IMAGE_NAME:-jgen-portal:local}"
CONTAINER_NAME="${CONTAINER_NAME:-jgen-portal}"
PORT="${PORT:-8080}"
WORKSPACE_DIR="${WORKSPACE_DIR:-${REPO_ROOT}/jgen/jgen-portal/.jgen-portal}"
M2_DIR="${M2_DIR:-${HOME}/.m2}"

mkdir -p "${WORKSPACE_DIR}" "${M2_DIR}"

docker build \
  -f "${REPO_ROOT}/jgen/jgen-portal/Dockerfile" \
  -t "${IMAGE_NAME}" \
  "${REPO_ROOT}"

if docker ps -a --format '{{.Names}}' | grep -Fxq "${CONTAINER_NAME}"; then
  docker rm -f "${CONTAINER_NAME}" >/dev/null
fi

echo "Starting JGen Portal at http://localhost:${PORT}"
echo "Workspace data: ${WORKSPACE_DIR}"

docker run \
  --name "${CONTAINER_NAME}" \
  -p "${PORT}:8080" \
  -v "${WORKSPACE_DIR}:/app/.jgen-portal" \
  -v "${M2_DIR}:/root/.m2" \
  "${IMAGE_NAME}"
