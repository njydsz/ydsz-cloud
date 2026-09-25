# ydsz-common-locales （YDSZ 国际化基座）

> 自动装配型国际化基座（Spring Boot AutoConfiguration），**勿直接 import** 配置类 —— 引入依赖即自动生效。
>
> L2 核心响应与基础设施层 — 为 YDSZ 后端提供统一 i18n 能力。

## 模块定位

独立承载 YDSZ 后端的**国际化基础设施**，不依赖任何 L3+ 模块，可供异常模块（L3）、Web 基座（L6）、八大引擎业务层平等引用，不违反层级单向依赖原则（YDIZ-ARCH-001）。

- **引入方式**：在 `pom.xml` 中声明 `ydsz-common-locales` 依赖即可，无需任何手动 `@Import` 或配置类定义
- **显式启用**（可选）：在业务模块配置类上标注 `@EnableYdszI18n` 以明确表达启用意图（IDE 友好、便于开发者发现）

## 核心能力清单

| 类型 | 名称 | 说明 |
|------|------|------|
| Bean | `ydszMessageSource` | MessageSource（多模块资源聚合 + 通配符自动发现） |
| Bean | `ydszLocaleResolver` | AcceptHeaderLocaleResolver / UserPriorityLocaleResolver（Web 请求 Locale 解析） |
| Bean | `localeChangeInterceptor` | `?lang=en_US` URL 参数切换 Locale |
| Bean | `ydszValidator` | LocalValidatorFactoryBean（JSR-303 校验注解的 i18n 消息） |
| Bean | `i18nMessages` | I18nMessages（可注入 Bean，Service/Controller 推荐方式） |
| 静态工具 | `I18n.message()` | 异常构造器、DTO、工具类（无 Spring 注入能力场景） |
| 静态工具 | `Locales.current()` | 获取当前请求 Locale |
| 静态工具 | `Locales.withLocale()` | 临时切换 Locale 上下文执行操作 |
| 静态工具 | `I18nContextPropagator.wrap()` | 跨线程异步传播 LocaleContext |
| 常量 | `KnownLocaleTags` | 统一管理已知的 locale tag 白名单与校验 |
| SPI | `I18nBasenameProvider` | 业务模块通过 SPI 自声明非标准路径的资源前缀 |

## 依赖约束

```
ydsz-common-locales (L2)
  ├── ydsz-common-core (L2, 同层允许)      — 提供 BizContextKeys/TenantContextHolder 等核心上下文
  ├── ydsz-common-util (通过 core 传递)      — 工具类基类
  ├── spring-boot (optional)               — 提供 @ConfigurationProperties
  ├── spring-boot-autoconfigure (optional)  — 提供 @AutoConfiguration
  ├── spring-context (optional)             — 提供 MessageSource
  └── spring-webmvc (optional)              — 提供 LocaleResolver（Web 按需）
```

## 使用引入

### Maven 依赖

```xml
<dependency>
    <groupId>com.njydsz</groupId>
    <artifactId>ydsz-common-locales</artifactId>
</dependency>
```

引入后所有国际化 Bean 自动装配生效，可直接使用以下方法获取 i18n 能力。

## 使用示例

### 引入依赖

```xml
<dependency>
    <groupId>com.njydsz</groupId>
    <artifactId>ydsz-common-locales</artifactId>
</dependency>
```

引入模块后自动装配生效，可直接使用静态工具或注入 Bean 获取 i18n 能力。

### 静态工具（异常 / DTO / 工具类 — 无需注入）

```java
// 异常构造器中
throw new BusinessException(ErrorCode.USER_NOT_FOUND)
    .msg(I18n.message("userinfo.user.not.found", new Object[]{userId}));

// 带指定 Locale
String english = I18n.message("userinfo.welcome", null, Locale.US);
```

### 可注入 Bean（Service / Controller — 推荐）

```java
@Service
public class UserService {
    private final I18nMessages i18n;

    public UserService(I18nMessages i18n) {
        this.i18n = i18n; // 构造器注入
    }

    public void validate(User user) {
        if (user == null) {
            throw BusinessException.of(CoreExceptionCode.PARAM_ERROR)
                .msg(i18n.resolve("user.null"));
        }
    }
}
```

### 获取当前用户语言环境

