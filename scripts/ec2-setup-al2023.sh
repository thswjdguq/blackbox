#!/bin/bash
# EC2(Amazon Linux 2023) 서버 준비. 개발 PC가 아니라 서버에서 돈다. 여러 번 돌려도 된다. 비밀값은 화면에 찍지 않는다.
#   PC에서: ssh -i <키>.pem ec2-user@<주소> 'bash -s -- <배포 설정을 받을 브랜치>' < scripts/ec2-setup-al2023.sh
# 브랜치를 주지 않으면 main이다. 절차는 docs/deploy-aws.md 5장
set -euo pipefail
BRANCH=${1:-main}

echo "== 1. Docker, git, Compose"
sudo dnf install -y -q docker git
sudo systemctl enable --now docker
sudo usermod -aG docker ec2-user
if ! sudo docker compose version >/dev/null 2>&1; then   # Amazon Linux 저장소에는 Compose 플러그인이 없다
  sudo mkdir -p /usr/local/lib/docker/cli-plugins
  sudo curl -fsSL https://github.com/docker/compose/releases/latest/download/docker-compose-linux-x86_64 -o /usr/local/lib/docker/cli-plugins/docker-compose
  sudo chmod +x /usr/local/lib/docker/cli-plugins/docker-compose
fi

echo "== 2. 컨테이너 로그가 디스크를 채우지 않게"
if [ ! -f /etc/docker/daemon.json ]; then
  echo '{"log-driver":"json-file","log-opts":{"max-size":"20m","max-file":"3"}}' | sudo tee /etc/docker/daemon.json >/dev/null
  sudo systemctl restart docker
fi

echo "== 3. 스왑 1GB (메모리 2GB를 넘는 순간을 받아 준다)"
if ! sudo swapon --show | grep -q /swapfile; then
  sudo dd if=/dev/zero of=/swapfile bs=1M count=1024 status=none
  sudo chmod 600 /swapfile && sudo mkswap /swapfile >/dev/null && sudo swapon /swapfile
  grep -q '^/swapfile' /etc/fstab || echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab >/dev/null
fi

echo "== 4. 저장소 (배포 설정이 있는 브랜치)"
cd ~
[ -d blackbox ] || git clone -q -b "$BRANCH" https://github.com/thswjdguq/blackbox.git
cd blackbox && git pull -q

echo "== 5. .env (없을 때만 만든다. DB 비밀번호와 JWT 비밀값은 이 서버에서 새로 만든다)"
if [ ! -f .env ]; then
  rand() { head -c 48 /dev/urandom | base64 | tr -d '/+=\n' | head -c "$1"; }
  umask 077
  cat > .env <<ENV
DB_NAME=blackbox_db
DB_USER=blackbox
DB_PASSWORD=$(rand 32)
JWT_SECRET=$(rand 64)
# 접속 주소가 정해지면 적는다(예: https://이름.duckdns.org). 비어 있으면 서버가 뜨지 않는다
APP_CORS_ORIGINS=
FRONTEND_BASE_URL=
# 띄울 이미지. 레지스트리에서 받으면 IMAGE_REPO에 주소를 적고(예: ghcr.io/thswjdguq/), 비우면 PC에서 보낸 이미지를 쓴다.
# 태그가 브랜치 이름(main)이면 자동 갱신이 따라가고, 커밋이 붙은 태그(main-1a2b3c4)면 고정된다(docs/deploy-aws.md 7장)
IMAGE_REPO=
IMAGE_TAG=
# 배포 설정(compose, nginx)을 받을 브랜치. 이미지를 어느 브랜치로 만들었든 설정은 여기서 받는다
CONFIG_BRANCH=main
# AI 기능. 키는 여기에만 적는다(docs/deploy-aws.md 10장)
OPENAI_API_KEY=
LLM_BASE_URL=
LLM_MODEL=bedrock-haiku
SPRING_AI_MODEL_CHAT=none
ENV
  echo ".env를 만들었다(권한 600)"
else
  echo ".env가 이미 있어 그대로 둔다"
fi

echo "== 6. 자동 갱신 타이머(정해진 시각과 켜진 직후)"
sudo cp ~/blackbox/scripts/systemd/blackbox-update.service ~/blackbox/scripts/systemd/blackbox-update.timer /etc/systemd/system/
sudo systemctl daemon-reload && sudo systemctl enable -q --now blackbox-update.timer

echo "== 결과"
TOKEN=$(curl -s -m 3 -X PUT http://169.254.169.254/latest/api/token -H 'X-aws-ec2-metadata-token-ttl-seconds: 60' || true)
echo "인스턴스 유형: $(curl -s -m 3 -H "X-aws-ec2-metadata-token: $TOKEN" http://169.254.169.254/latest/meta-data/instance-type || echo '?')"
echo "인스턴스 프로필: $(curl -s -m 3 -H "X-aws-ec2-metadata-token: $TOKEN" http://169.254.169.254/latest/meta-data/iam/info | grep -o 'instance-profile/[^"]*' || echo '없음')"
echo "메모리: $(free -m | awk '/Mem:/{print $2"MB"}') / 스왑: $(free -m | awk '/Swap:/{print $2"MB"}') / CPU: $(nproc)개"
echo "디스크: $(df -h / | awk 'NR==2{print $2" 중 "$3" 사용, "$4" 남음"}')"
echo "$(sudo docker --version) / $(sudo docker compose version)"
echo "저장소: $(git -C ~/blackbox log -1 --format='%h %s' | cut -c1-70)"
echo ".env에 비어 있는 항목: $(grep -E '^[A-Z_]+=$' ~/blackbox/.env | cut -d= -f1 | paste -sd' ')"
echo "다음 자동 갱신: $(systemctl list-timers blackbox-update.timer --no-legend | awk '{print $1, $2, $3}')"
