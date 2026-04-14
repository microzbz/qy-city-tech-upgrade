#!/usr/bin/env bash

set -euo pipefail

BASE_DIR="$(cd "$(dirname "$0")" && pwd)"
APP_NAME="city-tech-upgrade"
BACKEND_DIR="$BASE_DIR/backend"
TARGET_DIR="$BASE_DIR/target"
CONFIG_DIR="$BASE_DIR/config"
FALLBACK_CONFIG_DIR="$BASE_DIR/src/main/resources"
LOG_DIR="$BASE_DIR/logs"
RUN_DIR="$BASE_DIR/run"
PID_FILE="$RUN_DIR/$APP_NAME.pid"
CONSOLE_LOG="$LOG_DIR/$APP_NAME-console.log"
JAVA_XMS="${JAVA_XMS:-4g}"
JAVA_XMX="${JAVA_XMX:-6g}"

mkdir -p "$LOG_DIR" "$LOG_DIR/archive" "$RUN_DIR"

if ! command -v java >/dev/null 2>&1; then
    echo "未找到 java，请先安装并配置 JAVA_HOME/PATH"
    exit 1
fi

find_jar() {
    local jar=""

    if [ -d "$BACKEND_DIR" ]; then
        jar="$(find "$BACKEND_DIR" -maxdepth 1 -type f -name "$APP_NAME-*.jar" ! -name "*.jar.original" | sort | head -n 1)"
    fi

    if [ -z "$jar" ] && [ -d "$TARGET_DIR" ]; then
        jar="$(find "$TARGET_DIR" -maxdepth 1 -type f -name "$APP_NAME-*.jar" ! -name "*.jar.original" | sort | head -n 1)"
    fi

    printf '%s\n' "$jar"
}

find_config_dir() {
    if [ -f "$CONFIG_DIR/application.yml" ]; then
        printf '%s\n' "$CONFIG_DIR"
        return
    fi

    if [ -f "$FALLBACK_CONFIG_DIR/application.yml" ]; then
        printf '%s\n' "$FALLBACK_CONFIG_DIR"
        return
    fi

    printf '%s\n' ""
}

JAR_FILE="$(find_jar)"
ACTIVE_CONFIG_DIR="$(find_config_dir)"

if [ -z "$JAR_FILE" ]; then
    echo "未找到可启动的 JAR，期望路径：$BACKEND_DIR 或 $TARGET_DIR"
    exit 1
fi

if [ -f "$PID_FILE" ]; then
    EXISTING_PID="$(cat "$PID_FILE")"
    if kill -0 "$EXISTING_PID" >/dev/null 2>&1; then
        echo "服务已在运行，PID=$EXISTING_PID"
        exit 0
    fi
    rm -f "$PID_FILE"
fi

EXISTING_PID="$(pgrep -f "$APP_NAME-.*\\.jar" | head -n 1 || true)"
if [ -n "$EXISTING_PID" ]; then
    echo "检测到服务已在运行，PID=$EXISTING_PID"
    printf '%s\n' "$EXISTING_PID" > "$PID_FILE"
    exit 0
fi

LOGGING_OPTS=""
SPRING_CONFIG_ARG=""

if [ -n "$ACTIVE_CONFIG_DIR" ]; then
    if [ -f "$ACTIVE_CONFIG_DIR/logback.xml" ]; then
        LOGGING_OPTS="-Dlogging.config=$ACTIVE_CONFIG_DIR/logback.xml"
    fi
    SPRING_CONFIG_ARG="--spring.config.additional-location=file:$ACTIVE_CONFIG_DIR/"
fi

nohup java ${JAVA_OPTS:-} \
    -Xms"$JAVA_XMS" \
    -Xmx"$JAVA_XMX" \
    -Duser.timezone=Asia/Shanghai \
    -Dfile.encoding=UTF-8 \
    -DAPP_NAME="$APP_NAME" \
    -DLOG_HOME="$LOG_DIR" \
    ${LOGGING_OPTS} \
    -jar "$JAR_FILE" \
    ${SPRING_CONFIG_ARG} \
    >> "$CONSOLE_LOG" 2>&1 &

NEW_PID=$!
printf '%s\n' "$NEW_PID" > "$PID_FILE"

sleep 2

if kill -0 "$NEW_PID" >/dev/null 2>&1; then
    echo "启动成功"
    echo "PID: $NEW_PID"
    echo "JAR: $JAR_FILE"
    echo "JVM: -Xms$JAVA_XMS -Xmx$JAVA_XMX"
    echo "CONFIG: ${ACTIVE_CONFIG_DIR:-jar内置配置}"
    echo "LOG: $LOG_DIR"
    exit 0
fi

echo "启动失败，请检查日志：$CONSOLE_LOG"
rm -f "$PID_FILE"
exit 1
