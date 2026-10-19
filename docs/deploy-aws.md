# AWS 시연 서버 배포

엑스포에서 관람객이 QR 코드로 접속하는 시연용 서버를 학교가 준 AWS 계정에 올리는 절차다. 시연 시간에만 켜고 나머지 시간에는 인스턴스를 중지해 둔다.

개발은 Ubuntu(WSL)에서 하고 서버는 Amazon Linux 2023에서 돈다. 명령마다 어디서 실행하는지(**PC**, **서버**, **콘솔**) 적는다.

## 1. 구성

| 항목 | 정한 것 | 이유 |
| --- | --- | --- |
| 서버 | EC2 t3.small 한 대(Amazon Linux 2023)에 `docker-compose.yml` + `docker-compose.prod.yml` (db, backend, frontend, nginx) | 팀이 매일 쓰는 구성 그대로다 |
| DB | 같은 인스턴스의 PostgreSQL 컨테이너 | 이 계정의 RDS는 퍼블릭 액세스로만 만들 수 있어 DB가 인터넷에 열린다 |
| 업로드 파일 | 인스턴스의 Docker 볼륨 | S3로 옮기려면 서버 코드를 바꿔야 한다 |
| 주소 | Elastic IP + 무료 DDNS 이름(DuckDNS) + Let's Encrypt 인증서 | 3장 |
| AI 기능 | 학교가 준 LLM API(주소와 키) | 관리자가 Bedrock 대신 준 것이다. 10장 |
| 이미지 | PC에서 빌드해 SSH로 보낸다 | 인스턴스가 작아 빌드하기 어렵고, 액세스 키가 없어 밖에서 AWS의 레지스트리로 올릴 수 없다 |

## 2. 계정의 제약(학교 안내)

- 리전은 서울(ap-northeast-2)
- EC2는 t3 계열, t3.small까지. 쓰다가 CPU나 메모리가 모자라면 관리자가 확인하고 t3.medium으로 올려 준다
- Elastic IP와 볼륨 증설은 직접 할 수 없다. 인스턴스를 만든 뒤 관리자에게 요청하면 해 준다
- 인스턴스 메타데이터의 응답 홉 제한은 기본이 2다. 바꾸지 않는다
- 인스턴스를 만든 뒤 인스턴스 프로필을 `SafeInstanceProfile-blackbox`로 바꾼다
- 보안 그룹은 먼저 만들고, 태그가 자동으로 붙은 뒤(3~5분)에 인바운드 규칙을 편집한다. 인스턴스를 만들 때는 "기존 보안 그룹 선택"으로 그 보안 그룹을 고른다
- 액세스 키는 발급할 수 없다. 내가 만든 리소스만 중지·시작·삭제할 수 있다

## 3. 주소와 QR

QR에는 `https://team-blackbox.duckdns.org`를 넣는다.

- 이 계정으로는 도메인을 살 수 없다. DuckDNS가 무료로 이름 하나를 주고, 그 이름이 Elastic IP를 가리키게 한 번 맞춰 둔다
- Elastic IP가 붙어 있어 인스턴스를 중지했다 켜도 IP가 그대로다. DuckDNS를 다시 맞출 일이 없다
- 이름이 있어야 Let's Encrypt 인증서를 받을 수 있다. 인증서가 없으면 관람객의 휴대폰에 경고 화면이 먼저 뜬다

QR을 인쇄하기 전에 휴대폰의 모바일 데이터로 그 주소가 열리는지 확인한다.

## 4. 처음 한 번: 인스턴스 만들기(콘솔)

