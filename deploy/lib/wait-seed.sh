# 앱이 «쓸 수 있는 상태»가 될 때까지 기다린다 — demo-reset.sh·ec2/deploy.sh 가 source 한다.
#
# 🔑 「Started」를 기다리면 안 된다. Spring Boot 는 Started 로그 «뒤에» 시드(ApplicationRunner)를 돈다(G11-2 실측).
#    DemoSeedRunner 가 마지막에 남기는 「[데모시드] 끝 — 적재 [..] · 건너뜀 [..] · 실패 [..]」 한 줄을 기다린다.
#
#   wait_seed <since(UTC ISO)> <timeout초>   → 0 성공 / 1 실패·시간 초과
#   compose 파일이 있는 폴더에서 부른다.

wait_seed() {
  local since=$1 timeout=$2 logs deadline
  deadline=$(( $(date +%s) + timeout ))
  while [ "$(date +%s)" -lt "$deadline" ]; do
    logs=$(docker compose logs --no-log-prefix --since "$since" app 2>/dev/null || true)
    if printf '%s' "$logs" | grep -q 'APPLICATION FAILED'; then
      echo "🔴 앱이 뜨지 못했다 — docker compose logs app 으로 원인을 본다" >&2
      return 1
    fi
    if printf '%s' "$logs" | grep -q '\[데모시드\] 끝'; then
      printf '%s\n' "$logs" | grep '\[데모시드\]' | sed 's/^.*\(\[데모시드\]\)/   \1/'
      if printf '%s' "$logs" | grep '\[데모시드\] 끝' | grep -q '실패 \[\]'; then
        return 0
      fi
      echo "🔴 시드에 실패한 공급사가 있다 — 그 공급사는 비어 있다(다음 기동 때 다시 시도)" >&2
      return 1
    fi
    sleep 3
  done
  echo "🔴 ${timeout}초 안에 시드 완료 로그가 없다 — docker compose logs app 으로 확인한다" >&2
  return 1
}
