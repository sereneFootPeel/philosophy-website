#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

APP_DIR="${APP_DIR:-${PROJECT_ROOT}}"
GIT_REMOTE="${GIT_REMOTE:-origin}"
GIT_BRANCH="${GIT_BRANCH:-main}"
SERVICE_NAME="${SERVICE_NAME:-philosophy-website}"
SPRING_PROFILE="${SPRING_PROFILE:-prod}"
MVN_CMD="${MVN_CMD:-./mvnw}"
JAR_NAME="${JAR_NAME:-philosophy-website.jar}"
DEPLOY_DIR="${DEPLOY_DIR:-${APP_DIR}/deploy}"
LOG_FILE="${LOG_FILE:-${APP_DIR}/logs/deploy.log}"

mkdir -p "$(dirname "${LOG_FILE}")"
touch "${LOG_FILE}"

log() {
  local timestamp
  timestamp="$(date '+%Y-%m-%d %H:%M:%S')"
  echo "[${timestamp}] $*" | tee -a "${LOG_FILE}"
}

abort() {
  log "ERROR: $*"
  exit 1
}

run_step() {
  local description="$1"
  shift
  log "${description}"
  "$@" 2>&1 | tee -a "${LOG_FILE}"
}

ensure_command() {
  local cmd="$1"
  command -v "${cmd}" >/dev/null 2>&1 || abort "Command '${cmd}' is required but not found in PATH"
}

ensure_command git
ensure_command java

if [[ ! -x "${APP_DIR}/mvnw" && -f "${APP_DIR}/mvnw" ]]; then
  chmod +x "${APP_DIR}/mvnw" || abort "Unable to set execute bit on mvnw"
fi

cd "${APP_DIR}" || abort "Unable to change directory to ${APP_DIR}"

run_step "Fetching latest changes from ${GIT_REMOTE}/${GIT_BRANCH}" git fetch --prune "${GIT_REMOTE}"
run_step "Checking out ${GIT_BRANCH}" git checkout "${GIT_BRANCH}"
run_step "Resetting to ${GIT_REMOTE}/${GIT_BRANCH}" git reset --hard "${GIT_REMOTE}/${GIT_BRANCH}"
run_step "Cleaning untracked files" git clean -fd

MVN_WRAPPER="${APP_DIR}/mvnw"
if [[ -x "${MVN_WRAPPER}" ]]; then
  MVN_CMD="${MVN_CMD:-${MVN_WRAPPER}}"
else
  ensure_command mvn
  MVN_CMD="${MVN_CMD:-mvn}"
fi

run_step "Running Maven clean" "${MVN_CMD}" -B clean || abort "Maven clean failed"

run_step "Building project" "${MVN_CMD}" -B package -DskipTests -Dspring.profiles.active="${SPRING_PROFILE}" || abort "Maven package failed"

ARTIFACT_PATH=$(find "${APP_DIR}/target" -maxdepth 1 -type f -name '*.jar' ! -name '*sources*' ! -name '*javadoc*' | head -n 1)

if [[ -z "${ARTIFACT_PATH}" ]]; then
  abort "No JAR artifact produced in target directory"
fi

mkdir -p "${DEPLOY_DIR}"
cp "${ARTIFACT_PATH}" "${DEPLOY_DIR}/${JAR_NAME}"
log "Copied artifact to ${DEPLOY_DIR}/${JAR_NAME}"

if command -v systemctl >/dev/null 2>&1; then
  run_step "Reloading systemd units" sudo systemctl daemon-reload || abort "systemctl daemon-reload failed"
  run_step "Restarting service ${SERVICE_NAME}" sudo systemctl restart "${SERVICE_NAME}" || abort "Failed to restart service ${SERVICE_NAME}"
else
  log "systemctl not found; attempting to restart JAR directly"
  pkill -f "${DEPLOY_DIR}/${JAR_NAME}" >/dev/null 2>&1 || true
  nohup java -jar "${DEPLOY_DIR}/${JAR_NAME}" --spring.profiles.active="${SPRING_PROFILE}" >/dev/null 2>&1 &
  log "Application restarted via nohup"
fi

log "Deployment finished successfully"