1. 보안 그룹을 만든다(이름과 설명 입력). 태그가 붙으면 인바운드 규칙에 22(개발자의 SSH), 80·443(사용자의 접속)을 Anywhere-IPv4로 추가한다. DB 포트(5432)는 추가하지 않는다
2. 인스턴스를 만든다
   - AMI: Amazon Linux 2023(기본값)
   - 타입: t3.small
   - 키 페어: 새로 만든다. `.ppk`로 받았다면 PC에서 OpenSSH 형식으로 바꾼다(아래). 키는 저장소에 넣지 않는다
   - 네트워크: "기존 보안 그룹 선택"으로 1에서 만든 것
   - 스토리지: 기본 8GB로 만들고, 만든 뒤 관리자에게 20GB 증설을 요청한다(8GB는 이미지 두 벌을 담기에 빠듯하다). 증설된 뒤 인스턴스를 켜면 파티션과 파일 시스템은 Amazon Linux가 스스로 늘린다
3. 만든 뒤 작업 → 보안 → IAM 역할 수정에서 `SafeInstanceProfile-blackbox`를 고른다. 이 역할은 서버가 AWS의 서비스를 부를 때 쓰는 것이고, 밖에서 서버로 들어오는 길은 보안 그룹과 키 페어가 정한다
4. 관리자에게 Elastic IP를 요청한다(요청 양식에 인스턴스 ID를 적는다)

**PC**: 키를 WSL의 `~/.ssh`에 둔다. Windows 폴더(`/mnt/c/...`)에 두면 권한 때문에 SSH가 거부한다. 저장소 안에도 두지 않는다(`.ppk`는 `.gitignore`에 걸리지 않는다).

```bash
sudo apt install -y putty-tools                                      # .ppk를 받았을 때만
puttygen ~/.ssh/blackbox.ppk -O private-openssh -o ~/.ssh/blackbox.pem
chmod 600 ~/.ssh/blackbox.pem
ssh -i ~/.ssh/blackbox.pem ec2-user@<Elastic IP> 'echo 접속됨'
```

## 5. 처음 한 번: 서버 준비

**PC**: 서버 준비 스크립트를 서버에서 돌린다. Docker·git·Compose 설치, 컨테이너 로그 크기 제한, 스왑 1GB, 저장소 받기, `.env` 만들기를 한다. `.env`의 DB 비밀번호와 JWT 비밀값은 서버에서 새로 만들고 화면에 찍지 않는다. 여러 번 돌려도 된다.

```bash
ssh -i ~/.ssh/blackbox.pem ec2-user@<Elastic IP> 'bash -s -- <배포 설정을 받을 브랜치>' < scripts/ec2-setup-al2023.sh
```

브랜치를 주지 않으면 main이다. 끝에 인스턴스 유형, 프로필, 메모리, 디스크, `.env`에 비어 있는 항목이 출력된다. Amazon Linux의 저장소에는 Compose 플러그인이 없어 GitHub 릴리스에서 받는다.

### 주소(DuckDNS)

**브라우저**: duckdns.org에서 이름을 만들고, 그 이름의 IP 칸에 Elastic IP를 넣어 저장한다.

### 인증서(Let's Encrypt)

**PC**: 80 포트를 쓰므로 스택을 띄우기 전에 한다. 인증서는 90일 동안 유효하다.

```bash
ssh -i ~/.ssh/blackbox.pem ec2-user@<Elastic IP> "sudo docker run --rm -p 80:80 -v /etc/letsencrypt:/etc/letsencrypt certbot/certbot certonly --standalone -n --agree-tos --register-unsafely-without-email -d <이름>.duckdns.org && sudo docker rmi certbot/certbot"
ssh -i ~/.ssh/blackbox.pem ec2-user@<Elastic IP> "cd ~/blackbox && sudo cp -L /etc/letsencrypt/live/<이름>.duckdns.org/fullchain.pem ssl/server.crt && sudo cp -L /etc/letsencrypt/live/<이름>.duckdns.org/privkey.pem ssl/server.key && sudo chown ec2-user ssl/server.crt ssl/server.key && chmod 600 ssl/server.key"
```

