#!/usr/bin/env bash
# =============================================================
# 停止 / 卸载整套服务
#
# 用法：
#   ./deploy/scripts/stop.sh          # 停止服务，保留数据卷和镜像
#   ./deploy/scripts/stop.sh --purge  # 连数据卷一起删（危险）
# =============================================================
set -euo pipefail
STACK_NAME="${STACK_NAME:-tycoon}"
RUNTIME_DIR="${RUNTIME_DIR:-/opt/tycoon-wheel}"

echo "==> 移除栈 $STACK_NAME"
docker stack rm "$STACK_NAME" 2>/dev/null || true

for _ in $(seq 1 40); do
  n=$(docker service ls --filter "name=${STACK_NAME}_" -q 2>/dev/null | wc -l)
  [ "$n" -eq 0 ] && break
  sleep 3
done
echo "    服务已移除"

if [ "${1:-}" = "--purge" ]; then
  echo "==> 删除数据卷（MySQL / ES / Nacos / Grafana / Registry）"
  for v in tycoon_mysql-data tycoon_es-data tycoon_nacos-data tycoon_grafana-data registry-data; do
    docker volume rm "$v" 2>/dev/null && echo "    删除 $v" || true
  done
  echo "==> 删除运行时目录 $RUNTIME_DIR"
  sudo rm -rf "$RUNTIME_DIR"
  echo
  echo "已彻底清理。要重新部署请执行 ./deploy/scripts/build.sh && ./deploy/scripts/install.sh"
else
  echo
  echo "数据卷和镜像已保留。重新启动： ./deploy/scripts/install.sh"
fi
