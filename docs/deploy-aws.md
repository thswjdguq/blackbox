# AWS 시연 서버 배포

엑스포에서 관람객이 QR 코드로 접속하는 시연용 서버를 학교가 준 AWS 계정에 올리는 절차다. 시연 시간에만 켜고 나머지 시간에는 인스턴스를 중지해 둔다.

## 1. 구성

| 항목 | 정한 것 | 이유 |
| --- | --- | --- |
| 서버 | EC2 한 대에 `docker-compose.yml` + `docker-compose.prod.yml` (db, backend, frontend, nginx) | 팀이 매일 쓰는 구성 그대로다 |
| DB | 같은 인스턴스의 PostgreSQL 컨테이너 | 이 계정의 RDS는 퍼블릭 액세스로만 만들 수 있어 DB가 인터넷에 열린다 |
| 업로드 파일 | 인스턴스의 Docker 볼륨 | S3로 옮기려면 서버 코드를 바꿔야 한다 |
| 주소 | 무료 DDNS 이름(DuckDNS) + Let's Encrypt 인증서 | 3장 |
| AI 도우미 | Bedrock(us-east-1), 자격 증명은 인스턴스 프로필 | 액세스 키를 발급할 수 없다. 9장 |
| 이미지 | PC에서 빌드해 SSH로 보낸다 | 인스턴스가 작아 빌드하기 어렵고, 액세스 키가 없어 밖에서 AWS의 레지스트리로 올릴 수 없다 |

## 2. 계정의 제약(학교 안내)

- 리전은 서울(ap-northeast-2). Bedrock만 us 리전
- EC2는 t3.nano~t3.small(안내 표에는 t3.medium까지로 적혀 있다. 만들 때 되는 쪽을 확인한다)
- 인스턴스를 만든 뒤 인스턴스 프로필을 `SafeInstanceProfile-blackbox`로 바꾼다
- 보안 그룹은 먼저 만들고, 태그가 자동으로 붙은 뒤에 인바운드 규칙을 편집한다
- 액세스 키는 발급할 수 없다. 내가 만든 리소스만 중지·시작·삭제할 수 있다

## 3. 주소와 QR

QR에는 `https://<이름>.duckdns.org`를 넣는다.

- 인스턴스를 중지했다 켜면 공인 IP가 바뀌므로 IP를 QR에 넣을 수 없다
- 이 계정으로는 도메인을 살 수 없다. DuckDNS는 무료로 이름 하나를 주고, 서버가 켜질 때 지금 IP를 알려 주면 그 이름이 새 IP를 가리킨다
- 이름이 있어야 Let's Encrypt 인증서를 받을 수 있다. 인증서가 없으면 관람객의 휴대폰에 경고 화면이 먼저 뜬다
- Elastic IP를 만들 수 있으면 하나 만들어 인스턴스에 붙인다. IP가 바뀌지 않아 이름이 새 IP를 가리킬 때까지 기다릴 일이 없다. 없어도 아래 절차는 같다

QR을 인쇄하기 전에 휴대폰의 모바일 데이터로 그 주소가 열리는지 확인한다.

## 4. 처음 한 번: 인스턴스 만들기(콘솔)

1. 보안 그룹을 만든다(이름과 설명 입력). 태그가 붙으면 인바운드 규칙에 22(개발자의 SSH), 80·443(사용자의 접속)을 Anywhere-IPv4로 추가한다. DB 포트(5432)는 추가하지 않는다
2. 인스턴스를 만든다
   - AMI: Ubuntu Server 24.04 LTS
   - 타입: t3.small(되면 t3.medium)
   - 키 페어: 새로 만들어 `.pem` 파일을 PC에 둔다. 저장소에 넣지 않는다
   - 보안 그룹: 1에서 만든 것
   - 스토리지: 20GB (기본 8GB는 이미지와 DB를 담기에 모자란다)
   - 고급 세부 정보 → 메타데이터 응답 홉 제한: 2 (컨테이너 안의 서버가 인스턴스 프로필의 자격 증명을 받으려면 필요하다)
3. 만든 뒤 작업 → 보안 → IAM 역할 수정에서 `SafeInstanceProfile-blackbox`를 고른다. 이 역할은 서버가 AWS의 서비스(Bedrock 등)를 부를 때 쓰는 것이고, 밖에서 서버로 들어오는 길은 보안 그룹과 키 페어가 정한다
4. (되면) Elastic IP를 할당해 인스턴스에 연결한다

## 5. 처음 한 번: 서버 준비(SSH)

