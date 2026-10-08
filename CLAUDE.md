# CLAUDE.md — ydsz-cloud AI 编码规则

> **本文件为 Claude Code / Cursor / Windsurf / Aider 等 AI 编码工具的 `always` 规则。**
> 任何 AI 编码助手在本项目中编程时，都必须遵守本文件中的规则。
>
> **规范版本**: v26.10.08-v2（190 条红线规则，P0=64 P1=89 P2=24）
> **完整规则源**: `docs/ai-rules/shared-rules.yaml`（单一权威源）
> **编码规范参考**: `docs/云顶编码规范.md`（v1.0.16）

---

## 项目速览

| 维度 | 描述 |
|------|------|
| 产品 | ydsz-cloud（云顶云平台）— 企业级微服务开发平台 |
| 语言 | Java 21（启用虚拟线程 + 密封类 + record 模式匹配）|
| 框架 | Spring Boot 4.1.0 + Spring Cloud 2025.1.2 + Spring Cloud Alibaba 2025.1.0.0 |
| ORM | MyBatis-Plus 3.5.16 + ydsz-common-jdbc 行/列权限增强 |
| 数据库 | PostgreSQL 18（HikariCP 连接池，**Flyway 版本管理**）|
| 缓存 | Redis（Redisson 4.6.1），本地缓存通过 ydsz-common-cache（W-TinyLFU）|
| 消息队列 | RocketMQ 2.3.1 + ydsz-common-queue 6 种 MQ 引擎封装 |
| 网关 | ydsz-gateway（WebFlux 响应式），12 个全局过滤器 |
| 服务注册 | Nacos（注册中心 + 配置中心）|
| API 文档 | SpringDoc OpenAPI 3 + Knife4j 4.5 |
| 对象映射 | MapStruct 1.6.3 |
| 构建 | Maven 3.9+，checkstyle 强制（`checkstyle.skip=false`）|
| 部署 | Docker + Docker Compose |

---

## 模块拓扑

```
ydsz-cloud/
├── ydsz-common/          # 公共能力底座（30 子模块，L1-L6 分层，不独立部署）
├── ydsz-gateway/         # API 网关（WebFlux，单模块，端口 9000）
├── ydsz-system/          # 系统引擎（参数/字典/多租户/全局搜索，端口 9001）
├── ydsz-userinfo/        # 身份引擎（RBAC，端口 9002）
├── ydsz-message/         # 消息引擎（全渠道通知，端口 9004）
├── ydsz-workflow/        # 流程引擎（自研工作流，端口 9005）
├── ydsz-cronjob/         # 任务引擎（分布式调度，端口 9006）
├── ydsz-literule/        # 规则引擎（DMN/决策表，端口 9007）
├── ydsz-nextwiki/        # 文件引擎（网盘知识库，端口 9003）
├── ydsz-agent/           # 智能引擎（AI 智能体，端口 9008）
└── ydzs-generator/       # 代码生成器（端口 9090）
```

> **DDD 分层（业务模块）**：`api` / `domain` / `infra` / `server` / `app` / `web`
> （gateway 为单模块 reactive 栈，不拆分 DDD 层）
>
> **模块内部依赖关系（基于 pom 实际结构）**：
> - `domain` 位于中心，零内部模块依赖，仅引用 common
> - `infra` 唯一逆向依赖 `domain`（实现 Repository 接口）
> - `server` 仅依赖 `domain`（不依赖 infra，遵循依赖倒置）
> - `web` 同时依赖 `server` + `infra` + `domain`（组合根/接线板）
> - `app` 依赖 `domain`（移动端入口，按需启用）
> - `api` 以 provided 反向依赖 `domain`（Feign 契约）
>
> 详细分层规范见下方「DDD 分层编码规范」章节。

### ydsz-common 子模块分层

| 层级 | 子模块 | 定位 |
|------|--------|------|
| **L1（零依赖）** | json, util, cache, excel, core-base | 纯 JDK，可被任何层引入 |
| **L2（Spring 基础）** | core, domain, exception | Spring Context 依赖，无外部中间件 |
| **L3（中间件客户端）** | jdbc, redis, lock, tenant, thread | 依赖数据库/Redis 等中间件客户端 |
| **L4（跨服务通信）** | feign, notify, queue, event, audit, safe, auth, netty, socket, file | 涉及跨进程通信或安全 |
| **L5（复合能力）** | sentry, config, search, docs, app, web | 组合下层能力形成业务场景 |

