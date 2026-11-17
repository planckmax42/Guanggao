#!/usr/bin/env bash
set -euo pipefail
cd /usr/share/elasticsearch

if [[ ! -f config/certs/ca/ca.crt ]]; then
    bin/elasticsearch-certutil ca --silent --pem --out config/certs/ca.zip
    unzip -o config/certs/ca.zip -d config/certs
    rm config/certs/ca.zip
fi

for node in es01 es02 es03; do
    if [[ ! -f "config/certs/${node}/${node}.crt" ]]; then
        bin/elasticsearch-certutil cert --silent --pem \
            --ca-cert config/certs/ca/ca.crt \
            --ca-key config/certs/ca/ca.key \
            --name "${node}" --dns "${node},localhost" --ip 127.0.0.1 \
            --out "config/certs/${node}.zip"
        unzip -o "config/certs/${node}.zip" -d config/certs
        rm "config/certs/${node}.zip"
    fi
done

chown -R root:root config/certs
find config/certs -type d -exec chmod 750 {} \;
find config/certs -type f -exec chmod 640 {} \;
# ES 容器用户属于 root 组，但无需读取签发证书的 CA 私钥。
chmod 600 config/certs/ca/ca.key