```bash
ssh -i <키>.pem ubuntu@<공인 IP>

# Docker와 Compose
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker ubuntu     # 접속을 끊었다 다시 들어와야 적용된다

# 메모리가 2GB라 스왑을 둔다
sudo fallocate -l 2G /swapfile && sudo chmod 600 /swapfile && sudo mkswap /swapfile && sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab

# 컨테이너 로그가 디스크를 채우지 않게 한다
echo '{"log-driver":"json-file","log-opts":{"max-size":"20m","max-file":"3"}}' | sudo tee /etc/docker/daemon.json
sudo systemctl restart docker

git clone https://github.com/thswjdguq/blackbox.git && cd blackbox
```

### 주소(DuckDNS)

duckdns.org에서 이름을 만들고 토큰을 받는다. 토큰은 서버의 이 파일에만 둔다.

```bash
echo 'curl -fsS "https://www.duckdns.org/update?domains=<이름>&token=<토큰>&ip="' > ~/duckdns.sh
chmod 700 ~/duckdns.sh && ~/duckdns.sh      # OK가 나오면 된다
(crontab -l 2>/dev/null; echo '@reboot sleep 20; ~/duckdns.sh') | crontab -
```

### 인증서(Let's Encrypt)

80 포트를 쓰므로 스택을 띄우기 전에 한다. 90일 동안 유효하다. 그 뒤에도 쓰면 스택을 내리고 같은 명령으로 다시 받는다.

```bash
sudo docker run --rm -p 80:80 -v /etc/letsencrypt:/etc/letsencrypt certbot/certbot \
  certonly --standalone -n --agree-tos -m <이메일> -d <이름>.duckdns.org
sudo cp -L /etc/letsencrypt/live/<이름>.duckdns.org/fullchain.pem ssl/server.crt
sudo cp -L /etc/letsencrypt/live/<이름>.duckdns.org/privkey.pem  ssl/server.key
sudo chown ubuntu ssl/server.* && chmod 600 ssl/server.key
```

### 환경 변수(`.env`)

개발용 `.env`를 복사하지 않는다. 비밀값은 서버에서 새로 만든다(`openssl rand -base64 48`).

```bash
cat > .env <<'EOF'
DB_NAME=blackbox_db
DB_USER=blackbox
DB_PASSWORD=<새로 만든 값>
JWT_SECRET=<새로 만든 값, 32자 이상>
APP_CORS_ORIGINS=https://<이름>.duckdns.org
FRONTEND_BASE_URL=https://<이름>.duckdns.org
SPRING_AI_MODEL_CHAT=none
IMAGE_TAG=<띄울 이미지의 태그>
EOF
chmod 600 .env
```

`APP_CORS_ORIGINS`가 비어 있으면 스택이 뜨지 않는다. 적은 주소가 아닌 출처의 요청은 403이 된다.

## 6. 접속하는 방법

| 누가 | 방법 | 열려 있는 것 |
| --- | --- | --- |
| 사용자 | `https://<이름>.duckdns.org` | 보안 그룹의 80, 443 |
| 개발자 | SSH(키) | 보안 그룹의 22 |
| 개발자의 DB 접속 | SSH 터널 | 없음. 5432는 서버 안(127.0.0.1)에서만 열린다 |

### 팀원의 SSH

`.pem` 파일을 돌려 쓰지 않는다. 팀원이 자기 PC에서 키를 만들고 공개 키(`.pub`)만 보낸다. 빼려면 그 줄을 지운다.

```bash
ssh-keygen -t ed25519 -f ~/.ssh/blackbox-aws          # 팀원의 PC에서. blackbox-aws.pub의 한 줄을 보낸다
echo '<받은 공개 키 한 줄>' >> ~/.ssh/authorized_keys   # 서버에서
ssh -i ~/.ssh/blackbox-aws ubuntu@<이름>.duckdns.org   # 팀원의 PC에서
```

### DB

PC에서 터널을 열어 두고 `localhost:15432`로 접속한다. DBeaver는 연결 설정의 SSH 탭에 같은 값을 넣으면 된다. 계정과 비밀번호는 서버 `.env`의 `DB_USER`, `DB_PASSWORD`다.

```bash
ssh -i <키> -N -L 15432:127.0.0.1:5432 ubuntu@<이름>.duckdns.org
psql -h localhost -p 15432 -U blackbox blackbox_db       # 다른 창에서
```

서버 안에서는 `docker exec -it blackbox_db psql -U blackbox -d blackbox_db`.

## 7. 배포와 갱신

PC에서 올릴 브랜치를 받아 이미지를 만들어 보낸다(압축해서 약 250MB). 태그는 어느 브랜치의 것인지 알 수 있게 붙인다.

```bash
git switch <올릴 브랜치> && git pull
docker build -t blackbox-backend:<태그> backend
docker build -t blackbox-frontend:<태그> frontend
docker build -t blackbox-nginx:<태그> nginx
docker save blackbox-backend:<태그> blackbox-frontend:<태그> blackbox-nginx:<태그> | gzip | ssh -i <키> ubuntu@<주소> 'gunzip | docker load'
```

