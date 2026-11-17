#!/usr/bin/env bash
# 백업으로 데모를 되돌린다 (G11-5). 지금 DB 의 app 스키마와 (tar 를 주면) 업로드 이미지가 백업으로 바뀐다.
#
#   deploy/restore.sh <esti-*.dump> [<uploads-*.tar.gz>]          확인을 묻는다
#   deploy/restore.sh --yes <esti-*.dump> [<uploads-*.tar.gz>]    묻지 않는다
#
# 순서는 demo-reset.sh 와 같다 — 앱 정지 → 스키마 삭제 → (시드 대신) 덤프 복원 → 기동.
# 기동하면 DemoSeedRunner 는 제품이 있는 공급사를 «건너뜀»으로 끝낸다.
#
# 🔑 덤프를 뜬 때와 같은 앱 버전(ESTI_TAG)으로 복구한다. 엔티티가 바뀌었으면 ddl-auto=update 가
#    컬럼 추가는 따라가지만 삭제·이름 변경은 못 따라간다.
#
# 종료코드: 0 = 복원·시드 완료 로그까지 확인 / 1 = 실패
#   0단계(파일·DB 확인)에서 실패하면 아무것도 바꾸지 않는다.
#   그 뒤에서 실패하면 데모가 비었거나 내려가 있을 수 있다 — 로그를 보고 demo-reset.sh 로 되살린다.

set -euo pipefail

yes=0
if [ "${1:-}" = "--yes" ]; then yes=1; shift; fi
[ $# -ge 1 ] && [ $# -le 2 ] || { echo "쓰는 법: $0 [--yes] <dump> [<uploads.tar.gz>]" >&2; exit 1; }

# 상대 경로를 cd 전에 절대 경로로
abs() { (cd "$(dirname "$1")" && echo "$(pwd)/$(basename "$1")"); }
[ -s "$1" ] || { echo "🔴 덤프 파일이 없거나 비었다: $1" >&2; exit 1; }
dump=$(abs "$1")
tarball=""
if [ $# -eq 2 ]; then
  [ -s "$2" ] || { echo "🔴 업로드 tar 가 없거나 비었다: $2" >&2; exit 1; }
  tarball=$(abs "$2")
  gzip -t "$tarball" || { echo "🔴 업로드 tar 가 깨졌다: $2" >&2; exit 1; }
fi

cd "$(dirname "$0")"
. lib/wait-seed.sh
SEED_TIMEOUT=${SEED_TIMEOUT:-300}

# 0) 바꾸기 전에 다 확인한다 — DB 가 살아 있고, 덤프가 읽히고, 테이블이 들어 있는가
docker compose exec -T db pg_isready -U esti -d esti >/dev/null \
  || { echo "🔴 db 컨테이너에 붙지 못했다 — 아무것도 바꾸지 않았다" >&2; exit 1; }
tables=$(docker compose exec -T db pg_restore --list < "$dump" 2>/dev/null | grep -c ' TABLE DATA ' || true)
[ "$tables" -gt 0 ] || { echo "🔴 덤프를 읽지 못했거나 테이블이 없다 — 아무것도 바꾸지 않았다" >&2; exit 1; }

if [ "$yes" != 1 ]; then
  echo "덤프: $(basename "$dump") (테이블 ${tables}개)${tarball:+ / 이미지: $(basename "$tarball")}"
  read -r -p "지금 데모 데이터를 이 백업으로 바꾼다. 계속할까? [y/N] " answer
  [ "$answer" = "y" ] || [ "$answer" = "Y" ] || { echo "취소했다."; exit 1; }
fi

echo "1/4 앱 정지"
docker compose stop app

echo "2/4 스키마 삭제 → 덤프 복원"
docker compose exec -T db psql -U esti -d esti -v ON_ERROR_STOP=1 -qc "set client_min_messages = warning; drop schema if exists app cascade"
docker compose exec -T db pg_restore -U esti -d esti --no-owner --exit-on-error < "$dump"

if [ -n "$tarball" ]; then
  echo "3/4 업로드 이미지 복원"
  docker compose run --rm --no-deps -T --entrypoint sh app -c \
    'find /app/uploads -mindepth 1 -delete && tar xzf - -C /app && echo "   파일 $(find /app/uploads -type f | wc -l)개"' < "$tarball"
else
  echo "3/4 업로드 이미지 — tar 가 없어 건드리지 않는다"
fi

echo "4/4 앱 기동 — 시드 완료 로그까지 대기 (최대 ${SEED_TIMEOUT}초)"
since=$(date -u +%Y-%m-%dT%H:%M:%SZ)
docker compose start app

if wait_seed "$since" "$SEED_TIMEOUT"; then
  echo "✅ 백업으로 되돌렸다"
else
  exit 1
fi
