#!/bin/bash
# main이 바뀐 후 열린 PR 브랜치에 최신 main을 병합한다(update-branch API).
# .github/workflows/update-pr-branches.yml이 부른다. 필요: gh(GH_TOKEN=PR_UPDATE_TOKEN), REPO=<owner/name>
#
# 최신화는 개인 액세스 토큰으로 하므로 push가 봇이 아닌 토큰 소유자의 것으로 기록되고, 그 push에서 CI(ci.yml, pull_request)가 승인 없이 바로 실행된다.
# 이 스크립트는 검사를 따로 부르지 않는다.
# 이미 최신이거나 충돌이 나는 PR은 GitHub가 422로 거절하므로 건너뛴다. 그 밖의 실패(권한, 조회 실패)가 하나라도 있으면
# 1로 끝나 워크플로 실행이 실패로 보인다. 다시 실행하면 아직 뒤처진 PR만 갱신된다
set -uo pipefail
: "${REPO:?REPO가 필요하다}"
: "${GH_TOKEN:?GH_TOKEN이 없다. 저장소 Secret PR_UPDATE_TOKEN을 확인한다}"

# Draft는 아직 병합할 PR이 아니라 갱신하지 않는다. 다른 저장소(fork)의 브랜치에는 쓸 수 없어 뺀다
prs=$(gh pr list -R "$REPO" --base main --state open --limit 100 \
        --json number,isDraft,isCrossRepository \
        -q '.[] | select((.isDraft | not) and (.isCrossRepository | not)) | .number') \
  || { echo "PR 목록을 읽지 못함"; exit 1; }
err=$(mktemp); trap 'rm -f "$err"' EXIT
failed=0

while read -r n; do
  [ -n "${n:-}" ] || continue
  head=$(gh api "repos/$REPO/pulls/$n" -q .head.sha) || { echo "#$n head를 읽지 못함"; failed=1; continue; }

  # expected_head_sha: 그사이 작성자가 push했으면 갱신하지 않는다
  if gh api -X PUT "repos/$REPO/pulls/$n/update-branch" -f expected_head_sha="$head" --silent 2> "$err"; then
    echo "#$n 갱신 요청(${head:0:8}에 main 병합)"
  elif grep -q "HTTP 422" "$err"; then
    echo "#$n 갱신하지 않음(이미 최신이거나 충돌): $(head -c 200 "$err")"
  else
    echo "#$n 갱신 실패: $(head -c 200 "$err")"
    failed=1
  fi
done <<< "$prs"

exit "$failed"
