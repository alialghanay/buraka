#!/usr/bin/env bash
# End-to-end check: seed data present, review flows to catalog rating via RabbitMQ.
# Prereqs: infra up (docker compose -f buraka-back/docker-compose.yml up -d),
#          catalog-service on :8081, review-service on :8082 (./gradlew bootRun in each).
set -euo pipefail

CATALOG=http://localhost:8081
REVIEW=http://localhost:8082

echo "1/5 catalog list endpoint"
curl -sf "$CATALOG/api/products?page=0&size=5" | grep -q '"product-1"' || { echo "FAIL: product-1 not in list"; exit 1; }

echo "2/5 catalog detail endpoint"
curl -sf "$CATALOG/api/products/product-1" | grep -q '"slug":"product-1"' || { echo "FAIL: detail"; exit 1; }

PRODUCT_ID=$(curl -sf "$CATALOG/api/products/product-1" | sed -n 's/.*"productId":\([0-9]*\).*/\1/p')
echo "3/5 posting review for productId=$PRODUCT_ID"
curl -sf -X POST "$REVIEW/api/reviews" \
  -H 'Content-Type: application/json' \
  -H 'X-User-Id: 1' \
  -d "{\"productId\":$PRODUCT_ID,\"title\":\"Smoke\",\"rating\":5}" > /dev/null || { echo "FAIL: create review"; exit 1; }

echo "4/5 review readable"
curl -sf "$REVIEW/api/reviews?productId=$PRODUCT_ID" | grep -q '"rating":5' || { echo "FAIL: read review"; exit 1; }

echo "5/5 waiting for rating propagation via rabbitmq (up to 15s)"
for i in $(seq 1 15); do
  COUNT=$(curl -sf "$CATALOG/api/products/product-1" | sed -n 's/.*"ratingsQuantity":\([0-9]*\).*/\1/p')
  if [ "$COUNT" -ge 1 ] 2>/dev/null; then
    echo "PASS: ratingsQuantity=$COUNT after ${i}s"
    exit 0
  fi
  sleep 1
done
echo "FAIL: rating did not propagate within 15s"
exit 1
