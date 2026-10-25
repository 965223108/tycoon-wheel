#!/bin/sh
# Swarm 容器里有多个网卡（overlay eth0 + gwbridge eth1 + ingress）。
# Dubbo 默认可能选中 172.18.x 的 gwbridge 地址，导致其它服务连不上。
# 这里显式取 eth0（overlay）的 IP 作为注册地址。
set -e

IP=""
if command -v ip >/dev/null 2>&1; then
  IP=$(ip -4 -o addr show eth0 2>/dev/null | awk '{print $4}' | cut -d/ -f1)
fi
if [ -z "$IP" ]; then
  IP=$(hostname -i 2>/dev/null | awk '{print $1}')
fi

if [ -n "$IP" ]; then
  export DUBBO_IP_TO_REGISTER="$IP"
  export DUBBO_IP_TO_BIND="$IP"
fi

echo "[entrypoint] DUBBO_IP_TO_REGISTER=${DUBBO_IP_TO_REGISTER:-<auto>}"

if [ "$SW_AGENT_ENABLED" = "true" ] && [ -f /app/skywalking-agent/skywalking-agent.jar ]; then
  echo "[entrypoint] SkyWalking agent enabled: $SW_AGENT_NAME -> $SW_AGENT_COLLECTOR"
  exec java $JAVA_OPTS \
    -javaagent:/app/skywalking-agent/skywalking-agent.jar \
    -Dskywalking.agent.service_name="$SW_AGENT_NAME" \
    -Dskywalking.collector.backend_service="$SW_AGENT_COLLECTOR" \
    -jar /app/app.jar
else
  exec java $JAVA_OPTS -jar /app/app.jar
fi
