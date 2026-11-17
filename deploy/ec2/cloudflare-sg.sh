#!/usr/bin/env bash
# 데모 보안그룹을 «443 ← Cloudflare 대역만»으로 맞춘다 (G11-6). 노트북에서 돌린다(AWS 자격증명 필요).
#
#   deploy/ec2/cloudflare-sg.sh            바뀔 내용만 보여 준다
#   deploy/ec2/cloudflare-sg.sh --apply    실제로 바꾼다
#
# 원본 직접 접근을 막는다 — 방문자는 Cloudflare 를 거쳐야만 닿는다.
# 대역은 Cloudflare 공식 목록(https://www.cloudflare.com/ips-v4)을 그때그때 받는다. 목록은 드물게 바뀌므로
# 가끔(또는 Cloudflare 공지 때) 다시 돌린다. 몇 번을 돌려도 같은 결과다.
#
# 🔴 목록에 없는 인바운드 규칙은 전부 지운다 — 80 ← 내 IP(G11-4 검증용)도 여기서 사라진다.
#    22 는 처음부터 없다(SSM 만 쓴다).
#
# 종료코드: 0 = 이미 맞음/바꿈 / 1 = 실패(목록을 못 받으면 아무것도 바꾸지 않는다)

set -euo pipefail

SG_NAME=${SG_NAME:-esti-demo-web}
export AWS_DEFAULT_REGION=${AWS_DEFAULT_REGION:-ap-northeast-2}
apply=0; [ "${1:-}" = "--apply" ] && apply=1

want=$(curl -fsS https://www.cloudflare.com/ips-v4 | tr -d '\r' | grep -E '^[0-9.]+/[0-9]+$' | sort -u)
n=$(printf '%s\n' "$want" | grep -c . || true)
[ "$n" -ge 10 ] || { echo "🔴 Cloudflare 대역 목록이 이상하다(${n}개) — 아무것도 바꾸지 않았다" >&2; exit 1; }

sg=$(aws ec2 describe-security-groups --group-names "$SG_NAME" --query 'SecurityGroups[0].GroupId' --output text)

# 지금 규칙을 «프로토콜 포트 CIDR» 줄로
have=$(aws ec2 describe-security-groups --group-ids "$sg" --output json \
  | python3 -c 'import json,sys
for p in json.load(sys.stdin)["SecurityGroups"][0]["IpPermissions"]:
    for r in p.get("IpRanges", []):
        print(p["IpProtocol"], p.get("FromPort", "-"), r["CidrIp"])
    for r in p.get("Ipv6Ranges", []):
        print(p["IpProtocol"], p.get("FromPort", "-"), r["CidrIpv6"])' | sort -u)
want_rules=$(printf '%s\n' "$want" | sed 's/^/tcp 443 /' | sort -u)

add=$(comm -13 <(printf '%s\n' "$have") <(printf '%s\n' "$want_rules") | grep . || true)
del=$(comm -23 <(printf '%s\n' "$have") <(printf '%s\n' "$want_rules") | grep . || true)

echo "보안그룹 ${SG_NAME}: Cloudflare 대역 ${n}개 / 추가 $(printf '%s' "$add" | grep -c . || true) / 삭제 $(printf '%s' "$del" | grep -c . || true)"
[ -n "$del" ] && printf '%s\n' "$del" | sed 's/^/   - /'
[ -z "$add" ] && [ -z "$del" ] && { echo "✅ 이미 맞다"; exit 0; }
[ "$apply" = 1 ] || { echo "(보기만 했다 — 바꾸려면 --apply)"; exit 0; }

if [ -n "$add" ]; then
  ranges=$(printf '%s\n' "$add" | awk '{printf "%s{CidrIp=%s,Description=cloudflare}", (NR>1?",":""), $3}')
  aws ec2 authorize-security-group-ingress --group-id "$sg" \
    --ip-permissions "IpProtocol=tcp,FromPort=443,ToPort=443,IpRanges=[${ranges}]" >/dev/null
fi
# 추가를 먼저 한다 — 지우다 실패해도 Cloudflare 경로는 열려 있게
printf '%s\n' "$del" | while read -r proto port cidr; do
  [ -n "$proto" ] || continue
  if [[ "$cidr" == *:* ]]; then key=Ipv6Ranges; field=CidrIpv6; else key=IpRanges; field=CidrIp; fi
  if [ "$proto" = "-1" ]; then perm="IpProtocol=-1,${key}=[{${field}=${cidr}}]"
  else perm="IpProtocol=${proto},FromPort=${port},ToPort=${port},${key}=[{${field}=${cidr}}]"; fi
  aws ec2 revoke-security-group-ingress --group-id "$sg" --ip-permissions "$perm" >/dev/null
done
echo "✅ 바꿨다"
