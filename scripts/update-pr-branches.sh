#!/bin/bash
# main이 바뀐 후 열린 PR 브랜치에 최신 main을 병합하고, 그 PR의 head 커밋에 CI 실행이 없으면 CI를 실행한다.
# .github/workflows/update-pr-branches.yml이 부른다. 필요: gh(GH_TOKEN), REPO=<owner/name>
#
# CI를 실행할지는 "이번에 갱신했는가"가 아니라 "지금 head에 CI 실행 기록이 있는가"로 정한다. 그래서
#  - 갱신한 PR은 새 head에 기록이 없으므로 실행하고
#  - 앞선 실행에서 CI 요청이 실패한 PR은, 다시 돌릴 때 브랜치가 이미 최신이어도 기록이 없어 다시 실행되며
#  - pull_request로 이미 CI가 돈 head는 중복 실행하지 않는다
# 조회 실패, 갱신 대기 시간 초과, CI 요청 실패가 하나라도 있으면 1로 끝나 워크플로 실행이 실패로 보인다
set -uo pipefail
: "${REPO:?REPO가 필요하다}"
WAIT_SECONDS=${WAIT_SECONDS:-60}   # update-branch는 비동기다. 브랜치 head가 바뀔 때까지 기다리는 최대 시간

# Draft는 아직 병합할 PR이 아니라 갱신하지 않는다. 다른 저장소(fork)의 브랜치에는 쓸 수 없어 뺀다
prs=$(gh pr list -R "$REPO" --base main --state open --limit 100 \
        --json number,headRefName,isDraft,isCrossRepository \
        -q '.[] | select((.isDraft | not) and (.isCrossRepository | not)) | "\(.number) \(.headRefName)"') \
  || { echo "PR 목록을 읽지 못함"; exit 1; }
err=$(mktemp); trap 'rm -f "$err"' EXIT
failed=0

while read -r n ref; do
  [ -n "${n:-}" ] || continue
  head=$(gh api "repos/$REPO/pulls/$n" -q .head.sha) || { echo "#$n head를 읽지 못함"; failed=1; continue; }

  # expected_head_sha: 그사이 작성자가 push했으면 갱신하지 않는다. 이미 최신이거나 충돌이면 422로 거절된다
  if gh api -X PUT "repos/$REPO/pulls/$n/update-branch" -f expected_head_sha="$head" --silent 2> "$err"; then
    before=$head
    for ((waited = 0; waited < WAIT_SECONDS; waited += 2)); do
      head=$(gh api "repos/$REPO/pulls/$n" -q .head.sha) || head=$before
      [ "$head" != "$before" ] && break
      sleep 2
    done
    if [ "$head" = "$before" ]; then
      echo "#$n 갱신을 요청했지만 ${WAIT_SECONDS}초 안에 브랜치가 바뀌지 않음. 다시 실행하면 이어서 처리한다"
      failed=1; continue
    fi
    echo "#$n 갱신(${before:0:8} → ${head:0:8})"
  else
    echo "#$n 갱신하지 않음(이미 최신이거나 충돌): $(head -c 200 "$err")"
  fi

  runs=$(gh api "repos/$REPO/actions/workflows/ci.yml/runs?head_sha=$head" -q .total_count) \
    || { echo "#$n CI 실행 기록을 읽지 못함"; failed=1; continue; }
  [ "$runs" != 0 ] && continue
  # GITHUB_TOKEN으로 만든 push는 pull_request 실행을 만들지 않으므로 CI를 직접 부른다.
  # 같은 커밋에 같은 이름의 검사가 붙어 main 보호 규칙의 필수 검사를 채운다
  if gh workflow run ci.yml -R "$REPO" --ref "$ref"; then
    echo "#$n CI 실행(${head:0:8})"
  else
    echo "#$n CI 실행 요청 실패. 다시 실행하면 이 head에 CI 기록이 없어 다시 요청한다"
    failed=1
  fi
done <<< "$prs"

exit "$failed"
