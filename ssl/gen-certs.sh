#!/bin/sh
# 데모용 self-signed SSL 인증서 생성 (유효기간 365일)
# 사용: sh ssl/gen-certs.sh

set -e

CERT_DIR="$(dirname "$0")"

openssl req -x509 -nodes -days 365 \
  -newkey rsa:2048 \
  -keyout "${CERT_DIR}/server.key" \
  -out    "${CERT_DIR}/server.crt" \
  -subj "/C=KR/ST=Seoul/L=Seoul/O=TeamBlackbox/CN=localhost" \
  -addext "subjectAltName=DNS:localhost,IP:127.0.0.1"

echo "✅ SSL 인증서 생성 완료: ssl/server.crt, ssl/server.key"
echo "   (브라우저에서 https://localhost 접속 시 '안전하지 않음' 경고 → 고급 → 계속 진행 클릭)"