---

## 编码红线速查（P0 强制，53 条）

### 文件与 Import

| 规则 | 要求 |
|------|------|
| CODE-001 | 所有源文件 UTF-8 无 BOM（AI 生成文件后必须确认编码）|
| IMPORT-001 | **禁止行内 FQN**，所有类型通过 import 声明 |
| IMPORT-002 | **禁止通配符 import**（`import xxx.*`）|
| IMPORT-003 | **禁止未使用的 import** |
| IMPORT-004 | 业务代码禁止直接 import Jackson/Fastjson/Gson，必须用 `com.njydsz.common.json.JsonUtils` |
| IMPORT-005 | 禁止直接 import Netty，必须通过 ydsz-common-netty 封装 |

### 命名

| 规则 | 要求 |
|------|------|
| NAME-001 | entity 包下**禁止 DO 后缀**，直接用业务名 |
| NAME-002 | 数据库表名必须以 `ydsz_` 前缀（`@TableName("ydsz_xxx")`）|
| DB-001 | 排序字段统一命名 `sort`（禁用 `sort_order`/`sortOrder`）|
| DB-007 | 布尔字段列**必须 `SMALLINT`**，禁止 `BOOLEAN`/`BOOL`/`TINYINT(1)` |
| DB-008 | **ID 类字段统一 `VARCHAR(36)`**，Java 实体 `MpBaseEntity<String>` + 雪花ID转String；禁止 `MpBaseEntity<Long>` / `BIGINT` 自增 / 其他 VARCHAR 宽度 |

### OOP

| 规则 | 要求 |
|------|------|
| OOP-001 | 覆盖 equals **必须同时覆盖** hashCode |
| OOP-002 | 包装类比较**必须用 equals**，禁止 `==` |
| OOP-003 | 浮点金額**必须用 BigDecimal**，禁止 double/float |
| OOP-006 | 布尔字段**必须 `is` 前缀**，数据库列**必须 `SMALLINT`**（Java `isDeleted` ↔ DB `is_deleted SMALLINT`，禁用 `BOOLEAN`）|

### 异常与日志

| 规则 | 要求 |
|------|------|
| LOG-001 | **禁止空 catch 块**（吞异常）|
| LOG-002 | 日志**必须用 {} 占位符**，禁止字符串拼接 |
| LOG-003 | **禁止 System.out/err** 和 `e.printStackTrace()` |

### 日期与集合

| 规则 | 要求 |
|------|------|
| DATE-001 | **禁止 SimpleDateFormat 作为共享变量** |
| DATE-002 | 使用 java.time.* API，禁止 Date/Calendar |
### 配置管理

| 规则 | 要求 |
|------|------|
| CONFIG-004 | **业务模块自定义配置必须使用 `ydsz.{module}.*` 前缀**，禁止裸 `{module}.*` 作前缀（如 `generator.output-dir` → `ydsz.generator.output-dir`），`@ConfigurationProperties(prefix)` 和 `@Value` 中的 key 必须同步追加 `ydzs.` 前缀 |

### DDD 分层

| 规则 | 要求 |
|------|------|
| DDD-001 | 依赖方向必须**单向**：web → server → domain ← infra，禁止反向依赖 |
| DDD-004 | Entity 仅在 `{module}-domain/entity/` 下定义，禁止 infra 自建 DO/PO 替身 |
| DDD-007 | infra 层**必须通过 Converter 转换后返回 VO**，禁止将 PO 直接返回给 server 层 |
| DDD-008 | **【新 P0】** 所有业务层 Entity 必须继承 MpBaseEntity（或 MpBaseIdEntity/MpBaseAuditEntity），禁止覆盖平台基础字段（id/status/isDeleted/tenantId/sort/revision/审计四字段），禁止自建持久化基类，禁止 domain/infra 双层 entity 分层 |

### 异常体系

| 规则 | 要求 |
|------|------|
| EXCEPT-001 | 业务异常**必须继承 AbstractYdszException**，使用枚举错误码 |
| EXCEPT-002 | 异常消息**必须走 i18n key**，禁止硬编码中文/英文 |
| EXCEPT-003 | 禁止new `new RuntimeException()`，必须使用业务异常类 |

### 国际化

