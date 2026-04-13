FROM nginx:alpine
# openssl 설치 + 로컬 개발용 자체 서명 인증서 생성 (localhost용, 10년 유효)
RUN apk add --no-cache openssl && \
    mkdir -p /etc/nginx/certs && \
    openssl req -x509 -nodes -days 3650 -newkey rsa:2048 \
      -keyout /etc/nginx/certs/nginx.key \
      -out    /etc/nginx/certs/nginx.crt \
      -subj   '/C=KR/ST=Seoul/O=Blackbox/CN=localhost' \
      2>/dev/null