```java
// 静态快捷方式
Locale locale = Locales.current();

// 带 Optional 包装
Optional<Locale> maybeLocale = Locales.currentOptional();

// 临时切换 Locale 上下文
Locales.withLocale(Locale.US, () -> {
    String report = reportService.generate(); // 使用英文生成报告
});
```

### 显式声明启用（可选）

```java
@SpringBootApplication
@EnableYdszI18n   // 仅用于明确声明，省略也能自动装配
public class Application { }
```

## 配置项说明（ydsz.i18n.* 前缀）

| 配置键 | 默认值 | 说明 |
|-------|--------|------|
| `ydsz.i18n.basename` | 见下方默认列表 | 逗号分隔多资源前缀（classpath: / file: 协议） |
| `ydsz.i18n.encoding` | UTF-8 | 资源文件编码 |
| `ydsz.i18n.devCacheSeconds` | 0 | 开发环境缓存秒数（0 = 不缓存，修改立即生效） |
| `ydsz.i18n.prodCacheSeconds` | 3600 | 生产环境缓存秒数 |
| `ydsz.i18n.fallbackToSystemLocale` | false | 是否回退系统 Locale（false 固定使用 defaultLocale） |
| `ydsz.i18n.defaultLocale` | zh_CN | 默认 Locale |
| `ydsz.i18n.fallbackMessage` | 未找到对应的提示信息: {0} | 缺省 i18n 提示（占位符 {0} 为消息键） |
| `ydsz.i18n.supportedLocales` | [zh_CN, en_US, zh_TW] | 支持的翻译语言列表 |
| `ydsz.i18n.langParamName` | lang | URL 语言参数名 |
| `ydsz.i18n.locale-resolver-type` | accept-header | Locale 解析器类型：accept-header / user-priority |
| `ydsz.i18n.validateOnStartup` | true | 启动时校验 key 存在性（fail-fast） |
| `ydsz.i18n.wildcard-scan-enabled` | true | 是否启用 classpath 通配符自动扫描（发现新增模块资源） |
| `ydsz.i18n.missing-translation-log-enabled` | true | 是否启用翻译缺失 WARN 日志告警 |
| `ydsz.i18n.missing-translation-log-buffer-capacity` | 200 | 翻译缺失日志节流器缓冲区容量 |
| `ydsz.i18n.negative-cache-enabled` | true | 是否启用负缓存（避免已知 miss 的 key 重复遍历 basename） |
| `ydsz.i18n.negative-cache-capacity` | 500 | 负缓存的 LRU 容量上限 |
| `ydsz.i18n.runtime-strictness` | null（使用 legacy 字段） | 运行时严格度等级：STRICT / RELAXED / OFF |
| `ydsz.i18n.metadata-api-enabled` | false | 是否启用 i18n 元数据 REST API 端点 |
| `ydsz.i18n.admin-api-enabled` | false | 是否启用 i18n 管理 REST API 端点 |

**默认 basename 列表（ydsz.common.locales.config.I18nProperties.DEFAULT_BASENAMES）：**

```
classpath:i18n/exception-messages
classpath:i18n/base-messages
classpath:i18n/config-messages
classpath:i18n/core-messages
classpath:i18n/docs-messages
classpath:i18n/jdbc-messages
classpath:i18n/lock-messages
classpath:i18n/notify-messages
classpath:i18n/redis-messages
classpath:i18n/safe-messages
classpath:i18n/search-messages
classpath:i18n/seata-messages
classpath:i18n/tenant-messages
classpath:i18n/common-web-messages
classpath:i18n/userinfo-messages
...（详见 I18nProperties 源码）
```

**新增模块资源声明方式（按优先级合并）：**

1. **通配符扫描**（默认启用）：资源文件放在 `classpath:i18n/*-messages*.properties` 路径下自动发现
2. **SPI 自声明**：实现 `I18nBasenameProvider` 接口，在 `META-INF/services/` 中注册
3. **手动配置**：在 `ydsz.i18n.basename` 中追加自定义资源前缀

## 跨线程上下文传播

Spring 的 `LocaleContextHolder` 将 Locale 绑定到当前线程的 ThreadLocal。当代码切换到子线程（`CompletableFuture`、`@Async`、线程池）时，子线程无法自动继承父线程的 Locale。