| 规则 | 要求 |
|------|------|
| I18N-001 | 资源文件**必须带模块前缀**（`{module}-messages*.properties`），禁止裸 `messages.properties` |
| I18N-002 | **禁止 Service/Controller 层硬编码中文文案**，必须走 `I18n.message()` 或注入 `I18nMessages` |

---

## 公共能力使用约束（关键）

业务模块使用以下能力时，**必须通过 common 封装**，禁止自行实现或使用第三方直连：

| 能力 | 必须使用 | 禁止 |
|------|---------|------|
| JSON 序列化 | `JsonUtils`（ydsz-common-json）| 直接 import Jackson/Fastjson |
| Excel 处理 | `ExcelFacade`（ydsz-common-excel）| 直接 import Apache POI |
| 日期格式化 | `DateUtils`（ydsz-common-util）| 直接使用 DateTimeFormatter |
| 敏感信息脱敏 | `MaskUtils`（ydsz-common-util）| 自行 substring + `*` |
| 缓存 | `YdszCache`（ydsz-common-cache）| ConcurrentHashMap 手写 TTL |
| HTTP 请求 | `RestTemplateUtils` | 自行 new RestTemplate |
| ID 生成 | `SnowflakeIdGenerator` | UUID.randomUUID() 作为主键 |
| 密码哈希 | `PwdUtils` | 直接使用 BCryptPasswordEncoder |
| 安全加密 | `CryptoUtils`/`DigestUtils` | 自行实现 AES/RSA/SM4 |
| 通知推送 | `NotifyHelper`（ydsz-common-notify）| Feign 直接调用通知服务 |
| 分布式锁 | `LockUtils`（ydsz-common-lock）| 自行实现 Redisson 看门狗 |
| 分布式 ID | `IdGenerator` | UUID 或数据库自增 |
| 本地缓存 | `YdszCache.newBuilder()` | Caffeine 直连 |
| Map 转换 | MapStruct 接口 | BeanUtils.copyProperties |
| Feign 调用 | `@FeignClient` + ydsz-common-feign | 自行配置 Feign |
| 文件存储 | `IFileStorageProvider`（ydsz-common-file）| 直接 SDK 调用 MinIO/OSS |
| WebSocket | `StompMessageSender`（ydsz-common-socket）| 直连 SimpMessagingTemplate |
| 文档处理（解析/安全扫描/PII） | `DocumentService`（ydsz-common-docs，YDIZ-DOCS-001 P0）| 直接 import PDFBox / POI / Jsoup |
| PDF 水印 | `PdfWatermarkApplier`（ydsz-common-docs）| 直接 import PDFBox |
| 图片处理（缩放/裁剪/水印） | `ImageProcessor`（ydsz-common-file）| 直接 import Java AWT |
| 审计日志 | `@Audit` AOP（ydsz-common-audit）| 手动 insert 审计表 |

---

## DDD 分层编码规范

### 依赖方向（强制，基于项目实际 pom 结构）

```
web (Composition Root)
 ├──→ server ──→ domain
 ├──→ infra ──→ domain
 └──→ domain

api ──→ domain（provided scope，对外暴露 Feign 契约）
app ──→ domain（移动端入口基座，大部分业务模块未启用）
```

**核心规则**：
- `domain` 是分层中心，**零项目内部模块依赖**（只依赖 common），定义 Entity / Repository 接口 / VO / Query
- `infra` 唯一逆向依赖 `domain`（实现 Repository 接口，基于依赖倒置原则）
- `server` **不依赖** `infra`（注释：P1-2 已整改），通过注入 domain 的 Repository 接口编程
- `web` 是**组合根（Composition Root）**：同时依赖 `server` + `infra` + `domain`，由 Spring 自动装配将 infra 的 Repository 实现注入到 server 层的 Service 中
- `app` 依赖 `domain`，封装移动端入口（健康检查、OpenAPI 配置），大多数业务模块不启用
- `api` 以 `provided` 作用域反向依赖 `domain`（仅引用 DTO/枚举定义 Feign 契约）

> **为什么 web 同时依赖 infra**：Spring 需要 infra 的 `@Repository` Bean 在同一个 ApplicationContext 中才能注入到 server 层。web 入口负责扫描 infra 包，相当于"接线板"。server 层仅面向 domain 接口编程，不感知 infra 存在。

