#!/usr/bin/env bash
# WAR 빌드(Docker Maven, JDK 17). 결과: target/egov-asset-mgmt-1.0.0.war
set -euo pipefail
cd "$(dirname "$0")/.."
ROOT_HOST=$( (pwd -W 2>/dev/null) || pwd )
export MSYS_NO_PATHCONV=1
docker run --rm -v "$ROOT_HOST:/src" -v egov-m2:/root/.m2 -w /src maven:3.9-eclipse-temurin-17 \
  mvn -B -DskipTests -Dmaven.javadoc.skip=true package
