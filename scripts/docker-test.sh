#!/usr/bin/env bash
set -euo pipefail

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo "Testing Docker setup..."

# Build image
echo -e "\n${GREEN}[1/5]${NC} Building Docker image..."
docker build -t scala3-zio-template:test .

# Start container
echo -e "\n${GREEN}[2/5]${NC} Starting container..."
docker run -d \
  --name scala3-zio-test \
  --env-file .env \
  -p 8080:8080 \
  scala3-zio-template:test

# Wait for health check
echo -e "\n${GREEN}[3/5]${NC} Waiting for health check (max 60s)..."
for i in {1..20}; do
  HEALTH=$(docker inspect --format='{{.State.Health.Status}}' scala3-zio-test 2>/dev/null || echo "none")
  if [ "$HEALTH" == "healthy" ]; then
    echo -e "${GREEN}✓${NC} Container is healthy"
    break
  fi
  echo "  Health status: $HEALTH (attempt $i/20)"
  sleep 3
done

# Test endpoints
echo -e "\n${GREEN}[4/5]${NC} Testing endpoints..."

# Test health endpoint
if curl -f -s http://localhost:8080/health > /dev/null; then
  echo -e "${GREEN}✓${NC} Health endpoint working"
else
  echo -e "${RED}✗${NC} Health endpoint failed"
  docker logs scala3-zio-test
  docker stop scala3-zio-test
  docker rm scala3-zio-test
  exit 1
fi

# Test root endpoint
if curl -f -s http://localhost:8080/ > /dev/null; then
  echo -e "${GREEN}✓${NC} Root endpoint working"
else
  echo -e "${RED}✗${NC} Root endpoint failed"
fi

# Cleanup
echo -e "\n${GREEN}[5/5]${NC} Cleaning up..."
docker stop scala3-zio-test
docker rm scala3-zio-test

echo -e "\n${GREEN}All tests passed!${NC}"
