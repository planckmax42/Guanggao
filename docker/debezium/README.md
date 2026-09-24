{
"connector.class": "io.debezium.connector.mysql.MySqlConnector",使用Debezium的mysql数据捕获连接器
"tasks.max": "1",mysql连接器本身只使用一个任务 所以task设置为1
"database.hostname": "127.0.0.1",Mysql地址
"database.port": "3306",Mysql端口
"database.user": "debezium",Debezium登陆的账号
"database.password": "DbzLocal_2026!",Debezium登陆的Mysql的密码
"database.server.id": "5401",Debezium读取mysql binlog使用的身份编号，人为指定
"database.ssl.mode": "disabled",//这两条都和加密有关，后续可以看看
"driver.allowPublicKeyRetrieval": "true",
"topic.prefix": "ad-platform-cdc",指定Topic前缀，Debezium会根据这个前缀生成默认的topic名称
"database.include.list": "ad_platform",采集范围限定这个数据库
"table.include.list": "ad_platform.outbox_message",采集这个表的数据变化
"include.schema.changes": "false",只向Kafka发送数据变化消息，不发送表结构变化消息
"schema.history.internal.store.only.captured.tables.ddl": "true",只记录被采集表的结构历史，用于解析binlog把值和列对应起来，改为false后会采集所有表结构
"schema.history.internal.kafka.bootstrap.servers": "127.0.0.1:9092",Kafka的端口号和地址
"schema.history.internal.kafka.topic": "ad-platform-schema-history",保留表结构历史使用的topic
"snapshot.mode": "no_data",初始化时采集表结构，不读取已有生成快照消息，随后读取binlog
"tombstones.on.delete": "false",
"heartbeat.interval.ms": "10000",心跳间隔10s 定时向默认topic发送消息 表明Debezium仍旧在工作，同时记录binlog读取进度
"key.converter": "org.apache.kafka.connect.storage.StringConverter",Kafka消息key按照字符串序类化
"value.converter": "org.apache.kafka.connect.json.JsonConverter",Kafka消息value按照JSON序类化
"value.converter.schemas.enable": "false",JSON消息是否带上数据类型说明 例如说明 3 为long 这里设置为false所以不带
"predicates": "isOutbox",声明一个判断条件,命名为isOutbox
"predicates.isOutbox.type": "org.apache.kafka.connect.transforms.predicates.TopicNameMatches",按照topic名称判断
"predicates.isOutbox.pattern": "ad-platform-cdc\\.ad_platform\\.outbox_message",匹配ad-platform-cdc.ad_platform.outbox_message
"transforms": "outbox",声明一个名为outbox的消息转换器
"transforms.outbox.type": "io.debezium.transforms.outbox.EventRouter",声明转换器类型
"transforms.outbox.predicate": "isOutbox",转换器执行判断条件
"transforms.outbox.table.field.event.id": "event_id",将event_id放入Kafka header的id行
"transforms.outbox.table.field.event.key": "message_key",设置Kafka消息key
"transforms.outbox.table.field.event.payload": "payload",将payload设置为消息正文
"transforms.outbox.table.fields.additional.placement": "message_type:header:type",将message_type放入Kafka header的type行
"transforms.outbox.table.expand.json.payload": "true",将json内容解析出来，再作为消息正文输出，避免把整个json当作普通字符串
"transforms.outbox.route.by.field": "topic",直接读取topic列字段作为路由字段
"transforms.outbox.route.topic.replacement": "${routedByValue}",不加任何前缀，直接作为最终topic
"transforms.outbox.table.op.invalid.behavior": "warn",预期表只会insert 遇到update会记录警告 跳过update 继续处理后续记录
"errors.tolerance": "none"遇到异常时，直接停止该连接，而不是跳过错误消息继续运行
}