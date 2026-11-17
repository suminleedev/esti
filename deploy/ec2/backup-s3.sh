#!/bin/bash
# EC2 에서 데모를 백업해 S3 에 올린다 (G11-5). systemd timer(esti-backup.timer)가 매일 부른다.
#
#   손으로 한 번:  systemctl start esti-backup.service   → journalctl -u esti-backup
#
# 버킷 이름은 저장소에 없다 — SSM /esti-demo/BACKUP_BUCKET 에서 읽고, 찍지 않는다.
# 인스턴스 역할에 Delete 권한이 없다. 오래된 백업은 버킷 수명 주기 규칙(14일)이 지운다.
#
# 종료코드: 0 = 두 파일 업로드 / 1 = 실패 (systemctl status esti-backup 에 failed 로 남는다)

set -euo pipefail
cd "$(dirname "$0")/.."

export AWS_DEFAULT_REGION=${AWS_DEFAULT_REGION:-ap-northeast-2}

# 부팅 직후(Persistent=true 로 놓친 실행을 따라잡을 때) DB 컨테이너가 아직 뜨는 중일 수 있다
for _ in $(seq 1 40); do
  docker compose exec -T db pg_isready -U esti -d esti >/dev/null 2>&1 && break
  sleep 3
done

bucket=$(aws ssm get-parameter --name /esti-demo/BACKUP_BUCKET --query Parameter.Value --output text)

tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
files=$(BACKUP_DIR="$tmp" ./backup.sh)

for f in $files; do
  aws s3 cp --only-show-errors "$f" "s3://${bucket}/daily/$(basename "$f")"
  echo "   올림: daily/$(basename "$f")"
done
echo "✅ S3 백업 완료"
