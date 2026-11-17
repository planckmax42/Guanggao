# 本机三节点 Elasticsearch

三个节点均为 master 候选和 data 节点，自带协调能力；HTTPS 端口为 9200、9201、9202。
每节点独立数据卷，容器内存 2 GB、堆 1 GB，总计预留 6 GB。
启用认证、HTTP TLS 和节点间 TLS。证书初始化容器结束后，三个 ES 容器并行启动。
固定 ES 8.13.4 以匹配当前项目客户端；这是一套本机演练配置，并非最新生产版本基线。
同一宿主机上的容器不能提供宿主机级容灾；不会自动迁移本机旧 ES 的索引。

## 首次启动

在项目根目录执行。先停止占用 9200 的本机 ES，并提高宿主机 mmap 限制：

```bash
sudo systemctl stop elasticsearch.service
sudo sysctl -w vm.max_map_count=1048576
read -rsp '设置 ES elastic 用户密码: ' ELASTIC_PASSWORD
export ELASTIC_PASSWORD
export ES_INITIAL_MASTER_NODES=es01,es02,es03
docker compose -f docker/docker-elasticsearch.yml up -d --wait --wait-timeout 300
```

`sysctl -w` 重启后失效；需要持久化时将 `vm.max_map_count=1048576` 写入 `/etc/sysctl.d/` 下的配置文件。
请保存首次设置的密码；已有数据卷时，仅改变环境变量不会修改已存在用户的密码。

确认三个节点均已加入，再移除首次引导参数并重新应用配置：

```bash
docker compose -f docker/docker-elasticsearch.yml exec es01 bash -c \
  'curl --fail --cacert config/certs/ca/ca.crt -u "elastic:${ELASTIC_PASSWORD}" "https://localhost:9200/_cat/nodes?v"'
unset ES_INITIAL_MASTER_NODES
docker compose -f docker/docker-elasticsearch.yml up -d --wait --wait-timeout 300
```

后续启动保持 `ES_INITIAL_MASTER_NODES` 未设置；只有创建全新集群时才使用该参数。
普通健康检查仅检查集群 API 是否可访问，不能代替上面的三节点数量检查和索引副本检查。

## 本机 Spring Boot 应用连接

导出公开 CA 证书（不是私钥）到临时目录：

```bash
docker compose -f docker/docker-elasticsearch.yml cp es01:/usr/share/elasticsearch/config/certs/ca/ca.crt /tmp/ad-platform-es-ca.crt
export ELASTICSEARCH_URIS=https://localhost:9200,https://localhost:9201,https://localhost:9202
export ELASTICSEARCH_USERNAME=elastic
export ELASTICSEARCH_PASSWORD="$ELASTIC_PASSWORD"
export SPRING_SSL_BUNDLE_PEM_ES_TRUSTSTORE_CERTIFICATE=file:/tmp/ad-platform-es-ca.crt
export SPRING_ELASTICSEARCH_RESTCLIENT_SSL_BUNDLE=es
```

从设置这些变量的终端启动应用；IDE 启动时需要设置对应环境变量。
`elastic` 仅用于本地联调，正式部署应为应用建立权限限定到候选索引/别名的独立账号。
如果应用使用自定义 ES 客户端而非 Spring Boot 自动配置，需要在自定义客户端中配置 CA 信任。

## 验证与故障演练

```bash
curl --cacert /tmp/ad-platform-es-ca.crt -u "elastic:$ELASTIC_PASSWORD" 'https://localhost:9200/_cluster/health?pretty'
curl --cacert /tmp/ad-platform-es-ca.crt -u "elastic:$ELASTIC_PASSWORD" 'https://localhost:9200/_cat/shards?v'
docker compose -f docker/docker-elasticsearch.yml stop es01
# 改访问 9201/9202，检查选主和应用查询；然后恢复节点。
docker compose -f docker/docker-elasticsearch.yml start es01
```

应用创建的索引应至少有 1 个副本，并确认副本已分配后再模拟故障。
本配置不覆盖应用的分片设置，也不创建候选索引；当前 CandidateDocument 声明了 2 个主分片。
可通过项目的候选索引重建流程生成数据，或使用快照迁移已有数据。

停止并保留数据：`docker compose -f docker/docker-elasticsearch.yml down`。
不要加 `-v`，它会删除本集群的数据卷和证书。

参考：[Elastic 官方三节点 Docker 部署](https://www.elastic.co/docs/deploy-manage/deploy/self-managed/install-elasticsearch-docker-compose)。