서버에서 `.env`의 `IMAGE_TAG`를 그 태그로 바꾸고 띄운다. DB 마이그레이션은 서버가 뜰 때 적용된다.

```bash
cd blackbox && git pull
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d
```

서버에서는 빌드하지 않는다. 그 태그의 이미지가 서버에 없으면 띄우지 않고 오류(`pull access denied`)로 멈춘다. 이미지를 다시 보낸다.

### main에 병합되지 않은 브랜치를 올릴 때

그 브랜치의 이미지를 띄운 DB는 그 브랜치의 마이그레이션 이력을 갖는다. main의 이미지가 같은 DB를 쓰면 이력이 달라 서버가 뜨지 않을 수 있다. 섞이지 않게 둘을 나눈다.

- 서버 `.env`에 `COMPOSE_PROJECT_NAME=<브랜치를 알 수 있는 이름>`을 적는다. DB와 업로드 볼륨이 그 이름으로 따로 만들어진다. 지우면 main용 볼륨(`blackbox_*`)을 쓴다
- 컨테이너 이름은 같으므로 바꿔 띄우기 전에 띄워 둔 쪽을 `docker compose ... down`으로 내린다(볼륨은 남는다)
- 그 브랜치에만 필요한 설정이 생기면 이 저장소의 compose 파일을 고치지 않고, 서버에 덮어쓰기 파일을 하나 더 두어 `-f`로 붙인다

`docker compose down -v`는 DB와 업로드 파일을 지운다. 쓰지 않는다.

## 8. 시연하는 날

1. 콘솔에서 인스턴스를 시작한다. Docker와 컨테이너는 스스로 다시 뜨고, DuckDNS 이름도 새 IP로 바뀐다
2. 2~3분 뒤 휴대폰으로 QR을 찍어 로그인 화면이 열리는지 본다
3. 끝나면 인스턴스를 중지한다(종료가 아니다. 종료하면 DB와 파일이 지워진다)

열리지 않으면 `docker compose ps`와 `docker compose logs backend --tail 50`을 본다.

## 9. AI 도우미(Bedrock)

`.env`에 아래를 넣고 backend를 다시 띄운다. 키는 적지 않는다.

```bash
SPRING_AI_MODEL_CHAT=bedrock-converse
BEDROCK_MODEL=<Bedrock 콘솔(us-east-1)에서 이 계정이 쓸 수 있는 모델 또는 추론 프로파일의 ID>
# BEDROCK_REGION=us-east-1   # 기본값. 다른 us 리전을 쓸 때만 적는다
```

- 화면에서 "지금 상태 살펴보기"를 눌러 답이 오는지 본다
- "AI 응답을 받지 못했습니다"가 나오면 `docker compose logs backend | grep -i -E "credentials|AccessDenied|ValidationException"`으로 원인을 본다. 자격 증명을 못 받으면 4장의 홉 제한과 인스턴스 프로필을, 권한이나 모델 오류면 모델 ID와 리전을 확인한다
- 해결되지 않으면 `SPRING_AI_MODEL_CHAT=none`으로 둔다. AI 도우미만 꺼지고 나머지는 그대로 동작한다

## 10. 확인한 것과 확인하지 못한 것

PC에서 같은 compose 파일로 띄워 확인한 것:

- https 접속, http는 https로 넘어감
- 적은 주소에서 온 요청만 통과(다른 출처 403), 로그인 쿠키에 `Secure`
- 파일 업로드와 권한을 확인하는 다운로드. 업로드 폴더가 로그인 없이 열리지 않음
- 유휴 메모리 약 490MB(backend 387, db 55, frontend 34, nginx 10)
- 서버에 없는 태그로 띄우면 빌드하지 않고 오류로 멈춤
- DB 포트가 127.0.0.1에서만 열리고 거기서 접속됨
- Bedrock을 고르면 서버가 뜨고, 자격 증명이 없을 때는 실행이 오류 문장으로 끝남

AWS에서만 볼 수 있어 확인하지 못한 것:

- 4~8장의 콘솔·서버 명령 전체(인스턴스 타입, Elastic IP 가능 여부, DuckDNS, Let's Encrypt 발급)
- Bedrock 호출(이 계정이 쓸 수 있는 모델, 컨테이너에서 자격 증명 받기, 응답 품질과 속도)
- t3.small에서의 실제 응답 속도

## 11. 하지 않은 것

- main에 병합되면 자동으로 배포하는 것. 지금은 7장을 손으로 한다
- DB 백업. 인스턴스를 중지해도 디스크는 남는다. 종료하기 전에 남길 자료가 있으면 `docker exec blackbox_db pg_dump -U blackbox blackbox_db > backup.sql`로 받는다

## 12. 지우기

시연이 모두 끝나면 인스턴스를 종료하고, Elastic IP를 만들었으면 릴리스한다. 보안 그룹과 키 페어, DuckDNS 이름도 지운다.