만료 알림 메일을 받으려면 `--register-unsafely-without-email` 대신 `-m <이메일>`을 준다. 90일 뒤에도 쓰면 스택을 내리고 같은 명령으로 다시 받는다.

### 환경 변수(`.env`)

**PC**: 준비 스크립트가 만든 `.env`에 접속 주소를 적는다. 개발용 `.env`를 복사하지 않는다.

```bash
ssh -i ~/.ssh/blackbox.pem ec2-user@<Elastic IP> "cd ~/blackbox && sed -i 's#^APP_CORS_ORIGINS=.*#APP_CORS_ORIGINS=https://<이름>.duckdns.org#; s#^FRONTEND_BASE_URL=.*#FRONTEND_BASE_URL=https://<이름>.duckdns.org#' .env"
```

`APP_CORS_ORIGINS`가 비어 있으면 스택이 뜨지 않는다. 적은 주소가 아닌 출처의 요청은 403이 된다. IP로 직접 들어오면 로그인이 되지 않는 것이 정상이다.

LLM 키와 주소는 10장대로 넣는다. 이 PC의 `.env`에 이미 있다면 값을 화면에 찍지 않고 옮길 수 있다.

```bash
grep -E '^(OPENAI_API_KEY|LLM_BASE_URL)=' .env | ssh -i ~/.ssh/blackbox.pem ec2-user@<Elastic IP> 'cd ~/blackbox && while IFS= read -r l; do k=${l%%=*}; sed -i "/^$k=/d" .env; echo "$l" >> .env; done && chmod 600 .env'
```

## 6. 접속하는 방법

| 누가 | 방법 | 열려 있는 것 |
| --- | --- | --- |
| 사용자 | `https://team-blackbox.duckdns.org` | 보안 그룹의 80, 443 |
| 개발자 | SSH(키). 비밀번호 접속은 꺼져 있다 | 보안 그룹의 22 |
| 개발자의 DB 접속 | SSH 터널 | 없음. 5432는 서버 안(127.0.0.1)에서만 열린다 |

### 팀원의 SSH

키 파일을 돌려 쓰지 않는다. 팀원이 자기 PC에서 키를 만들고 공개 키(`.pub`)만 보낸다. 빼려면 그 줄을 지운다.

```bash
ssh-keygen -t ed25519 -f ~/.ssh/blackbox-aws               # 팀원의 PC. blackbox-aws.pub의 한 줄을 보낸다
echo '<받은 공개 키 한 줄>' >> ~/.ssh/authorized_keys        # 서버
ssh -i ~/.ssh/blackbox-aws ec2-user@team-blackbox.duckdns.org   # 팀원의 PC
```

### DB

**PC**: 터널을 열어 두고 `localhost:15432`로 접속한다. DBeaver는 연결 설정의 SSH 탭에 같은 값을 넣으면 된다. 계정과 비밀번호는 서버 `.env`의 `DB_USER`, `DB_PASSWORD`다.

```bash
ssh -i ~/.ssh/blackbox.pem -N -L 15432:127.0.0.1:5432 ec2-user@team-blackbox.duckdns.org
psql -h localhost -p 15432 -U blackbox blackbox_db       # 다른 창에서
```

서버 안에서는 `docker exec -it blackbox_db psql -U blackbox -d blackbox_db`.

## 7. 배포와 갱신

### 이미지 만들고 보내기(PC)

올릴 브랜치를 옆 폴더에 받아 빌드한다(README 6장). 지금 작업 중인 폴더의 브랜치는 건드리지 않는다. 태그에는 브랜치와 커밋을 적는다(예: `expo-db84780`). 서버에서 `docker ps`만 봐도 어느 코드가 떠 있는지 알 수 있다.

