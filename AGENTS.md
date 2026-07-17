# Repository Guidelines

## 项目结构与模块组织

本项目是基于 Java 17、Spring Boot 3.3 和 Maven 的广告投放平台。业务代码位于 `src/main/java/com/example/adplatform`，按 `admin`、`delivery`、`tracking`、`report` 和 `search` 等业务域组织；公共代码和基础设施分别放在 `common` 与 `infra`。配置文件及按顺序执行的数据库脚本位于 `src/main/resources`，测试代码在 `src/test/java` 中镜像生产包结构。Docker、Debezium 和可观测性配置位于 `docker/` 及根目录 Compose 文件；JMeter 资源位于 `scripts/jmeter/`。

## 构建、测试与本地开发

- `mvn clean package`：编译项目、执行默认测试并生成可执行 JAR。
- `mvn test`：运行 JUnit 5 单元测试和 Spring 测试。
- `mvn -Dtest=SearchPipelineIT test`：显式运行搜索链路集成测试，需要本地 MySQL、Redis 和 Elasticsearch。
- `mvn spring-boot:run -Dspring-boot.run.profiles=local`：使用本地配置启动 API。
- `docker compose -f docker-compose.elasticsearch.yml up -d`：启动 Elasticsearch 和 Debezium Connect。
- `docker compose -f docker-compose.logging.yml up -d`：启动 Grafana、Prometheus 和 Loki。

数据库初始化顺序见 `README.md`，压测方法见 `scripts/jmeter/README.md`。

## 编码风格与命名规范

Java 代码使用 4 个空格缩进，并保持现有格式；项目尚未配置自动格式化或静态检查工具。包名使用小写，类名使用 `PascalCase`，方法和字段使用 `camelCase`。按职责使用 `Controller`、`Service`、`ServiceImpl`、`Mapper`、`Entity`、`Request` 和 `Response` 等后缀。Controller 只负责校验、调用服务和返回结果；业务编排与事务放在 Service。不得将 Entity 直接作为 API 响应，并继续使用统一的 `Result` 返回结构。

## 测试规范

根据需要使用 JUnit 5、AssertJ、Mockito、Spring Boot Test 和 Embedded Kafka。常规测试类以 `*Tests.java` 结尾，依赖外部服务的集成测试以 `*IT.java` 结尾。新增或修复行为时，应补充对应回归测试，覆盖成功、失败及幂等性场景。项目目前没有强制覆盖率门槛。

## 提交与合并请求规范

近期历史提交多使用简短的中文说明；`DEVELOPMENT.md` 规定使用 `type: description` 格式，类型包括 `feat`、`fix`、`docs`、`refactor`、`test` 和 `chore`。建议遵循该格式，例如 `fix: 处理重复广告事件`。合并请求应说明变更范围和运维影响、列出验证命令、关联相关 Issue，并明确标注数据库或配置变更。Grafana 及看板变更需附截图，API 变更需附请求和响应示例。
