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
#  prs: "번호 브랜치" 줄들 / head_<번호>: 그 PR의 head / update_<번호>: ok이면 갱신 성공(한 번), 없으면 422
#  stuck_<번호>: 있으면 갱신은 받아들이되 head가 바뀌지 않음 / runs_<sha>: 그 커밋의 CI 실행 수
#  dispatch_fail: 있으면 CI 요청 실패 / dispatched: CI를 요청한 브랜치 기록
cat > "$work/bin/gh" <<'FAKE_GH'
#!/bin/bash
s=$STATE
if [ "$1 $2" = "pr list" ]; then cat "$s/prs"; exit 0; fi
if [ "$1 $2" = "workflow run" ]; then
  [ -f "$s/dispatch_fail" ] && { echo "HTTP 403" >&2; exit 1; }
  ref=${!#}; n=$(awk -v r="$ref" '$2==r {print $1}' "$s/prs")
  echo "$ref" >> "$s/dispatched"; echo 1 > "$s/runs_$(cat "$s/head_$n")"; exit 0
fi
if [ "$1 $2 $3" = "api -X PUT" ]; then
  n=$(basename "$(dirname "$4")")
  if [ "$(cat "$s/update_$n" 2>/dev/null)" = ok ]; then
    rm -f "$s/update_$n"; [ -f "$s/stuck_$n" ] || echo "merged-$(cat "$s/head_$n")" > "$s/head_$n"; exit 0
  fi
  echo '{"message":"There are no new commits on the base branch."}' >&2; exit 1
fi
if [ "$1" = api ]; then
  case "$2" in
    */actions/workflows/ci.yml/runs\?head_sha=*) sha=${2##*=}; cat "$s/runs_$sha" 2>/dev/null || echo 0 ;;
    */pulls/*) cat "$s/head_$(basename "$2")" ;;
    *) exit 1 ;;
  esac
  exit 0
fi
exit 1
FAKE_GH
cat > "$work/bin/sleep" <<'FAKE_SLEEP'
#!/bin/bash
exit 0
FAKE_SLEEP
chmod +x "$work/bin/gh" "$work/bin/sleep"

failures=0
check() { if [ "$2" = "$3" ]; then echo "  통과: $1"; else echo "  실패: $1 (기대 '$3', 실제 '$2')"; failures=$((failures + 1)); fi; }
reset() { rm -rf "${work:?}/state"/*; : > "$work/state/dispatched"; }
pr() { echo "$1 $2" >> "$work/state/prs"; echo "$3" > "$work/state/head_$1"; }   # 번호 브랜치 head
run() {   # 스크립트를 한 번 돌리고 종료 코드를 rc에 남긴다
  env "PATH=$work/bin:$PATH" STATE="$work/state" REPO=example/repo WAIT_SECONDS=4 bash scripts/update-pr-branches.sh > "$work/out" 2>&1
  rc=$?
}
dispatched() { paste -sd' ' "$work/state/dispatched"; }

echo "1) 뒤처진 PR을 갱신하면 새 head에서 CI를 실행한다"
reset; pr 10 feat/a aaa; echo ok > "$work/state/update_10"; echo 1 > "$work/state/runs_aaa"
run
check "종료 0" "$rc" 0; check "CI 실행" "$(dispatched)" "feat/a"; check "새 head에 CI 기록" "$(cat "$work/state/runs_merged-aaa")" 1

echo "2) 이미 최신이고 head에 CI가 있으면 다시 돌리지 않는다"
reset; pr 11 feat/b bbb; echo 1 > "$work/state/runs_bbb"
run
check "종료 0" "$rc" 0; check "CI 실행 없음" "$(dispatched)" ""

echo "3) CI 요청이 실패하면 실패로 끝나고, 다시 돌리면 이미 최신이어도 CI를 실행한다"
reset; pr 12 feat/c ccc; echo ok > "$work/state/update_12"; echo 1 > "$work/state/runs_ccc"; touch "$work/state/dispatch_fail"
run
check "첫 실행 종료 1" "$rc" 1
check "실패를 기록" "$(grep -c 'CI 실행 요청 실패' "$work/out")" 1
check "성공처럼 기록하지 않음" "$(grep -c 'CI 실행(' "$work/out")" 0
rm -f "$work/state/dispatch_fail"
run
check "다시 실행 종료 0" "$rc" 0
check "이미 최신이라 갱신하지 않음" "$(grep -c '#12 갱신하지 않음' "$work/out")" 1
check "그래도 CI를 실행" "$(dispatched)" "feat/c"

echo "4) 갱신을 요청했지만 head가 바뀌지 않으면 CI를 돌리지 않고 실패로 끝난다"
reset; pr 13 feat/d ddd; echo ok > "$work/state/update_13"; touch "$work/state/stuck_13"; echo 1 > "$work/state/runs_ddd"
run
check "종료 1" "$rc" 1; check "CI 실행 없음" "$(dispatched)" ""

echo "5) 한 PR이 실패해도 나머지 PR은 처리한다"
reset; pr 14 feat/e eee; pr 15 feat/f fff; echo ok > "$work/state/update_14"; touch "$work/state/stuck_14"; echo ok > "$work/state/update_15"
echo 1 > "$work/state/runs_eee"; echo 1 > "$work/state/runs_fff"
run
check "종료 1" "$rc" 1; check "다른 PR은 CI 실행" "$(dispatched)" "feat/f"

[ "$failures" = 0 ] && { echo "PR 브랜치 최신화 스크립트 검사 통과"; exit 0; }
echo "PR 브랜치 최신화 스크립트 검사 실패 $failures건"; exit 1
