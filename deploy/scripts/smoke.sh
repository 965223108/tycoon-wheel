#!/usr/bin/env bash
# =============================================================
# 冒烟测试：验证玩家端、后台、网关鉴权、注册中心
# 退出码：0 全部通过 / 1 有失败项
# =============================================================
set -uo pipefail

GW="${GATEWAY_URL:-http://127.0.0.1:8090}"
NACOS="${NACOS_ADDR:-127.0.0.1:8848}"
ADMIN_USER="${ADMIN_USER:-admin}"
ADMIN_PASS="${ADMIN_PASS:-admin123}"

pass=0; fail=0
ok()   { printf '  \033[32m✓\033[0m %s\n' "$*"; pass=$((pass+1)); }
bad()  { printf '  \033[31m✗\033[0m %s\n' "$*"; fail=$((fail+1)); }
check_code() {  # check_code <描述> <期望码> <实际码>
  [ "$2" = "$3" ] && ok "$1 -> $3" || bad "$1 -> $3（期望 $2）"
}

echo "=== 1. 玩家端接口 ==="
c=$(curl -s -o /dev/null -w '%{http_code}' --max-time 10 "$GW/api/config" || echo 000)
check_code "GET /api/config" 200 "$c"
printf '     /api/instance: '; curl -s --max-time 8 "$GW/api/instance"; echo
printf '     /api/stats   : '; curl -s --max-time 8 "$GW/api/stats"; echo

echo
echo "=== 2. 网关鉴权（未带 token 应 401）==="
c=$(curl -s -o /dev/null -w '%{http_code}' --max-time 8 "$GW/api/admin/overview" || echo 000)
check_code "GET /api/admin/overview 无 token" 401 "$c"
c=$(curl -s -o /dev/null -w '%{http_code}' --max-time 8 "$GW/admin/index.html" || echo 000)
check_code "GET /admin/index.html（登录页需公开）" 200 "$c"

echo
echo "=== 3. 后台登录 ==="
LOGIN=$(curl -s --max-time 10 -X POST "$GW/api/admin/auth/login" \
  -H 'Content-Type: application/json' \
  -d "{\"username\":\"$ADMIN_USER\",\"password\":\"$ADMIN_PASS\"}")
TOKEN=$(echo "$LOGIN" | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')
[ -n "$TOKEN" ] && ok "登录成功，JWT 长度 ${#TOKEN}" || bad "登录失败：$(echo "$LOGIN" | head -c 120)"

echo
echo "=== 4. 后台接口（带 token）==="
if [ -n "$TOKEN" ]; then
  body=$(curl -s --max-time 10 -H "Authorization: Bearer $TOKEN" "$GW/api/admin/overview")
  echo "     overview: $(echo "$body" | head -c 160)"
  echo "$body" | grep -q '"userTotal"' && ok "概览接口返回正常" || bad "概览接口异常"
  c=$(curl -s -o /dev/null -w '%{http_code}' --max-time 10 -H "Authorization: Bearer $TOKEN" "$GW/api/admin/segments")
  check_code "GET /api/admin/segments" 200 "$c"
  s=$(curl -s --max-time 10 -H "Authorization: Bearer $TOKEN" "$GW/api/admin/segments/percent-sum")
  echo "     概率合计: $s"
fi

echo
echo "=== 5. 错误密码应被拒 ==="
c=$(curl -s -o /dev/null -w '%{http_code}' --max-time 8 -X POST "$GW/api/admin/auth/login" \
  -H 'Content-Type: application/json' -d "{\"username\":\"$ADMIN_USER\",\"password\":\"wrong-password\"}")
check_code "POST 错误密码" 401 "$c"

echo
echo "=== 6. 注册中心服务列表（应只有应用名）==="
svc=$(curl -s --max-time 8 "http://$NACOS/nacos/v1/ns/service/list?pageNo=1&pageSize=30&namespaceId=public")
echo "     $svc"
echo "$svc" | grep -q 'providers:' && bad "发现接口级注册（providers:xxx）" || ok "仅应用名，无接口级注册"

echo
echo "=========================================="
printf '通过 %d 项，失败 %d 项\n' "$pass" "$fail"
[ "$fail" -eq 0 ] && { printf '\033[32m冒烟全部通过\033[0m\n'; exit 0; } || { printf '\033[31m存在失败项\033[0m\n'; exit 1; }
