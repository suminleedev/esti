#!/bin/bash
# EC2 에서 데모를 띄우거나 새 이미지로 바꾼다 (G11-4). 인스턴스 안에서 root 로 돈다.
#
#   노트북에서:  aws ssm send-command --instance-ids <id> --document-name AWS-RunShellScript \
#                  --parameters 'commands=["/opt/esti/deploy/ec2/deploy.sh"]'
#   (런북에 전체 절차가 있다)
#
# 하는 일: 저장소 갱신 → SSM 비밀값으로 .env → ECR 로그인 → 이미지 pull → 띄움 → 시드 완료까지 대기 → 백업 타이머 설치
# ESTI_TAG 로 이미지 태그를 고를 수 있다(기본 latest — 롤백은 커밋 SHA 태그).
#
# 종료코드: 0 = 시드 완료·응답 확인 / 1 = 실패

set -euo pipefail
cd /opt/esti

export AWS_DEFAULT_REGION=${AWS_DEFAULT_REGION:-ap-northeast-2}
TAG=${ESTI_TAG:-latest}
# 공개 주소(G11-6). Caddy 사이트 주소·CORS 허용 출처가 여기 하나에서 나온다
SITE_DOMAIN=${SITE_DOMAIN:-estiapp.dev}

echo "1/6 저장소 갱신"
# 🔴 pull --ff-only 를 쓰지 않는다. 얕은 clone(--depth 1)은 새 커밋의 부모를 모르니 fast-forward 판정이
#    늘 실패한다(G11-5에서 발견 — 10-07 이후 서버가 master 갱신을 한 번도 못 받았다).
#    서버 checkout 은 손대지 않는 사본이라 원격 끝에 맞춘다. .env 는 gitignore 라 reset 이 건드리지 않는다.
branch=$(git rev-parse --abbrev-ref HEAD)
git fetch --quiet --depth 1 origin "$branch"
git reset --quiet --hard FETCH_HEAD
git log --oneline -1
cd deploy
. lib/wait-seed.sh

echo "2/6 비밀값 → .env·인증서 (SSM)"
param() { aws ssm get-parameter --name "$1" --with-decryption --query Parameter.Value --output text; }
registry="$(aws sts get-caller-identity --query Account --output text).dkr.ecr.${AWS_DEFAULT_REGION}.amazonaws.com"
umask 077
{
  echo "ESTI_DB_PASSWORD=$(param /esti-demo/DB_PASSWORD)"
  echo "ADMIN_PASSWORD=$(param /esti-demo/ADMIN_PASSWORD)"
  echo "ESTI_IMAGE=${registry}/esti-demo:${TAG}"
  echo "CADDYFILE=site.caddy"
  echo "SITE_DOMAIN=${SITE_DOMAIN}"
  echo "APP_CORS_ALLOWED_ORIGINS=https://${SITE_DOMAIN}"
} > .env
# Cloudflare Origin CA (G11-6) — Caddy 컨테이너(root)만 읽는다. 값은 찍지 않는다
install -d -m 700 certs
param /esti-demo/ORIGIN_CERT > certs/origin.pem
param /esti-demo/ORIGIN_KEY  > certs/origin.key
chmod 600 certs/origin.pem certs/origin.key

echo "3/6 ECR 로그인·pull (${TAG})"
aws ecr get-login-password | docker login --username AWS --password-stdin "$registry" >/dev/null
docker compose pull --quiet

echo "4/6 기동"
since=$(date -u +%Y-%m-%dT%H:%M:%SZ)
docker compose up -d --no-build
# 이미지가 같으면 compose 는 앱을 다시 띄우지 않는다 → 시드 완료 로그가 안 나와 대기가 끝나지 않는다.
# 배포는 언제나 «새로 뜬 앱»을 확인하는 것으로 끝낸다(데모라 30초 남짓의 중단은 감수).
docker compose up -d --no-build --force-recreate --no-deps app
# Caddy 는 설정·인증서 파일이 바뀌어도 스스로 다시 읽지 않는다 — 같이 새로 띄운다
docker compose up -d --no-build --force-recreate --no-deps caddy

echo "5/6 시드 완료 대기"
wait_seed "$since" 300
# Cloudflare 를 거치지 않고 원본 443 을 직접 찔러 «인증서·SNI·프록시»를 한 번에 본다.
# Origin CA 는 공개 신뢰 체인이 아니라 -k (확인하려는 것은 Caddy→앱 경로다)
code=$(curl -sk -o /dev/null -w '%{http_code}' --resolve "${SITE_DOMAIN}:443:127.0.0.1" "https://${SITE_DOMAIN}/" || true)
[ "$code" = "200" ] || { echo "🔴 Caddy(HTTPS) 경유 응답이 $code — docker compose logs caddy app" >&2; exit 1; }

docker image prune -f >/dev/null

# 백업 타이머(G11-5) — user-data 는 첫 부팅에만 돌아서, 유닛은 배포 때마다 저장소 것으로 맞춘다
echo "6/6 백업 타이머"
install -m 644 ec2/esti-backup.service ec2/esti-backup.timer /etc/systemd/system/
systemctl daemon-reload
systemctl enable --now esti-backup.timer
echo "   다음 백업: $(systemctl show esti-backup.timer -p NextElapseUSecRealtime --value)"

# 레지스트리 주소(계정 ID 포함)는 찍지 않는다 — 태그와 이미지 ID 앞자리만
image_id=$(docker inspect -f '{{.Image}}' "$(docker compose ps -q app)" | cut -d: -f2 | cut -c1-12)
echo "✅ 배포 완료 (esti-demo:${TAG}, 이미지 ${image_id})"