### 各层职责

| 层 | 包名 | 内部依赖 | 职责 |
|----|------|----------|------|
| `web` | `com.njydsz.module.web` | server + infra + domain + common | Controller + ExceptionFilter + 请求响应适配 + **Bean 装配** |
| `server` | `com.njydsz.module.server` | domain + common | 业务编排 Service + 缓存 + 事务 |
| `domain` | `com.njydsz.module.domain` | 仅 common 模块 | Entity + VO + Repository **接口** + Query + DTO + Enums |
| `infra` | `com.njydsz.module.infra` | domain + common-jdbc | RepositoryImpl + Mapper + Converter |
| `api` | `com.njydsz.module.api` | domain(**provided**) + common-feign | Feign Client 接口 + Fallback |
| `app` | `com.njydsz.module.app` | domain + common-app | 移动端健康检查 + OpenAPI 配置（按需启用）|

### Entity / PO 规范（【强制】继承 MpBaseEntity，YDIZ-DDD-008）

> **规范版本 v26.10.01-v25 +1 P0：所有业务模块 Entity 必须继承 `MpBaseEntity`**（YDIZ-DDD-008 阻断级）。

**平台基础字段清单**（共 10 个，由 `MpBaseEntity` 提供，业务模块禁止覆盖）：

| Java 字段 | DB 列 | 说明 |
|-----------|--------|------|
| `id` | `id` | 主键 |
| `sort` | `sort` | 排序权重 |
| `status` | `status` | 状态标识 |
| `revision` | `revision` | 乐观锁版本号 |
| `tenantId` | `tenant_id` | 租户 ID |
| `isDeleted` | `is_deleted` | 逻辑删除 |
| `createdBy` | `created_by` | 创建人 |
| `createdAt` | `created_at` | 创建时间 |
| `updatedBy` | `updated_by` | 更新人 |
| `updatedAt` | `updated_at` | 更新时间 |

**【强制】继承规则**：
- Entity 定义在 `domain/entity/` 包下，直接携带 `@TableName` `@TableField` 等 ORM 注解
- **必须继承** `com.njydsz.common.jdbc.entity.MpBaseEntity<String>`（或 `MpBaseEntity<Long>`）
- **禁止**自建 `AbstractXxxEntity` / `BaseXxxEntity` 绕过 `MpBaseEntity`
- **禁止**在 Entity 中声明与上述 10 个基础字段同名的属性（即使已通过继承获得）
- infra 层**不另立 DO/PO/Entity 类**，直接引用 domain 的 Entity
- RepositoryImpl 返回类型必须是 `domain/vo/` 下的 VO，通过 Converter 从 Entity 转换
- Repository 接口定义在 `domain/repository/`，实现在 `infra/repository/`，Spring 在 web 层完成装配

```java
// ✅ 正例
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_xxx")
public class Xxx extends MpBaseEntity<String> {
    @TableField("name")
    private String name;
    // 不声明 sort/status/tenantId/isDeleted 等——从 MpBaseEntity 继承
}

// ❌ 反例 — 缺少继承
public class Xxx {
    private Long id;
    // 缺少基础字段
}

// ❌ 反例 — 自建基类
public abstract class AbstractXxxEntity {
    private Long id; private String sort;
}

// ❌ 反例 — 覆盖基础字段
public class Space extends MpBaseEntity<String> {
    @TableField("tenant_id")
    private String tenantId;  // ❌ 与 MpBaseEntity 继承的 tenantId 冲突
}
```

---

## i18n 编码规范

### 资源文件命名
```
{src}/resources/i18n/{module}-messages.properties        # 默认（中文）
{src}/resources/i18n/{module}-messages_en_US.properties # 英文
{src}/resources/i18n/{module}-messages_zh_CN.properties # 中文（可选覆盖）
```

### 注入方式（按场景选择）

```java
// 方式1：Spring 托管 Bean（推荐，Service/Controller）
@Service
public class TenantService {
    private final I18nMessages i18n;
    public TenantService(I18nMessages i18n) { this.i18n = i18n; }
    public void doSomething() {
        throw new BusinessException(i18n.tenantNotFound(tenantId));
    }
}

// 方式2：静态调用（无注入场景：工具类/枚举/DTO）
String msg = I18n.message("module.scenario.key", new Object[]{param1, param2});
```

