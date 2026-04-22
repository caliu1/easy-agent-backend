#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

ENV_FILE="${ENV_FILE:-.env}"
BASE_COMPOSE_FILE="${BASE_COMPOSE_FILE:-docker-compose.yml}"
PROD_COMPOSE_FILE="${PROD_COMPOSE_FILE:-docker-compose.prod.yml}"
REUSE_COMPOSE_FILE="${REUSE_COMPOSE_FILE:-docker-compose.reuse.yml}"
DEPLOY_MODE="${DEPLOY_MODE:-standalone}" # standalone | reuse
ACTION="${1:-up}"

required_commands=(docker)

log() {
  printf '[deploy] %s\n' "$*"
}

die() {
  printf '[deploy][error] %s\n' "$*" >&2
  exit 1
}

usage() {
  cat <<'EOF'
Usage:
  ./deploy.sh up        # pull + run
  ./deploy.sh pull      # pull images only
  ./deploy.sh restart   # recreate containers
  ./deploy.sh status    # container status
  ./deploy.sh logs      # stream logs
  ./deploy.sh down      # stop/remove containers

Modes (set DEPLOY_MODE in .env):
  standalone: run mysql + backend + frontend + gateway
  reuse:      reuse existing mysql/nginx, run backend + frontend only

Notes:
  1) First run creates .env from .env.example and exits.
  2) BACKEND_IMAGE / FRONTEND_IMAGE are required.
  3) ACR_USERNAME / ACR_PASSWORD are optional for auto docker login.
EOF
}

ensure_commands() {
  local cmd
  for cmd in "${required_commands[@]}"; do
    command -v "$cmd" >/dev/null 2>&1 || die "missing command: $cmd"
  done
  docker compose version >/dev/null 2>&1 || die "docker compose plugin not found"
}

ensure_env_file() {
  if [[ -f "$ENV_FILE" ]]; then
    return
  fi

  if [[ -f ".env.example" ]]; then
    cp .env.example "$ENV_FILE"
    die "generated $ENV_FILE, please edit it first"
  fi

  die "$ENV_FILE not found"
}