```bash
git fetch origin && git worktree add --detach ../blackbox-build origin/<올릴 브랜치>
cd ../blackbox-build
T=<브랜치를 알 수 있는 이름>-$(git rev-parse --short=7 HEAD)
docker build -t blackbox-backend:$T backend && docker build -t blackbox-frontend:$T frontend && docker build -t blackbox-nginx:$T nginx
docker save blackbox-backend:$T blackbox-frontend:$T blackbox-nginx:$T | gzip | ssh -i ~/.ssh/blackbox.pem ec2-user@team-blackbox.duckdns.org 'gunzip | docker load'
cd - && git worktree remove ../blackbox-build
```

압축해서 약 220MB이고 보내는 데 30초쯤 걸린다.

### 임시 스택으로 먼저 확인(서버, PC)

시연 DB에 확인용 자료가 남지 않게, 새 이미지는 임시 프로젝트 이름으로 먼저 띄워 확인하고 지운다.

```bash
# 서버: 지금 떠 있는 스택을 내리고(볼륨은 남는다) 임시 이름으로 띄운다
cd ~/blackbox && git pull
docker compose -f docker-compose.yml -f docker-compose.prod.yml down
printf 'COMPOSE_PROJECT_NAME=blackbox-check\nIMAGE_TAG=<태그>\n' > check.env
docker compose --env-file .env --env-file check.env -f docker-compose.yml -f docker-compose.prod.yml up -d
```

```bash
# PC: 가입, 파일, 회의록 요약, AI 질문의 스트리밍까지 본다
ALLOW_TEST_DATA=1 bash scripts/ec2-check.sh https://team-blackbox.duckdns.org
```

```bash
# 서버: 임시 스택과 그 볼륨만 지운다
docker compose --env-file .env --env-file check.env -f docker-compose.yml -f docker-compose.prod.yml down
docker volume rm blackbox-check_pgdata blackbox-check_uploads && rm check.env
```

### 실제로 띄우기(서버)

`.env`의 `IMAGE_TAG`를 새 태그로 바꾸고 띄운다. DB 마이그레이션은 서버가 뜰 때 적용된다. 옛 이미지는 지워 디스크를 비운다.

```bash
cd ~/blackbox && sed -i 's#^IMAGE_TAG=.*#IMAGE_TAG=<태그>#' .env
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d
docker image prune -a -f      # 컨테이너가 쓰지 않는 이미지를 지운다
```

서버에서는 빌드하지 않는다. 그 태그의 이미지가 서버에 없으면 띄우지 않고 오류(`pull access denied`)로 멈춘다. 이미지를 다시 보낸다.

### main에 병합되지 않은 브랜치를 올릴 때

그 브랜치의 이미지를 띄운 DB는 그 브랜치의 마이그레이션 이력을 갖는다. main의 이미지가 같은 DB를 쓰면 이력이 달라 서버가 뜨지 않을 수 있다. 섞이지 않게 둘을 나눈다.

- 서버 `.env`에 `COMPOSE_PROJECT_NAME=<브랜치를 알 수 있는 이름>`을 적는다(엑스포는 `blackbox-expo`). DB와 업로드 볼륨이 그 이름으로 따로 만들어진다. 지우면 main용 볼륨(`blackbox_*`)을 쓴다
- 컨테이너 이름은 같으므로 바꿔 띄우기 전에 띄워 둔 쪽을 `docker compose ... down`으로 내린다(볼륨은 남는다)
- 그 브랜치에만 필요한 설정이 생기면 이 저장소의 compose 파일을 고치지 않고, 서버에 override 파일을 하나 더 두어 `-f`로 붙인다

`docker compose down -v`는 DB와 업로드 파일을 지운다. 쓰지 않는다.

## 8. 백업과 복원(PC)

인스턴스를 중지해도 디스크는 남지만, 백업은 따로 없다. 시연 자료를 넣은 뒤와 시연이 끝난 날 PC로 받아 둔다. 백업 파일은 저장소 밖(`~/blackbox-backups`)에 둔다.

