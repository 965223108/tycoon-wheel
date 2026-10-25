# syntax=docker/dockerfile:1
# =============================================================
# 富豪大转盘 · 业务服务镜像（tycoon-wheel）
#
# 构建上下文要求：
#   app.jar            —— 由 mvn package 产出后重命名的可执行 jar
#   skywalking-agent/  —— 可选，SkyWalking Java Agent 解压目录
#                         没有该目录时请加 --build-arg WITH_AGENT=false
# =============================================================
FROM eclipse-temurin:21-jre-alpine

ARG WITH_AGENT=true

RUN apk add --no-cache tzdata curl wget && \
    cp /usr/share/zoneinfo/Asia/Shanghai /etc/localtime && \
    echo "Asia/Shanghai" > /etc/timezone

WORKDIR /app
COPY app.jar /app/app.jar

# 链路追踪探针（可选）
COPY skywalking-agent/ /app/skywalking-agent/

# 启用 optional-plugins：MyBatis 让 SQL 进链路，Nacos 让注册中心调用可见
RUN set -eux; \
    if [ "$WITH_AGENT" = "true" ] && [ -d /app/skywalking-agent/plugins ]; then \
      for p in apm-mybatis-3.x apm-nacos-client-2.x; do \
        cp /app/skywalking-agent/optional-plugins/${p}-plugin-*.jar /app/skywalking-agent/plugins/ 2>/dev/null || true; \
      done; \
      ls /app/skywalking-agent/plugins/ | grep -E 'mybatis|nacos' | sed 's/^/enabled: /'; \
    else \
      echo "skywalking agent not bundled"; \
    fi

# Xss512k：Dubbo 默认 200 工作线程 × 1MB 栈 = 200MB，容器里放不下
ENV JAVA_OPTS="-XX:MaxRAMPercentage=65 -XX:+UseG1GC -Xss512k -Duser.timezone=Asia/Shanghai" \
    SW_AGENT_ENABLED=false \
    SW_AGENT_NAME=tycoon-wheel \
    SW_AGENT_COLLECTOR=skywalking-oap:11800

EXPOSE 8787

ENTRYPOINT ["sh", "-c", "if [ \"$SW_AGENT_ENABLED\" = \"true\" ] && [ -f /app/skywalking-agent/skywalking-agent.jar ]; then exec java $JAVA_OPTS -javaagent:/app/skywalking-agent/skywalking-agent.jar -Dskywalking.agent.service_name=$SW_AGENT_NAME -Dskywalking.collector.backend_service=$SW_AGENT_COLLECTOR -jar /app/app.jar; else exec java $JAVA_OPTS -jar /app/app.jar; fi"]
