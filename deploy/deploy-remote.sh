#!/usr/bin/env bash
# Único comando que puede ejecutar la clave SSH de GitHub Actions. En ~/.ssh/authorized_keys:
#   command="/home/garik/valkyria/deploy-remote.sh",restrict ssh-ed25519 AAAA... github-actions-deploy
# SSH ignora el comando que envía el cliente y ejecuta este script; lo enviado (la versión) llega en
# SSH_ORIGINAL_COMMAND y solo se acepta si tiene el formato vX.Y.Z.
set -euo pipefail
cd "$(dirname "$0")"

VERSION="${SSH_ORIGINAL_COMMAND:-}"
if [[ ! "$VERSION" =~ ^v[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  echo "ERROR: versión no válida (formato vX.Y.Z)" >&2
  exit 1
fi

# Un solo despliegue a la vez
exec 9> /tmp/valkyria-deploy.lock
if ! flock -n 9; then
  echo "ERROR: ya hay un despliegue en marcha" >&2
  exit 1
fi

./backup.sh
./deploy.sh "$VERSION"
