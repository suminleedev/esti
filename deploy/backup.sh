#!/usr/bin/env bash
# 데모 데이터를 백업한다 (G11-5). DB 덤프 + 업로드 이미지 tar 를 로컬 폴더에 남긴다.
#
#   deploy/backup.sh                 → deploy/backups/ 에 두 파일
#   BACKUP_DIR=/tmp/x deploy/backup.sh
#
# 앱을 멈추지 않는다(읽기만). DB 와 이미지 사이 몇 초의 차이는 데모라 감수한다.
# 지키는 것은 «방문자가 만든 데이터»다 — 서버는 재생성, 시드는 demo-reset.sh 로 돌아온다.
# EC2 에서는 ec2/backup-s3.sh 가 이걸 불러 S3 에 올린다. 복구는 restore.sh.
#
# 출력: 마지막 두 줄이 만든 파일의 절대 경로(덤프, tar) — backup-s3.sh 가 읽는다.
# 종료코드: 0 = 두 파일 생성·덤프 목차 확인 / 1 = 실패(빈 덤프를 성공으로 넘기지 않는다)
#
# compose.yml 의 서비스 이름(app·db)을 그대로 쓴다.

set -euo pipefail
cd "$(dirname "$0")"

BACKUP_DIR=${BACKUP_DIR:-backups}
mkdir -p "$BACKUP_DIR"
BACKUP_DIR=$(cd "$BACKUP_DIR" && pwd)
ts=$(date -u +%Y%m%dT%H%M%SZ)
dump="$BACKUP_DIR/esti-$ts.dump"
tarball="$BACKUP_DIR/uploads-$ts.tar.gz"

# 중간에 실패하면 반쪽 파일을 남기지 않는다
ok=0
cleanup() { [ "$ok" = 1 ] || rm -f "$dump" "$tarball"; }
trap cleanup EXIT

docker compose exec -T db pg_isready -U esti -d esti >/dev/null \
  || { echo "🔴 db 컨테이너에 붙지 못했다" >&2; exit 1; }

echo "1/3 DB 덤프 (스키마 app, 커스텀 포맷)" >&2
umask 077
docker compose exec -T db pg_dump -U esti -d esti -Fc -n app > "$dump"

echo "2/3 덤프 확인" >&2
tables=$(docker compose exec -T db pg_restore --list < "$dump" | grep -c ' TABLE DATA ' || true)
[ "$tables" -gt 0 ] || { echo "🔴 덤프에 테이블이 없다 — 스키마가 비었거나 덤프가 깨졌다" >&2; exit 1; }

echo "3/3 업로드 이미지 tar" >&2
# 앱이 내려가 있어도 되게 일회용 컨테이너로 볼륨을 읽는다(demo-reset.sh 와 같은 방식)
docker compose run --rm --no-deps -T --entrypoint sh app -c 'tar czf - -C /app uploads' > "$tarball"
gzip -t "$tarball" || { echo "🔴 업로드 tar 가 깨졌다" >&2; exit 1; }

ok=1
echo "✅ 백업 완료 — 테이블 ${tables}개, 덤프 $(du -h "$dump" | cut -f1), 이미지 $(du -h "$tarball" | cut -f1)" >&2
echo "$dump"
echo "$tarball"
