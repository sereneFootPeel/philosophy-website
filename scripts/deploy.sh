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

ensure_command() {
  local cmd="$1"
  command -v "${cmd}" >/dev/null 2>&1 || abort "Command '${cmd}' is required but not found in PATH"
}

ensure_command git
ensure_command java

cd "${APP_DIR}" || abort "Unable to change directory to ${APP_DIR}"

log "Fetching latest changes from ${GIT_REMOTE}/${GIT_BRANCH}";
git fetch --prune "${GIT_REMOTE}" || abort "git fetch failed"
git checkout "${GIT_BRANCH}" || abort "Unable to checkout branch ${GIT_BRANCH}"
git reset --hard "${GIT_REMOTE}/${GIT_BRANCH}" || abort "git reset failed"
git clean -fd || abort "git clean failed"

log "Cleaning previous build artifacts"
${MVN_CMD} clean || abort "Maven clean failed"

log "Building project"
${MVN_CMD} package -DskipTests -Dspring.profiles.active="${SPRING_PROFILE}" || abort "Maven package failed"

ARTIFACT_PATH=$(find "${APP_DIR}/target" -maxdepth 1 -type f -name '*.jar' ! -name '*sources*' ! -name '*javadoc*' | head -n 1)

if [[ -z "${ARTIFACT_PATH}" ]]; then
  abort "No JAR artifact produced in target directory"
fi

mkdir -p "${DEPLOY_DIR}"
cp "${ARTIFACT_PATH}" "${DEPLOY_DIR}/${JAR_NAME}"
log "Copied artifact to ${DEPLOY_DIR}/${JAR_NAME}"

if command -v systemctl >/dev/null 2>&1; then
  log "Restarting systemd service ${SERVICE_NAME}"
  sudo systemctl daemon-reload || abort "systemctl daemon-reload failed"
  sudo systemctl restart "${SERVICE_NAME}" || abort "Failed to restart service ${SERVICE_NAME}"
else
  log "systemctl not found; attempting to restart JAR directly"
  pkill -f "${DEPLOY_DIR}/${JAR_NAME}" >/dev/null 2>&1 || true
  nohup java -jar "${DEPLOY_DIR}/${JAR_NAME}" --spring.profiles.active="${SPRING_PROFILE}" >/dev/null 2>&1 &
  log "Application restarted via nohup"
fi

log "Deployment finished successfully"