**解决方案 — `I18nContextPropagator`：**

```java
// 子线程任务中保持当前请求的 Locale
Locale requestLocale = Locales.current();
CompletableFuture<String> future = CompletableFuture.supplyAsync(
    I18nContextPropagator.wrap(() -> i18n.resolve("report.title"), requestLocale));

// Spring @Async 场景
I18nContextPropagator.wrap(this::generateReportInCurrentLocale, requestLocale).call();

// 快捷方法：自动继承当前 Locale
I18nContextPropagator.wrapWithCurrentLocale(() -> {
    // 使用父线程的 Locale 执行
});

// 在 @Async 或线程池中批量提交
ExecutorService pool = Executors.newFixedThreadPool(4);
pool.submit(I18nContextPropagator.wrapWithCurrentLocale(() -> {
    // 子线程中 Locale 自动继承
}));
```

`I18nContextPropagator.wrap()` 包装后的任务在执行前自动 `LocaleContextHolder.setLocale()`，执行后恢复原 Locale，保证不污染后续复用线程的 Locale 状态。

**注**：若不特殊处理，定时任务、MQ 消费、@Async 方法内调用 `I18n.message()` 或 `Locales.current()` 将返回 Locale.ROOT（系统默认），而非用户期望的语言。

## 负缓存优化

### 工作原理

当 `devCacheSeconds=0`（开发环境）或某个 i18n key 不存在时，每次 `I18nMessageSource` 都需要遍历所有 basename 的 Properties 文件，造成不必要的性能开销。

负缓存通过记录「key + Locale → 已确认不存在」的映射，使得后续对同一 miss key 的查询可以直接走快速路径返回，**跳过全部 basename 扫描**。

### 内部实现

- 数据结构：`LinkedHashMap<String, Boolean>` 实现 LRU（读写锁 `ReadWriteLock` 保证线程安全）
- 缓存 key 格式：`{i18nKey}|{locale.toString()}`（如 `user.not.found|en_US`）
- 默认容量：500 条（可配置 `ydsz.i18n.negative-cache-capacity`）
- 写入时机：`MessageSourceHolder.resolve()` 检测到 miss（返回值 == key）时写入

### 运行时严格度（RuntimeStrictness）

可以通过单一开关替代分散的 boolean 字段：

| 等级 | 负缓存 | 缺失节流日志 | 适用场景 |
|------|--------|------------|---------|
| STRICT | ✅ | ✅ | 生产环境（默认语义） |
| RELAXED | ✅ | ❌ | 性能敏感、翻译完备 |
| OFF | ❌ | ❌ | 纯调试 / 单元测试 |

配置方式：
```yaml
ydsz:
  i18n:
    runtime-strictness: STRICT   # 或 RELAXED / OFF
```

设置 `runtime-strictness` 后，`negative-cache-enabled` / `missing-translation-log-enabled` 两个 legacy 字段将被忽略。

## 命名规范

消息 key 命名约定：`{module}.{feature}.{语义}`

- `{module}` — 业务模块（如 userinfo、order、product）
- `{feature}` — 功能域（如 user、role、permission）
- `{语义}` — 具体提示（如 not.found、already.exists）

示例：`userinfo.user.not.found`、`role.permission.denied`、`order.payment.expired`

详见云顶编码规范 国际化规范（YDIZ-I18N-001 / YDIZ-I18N-002）。

## 与 ydsz-common-base 的关系

`ydsz-common-base`（L6）已内嵌集成 `ydsz-common-locales`，无需额外配置。Base 的 `BaseI18nConfiguration` 抽象基类在 locales 的 `LocalesAutoConfiguration` 上层封装了更友好的资源 basename 配置能力。

**使用 ydsz-common-base 的业务模块**：只需引入 `ydsz-common-base`，国际化能力即自动生效；如需要自定义 basename 覆盖默认列表，继承 `BaseI18nConfiguration` 即可。

## 与 ydsz-common-exception 的关系

`ydsz-common-locales`（L2）被 `ydsz-common-exception`（L3）**单向依赖**（合法方向）。异常体系通过 `MessageSourceHolder.resolve()` 静态桥接消费 i18n 消息，异常模块不感知 Spring MessageSource 的实现细节。

## 常见 FAQ

### Q1：引入了依赖但 `I18n.message()` 返回原始 key？

