#!/usr/bin/env bash
# =============================================================
# 查看整套服务状态
# =============================================================
set -uo pipefail
STACK_NAME="${STACK_NAME:-tycoon}"

echo "=== 副本状态 ==="
docker stack services "$STACK_NAME" --format '  {{.Name}}  {{.Replicas}}' 2>/dev/null | sort

echo
echo "=== 未就绪 ==="
any=0
while read -r n r; do
  up=${r%%/*}; want=${r##*/}
  if [ "$up" != "$want" ]; then echo "  $n $r"; any=1; fi
done < <(docker stack services "$STACK_NAME" --format '{{.Name}} {{.Replicas}}' 2>/dev/null)
[ "$any" = "0" ] && echo "  （无，全部就绪）"

echo
echo "=== 业务容器资源 ==="
docker stats --no-stream --format '  {{.Name}} {{.CPUPerc}} {{.MemUsage}} {{.MemPerc}}' 2>/dev/null \
  | grep -E "tycoon_(app|gateway|user|admin)\." || true

echo
echo "=== 宿主内存 ==="
free -m | head -2 | sed 's/^/  /'
