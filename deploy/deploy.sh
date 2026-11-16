#!/bin/bash
set -e

TAG=$(cat /opt/shipmed-deploy/IMAGE_TAG)
IMAGE=mxu3etl1.kr.private-ncr.ntruss.com/shipmed:$TAG

docker pull $IMAGE
docker rm -f shipmed-app || true
docker run -d --name shipmed-app --restart unless-stopped \
  --env-file /etc/shipmed/shipmed.env -p 8080:8080 $IMAGE

curl -f --retry 24 --retry-delay 5 --retry-all-errors http://localhost:8080/actuator/health/liveness
