#!/usr/bin/env bash
# 서버에서 실행하는 배포: 이미지 pull → compose up. 순서와 롤백은 infra/README.md "배포·롤백".
# 사용: infra/deploy/deploy.sh <server1|server2> [이미지 태그, 기본 latest. 롤백은 sha-xxxxxxx]
#       ENV_FILE=/다른/경로/.env 로 .env 위치를 바꿀 수 있다(기본 /opt/rental/.env)
set -euo pipefail

SERVER="${1:-}"
TAG="${2:-latest}"
case "$SERVER" in
	server1 | server2) ;;
	*)
		echo "사용: $0 <server1|server2> [태그]" >&2
		exit 1
		;;
esac

INFRA_DIR="$(cd "$(dirname "$0")/.." && pwd)"
export ENV_FILE="${ENV_FILE:-/opt/rental/.env}"
export IMAGE_TAG="$TAG"

if [ ! -f "$ENV_FILE" ]; then
	echo "$ENV_FILE 이 없다. infra/.env.prod.example을 복사해 채운다" >&2
	exit 1
fi
if [ "$(stat -c %a "$ENV_FILE")" != "600" ]; then
	echo "$ENV_FILE 권한을 600으로 바꾼다" >&2
	chmod 600 "$ENV_FILE"
fi

COMPOSE=(docker compose -f "$INFRA_DIR/docker-compose.$SERVER.yml" --env-file "$ENV_FILE")

echo "[$SERVER] 이미지 태그 $TAG pull"
"${COMPOSE[@]}" pull
echo "[$SERVER] 컨테이너 갱신"
"${COMPOSE[@]}" up -d --remove-orphans
# 태그가 빠진 옛 이미지 정리. 롤백은 GHCR의 sha-xxxxxxx 태그를 다시 pull한다
docker image prune -f > /dev/null
"${COMPOSE[@]}" ps
