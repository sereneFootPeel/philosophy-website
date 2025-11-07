#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

APP_DIR="${APP_DIR:-${PROJECT_ROOT}}"
GIT_REMOTE="${GIT_REMOTE:-origin}"
GIT_BRANCH="${GIT_BRANCH:-main}"
SPRING_PROFILE="${SPRING_PROFILE:-prod}"
MVN_CMD="${MVN_CMD:-./mvnw}"
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

cd "${APP_DIR}" || abort "Unable to change directory to ${APP_DIR}"

run_step "Fetching latest changes from ${GIT_REMOTE}/${GIT_BRANCH}" git fetch --prune "${GIT_REMOTE}"
run_step "Checking out ${GIT_BRANCH}" git checkout "${GIT_BRANCH}"
run_step "Resetting to ${GIT_REMOTE}/${GIT_BRANCH}" git reset --hard "${GIT_REMOTE}/${GIT_BRANCH}"
run_step "Cleaning untracked files" git clean -fd

MVN_WRAPPER="${APP_DIR}/mvnw"
if [[ -f "${MVN_WRAPPER}" ]]; then
  if [[ ! -x "${MVN_WRAPPER}" ]]; then
    run_step "Setting execute permission on mvnw" chmod +x "${MVN_WRAPPER}"
  fi
  if [[ -x "${MVN_WRAPPER}" ]]; then
    MVN_CMD="${MVN_CMD:-${MVN_WRAPPER}}"
  fi
fi

if [[ -z "${MVN_CMD}" || ! -x "${MVN_CMD}" ]]; then
  ensure_command mvn
  MVN_CMD="${MVN_CMD:-mvn}"
fi

run_step "Running Maven clean" "${MVN_CMD}" -B clean || abort "Maven clean failed"

run_step "Stopping existing spring-boot:run process" pkill -f "${MVN_CMD} spring-boot:run" || true

mkdir -p "${DEPLOY_DIR}"

log "Starting Spring Boot via mvn spring-boot:run"
(
  cd "${APP_DIR}"
  SPRING_PROFILES_ACTIVE="${SPRING_PROFILE}" nohup "${MVN_CMD}" spring-boot:run >>"${LOG_FILE}" 2>&1 &
  echo $! > "${DEPLOY_DIR}/spring-boot-run.pid"
) || abort "Failed to start spring-boot:run"

PID="$(cat "${DEPLOY_DIR}/spring-boot-run.pid" 2>/dev/null || true)"
if [[ -n "${PID}" ]]; then
  log "Spring Boot running with PID ${PID}"
else
  log "Spring Boot started (PID unknown)"
fi

log "Deployment finished successfully"

