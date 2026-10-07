#!/bin/bash
# EC2 첫 부팅 때 한 번 돈다 (G11-4). Amazon Linux 2023 arm64 기준.
# docker·compose·git 을 깔고 저장소를 /opt/esti 에 받아 둔다. 앱을 띄우는 것은 ec2/deploy.sh 가 한다.
#
# 비밀값은 여기 없다 — deploy.sh 가 SSM Parameter Store 에서 읽어 .env 를 만든다.
# 결과는 /var/log/esti-user-data.log 에 남는다.

set -euxo pipefail
exec > >(tee -a /var/log/esti-user-data.log) 2>&1

COMPOSE_VERSION=v2.29.7
REPO_URL=https://github.com/suminleedev/esti.git

dnf install -y docker git
systemctl enable --now docker

# AL2023 저장소에 compose 플러그인이 없어 릴리스 바이너리를 받는다(버전 고정)
install -d /usr/local/lib/docker/cli-plugins
curl -fsSL -o /usr/local/lib/docker/cli-plugins/docker-compose \
  "https://github.com/docker/compose/releases/download/${COMPOSE_VERSION}/docker-compose-linux-aarch64"
chmod +x /usr/local/lib/docker/cli-plugins/docker-compose
docker compose version

# 2GB 에 JVM·PostgreSQL·Caddy 를 올린다 — 순간 부족으로 OOM 킬이 나지 않게 스왑 1GB
if [ ! -f /swapfile ]; then
  dd if=/dev/zero of=/swapfile bs=1M count=1024
  chmod 600 /swapfile
  mkswap /swapfile
  swapon /swapfile
  echo '/swapfile none swap sw 0 0' >> /etc/fstab
fi

# 공개 저장소라 인증 없이 받는다. compose·Caddyfile·스크립트만 쓰고 소스는 쓰지 않는다(이미지는 ECR)
if [ ! -d /opt/esti/.git ]; then
  git clone --depth 1 "$REPO_URL" /opt/esti
fi

echo "user-data 끝 — 다음: deploy/ec2/deploy.sh"
