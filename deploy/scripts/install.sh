#!/usr/bin/env bash
# =============================================================
# 安装/更新整套服务到 Docker Swarm
#
# 用法：
#   ./deploy/scripts/install.sh
#
# 做的事：
#   1. 把 deploy/ 同步到 /opt/tycoon-wheel（容器要挂载里面的配置文件）
#   2. 等待 Nacos 就绪并发布 4 个 data-id 的配置
#   3. docker stack deploy
#   4. 等待收敛并冒烟
# =============================================================
set -euo pipefail

RUNTIME_DIR="${RUNTIME_DIR:-/opt/tycoon-wheel}"
STACK_NAME="${STACK_NAME:-tycoon}"
NACOS_ADDR="${NACOS_ADDR:-127.0.0.1:8848}"
PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

cyan() { printf '\033[36m%s\033[0m\n' "$*"; }
green() { printf '\033[32m%s\033[0m\n' "$*"; }
red() { printf '\033[31m%s\033[0m\n' "$*"; }

# 非 root 且装了 sudo 才用 sudo
SUDO=""
if [ "$(id -u)" -ne 0 ] && command -v sudo >/dev/null 2>&1; then
  SUDO="sudo"
fi

# ---------- 1. 同步部署目录 ----------
cyan "==> 同步部署文件到 $RUNTIME_DIR"
$SUDO mkdir -p "$RUNTIME_DIR"
if command -v rsync >/dev/null 2>&1; then
  $SUDO rsync -a --delete --exclude 'scripts' "$PROJECT_DIR/deploy/" "$RUNTIME_DIR/"
else
  $SUDO cp -rf "$PROJECT_DIR/deploy/." "$RUNTIME_DIR/"
fi
green "    OK"

# ---------- 2. 等 Nacos ----------
cyan "==> 等待 Nacos: $NACOS_ADDR"
for i in $(seq 1 60); do
  code=$(curl -s -o /dev/null -w '%{http_code}' --max-time 3 \
    "http://${NACOS_ADDR}/nacos/v1/console/health/readiness" 2>/dev/null || echo 000)
  [ "$code" = "200" ] && break
  [ "$i" -eq 60 ] && { red "Nacos 未就绪，先手动启动 nacos 服务"; }
  sleep 3
done

# ---------- 3. 发布配置 ----------
cyan "==> 发布 Nacos 配置"
shopt -s nullglob
for f in "$PROJECT_DIR"/deploy/config/nacos/*.yaml; do
  dataid=$(basename "$f")
  code=$(curl -s -o /dev/null -w '%{http_code}' --max-time 15 -X POST \
    "http://${NACOS_ADDR}/nacos/v1/cs/configs" \
    --data-urlencode "dataId=$dataid" \
    --data-urlencode "group=DEFAULT_GROUP" \
    --data-urlencode "type=yaml" \
    --data-urlencode "content@$f")
  echo "    $dataid -> $code"
done

# ---------- 4. 部署 ----------
cyan "==> docker stack deploy"
cd "$RUNTIME_DIR"
# --resolve-image never：镜像来自本地仓库，禁止到公网 registry 解析
docker stack deploy --resolve-image never -c "$RUNTIME_DIR/swarm/docker-stack.yml" "$STACK_NAME" >/dev/null
green "    已提交"

# ---------- 5. 等收敛 ----------
cyan "==> 等待收敛（最多 10 分钟）"
for i in $(seq 1 100); do
  out=$(docker stack services "$STACK_NAME" --format '{{.Name}} {{.Replicas}}' 2>/dev/null || true)
  if [ -n "$out" ] && echo "$out" | awk '{split($2,a,"/"); if (a[1]!=a[2]) bad=1} END{exit bad}' 2>/dev/null; then
    green "    全部就绪（${i} 次探测）"
    break
  fi
  sleep 6
done

docker stack services "$STACK_NAME" --format '  {{.Name}}  {{.Replicas}}' | sort

echo
cyan "==> 冒烟"
GW="${GATEWAY_URL:-http://127.0.0.1:8090}"
code=$(curl -s -o /dev/null -w '%{http_code}' --max-time 10 "$GW/api/config" || echo 000)
echo "    GET /api/config -> $code"
[ "$code" = "200" ] && green "部署成功" || red "冒烟失败，检查 docker service logs"
