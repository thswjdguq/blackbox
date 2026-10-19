#!/bin/bash
# 띄운 서버를 밖에서(개발 PC에서) 확인한다. 가입, 프로젝트, 파일, 회의록 요약, AI 질문을 실제로 만든다.
# 시연 DB에 자료가 남으므로 임시 프로젝트 이름으로 띄운 스택에만 쓴다(docs/deploy-aws.md 7장).
#   ALLOW_TEST_DATA=1 bash scripts/ec2-check.sh https://<이름>.duckdns.org
# AI 기능은 테스트용 문장만 보낸다. 학교 LLM API는 요청을 로그에 남긴다.
set -uo pipefail
O=${1:?확인할 주소를 준다(예: https://team-blackbox.duckdns.org)}
[ "${ALLOW_TEST_DATA:-}" = 1 ] || { echo "이 스크립트는 그 서버의 DB에 테스트 자료를 만든다. 임시 스택이 맞으면 ALLOW_TEST_DATA=1 을 붙여 다시 실행한다"; exit 1; }
J=$(mktemp); F=$(mktemp --suffix=.txt); echo "배포 확인용 파일" > "$F"; trap 'rm -f "$J" "$J.sum" "$F"' EXIT
c() { curl -s -m 60 "$@"; }
json() { python3 -c "import json,sys; d=json.load(sys.stdin); print($1)" 2>/dev/null; }

echo "화면 $(c -o /dev/null -w '%{http_code}' "$O/login") / API(로그인 전 401) $(c -o /dev/null -w '%{http_code}, 인증서 검증 %{ssl_verify_result}' "$O/api/auth/profile") / http→https $(c -o /dev/null -w '%{http_code}' "http://${O#https://}/")"
echo "가입 $(c -c "$J" -D - -o /dev/null -X POST "$O/api/auth/signup" -H 'content-type: application/json' -H "Origin: $O" -d '{"email":"deploy-check@demo.example.com","password":"Check-pass-2026","name":"김팀장","role":"STUDENT"}' | grep -i -c -E '^HTTP.* 201|accessToken=.*Secure') (2면 201과 Secure 쿠키) / 다른 출처(403) $(c -o /dev/null -w '%{http_code}' -X POST "$O/api/auth/login" -H 'content-type: application/json' -H 'Origin: https://evil.example.net' -d '{"email":"a@b.c","password":"x"}')"
PID=$(c -b "$J" -X POST "$O/api/projects" -H 'content-type: application/json' -H "Origin: $O" -d '{"name":"테스트용 프로젝트","description":"배포 확인"}' | json "d.get('id','')")
echo "프로젝트 ${PID:0:8} / 업무 목록 $(c -b "$J" -o /dev/null -w '%{http_code}' "$O/api/projects/$PID/tasks") / 제출물 목록 $(c -b "$J" -o /dev/null -w '%{http_code}' "$O/api/projects/$PID/deliverables")"
echo "업로드 $(c -b "$J" -o /dev/null -w '%{http_code}' -X POST "$O/api/projects/$PID/files" -H "Origin: $O" -F "file=@$F;filename=check.txt")"
FID=$(c -b "$J" "$O/api/projects/$PID/files" | json "d[0]['id'] if d else ''")
echo "다운로드: 로그인 $(c -b "$J" -o /dev/null -w '%{http_code}' "$O/api/files/$FID/download") / 로그인 없이(401) $(c -o /dev/null -w '%{http_code}' "$O/api/files/$FID/download")"
MID=$(c -b "$J" -X POST "$O/api/projects/$PID/meetings" -H 'content-type: application/json' -H "Origin: $O" -d '{"title":"설계 회의","meetingDate":"2026-10-14T10:00:00+09:00","purpose":"구조 정하기"}' | json "d.get('id','')")
c -b "$J" -o /dev/null -X PATCH "$O/api/projects/$PID/meetings/$MID" -H 'content-type: application/json' -H "Origin: $O" -d '{"notes":"구조도 초안은 이팀원이 그린다. 사용자 테스트는 박팀원이 진행한다.","decisions":"DB는 PostgreSQL로 한다"}'
echo "회의록 요약 $(c -b "$J" -o "$J.sum" -w '%{http_code}, %{time_total}초' -X POST "$O/api/projects/$PID/meetings/$MID/ai/summarize" -H "Origin: $O") → $(json "(d.get('summary') or '')[:70].replace(chr(10),' ')" < "$J.sum")"
echo "AI 상태 $(c -b "$J" "$O/api/projects/$PID/agent/status" | json "'available=%s 실행 종류 %d개' % (d.get('available'), len(d.get('skills',[])))")"
echo "AI 질문(이벤트가 도착한 시각, 초):"
T0=$(date +%s%N)
c -N -b "$J" -X POST "$O/api/projects/$PID/agent/runs" -H 'content-type: application/json' -H "Origin: $O" -d '{"skill":"ASK","input":{"question":"이 프로젝트에 등록된 업무가 있나요?"}}' | while IFS= read -r line; do
  case "$line" in event:*) printf "  %s초 %s\n" "$(python3 -c "print(round(($(date +%s%N) - $T0)/1e9, 1))")" "${line#event:}";; esac
done
