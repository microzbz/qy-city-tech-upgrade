#!/usr/bin/env bash

set -euo pipefail

BASE_DIR="$(cd "$(dirname "$0")" && pwd)"
APP_NAME="city-tech-upgrade"
RUN_DIR="$BASE_DIR/run"
PID_FILE="$RUN_DIR/$APP_NAME.pid"

stop_pid() {
    local pid="$1"

    if ! kill -0 "$pid" >/dev/null 2>&1; then
        return 0
    fi

    kill "$pid"

    for _ in $(seq 1 30); do
        if ! kill -0 "$pid" >/dev/null 2>&1; then
            return 0
        fi
        sleep 1
    done

    kill -9 "$pid" >/dev/null 2>&1 || true
}

STOPPED=0

if [ -f "$PID_FILE" ]; then
    PID="$(cat "$PID_FILE")"
    if [ -n "$PID" ] && kill -0 "$PID" >/dev/null 2>&1; then
        stop_pid "$PID"
        echo "已停止服务，PID=$PID"
        STOPPED=1
    fi
    rm -f "$PID_FILE"
fi

PIDS="$(pgrep -f "$APP_NAME-.*\\.jar" || true)"
if [ -n "$PIDS" ]; then
    for PID in $PIDS; do
        stop_pid "$PID"
        echo "已停止服务，PID=$PID"
        STOPPED=1
    done
fi

if [ "$STOPPED" -eq 0 ]; then
    echo "服务未运行"
    exit 0
fi

echo "停止完成"
