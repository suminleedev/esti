#!/bin/bash
# EC2 에서 데모를 띄우거나 새 이미지로 바꾼다 (G11-4). 인스턴스 안에서 root 로 돈다.
#
#   노트북에서:  aws ssm send-command --instance-ids <id> --document-name AWS-RunShellScript \
#                  --parameters 'commands=["/opt/esti/deploy/ec2/deploy.sh"]'
#   (런북에 전체 절차가 있다)
#
# 하는 일: 저장소 갱신 → SSM 비밀값으로 .env → ECR 로그인 → 이미지 pull → 띄움 → 시드 완료까지 대기
# ESTI_TAG 로 이미지 태그를 고를 수 있다(기본 latest — 롤백은 커밋 SHA 태그).
#
# 종료코드: 0 = 시드 완료·응답 확인 / 1 = 실패

set -euo pipefail
cd /opt/esti

export AWS_DEFAULT_REGION=${AWS_DEFAULT_REGION:-ap-northeast-2}
TAG=${ESTI_TAG:-latest}

echo "1/5 저장소 갱신"
git pull --ff-only --depth 1
cd deploy
. lib/wait-seed.sh

echo "2/5 비밀값 → .env (SSM)"
param() { aws ssm get-parameter --name "$1" --with-decryption --query Parameter.Value --output text; }
registry="$(aws sts get-caller-identity --query Account --output text).dkr.ecr.${AWS_DEFAULT_REGION}.amazonaws.com"
umask 077
{
  echo "ESTI_DB_PASSWORD=$(param /esti-demo/DB_PASSWORD)"
  echo "ADMIN_PASSWORD=$(param /esti-demo/ADMIN_PASSWORD)"
  echo "ESTI_IMAGE=${registry}/esti-demo:${TAG}"
} > .env

echo "3/5 ECR 로그인·pull (${TAG})"
aws ecr get-login-password | docker login --username AWS --password-stdin "$registry" >/dev/null
docker compose pull --quiet

echo "4/5 기동"
since=$(date -u +%Y-%m-%dT%H:%M:%SZ)
docker compose up -d --no-build
# 이미지가 같으면 compose 는 앱을 다시 띄우지 않는다 → 시드 완료 로그가 안 나와 대기가 끝나지 않는다.
# 배포는 언제나 «새로 뜬 앱»을 확인하는 것으로 끝낸다(데모라 30초 남짓의 중단은 감수).
docker compose up -d --no-build --force-recreate --no-deps app

echo "5/5 시드 완료 대기"
wait_seed "$since" 300
code=$(curl -s -o /dev/null -w '%{http_code}' http://localhost/ || true)
[ "$code" = "200" ] || { echo "🔴 Caddy 경유 응답이 $code — docker compose logs caddy app" >&2; exit 1; }

docker image prune -f >/dev/null
# 레지스트리 주소(계정 ID 포함)는 찍지 않는다 — 태그와 이미지 ID 앞자리만
image_id=$(docker inspect -f '{{.Image}}' "$(docker compose ps -q app)" | cut -d: -f2 | cut -c1-12)
echo "✅ 배포 완료 (esti-demo:${TAG}, 이미지 ${image_id})"
