#!/usr/bin/env bash
# Despliega una versión etiquetada de backend y frontend. Uso (desde ~/valkyria): ./deploy.sh v1.0.0
set -euo pipefail
cd "$(dirname "$0")"

VERSION="${1:?Uso: ./deploy.sh vX.Y.Z}"
REPOS=(backend/valkyria frontend/valkyria-frontend)

# Antes de tocar nada: el tag tiene que existir en los dos repos
for repo in "${REPOS[@]}"; do
  git -C "$repo" fetch --quiet --tags --prune origin
  if ! git -C "$repo" rev-parse --quiet --verify "refs/tags/$VERSION" > /dev/null; then
    echo "ERROR: el tag $VERSION no existe en $repo" >&2
    exit 1
  fi
done

for repo in "${REPOS[@]}"; do
  echo "==> $repo: $(git -C "$repo" describe --tags --always) -> $VERSION"
  git -C "$repo" checkout --quiet "$VERSION"
done

# La configuración de compose viaja con cada versión del backend
if ! cmp -s backend/valkyria/deploy/compose.yaml compose.yaml; then
  echo "==> compose.yaml actualizado desde el repo"
  cp backend/valkyria/deploy/compose.yaml compose.yaml
fi
if ! cmp -s backend/valkyria/deploy/deploy.sh deploy.sh; then
  echo "AVISO: deploy.sh ha cambiado en el repo. Actualízalo al terminar:"
  echo "       cp backend/valkyria/deploy/deploy.sh deploy.sh"
fi

echo "==> Construyendo imágenes"
docker compose build

echo "==> Arrancando contenedores"
docker compose up -d --remove-orphans

docker compose ps
echo "==> Desplegada la versión $VERSION"
