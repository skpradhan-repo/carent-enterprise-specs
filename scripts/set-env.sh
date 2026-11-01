#!/usr/bin/env bash
# Loads the environment variables the CareSync services need into the CURRENT shell.
# Source it (do not execute it) in every terminal before starting a service:
#
#     source scripts/set-env.sh
#
# First run: generates random values and saves them to .env.local.sh (git-ignored, never commit it),
# so every terminal gets the SAME JWT_SECRET. Delete that file to generate new values.
# The database password is read from infra/docker-compose.yml, so it always matches the container.
_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
_file="$_root/.env.local.sh"

if [ ! -f "$_file" ]; then
  {
    echo "export JWT_SECRET='$(openssl rand -hex 32)'"
    echo "export AUTH_MEMBER_PASSWORD='$(openssl rand -hex 12)'"
    echo "export AUTH_ADJUDICATOR_PASSWORD='$(openssl rand -hex 12)'"
    echo "export AUTH_ADMIN_PASSWORD='$(openssl rand -hex 12)'"
  } > "$_file"
  chmod 600 "$_file"
  echo "Generated new local secrets in $_file (git-ignored)."
fi
# shellcheck disable=SC1090
. "$_file"

SPRING_DATASOURCE_PASSWORD="$(sed -n 's/^[[:space:]]*POSTGRES_PASSWORD:[[:space:]]*//p' "$_root/infra/docker-compose.yml" | head -n 1)"
export SPRING_DATASOURCE_PASSWORD
unset _root _file

echo "Environment ready: SPRING_DATASOURCE_PASSWORD, JWT_SECRET, AUTH_MEMBER_PASSWORD, AUTH_ADJUDICATOR_PASSWORD, AUTH_ADMIN_PASSWORD"
