#!/bin/bash
# scripts/update-pr-branches.sh의 회귀 검사. 실제 GitHub 대신 가짜 gh로 돌린다(PR·브랜치·Actions에 닿지 않는다).
# 실행: bash scripts/test-update-pr-branches.sh   CI의 backend 작업에서도 돈다
# Draft·fork 제외는 gh의 -q 필터라 여기서는 검사하지 않는다(가짜 gh는 이미 거른 목록을 돌려준다)
set -uo pipefail
cd "$(dirname "$0")/.." || exit 1

work=$(mktemp -d) || exit 1
trap 'rm -rf "$work"' EXIT
mkdir -p "$work/bin" "$work/state"

# 가짜 gh. 상태는 $STATE의 파일로 둔다
#  prs: PR 번호 줄들 / head_<번호>: 그 PR의 head
#  update_<번호>: ok면 갱신 성공, forbidden이면 403, 없으면 422(이미 최신이거나 충돌)
#  updated: 갱신을 요청한 "번호 head" 기록 / calls: gh에 들어온 명령 기록
cat > "$work/bin/gh" <<'FAKE_GH'
#!/bin/bash
s=$STATE
echo "$*" >> "$s/calls"
if [ "$1 $2" = "pr list" ]; then cat "$s/prs"; exit 0; fi
if [ "$1 $2 $3" = "api -X PUT" ]; then
  n=$(basename "$(dirname "$4")")
  case "$(cat "$s/update_$n" 2>/dev/null)" in
    ok) echo "$n ${6#expected_head_sha=}" >> "$s/updated"; exit 0 ;;
    forbidden) echo "gh: Resource not accessible by personal access token (HTTP 403)" >&2; exit 1 ;;
    *) echo "gh: There are no new commits on the base branch. (HTTP 422)" >&2; exit 1 ;;
  esac
fi
if [ "$1" = api ] && [[ "$2" == */pulls/* ]]; then cat "$s/head_$(basename "$2")"; exit 0; fi
exit 1
FAKE_GH
chmod +x "$work/bin/gh"

failures=0
check() { if [ "$2" = "$3" ]; then echo "  통과: $1"; else echo "  실패: $1 (기대 '$3', 실제 '$2')"; failures=$((failures + 1)); fi; }
reset() { rm -rf "${work:?}/state"/*; : > "$work/state/updated"; : > "$work/state/calls"; }
pr() { echo "$1" >> "$work/state/prs"; echo "$2" > "$work/state/head_$1"; }   # 번호 head
run() {   # 스크립트를 한 번 돌리고 종료 코드를 rc에 남긴다
  env "PATH=$work/bin:$PATH" STATE="$work/state" REPO=example/repo GH_TOKEN="${TOKEN-test-token}" bash scripts/update-pr-branches.sh > "$work/out" 2>&1
  rc=$?
}
updated() { paste -sd' ' "$work/state/updated"; }

echo "1) 뒤처진 PR은 지금 head를 조건으로 갱신을 요청하고, 검사는 따로 부르지 않는다"
reset; pr 10 aaa; echo ok > "$work/state/update_10"
run
check "종료 0" "$rc" 0; check "head 조건으로 갱신" "$(updated)" "10 aaa"
check "검사(workflow run)를 부르지 않음" "$(grep -c '^workflow' "$work/state/calls")" 0

echo "2) 이미 최신이거나 충돌(422)이면 건너뛰고 성공으로 끝난다"
reset; pr 11 bbb
run
check "종료 0" "$rc" 0; check "건너뜀을 기록" "$(grep -c '#11 갱신하지 않음' "$work/out")" 1

echo "3) 422가 아닌 실패(권한 등)는 실패로 끝나고, 나머지 PR은 처리한다"
reset; pr 12 ccc; pr 13 ddd; echo forbidden > "$work/state/update_12"; echo ok > "$work/state/update_13"
run
check "종료 1" "$rc" 1; check "실패를 기록" "$(grep -c '#12 갱신 실패' "$work/out")" 1
check "다른 PR은 갱신" "$(updated)" "13 ddd"

echo "4) 토큰이 없으면(Secret 누락) gh를 부르기 전에 실패한다"
reset; pr 14 eee; echo ok > "$work/state/update_14"
TOKEN="" run
check "실패로 끝남" "$([ "$rc" != 0 ] && echo 실패)" "실패"
check "gh를 부르지 않음" "$(wc -l < "$work/state/calls" | tr -d ' ')" 0

[ "$failures" = 0 ] && { echo "PR 브랜치 최신화 스크립트 검사 통과"; exit 0; }
echo "PR 브랜치 최신화 스크립트 검사 실패 $failures건"; exit 1