load_env() {
  local line key raw val
  while IFS= read -r line || [[ -n "$line" ]]; do
    line="${line%$'\r'}"
    [[ -z "$line" ]] && continue
    [[ "$line" =~ ^[[:space:]]*# ]] && continue
    [[ "$line" != *=* ]] && continue

    key="${line%%=*}"
    raw="${line#*=}"

    key="${key#"${key%%[![:space:]]*}"}"
    key="${key%"${key##*[![:space:]]}"}"
    raw="${raw#"${raw%%[![:space:]]*}"}"

    if [[ ! "$key" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]]; then
      die "invalid env key in $ENV_FILE: $key"
    fi

    val="$raw"
    if [[ "$val" == \"*\" && "$val" == *\" && ${#val} -ge 2 ]]; then
      val="${val:1:${#val}-2}"
    elif [[ "$val" == \'*\' && "$val" == *\' && ${#val} -ge 2 ]]; then
      val="${val:1:${#val}-2}"
    fi

    export "$key=$val"
  done < "$ENV_FILE"

  : "${BACKEND_PORT:=8091}"
  : "${FRONTEND_PORT:=3000}"
  : "${DEPLOY_MODE:=standalone}"
}

require_vars() {
  local vars_common=(
    BACKEND_IMAGE
    FRONTEND_IMAGE
    PUBLIC_BASE_URL
    DASHSCOPE_API_KEY
  )
  local vars_standalone=(
    MYSQL_ROOT_PASSWORD
    MYSQL_DATABASE
    MYSQL_USER
    MYSQL_PASSWORD
  )
  local vars_reuse=(
    EXTERNAL_DB_URL
    EXTERNAL_DB_USERNAME
    EXTERNAL_DB_PASSWORD
  )

  local vars=("${vars_common[@]}")
  case "$DEPLOY_MODE" in
    standalone)
      vars+=("${vars_standalone[@]}")
      ;;
    reuse)
      vars+=("${vars_reuse[@]}")
      ;;
    *)
      die "DEPLOY_MODE must be standalone or reuse, got: $DEPLOY_MODE"
      ;;
  esac

  local missing=()
  local key
  for key in "${vars[@]}"; do
    if [[ -z "${!key:-}" ]]; then
      missing+=("$key")
    fi
  done

  if [[ "${#missing[@]}" -gt 0 ]]; then
    die "missing env vars in $ENV_FILE: ${missing[*]}"
  fi
}

render_prod_compose() {
  cat > "$PROD_COMPOSE_FILE" <<'EOF'
services:
  backend:
    image: ${BACKEND_IMAGE}
    build: null
  frontend:
    image: ${FRONTEND_IMAGE}
    build: null
EOF
}

render_reuse_compose_if_needed() {
  if [[ "$DEPLOY_MODE" != "reuse" ]]; then
    return
  fi

  cat > "$REUSE_COMPOSE_FILE" <<'EOF'
services:
  backend:
    depends_on: []
    extra_hosts:
      - "host.docker.internal:host-gateway"
    environment:
      SPRING_DATASOURCE_URL: ${EXTERNAL_DB_URL}
      SPRING_DATASOURCE_USERNAME: ${EXTERNAL_DB_USERNAME}
      SPRING_DATASOURCE_PASSWORD: ${EXTERNAL_DB_PASSWORD}
      SPRING_DATASOURCE_DRIVER_CLASS_NAME: ${EXTERNAL_DB_DRIVER_CLASS_NAME:-com.mysql.cj.jdbc.Driver}
    ports:
      - "${BACKEND_PORT}:8091"

  frontend:
    depends_on:
      - backend
    extra_hosts:
      - "host.docker.internal:host-gateway"
    ports:
      - "${FRONTEND_PORT}:3000"
EOF
}

compose() {
  local files=(
    -f "$BASE_COMPOSE_FILE"
    -f "$PROD_COMPOSE_FILE"
  )

  if [[ "$DEPLOY_MODE" == "reuse" ]]; then
    files+=(-f "$REUSE_COMPOSE_FILE")
  fi

  docker compose \
    --env-file "$ENV_FILE" \
    "${files[@]}" \
    "$@"
}

service_targets() {
  if [[ "$DEPLOY_MODE" == "reuse" ]]; then
    echo "backend frontend"
  else
    echo "mysql backend frontend gateway"
  fi
}

docker_login_if_configured() {
  if [[ -z "${ACR_USERNAME:-}" || -z "${ACR_PASSWORD:-}" ]]; then
    log "ACR_USERNAME/ACR_PASSWORD not set, skip docker login"
    return
  fi

  declare -A registries=()
  local image registry
  for image in "$BACKEND_IMAGE" "$FRONTEND_IMAGE"; do
    registry="${image%%/*}"
    if [[ "$registry" == "$image" ]]; then
      continue
    fi
    if [[ "$registry" == *.* || "$registry" == *:* ]]; then
      registries["$registry"]=1
    fi
  done

  local reg
  for reg in "${!registries[@]}"; do
    log "docker login $reg"
    printf '%s' "$ACR_PASSWORD" | docker login --username "$ACR_USERNAME" --password-stdin "$reg"
  done
}

health_check() {
  if ! command -v curl >/dev/null 2>&1; then
    log "curl not found, skip health check"
    return
  fi

  if [[ "$DEPLOY_MODE" == "reuse" ]]; then
    local fp="${FRONTEND_PORT:-3000}"
    local bp="${BACKEND_PORT:-8091}"

    if curl -fsS "http://127.0.0.1:${fp}/" >/dev/null; then
      log "frontend healthy: http://127.0.0.1:${fp}/"
    else
      log "frontend check failed on port ${fp}"
    fi

    if curl -fsS "http://127.0.0.1:${bp}/actuator/health" >/dev/null || curl -fsS "http://127.0.0.1:${bp}/" >/dev/null; then
      log "backend reachable: http://127.0.0.1:${bp}/"
    else
      log "backend check failed on port ${bp}"
    fi
  else
    local gp="${GATEWAY_PORT:-80}"
    if curl -fsS "http://127.0.0.1:${gp}/" >/dev/null; then
      log "gateway healthy: http://127.0.0.1:${gp}/"
    else
      log "gateway check failed on port ${gp}"
    fi
  fi
}

up() {
  docker_login_if_configured
  # shellcheck disable=SC2046
  compose pull $(service_targets)
  # shellcheck disable=SC2046
  compose up -d --no-build $(service_targets)
  # shellcheck disable=SC2046
  compose ps $(service_targets)
  health_check
}

pull() {
  docker_login_if_configured
  # shellcheck disable=SC2046
  compose pull $(service_targets)
}

restart() {
  docker_login_if_configured
  # shellcheck disable=SC2046
  compose pull $(service_targets)
  # shellcheck disable=SC2046
  compose up -d --force-recreate --no-build $(service_targets)
  # shellcheck disable=SC2046
  compose ps $(service_targets)
  health_check
}

status() {
  # shellcheck disable=SC2046
  compose ps $(service_targets)
}

logs() {
  # shellcheck disable=SC2046
  compose logs -f $(service_targets)
}

down() {
  if [[ "$DEPLOY_MODE" == "reuse" ]]; then
    compose stop backend frontend || true
    compose rm -f backend frontend || true
    return
  fi
  compose down
}

main() {
  case "$ACTION" in
    -h|--help|help)
      usage
      exit 0
      ;;
  esac

  ensure_commands
  ensure_env_file
  load_env
  require_vars
  render_prod_compose
  render_reuse_compose_if_needed

  case "$ACTION" in
    up) up ;;
    pull) pull ;;
    restart) restart ;;
    status) status ;;
    logs) logs ;;
    down) down ;;
    *)
      usage
      die "unknown action: $ACTION"
      ;;
  esac
}

main
