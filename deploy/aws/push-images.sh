#!/usr/bin/env bash
# Builds both images and pushes them to ECR, tagged with the git SHA and "latest".
#
# Usage:
#   AWS_REGION=eu-west-1 AWS_ACCOUNT_ID=123456789012 APP_DOMAIN=app.example.com ./deploy/aws/push-images.sh
#
# NEXT_PUBLIC_API_URL is baked into the frontend bundle at build time, so the frontend
# image is built for one API origin. With the recommended single-domain layout that is
# simply https://$APP_DOMAIN.
set -euo pipefail

: "${AWS_REGION:?set AWS_REGION}"
: "${AWS_ACCOUNT_ID:?set AWS_ACCOUNT_ID}"
: "${APP_DOMAIN:?set APP_DOMAIN}"

REGISTRY="${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"
TAG="$(git rev-parse --short HEAD)"
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"

aws ecr get-login-password --region "$AWS_REGION" \
  | docker login --username AWS --password-stdin "$REGISTRY"

for repo in recallai-backend recallai-frontend; do
  aws ecr describe-repositories --repository-names "$repo" --region "$AWS_REGION" >/dev/null 2>&1 \
    || aws ecr create-repository --repository-name "$repo" --region "$AWS_REGION" \
         --image-scanning-configuration scanOnPush=true >/dev/null
done

docker build -t "$REGISTRY/recallai-backend:$TAG" -t "$REGISTRY/recallai-backend:latest" "$ROOT/backend"
docker build \
  --build-arg "NEXT_PUBLIC_API_URL=https://$APP_DOMAIN" \
  -t "$REGISTRY/recallai-frontend:$TAG" -t "$REGISTRY/recallai-frontend:latest" "$ROOT/frontend"

docker push --all-tags "$REGISTRY/recallai-backend"
docker push --all-tags "$REGISTRY/recallai-frontend"

echo "Pushed tag $TAG. Update <TAG> in deploy/aws/ecs-task-*.json and register the task definitions:"
echo "  aws ecs register-task-definition --cli-input-json file://deploy/aws/ecs-task-backend.json"
echo "  aws ecs register-task-definition --cli-input-json file://deploy/aws/ecs-task-frontend.json"
