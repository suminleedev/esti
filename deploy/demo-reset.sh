#!/usr/bin/env bash
# 데모를 시드 상태로 되돌린다 (G11-3). 방문자가 만든 제안서·카탈로그 변경·업로드 이미지가 전부 지워진다.
#
#   deploy/demo-reset.sh          확인을 묻는다
#   deploy/demo-reset.sh --yes    묻지 않는다
#
# 앱 코드에 초기화 기능을 넣지 않았다. 스키마를 지우고 앱을 다시 띄우면, 빈 DB에 시드를 넣는
# 평소 기동 경로(DemoSeedRunner)가 그대로 다시 채운다 — 데모 전용 처리를 한 곳에만 두려는 것이다.
#
# compose.yml 의 서비스 이름(app·db)을 그대로 쓴다. 같은 폴더의 compose.yml·.env 를 읽는다.
#
# 종료코드: 0 = 시드 완료까지 확인 / 1 = 실패·시간 초과(데모가 비었거나 내려가 있을 수 있다 — 로그를 본다)

set -euo pipefail
cd "$(dirname "$0")"

SEED_TIMEOUT=${SEED_TIMEOUT:-300}   # 시드 완료 로그를 기다리는 최대 초

if [ "${1:-}" != "--yes" ]; then
  read -r -p "데모 데이터를 전부 지우고 시드 상태로 되돌린다. 계속할까? [y/N] " answer
  [ "$answer" = "y" ] || [ "$answer" = "Y" ] || { echo "취소했다."; exit 1; }
fi

# 0) DB가 살아 있는지 먼저 본다 — 앱부터 내렸는데 DB에 못 붙으면 데모만 내려간 채로 끝난다
docker compose exec -T db pg_isready -U esti -d esti >/dev/null \
  || { echo "🔴 db 컨테이너에 붙지 못했다 — 아무것도 바꾸지 않았다" >&2; exit 1; }

# 1) 앱을 내린다 — 지우는 도중에 방문자 요청·진행 중 업로드가 끼지 않게
echo "1/4 앱 정지"
docker compose stop app

# 2) 스키마째 지운다. 다음 기동 때 create_namespaces + ddl-auto=update 가 새로 만든다
echo "2/4 DB 스키마 삭제"
docker compose exec -T db psql -U esti -d esti -v ON_ERROR_STOP=1 -qc "set client_min_messages = warning; drop schema if exists app cascade"

# 3) 방문자가 올린 카탈로그의 이미지를 지운다. 플레이스홀더(demo-*)는 남긴다(어차피 기동 때 다시 놓인다).
#    앱이 내려가 있어 exec 를 못 하므로, 같은 이미지·같은 볼륨으로 일회용 컨테이너를 띄운다
echo "3/4 업로드 이미지 정리"
docker compose run --rm --no-deps -T --entrypoint sh app -c \
  'd=/app/uploads/product-images; [ -d "$d" ] || exit 0; n=$(find "$d" -type f ! -name "demo-*" | wc -l); find "$d" -type f ! -name "demo-*" -delete; echo "   지운 파일 $n개"'

# 4) 다시 띄우고 «시드 완료»까지 기다린다.
#    🔑 「Started」를 기다리면 안 된다 — Spring Boot 는 Started 로그 «뒤에» 시드를 돈다(G11-2에서 실측)
echo "4/4 앱 기동 — 시드 완료까지 대기 (최대 ${SEED_TIMEOUT}초)"
since=$(date -u +%Y-%m-%dT%H:%M:%SZ)
docker compose start app

deadline=$(( $(date +%s) + SEED_TIMEOUT ))
while [ "$(date +%s)" -lt "$deadline" ]; do
  logs=$(docker compose logs --no-log-prefix --since "$since" app 2>/dev/null || true)
  if printf '%s' "$logs" | grep -qE '적재 실패|APPLICATION FAILED'; then
    printf '%s\n' "$logs" | grep -E '\[데모시드\]|APPLICATION FAILED' | sed 's/^.*\(\[데모시드\]\)/   \1/' >&2
    echo "🔴 시드 실패 — docker compose logs app 으로 원인을 본다" >&2
    exit 1
  fi
  if printf '%s' "$logs" | grep -q '플레이스홀더 이미지 [0-9]*건 연결'; then
    printf '%s\n' "$logs" | grep '\[데모시드\]' | sed 's/^.*\(\[데모시드\]\)/   \1/'
    echo "✅ 시드 상태로 되돌렸다"
    exit 0
  fi
  sleep 3
done

echo "🔴 ${SEED_TIMEOUT}초 안에 시드 완료 로그가 없다 — docker compose logs app 으로 확인한다" >&2
exit 1