### 禁止事项
- 禁止直接使用 `MessageSource.getMessage()` 绕过 ydsz 封装
- 禁止在静态方法/枚举中 new `ReloadableResourceBundleMessageSource`
- **禁止硬编码中文/英文文案**（含异常消息、日志描述、注释中的 UI 文案）

---

## 代码风格速查

### 日志规范
```java
// 正例
log.info("用户{}操作失败，原因：{}", userId, reason);
log.warn("字典[{}]条目[{}]状态异常，跳过", dictCode, itemCode);

// 反例
log.info("用户" + userId + "操作失败");           // LOG-002 违规
System.out.println("debug");                       // LOG-003 违规
```

### 异常规范
```java
// 正例
throw new BusinessException(ExceptionCodeEnum.TENANT_NOT_FOUND, tenantId);

// 反例
throw new RuntimeException("租户不存在");           // 硬编码中文
try { ... } catch (Exception e) {}                  // 空 catch 吞异常
```

### 布尔字段命名与类型
```java
// 正例（Java + 数据库列）
@TableField("is_deleted")
private Boolean isDeleted;         // Lombok 生成 getIsDeleted()
// DDL: is_deleted SMALLINT NOT NULL DEFAULT 0

// 反例
private Boolean deleted;           // 缺少 is 前缀
// DDL: is_deleted BOOLEAN         // ❌ 必须用 SMALLINT
```

> **规范要求**：布尔字段数据库列**必须使用 `SMALLINT NOT NULL DEFAULT 0`**，**禁用 PostgreSQL `BOOLEAN` 类型**。
> 理由：跨数据库兼容、MyBatis-Plus 映射一致性、项目 ~300+ 布尔列已统一 SMALLINT。

### BigDecimal 使用
```java
// 正例
BigDecimal price = new BigDecimal("19.99");
BigDecimal tax = BigDecimal.valueOf(0.08);

// 反例
double price = 19.99;              // 精度丢失
```

### 更新日期/时间
```java
// 正例
String now = DateUtils.formatNow();
LocalDateTime parsed = DateUtils.parseLocalDateTime(str, "yyyy-MM-dd");

// 反例
DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");  // 必须走 DateUtils
```

---

## 编译与验证

```bash
# 全量构建（跳过测试，checkstyle 强制）
mvn clean package -DskipTests

# 单模块编译验证（推荐）
mvn compile -pl ydsz-system/ydsz-system-server -am

# checkstyle 检查（不可跳过，checkstyle.skip=false）
mvn checkstyle:check

# 全模块编译
mvn compile
```

> **AI 修改代码后的必做验证**：修改文件所属模块必须 `mvn compile` 零错误通过。

---

## 版本规范

- **版本格式**：`YY.MM.DD`（不含 V 前缀），如 `26.10.01-SNAPSHOT`
- **Flyway**：已启用（版本脚本目录：`data/sqls/flyway/`，命名格式 `V{YY.MM.DD}.sql`，当前基线版本 `V26.10.01`）
- **数据库变更**：通过 Flyway 版本脚本管理，脚本目录 `data/sqls/flyway/`，命名 `V{YY.MM.DD}.sql`

---

## 提交规范（AI 提交时）

```
type(scope): subject — 50 字符以内的祈使句

body（可选）：说明修改原因、影响范围、兼容性

footer（可选）：BREAKING CHANGE / Closes #issue

# type: feat|fix|refactor|docs|chore|style|test|perf|ci|revert
# scope: 模块名，如 system, userinfo, common-json
```

---

## AI 行为约束

1. 修改代码后**必须 `mvn compile` 验证**目标模块，确保零编译错误
2. 新文件创建后**必须确认 UTF-8 无 BOM** 编码
3. 生成 long/diff 等大型输出时，**严禁一次性覆盖多个文件**，必须每次仅改一个文件并验证
4. 不确定依赖边界的修改（如 DDD 层间通信），**必须先读取相关文件再决策**，禁止猜测
5. 遇到规范未覆盖的场景，**参考 `docs/云顶编码规范.md` 对应章节**
6. 任何涉及删除 `import` 的操作，**必须先 grep 全局引用**确认无其他调用
7. **禁止在不知晓方法用途的情况下去除 `@SuppressWarnings` 注解**
8. 批量重构涉及 5+ 文件时，**分批提交并标注批次序号**
