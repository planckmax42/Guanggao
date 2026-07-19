#!/usr/bin/env bash

set -euo pipefail

kafka_host="${KAFKA_WAIT_HOST:-127.0.0.1}"
kafka_port="${KAFKA_WAIT_PORT:-9092}"
wait_interval_seconds="${KAFKA_WAIT_INTERVAL_SECONDS:-2}"
attempt=0

while ! timeout 1 bash -c "exec 3<>/dev/tcp/${kafka_host}/${kafka_port}" 2>/dev/null; do
    attempt=$((attempt + 1))
    if ((attempt == 1 || attempt % 30 == 0)); then
        echo "Kafka ${kafka_host}:${kafka_port} is unavailable; Kafka Connect has not started yet."
    fi
    sleep "${wait_interval_seconds}"
done

echo "Kafka ${kafka_host}:${kafka_port} is available; starting Kafka Connect."
exec /docker-entrypoint.sh "$@"
