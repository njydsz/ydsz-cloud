# ydzsz-common-locales（YDSZ 国际化基座）

> L2 核心响应与基础设施层 — 为 YDSZ 后端提供统一 i18n 能力。

## 模块定位

独立承载 YDSZ 后端的**国际化基础设施**，不依赖任何 L3+ 模块，可供异常模块（L3）、Web 基座（L6）、八大引擎业务层平等引用，不违反层级单向依赖原则（YDIZ-ARCH-001）。

## 提供的能力

| Bean / 工具 | 角色 | 适用场景 |
|------------|------|---------|
| `ydszMessageSource` | MessageSource（多模块资源聚合） | Spring 自动装配，全局 i18n 消息源 |
| `ydszLocaleResolver` | AcceptHeaderLocaleResolver | Web 请求 Locale 解析（Header + lang 参数） |
| `localeChangeInterceptor` | LocaleChangeInterceptor | `?lang=en_US` 参数切换 Locale |
| `ydszValidator` | LocalValidatorFactoryBean | JSR-303 校验注解的 i18n 消息 |
| `i18nMessages` | I18nMessages（可注入 Bean） | Service/Controller 推荐的注入方式 |
| `I18n.message()` | 静态工具 | 异常构造器、DTO、工具类（无注入能力场景） |
| `Locales.current()` | 静态工具 | 获取当前请求 Locale |

## 依赖约束

```
ydsz-common-locales (L2)
  ├── ydsz-common-core (L2, 同层允许)      — . 仅依赖 L1
  ├── spring-boot (optional)               — 提供 @ConfigurationProperties
  ├── spring-boot-autoconfigure (optional)  — 提供 @AutoConfiguration
  ├── spring-context (optional)             — 提供 MessageSource
  └── spring-webmvc (optional)              — 提供 LocaleResolver（Web 按需）
```

## 快速使用

### 静态工具（异常 / DTO / 工具类）

```java
// 异常构造器中
throw new BusinessException(ErrorCode.USER_NOT_FOUND)
    .msg(I18n.message("userinfo.user.not.found", new Object[]{userId}));

// 带指定 Locale
String english = I18n.message("userinfo.welcome", null, Locale.US);
```

### 可注入工具（Service / Controller — 推荐）

```java
@Service
public class UserService {
    private final I18nMessages i18n;

    public UserService(I18nMessages i18n) {
        this.i18n = i18n; // Lombok 推荐构造器注入
    }

    public void validate(User user) {
        if (user == null) {
            throw BusinessException.of(CoreExceptionCode.PARAM_ERROR)
                .msg(i18n.resolve("user.null"));
        }
    }
}
```

## 配置项（ydsz.i18n.*）

| 配置键 | 默认值 | 说明 |
|-------|--------|------|
| `ydsz.i18n.basename` | 见 I18nProperties.DEFAULT_BASENAMES | 逗号分隔多资源前缀 |
| `ydsz.i18n.encoding` | UTF-8 | 资源文件编码 |
| `ydsz.i18n.devCacheSeconds` | 0 | 开发环境缓存秒数 |
| `ydsz.i18n.prodCacheSeconds` | 3600 | 生产环境缓存秒数 |
| `ydsz.i18n.fallbackToSystemLocale` | false | 是否回退系统 Locale |
| `ydsz.i18n.defaultLocale` | zh_CN | 默认 Locale |
| `ydsz.i18n.fallbackMessage` | 未找到对应的提示信息: {0} | 缺省 i18n 提示 |
| `ydsz.i18n.supportedLocales` | [zh_CN, en_US, zh_TW] | 支持的语言列表 |
| `ydsz.i18n.langParamName` | lang | URL 参数名 |
| `ydsz.i18n.validateOnStartup` | true | 启动时校验 key 存在性 |
| `ydsz.i18n.wildcard-scan-enabled` | true | 是否启用 classpath 通配符自动扫描（发现新增模块资源） |
| `ydsz.i18n.missing-translation-log-enabled` | true | 是否启用翻译缺失 WARN 日志告警 |
| `ydsz.i18n.missing-translation-log-buffer-capacity` | 200 | 翻译缺失日志节流器缓冲区容量 |
| `ydsz.i18n.negative-cache-enabled` | true | 是否启用负缓存（避免已知 miss 的 key 重复遍历 basename） |
| `ydsz.i18n.negative-cache-capacity` | 500 | 负缓存的 LRU 容量上限 |
| `ydsz.i18n.metadata-api-enabled` | false | 是否启用 i18n 元数据 REST API（Web 环境可用） |

## 命名规范

消息 key 命名约定：`{module}.{feature}.{语义}`

- `{module}` — 业务模块（如 userinfo、order、product）
- `{feature}` — 功能域（如 user、role、permission）
- `{语义}` — 具体提示（如 not.found、already.exists）

示例：`userinfo.user.not.found`、`role.permission.denied`、`order.payment.expired`

详见云顶编码规范 § 国际化规范（YDIZ-I18N-001 / YDIZ-I18N-002）。

## 与 ydsz-common-exception 的关系

`ydsz-common-locales`（L2）被 `ydsz-common-exception`（L3）**单向依赖**（合法方向）。异常体系通过 `MessageSourceHolder.resolve()` 静态桥接消费 i18n 消息， 异常模块不感知 Spring MessageSource 的实现细节。

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

