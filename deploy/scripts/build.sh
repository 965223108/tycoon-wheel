#!/usr/bin/env bash
# =============================================================
# 构建所有服务镜像并推送到本地镜像仓库
#
# 用法：
#   ./deploy/scripts/build.sh            # 构建全部 4 个服务
#   ./deploy/scripts/build.sh user       # 只构建 user
#
# 前置条件：
#   - JDK 21 + Maven（或先在宿主机执行 mvn package）
#   - Docker
# 说明：
#   Swarm 无法可靠使用「本地构建、无 registry digest」的镜像，
#   所以这里统一推到本地仓库 127.0.0.1:5000。
# =============================================================
set -euo pipefail

REGISTRY="${REGISTRY:-127.0.0.1:5000}"
REGISTRY_PORT="${REGISTRY##*:}"
PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
BUILD_DIR="${BUILD_DIR:-/tmp/tycoon-build}"
AGENT_DIR="${AGENT_DIR:-}"          # 可选：SkyWalking agent 解压目录

cyan() { printf '\033[36m%s\033[0m\n' "$*"; }
green() { printf '\033[32m%s\033[0m\n' "$*"; }

cyan "==> 项目根目录: $PROJECT_DIR"

# ---------- 1. 确保本地镜像仓库在跑 ----------
if ! curl -sf "http://${REGISTRY}/v2/" >/dev/null 2>&1; then
  cyan "==> 启动本地镜像仓库 ${REGISTRY}"
  if ! docker image inspect registry:2 >/dev/null 2>&1; then
    docker pull "docker.m.daocloud.io/library/registry:2" || docker pull registry:2
    docker tag "$(docker images --format '{{.Repository}}:{{.Tag}}' | grep -E 'registry:2$' | head -1)" registry:2 2>/dev/null || true
  fi
  docker rm -f local-registry >/dev/null 2>&1 || true
  docker run -d --name local-registry --restart unless-stopped \
    -p "${REGISTRY_PORT}:5000" -v registry-data:/var/lib/registry registry:2 >/dev/null
  for _ in $(seq 1 30); do
    curl -sf "http://${REGISTRY}/v2/" >/dev/null 2>&1 && break
    sleep 1
  done
fi
green "    仓库就绪: ${REGISTRY}"

# ---------- 2. Maven 打包 ----------
cyan "==> Maven 打包"
cd "$PROJECT_DIR"
if [ "${SKIP_MAVEN:-false}" != "true" ]; then
  mvn -B -q clean package -DskipTests
fi
green "    jar 已生成"

# ---------- 3. 准备构建上下文 ----------
cyan "==> 准备构建上下文: $BUILD_DIR"
rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR"

if [ -n "$AGENT_DIR" ] && [ -d "$AGENT_DIR" ]; then
  cp -r "$AGENT_DIR" "$BUILD_DIR/skywalking-agent"
  green "    已包含 SkyWalking agent"
else
  # 没有 agent 时给个空目录，Dockerfile 里的 COPY 不会失败
  mkdir -p "$BUILD_DIR/skywalking-agent"
  echo "    未提供 AGENT_DIR，镜像将不含链路追踪探针"
fi

declare -A JARS=(
  [wheel]="tycoon-wheel-java/target/tycoon-wheel-1.0.0.jar"
  [gateway]="tycoon-gateway/target/tycoon-gateway-1.0.0.jar"
  [user]="tycoon-user/target/tycoon-user-1.0.0.jar"
  [admin]="tycoon-admin/target/tycoon-admin-1.0.0.jar"
)
declare -A DOCKERFILES=(
  [wheel]="Dockerfile.app"
  [gateway]="Dockerfile.gateway"
  [user]="Dockerfile.user"
  [admin]="Dockerfile.admin"
)

TARGETS="${1:-wheel gateway user admin}"

# ---------- 4. 逐个构建并推送 ----------
for name in $TARGETS; do
  jar="${JARS[$name]:-}"
  [ -z "$jar" ] && { echo "未知服务: $name"; exit 1; }
  [ -f "$PROJECT_DIR/$jar" ] || { echo "缺少 jar: $jar"; exit 1; }

  cyan "==> 构建 $name"
  cp "$PROJECT_DIR/$jar" "$BUILD_DIR/app.jar"
  docker build -q \
    --build-arg WITH_AGENT="$([ -f "$BUILD_DIR/skywalking-agent/skywalking-agent.jar" ] && echo true || echo false)" \
    -f "$PROJECT_DIR/deploy/docker/${DOCKERFILES[$name]}" \
    -t "tycoon-$name:1.0.0" "$BUILD_DIR" >/dev/null

  docker tag "tycoon-$name:1.0.0" "${REGISTRY}/tycoon-$name:1.0.0"
  docker push "${REGISTRY}/tycoon-$name:1.0.0" >/dev/null
  green "    ${REGISTRY}/tycoon-$name:1.0.0"
done

echo
green "构建完成。下一步： ./deploy/scripts/install.sh"
