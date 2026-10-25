#!/usr/bin/env bash
ROOT=/mnt/d/agent-workplace/tycoon-wheel
fail=0

echo "=== YAML 语法 ==="
for f in \
  "$ROOT/deploy/swarm/docker-stack.yml" \
  "$ROOT/deploy/compose/docker-compose.yml" \
  "$ROOT/deploy/config/prometheus/prometheus.yml" \
  "$ROOT/deploy/config/filebeat/filebeat.yml" \
  "$ROOT/deploy/config/grafana/datasource.yml" \
  "$ROOT"/deploy/config/nacos/*.yaml
do
  if python3 -c "import yaml,sys; yaml.safe_load(open(sys.argv[1]))" "$f" 2>/dev/null; then
    echo "  ok   $(basename "$f")"
  else
    echo "  FAIL $(basename "$f")"; fail=1
  fi
done

echo
echo "=== Shell 语法 ==="
for f in "$ROOT"/deploy/scripts/*.sh "$ROOT/deploy/docker/entrypoint.sh" "$ROOT"/tools/loadtest/*.sh; do
  [ -f "$f" ] || continue
  if bash -n "$f" 2>/dev/null; then
    echo "  ok   $(basename "$f")"
  else
    echo "  FAIL $(basename "$f")"; bash -n "$f"; fail=1
  fi
done

echo
echo "=== Python 语法 ==="
if python3 -m py_compile "$ROOT/tools/loadtest/loadtest.py" 2>/dev/null; then
  echo "  ok   loadtest.py"
else
  echo "  FAIL loadtest.py"; fail=1
fi

echo
echo "=== SQL 基本检查 ==="
tables=$(grep -c 'CREATE TABLE IF NOT EXISTS' "$ROOT/deploy/database/schema.sql")
echo "  建表语句: $tables 条"
grep -o 'CREATE TABLE IF NOT EXISTS [a-z_]*' "$ROOT/deploy/database/schema.sql" | awk '{print "    - "$6}'

echo
[ "$fail" = "0" ] && echo "全部通过" || echo "存在失败项"
exit $fail
