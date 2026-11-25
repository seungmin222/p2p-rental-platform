#!/usr/bin/env bash
# 서버 최초 1회 설정 (Amazon Linux 2023, x86_64). 두 서버 모두 ec2-user로 실행: sudo bash setup.sh
# Docker·Compose 플러그인·git 설치, 스왑 2GB, /opt/rental 디렉터리와 .env 권한. 여러 번 실행해도 결과가 같다.
set -euo pipefail

if [ "$(id -u)" -ne 0 ]; then
	echo "sudo로 실행한다: sudo bash $0" >&2
	exit 1
fi

RUN_USER="${SUDO_USER:-ec2-user}"
APP_DIR=/opt/rental

echo "[1/4] Docker·git 설치"
dnf install -y docker git
systemctl enable --now docker
usermod -aG docker "$RUN_USER"

echo "[2/4] Docker Compose 플러그인"
# AL2023 저장소에는 compose 플러그인이 없어 공식 릴리스 바이너리를 받는다. 버전을 고정하려면 COMPOSE_VERSION=v2.x.y
if ! docker compose version > /dev/null 2>&1; then
	PLUGIN_DIR=/usr/local/lib/docker/cli-plugins
	mkdir -p "$PLUGIN_DIR"
	if [ -n "${COMPOSE_VERSION:-}" ]; then
		URL="https://github.com/docker/compose/releases/download/${COMPOSE_VERSION}/docker-compose-linux-x86_64"
	else
		URL="https://github.com/docker/compose/releases/latest/download/docker-compose-linux-x86_64"
	fi
	curl -fsSL "$URL" -o "$PLUGIN_DIR/docker-compose"
	chmod +x "$PLUGIN_DIR/docker-compose"
fi
docker compose version

echo "[3/4] 스왑 2GB (t3.small 메모리 2GB 보완)"
if ! swapon --show=NAME --noheadings | grep -q '^/swapfile$'; then
	if [ ! -f /swapfile ]; then
		dd if=/dev/zero of=/swapfile bs=1M count=2048 status=none
		chmod 600 /swapfile
		mkswap /swapfile
	fi
	swapon /swapfile
fi
grep -q '^/swapfile ' /etc/fstab || echo '/swapfile none swap sw 0 0' >> /etc/fstab
sysctl -q vm.swappiness=10
echo 'vm.swappiness=10' > /etc/sysctl.d/90-rental-swap.conf

echo "[4/4] $APP_DIR, .env 권한"
mkdir -p "$APP_DIR"
chown "$RUN_USER":"$RUN_USER" "$APP_DIR"
if [ -f "$APP_DIR/.env" ]; then
	chown "$RUN_USER":"$RUN_USER" "$APP_DIR/.env"
	chmod 600 "$APP_DIR/.env"
fi

cat <<EOF

완료. 다음 단계 (infra/README.md "최초 배포"):
  1) 다시 로그인해 docker 그룹 적용 (exit 후 재접속)
  2) git clone https://github.com/seungmin222/p2p-rental-platform.git $APP_DIR/repo
  3) cp $APP_DIR/repo/infra/.env.prod.example $APP_DIR/.env && chmod 600 $APP_DIR/.env, 값 채우기
  4) $APP_DIR/repo/infra/deploy/deploy.sh server1|server2
EOF
