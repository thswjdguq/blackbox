#!/usr/bin/env bash
#
# 전방통합 헬퍼 (DEC-WORKFLOW-011) — main→refactor/main 발산 흡수.
# 세션 시작 시 / Phase 2 도메인 Task 착수 전에 1회 실행.
#
# 사용: bash scripts/sync.sh   (WSL Ubuntu bash 기준)
#
# 하는 일:
#   1. origin fetch
#   2. refactor/main을 origin/refactor/main까지 fast-forward
#   3. main이 앞서 있으면(발산) origin/main을 refactor/main에 merge
#      - 충돌 나면 멈추고 안내(직접 해소 후 commit·push)
#      - 쓰레기(node_modules/backend/bin/__pycache__)가 딸려오면 untrack
#
# 주의: main은 절대 건드리지 않는다(백머지 동결, DEC-WORKFLOW-002).
set -euo pipefail

echo "▶ fetch origin..."
git fetch origin --prune

echo "▶ refactor/main 체크아웃 + 최신화..."
git checkout refactor/main
git merge --ff-only origin/refactor/main

behind=$(git rev-list --count origin/refactor/main..origin/main 2>/dev/null || echo 0)
if [ "$behind" -eq 0 ]; then
  echo "✅ 발산 0 — 흡수할 main 커밋 없음. 바로 작업 분기 가능."
  exit 0
fi

echo "⚠ main이 ${behind}커밋 앞섬 — 전방통합 머지 시작:"
git log --oneline origin/refactor/main..origin/main | head -30

if git merge --no-edit origin/main; then
  : # 충돌 없이 머지됨
else
  echo
  echo "✋ 충돌 발생. refactor/main 측에서 해소하세요(main은 건드리지 말 것):"
  git diff --name-only --diff-filter=U | sed 's/^/   - /'
  echo "   해소 후: git add <파일> && git commit && git push origin refactor/main"
  exit 1
fi

# 쓰레기 untrack (gitignore 보강돼 있어야 함)
for junk in node_modules backend/bin md/__pycache__; do
  if git ls-files --error-unmatch "$junk" >/dev/null 2>&1; then
    git rm -r --cached --quiet "$junk" && echo "🧹 untracked: $junk"
  fi
done

if ! git diff --cached --quiet; then
  git commit --amend --no-edit >/dev/null 2>&1 || git commit --no-edit -m "merge: origin/main 전방통합 (refactor/main 최신화, DEC-WORKFLOW-011)"
fi

echo "✅ 전방통합 완료. 빌드 검증 후 push 하세요:"
echo "   (backend) cd backend && JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew compileJava"
echo "   (frontend) cd frontend && npm run type-check"
echo "   git push origin refactor/main"