```bash
mkdir -p ~/blackbox-backups && D=$(date +%Y%m%d-%H%M)
ssh -i ~/.ssh/blackbox.pem ec2-user@team-blackbox.duckdns.org 'docker exec blackbox_db pg_dump -U blackbox -d blackbox_db -Fc' > ~/blackbox-backups/db-$D.dump
ssh -i ~/.ssh/blackbox.pem ec2-user@team-blackbox.duckdns.org 'docker exec blackbox_backend tar czf - -C /data/uploads .' > ~/blackbox-backups/uploads-$D.tgz
```

되돌릴 때는 업로드 파일을 먼저 넣고, backend를 멈춘 채 DB를 복원한 뒤 다시 띄운다. 복원은 백업에 있는 표의 내용을 백업 시점으로 바꾼다.

```bash
ssh -i ~/.ssh/blackbox.pem ec2-user@team-blackbox.duckdns.org 'docker exec -i blackbox_backend tar xzf - -C /data/uploads' < ~/blackbox-backups/uploads-<시각>.tgz
ssh -i ~/.ssh/blackbox.pem ec2-user@team-blackbox.duckdns.org 'cd ~/blackbox && docker compose -f docker-compose.yml -f docker-compose.prod.yml stop backend'
ssh -i ~/.ssh/blackbox.pem ec2-user@team-blackbox.duckdns.org 'docker exec -i blackbox_db pg_restore -U blackbox -d blackbox_db --clean --if-exists' < ~/blackbox-backups/db-<시각>.dump
ssh -i ~/.ssh/blackbox.pem ec2-user@team-blackbox.duckdns.org 'cd ~/blackbox && docker compose -f docker-compose.yml -f docker-compose.prod.yml start backend'
```

## 9. 시연하는 날

1. **콘솔**: 시작 5분 전에 인스턴스를 시작한다. Docker와 컨테이너는 스스로 다시 뜬다(1~2분)
2. **휴대폰**: 모바일 데이터로 QR을 찍어 로그인 화면이 열리는지 본다
3. AI 기능은 준비한 테스트용 프로젝트와 회의록으로만 보여 준다(10장)
4. 끝나면 8장대로 백업을 받고, **콘솔**에서 인스턴스를 **중지**한다. 종료하면 DB와 파일이 지워진다

열리지 않으면 서버에서 `docker ps`와 `docker logs blackbox_backend --tail 50`을 본다. 켜진 직후 몇 초 동안은 nginx 로그에 backend 연결 거부가 찍히는 것이 정상이다.

## 10. AI 기능(학교가 준 LLM API)

관리자가 Bedrock 대신 OpenAI 호환 API의 주소(BASE_URL)와 키를 준다. 키는 서버의 `.env`에만 적고 저장소, PR, 메신저에 올리지 않는다.

`.env`에 아래를 적고 backend를 다시 띄운다. 회의록 요약·액션 아이템 추출과 AI 도우미가 같은 키, 주소, 모델을 쓴다.

```bash
OPENAI_API_KEY=<받은 키>
LLM_BASE_URL=<받은 BASE_URL. 끝에 /v1을 붙이지 않는다>
LLM_MODEL=bedrock-haiku
SPRING_AI_MODEL_CHAT=openai     # main의 AI 도우미를 켠다. none이면 도우미만 꺼진다
```

- `CLAUDE_API_KEY`는 비워 둔다. 값이 있으면 회의록 요약이 그쪽으로 간다
- `SPRING_AI_MODEL_CHAT=openai`인데 `OPENAI_API_KEY`가 비어 있으면 서버가 뜨지 않는다
- 엑스포 브랜치(`shinguexpo`)의 도우미는 `SPRING_AI_MODEL_CHAT`을 쓰지 않고 `OPENAI_API_KEY`, `LLM_BASE_URL`, `LLM_MODEL`만으로 켜진다
- 모델은 별칭으로만 부른다: `bedrock-haiku`(Claude Haiku 4.5), `bedrock-sonnet`(Claude 3.5 Sonnet v2), `bedrock-gpt-5.6-luna`. 쓸 수 있는 별칭은 `curl -s "$LLM_BASE_URL/v1/models" -H "Authorization: Bearer $OPENAI_API_KEY"`로 본다
- 키마다 사용 한도가 있어 넘으면 호출이 막히고("Budget has been exceeded"), 짧은 시간에 몰리면 429가 난다
- 요청 내용은 게이트웨이의 로그에 남고, 개인정보를 프롬프트에 넣지 말라는 것이 사용 조건이다
- 호출이 실패하면 AI 기능만 오류가 나고 나머지는 그대로 동작한다

