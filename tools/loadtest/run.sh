#!/usr/bin/env bash
# 第三轮：修正登录编码 + 多用户绕开限流压测抽奖写路径
set -u
GW=http://127.0.0.1:8090
LT3="python3 $(dirname "/mnt/d/agent-workplace/tycoon-deploy/loadtest")/loadtest.py"
: > /tmp/lt-results3.jsonl
: > /tmp/cookies.txt

echo "################ 第三轮 $(date '+%F %T') ################"

# ---------- 1. 准备 60 个压测用户 ----------
echo "[准备] 创建压测用户"
for i in $(seq 1 60); do
  curl -s -o /dev/null --max-time 10 -X POST $GW/api/register \
    -H 'Content-Type: application/json' \
    -d "{\"username\":\"ltuser$i\",\"password\":\"ltpass123\",\"nickname\":\"压测$i\"}"
done
echo "  注册完成"

# ---------- 2. 批量补积分 + 收集登录 cookie ----------
echo "[准备] 登录并收集 cookie"
for i in $(seq 1 60); do
  c=$(curl -s -i --max-time 10 -X POST $GW/api/login -H 'Content-Type: application/json' \
        -d "{\"username\":\"ltuser$i\",\"password\":\"ltpass123\"}" \
      | grep -i '^set-cookie:' | head -1 | sed 's/^[Ss]et-[Cc]ookie: //;s/;.*//')
  [ -n "$c" ] && echo "$c" >> /tmp/cookies.txt
done
echo "  拿到 cookie: $(wc -l < /tmp/cookies.txt) 个"

# ---------- 3. 补积分（直接改库，快）----------
echo "[准备] 补积分"
docker exec $(docker ps -q --filter name=tycoon_mysql | head -1) \
  mysql -utycoon -ptycoon123 tycoon -e \
  "UPDATE users SET points=100000 WHERE username LIKE 'ltuser%';" 2>/dev/null \
  && echo "  已补积分" || echo "  补积分失败（继续）"

# ---------- 4. 登录压测（中文账号，body 已 UTF-8 编码）----------
echo
echo "===== 登录（PBKDF2 20万次，CPU 密集）====="
BODY='{"username":"ltuser1","password":"ltpass123"}'
for c in 5 10 25 50; do
  $LT3 "L_login_c$c" "$GW/api/login" POST $c 20 /tmp/cookies.txt "$BODY"
done

# ---------- 5. 抽奖写路径（多用户轮转）----------
echo
echo "===== 抽奖 POST /api/spin（写路径，60 用户轮转）====="
for c in 5 10 25 50; do
  $LT3 "L_spin_c$c" "$GW/api/spin" POST $c 20 /tmp/cookies.txt '{}'
done

echo
echo "################ 第三轮结束 $(date '+%F %T') ################"
python3 - <<'PY'
import json
rows = [json.loads(l) for l in open('/tmp/lt-results3.jsonl') if l.strip()]
print("  %-14s %5s %9s %8s %8s %8s %8s" % ("用例","并发","QPS","错误%","p50","p95","p99"))
print("  " + "-" * 66)
for r in rows:
    print("  %-14s %5d %9.1f %8.1f %8.1f %8.1f %8.1f" %
          (r["name"], r["threads"], r["qps"], r["err_rate"], r["p50"], r["p95"], r["p99"]))
    if r["errors"]:
        print("       ", r["errors"][0])
PY
