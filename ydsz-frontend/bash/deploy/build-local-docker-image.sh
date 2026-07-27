#!/bin/bash
set -e

# ==================== Build & Deploy Frontend Docker Image ====================
# Usage: ./build-local-docker-image.sh [tag]
# Default tag: latest

TAG=${1:-latest}
IMAGE_NAME="ydsz-pmis/frontend"
CONTEXT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"

echo "============================================"
echo "  Building ${IMAGE_NAME}:${TAG}"
echo "  Context: ${CONTEXT_DIR}"
echo "============================================"

# Step 1: Build Docker image
docker build \
  -f "${CONTEXT_DIR}/bash/deploy/Dockerfile" \
  -t "${IMAGE_NAME}:${TAG}" \
  "${CONTEXT_DIR}"

echo ""
echo "✅ Docker image built: ${IMAGE_NAME}:${TAG}"

# Step 2: Show image info
docker images "${IMAGE_NAME}:${TAG}"

echo ""
echo "To run: docker run -p 5600:8080 ${IMAGE_NAME}:${TAG}"
echo "To compose: docker compose -f docker-compose.yml up -d"