AI 도우미의 질문 세트(`AgentQuestionSetLiveTest`, 21항목)를 이 API로 한 번씩 돌린 결과(2026-10):

| 모델 | 통과 | 틀린 것 | 호출 하나에 걸린 시간 |
| --- | --- | --- | --- |
| `bedrock-haiku` | 18 | 업무로 나누기에서 카드 한 장이 아니라 요구사항마다 한 장씩 냈다(내용은 맞음) | 3~13초 |
| `bedrock-sonnet` | 19 | 필수 요구사항 수를 묻는 질문, 상태 요약에서 경보 전달 | 4~32초 |

모델의 답은 돌릴 때마다 달라진다. 모델을 바꾸면 같은 테스트를 다시 돌려 본다.

## 11. 확인한 것

PC에서 같은 compose 파일로 띄워 확인한 것:

- https 접속, http는 https로 넘어감
- 적은 주소에서 온 요청만 통과(다른 출처 403), 로그인 쿠키에 `Secure`
- 파일 업로드와 권한을 확인하는 다운로드. 업로드 폴더가 로그인 없이 열리지 않음
- 서버에 없는 태그로 띄우면 빌드하지 않고 오류로 멈춤
- DB 포트가 127.0.0.1에서만 열리고 거기서 접속됨
- 학교가 준 LLM API로 회의록 요약, 액션 아이템 추출, AI 도우미의 도구 호출(10장의 질문 세트)
- 8장의 백업·복원 명령(`pg_dump -Fc`, `pg_restore --clean --if-exists`, 서버 이미지의 `tar`)
- `scripts/ec2-check.sh`

AWS에서 확인한 것(2026-10-13~18):

- 4~6장의 절차대로 인스턴스, 보안 그룹, 키, 준비 스크립트, DuckDNS, Let's Encrypt 인증서, `.env`
- 엑스포 이미지를 임시 스택으로 띄워 `scripts/ec2-check.sh`의 항목 전부, 저장된 파일을 공개 경로로 요청하면 404, AI 질문의 이벤트가 nginx를 거쳐 순서대로 도착
- `blackbox-expo` 이름의 새 DB에 마이그레이션 V29까지 적용, 서버 로그 ERROR 0
- 유휴 메모리: backend 328MB, db 53MB, frontend 29MB, nginx 4MB. 여유 약 1GB
- 중지했다 켜면 1~2분 안에 컨테이너가 스스로 다시 뜸
- 볼륨 증설(8GB → 20GB) 뒤 켜면 파티션과 파일 시스템이 자동으로 늘어남
- SSH는 키로만 접속됨(비밀번호 접속 꺼짐)

## 12. 하지 않은 것

- main에 병합되면 자동으로 배포하는 것. 지금은 7장을 손으로 한다
- 인증서 자동 갱신. 계속 운영하려면 nginx가 인증 경로(`/.well-known/acme-challenge/`)를 https로 넘기지 않게 열어 줘야 한다
- 백업 자동화와 서버 감시

## 13. 지우기

시연이 모두 끝나면 8장대로 마지막 백업을 받고 인스턴스를 종료한다. Elastic IP는 관리자에게 반납을 알린다. 보안 그룹과 키 페어, DuckDNS 이름도 지운다.