**A**：检查：
1. `MessageSourceHolder` 是否已桥接（查看启动日志 `MessageSource → MessageSourceHolder 桥接完成`）
2. `.properties` 文件是否已放置到 `classpath:i18n/` 目录或其他显式声明的 basename 路径
3. key 是否在文件中存在（注意大小写与点分命名规则）

### Q2：新增业务模块后通配符扫描未发现资源？

**A**：确认：
1. 资源文件命名符合 `{prefix}_{localeTag}.properties` 格式（如 `foo-messages_zh_CN.properties`）
2. 资源文件放在 `classpath:i18n/` 目录下（或 classpath 根目录）
3. `ydsz.i18n.wildcard-scan-enabled=true`（默认开启）
4. 如路径不符合标准约定，改用 SPI（`I18nBasenameProvider`）或手动配置 `ydsz.i18n.basename`

### Q3：跨线程异步任务中 Locale 丢失？

**A**：使用 `I18nContextPropagator.wrap()` 包装 Runnable/Callable：

```java
CompletableFuture.supplyAsync(
    I18nContextPropagator.wrap(() -> generateReport(), Locales.current()));
```

如需全局配置线程池 TaskDecorator，参考 `I18nContextPropagator` 的实现原理。

### Q4：某个 Locale 下部分 key 翻译全部缺失如何快速定位？

**A**：
1. 开启 `ydsz.i18n.validate-on-startup=true`（默认 true），启动时会自动 warn 缺失的 key
2. 若已启用管理 API（`ydsz.i18n.admin-api-enabled=true`），调用 `GET /api/admin/i18n/missing?locale=en_US` 列出缺失 key
3. 查看 MissingTranslationLogger 的 WARN 日志（节流器保证不大量刷日志）

### Q5：启用负缓存后如何监控命中率？

**A**：负缓存目前仅供内部优化，未暴露外部指标。如需调试，可在 `I18nNegativeCache.size()` 处加断点或 JMX 探针。资源文件变更后，负缓存**不会**自动失效 —— 若出现翻译更新但缓存仍标记 miss 的情况，调用 `POST /api/admin/i18n/reload` 清除。

### Q6：如何在非 Spring 容器的纯 Java 环境中使用？

**A**：本模块强依赖 Spring MessageSource + LocaleContextHolder。纯 Java 场景建议接入独立的 `ResourceBundle` 方案或使用 YDSZ 的 `ydsz-common-core`（若已提供纯 Java 兼容版本）。

### Q7：`ydsz.i18n.fallbackToSystemLocale=true` 与 `defaultLocale` 冲突吗？

**A**：不冲突。`fallbackToSystemLocale=true` 表示当请求语言不在 `supportedLocales` 中时，回退到 JVM 默认 Locale（通常取操作系统区域设置）；`defaultLocale` 是兜底中的兜底（当请求语言不在 supportedLocales 中且 fallbackToSystemLocale=false 时使用 defaultLocale）。

## IDEA 开发效率配置

### Live Template（i18n / imsg）

在 IDEA → Settings → Editor → Live Templates 中新增模板组 `YDSZ-i18n`：

| 缩写 | 模板文本 | 适用上下文 |
|------|---------|-----------|
| `i18n` | `I18n.message("$KEY$", new Object[]{$PARAM$})$END$` | Java 语句 |
| `imsg` | `i18n.resolve("$KEY$", new Object[]{$PARAM$})$END$` | Java 语句（Spring Bean） |
| `locl` | `Locales.current()` | Java 语句 |
| `wloc` | `Locales.withLocale($LOCALE$, () -> { $END$ });` | Java 语句 |

每个模板的 `variables` 设置：
- `KEY` 建议正则 `'[a-z][a-z0-9]*(\.[a-z][a-z0-9]*)+'`（匹配点分命名）
- `PARAM` 默认值 `null`
- `LOCALE` 枚举值：`Locale.SIMPLIFIED_CHINESE`, `Locale.US`, `Locale.TRADITIONAL_CHINESE`

### 开启 Find Usages on i18n keys

在 IDEA → Settings → Editor → Inlay Hints → Java → "Implicit declaration" 中勾选 `MessageSource`，以便在 `I18n.message("key")` 处直接跳转到 resource 文件对应 key。
