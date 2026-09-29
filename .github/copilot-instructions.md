# GitHub Copilot 项目指令

## 项目定位
ydsz-cloud（云顶数字开发平台）是云顶数字内部企业级微服务开发平台，覆盖 9 大业务引擎模块。

技术栈：Java 21 + Spring Boot 4 + Spring Cloud 2025.1.2 + MyBatis-Plus + PostgreSQL + Redis

## 架构规则（严格）

### DDD 分层
- 业务模块使用 `api/domain/infra/server/app/web` 六层架构
- 依赖方向严格单向：web → server → domain ← infra
- Entity 仅定义在 `domain/entity/` 下，禁止 infra 自建 DO/PO/Entity 副本
- Repository 接口在 domain 层，实现在 infra 层，返回 VO 不返回 Entity

### 公共能力复用（禁止重复造轮子）
| 需求 | 必须用 | 禁止 |
|------|--------|------|
| JSON 序列化 | `com.njydsz.common.json.JsonUtils` | import Jackson |
| Excel 读写 | `com.njydsz.common.excel.ExcelFacade` | import Apache POI |
| 日期格式化 | `com.njydsz.common.util.DateUtils` | DateTimeFormatter |
| 敏感信息脱敏 | `com.njydsz.common.util.MaskUtils` | substring |
| 缓存操作 | `com.njydsz.common.cache.YdszCache` | ConcurrentHashMap |
| i18n 消息 | `I18n.message()` 或注入 `I18nMessages` | MessageSource 直连 |
| ID 生成 | `SnowflakeIdGenerator` | UUID.randomUUID() |

## 编码红线速查

### 阻断级（P0，不可违反）
- 文件编码 UTF-8 无 BOM
- 禁 FQN、禁 import *、禁直接 import Jackson/Netty
- 表名 ydsz_ 前缀、entity 禁 DO 后缀
- equals+hashCode 成对覆盖
- 金额用 BigDecimal，布尔字段 is 前缀
- 日志 {} 占位符，禁 System.out / printStackTrace
- 异常走 i18n key，禁硬编码中文/英文
- Controller 返回 `YdszResponse<T>`

### 命名示例
```java
// 正确
private Boolean isDeleted;      // DB: is_deleted
BigDecimal price = new BigDecimal("19.99");
@TableName("ydsz_user_config")

// 错误
private Boolean deleted;        // 缺 is 前缀
double price = 19.99;           // 精度问题
@TableName("user_config")       // 缺 ydsz_ 前缀
```

### i18n 资源文件
文件名：`{module}-messages_xx_XX.properties`
位置：`src/main/resources/i18n/`
禁用裸 `messages.properties`

## 已废弃技术栈
- Flyway / Liquibase（数据库变更通过 sqls/ 目录手动管理）
- Druid 连接池（使用 HikariCP）
- 原生 Apache POI（使用 ExcelFacade）
- 直接使用 RedisTemplate（通过 StringOps/HashOps 封装）
- new ThreadPoolExecutor（使用 ydsz-common-thread）

## 提交前验证
修改任何代码后，必须执行目标模块 `mvn compile` 确认零编译错误。
