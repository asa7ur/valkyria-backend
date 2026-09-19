#!/usr/bin/env bash
# Despliega una versión etiquetada de backend y frontend. Uso (desde ~/valkyria): ./deploy.sh v1.0.0
# Desde la 1.6.0 las imágenes se descargan de GHCR (las publica GitHub Actions); las versiones anteriores,
# que no tienen imagen publicada, se siguen construyendo aquí.
set -euo pipefail
cd "$(dirname "$0")"

VERSION="${1:?Uso: ./deploy.sh vX.Y.Z}"
if [[ ! "$VERSION" =~ ^v[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  echo "ERROR: versión no válida: $VERSION (formato vX.Y.Z)" >&2
  exit 1
fi
REPOS=(backend/valkyria frontend/valkyria-frontend)

# Lo usa compose.yaml para elegir las imágenes (ghcr.io/...:X.Y.Z)
export VALKYRIA_VERSION="${VERSION#v}"

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
# Los scripts no se sustituyen solos (deploy.sh se está ejecutando ahora mismo)
for script in deploy.sh backup.sh deploy-remote.sh; do
  if ! cmp -s "backend/valkyria/deploy/$script" "$script"; then
    echo "AVISO: $script ha cambiado en el repo. Actualízalo al terminar:"
    echo "       cp backend/valkyria/deploy/$script $script"
  fi
done

# Falla aquí (antes de descargar o parar nada) si al .env le falta alguna variable obligatoria
docker compose config --quiet

# Solo backend y frontend: la base de datos no se actualiza sola con cada despliegue
echo "==> Descargando imágenes"
docker compose pull --ignore-buildable backend frontend

# Solo hace algo con versiones anteriores a la 1.6.0 (compose.yaml con "build")
docker compose build

echo "==> Arrancando contenedores"
docker compose up -d --remove-orphans

# Guardar la versión en el .env para que "docker compose ps/logs/restart" funcionen a mano
if grep -q '^VALKYRIA_VERSION=' .env; then
  sed -i "s/^VALKYRIA_VERSION=.*/VALKYRIA_VERSION=$VALKYRIA_VERSION/" .env
else
  echo "VALKYRIA_VERSION=$VALKYRIA_VERSION" >> .env
fi

docker compose ps
echo "==> Desplegada la versión $VERSION"
