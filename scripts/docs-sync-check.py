#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
docs-sync-check: 校验模块 README 中引用的类名与源码文件一致性。

背景：ydsz-common-core 曾出现 README 描述 30+ 类、实际源码仅 18 类的
"文档漂移"问题（12 个幽灵类）。本脚本自动检测此类问题。

用法：
    python docs-sync-check.py <module-dir> [module-dir...]
    python docs-sync-check.py --repo-root <repo-root> <module-dir>...

检查规则：
    1. 提取 README.md 中反引号 `` 包裹的类名/接口名
    2. 排除 Java 关键字 / JDK 类型 / Spring 框架类型（白名单）
    3. 与源码树中的 .java 文件名比对（默认仅本模块；--repo-root 时搜索全仓库）
    4. 若 README 引用但源码不存在 → 输出"幽灵引用"并退出码 1

退出码：
    0 = 通过（或模块无 README / 无源码）
    1 = 发现幽灵引用
    2 = 用法错误
"""
import pathlib
import re
import sys

# 白名单：README 中可能引用但不在源码树中的合法外部类型（JDK / 框架 / 其他模块）
EXTERNAL_TYPES = {
    # JDK
    "String", "Integer", "Long", "Boolean", "Object", "List", "Map", "URI",
    "UUID", "Exception", "RuntimeException", "Serializable", "ThreadLocal",
    "AutoCloseable", "Supplier", "Runnable", "Optional", "ConcurrentHashMap",
    "HashMap", "Map", "Set", "Collection", "Arrays", "Class", "System",
    "Math", "Thread", "TimeUnit", "Date", "Instant", "InputStream",
    "OutputStream", "ByteArrayInputStream", "ByteArrayOutputStream", "File",
    "Path", "Files", "URL", "InputStream", "Resource",
    # SLF4J
    "MDC", "Logger", "SLF4J",
    # TTL（第三方库）
    "TransmittableThreadLocal", "TtlExecutors", "TtlRunnable",
    # Spring Core / Context
    "MessageSource", "MessageResolver", "Bean", "Configuration", "AutoConfiguration",
    "SpringMessageResolver", "Filter", "FilterRegistrationBean", "SmartInitializingSingleton",
    "ConditionalOnProperty", "ConditionalOnClass", "ConditionalOnBean",
    "EnableConfigurationProperties", "Ordered", "HttpServletRequest", "HttpServletResponse",
    "RestTemplate", "WebClient", "OkHttp", "ClientHttpRequestInterceptor",
    "HttpRequest", "ClientHttpResponse", "ClientHttpRequestExecution", "IOException",
    "BeanPostProcessor", "ApplicationContext", "ApplicationEvent", "Component",
    "EventListener", "Value", "Autowired", "Qualifier", "Primary", "Resource",
    "ContextClosedEvent", "ApplicationReadyEvent", "ApplicationFailedEvent",
    "WebServerInitializedEvent", "ApplicationEventPublisher", "TaskDecorator",
    "ThreadPoolTaskExecutor", "Async", "AsyncConfigurer", "ReactiveStringRedisTemplate",
    "RedisConnectionFactory", "RedisSerializer", "MeterRegistry", "RedisRateLimiter",
    "Mono", "Flux", "WebMvcConfigurer", "WebMvcRegistrations", "RequestCondition",
    "RequestMappingHandlerMapping", "HandlerInterceptor", "OncePerRequestFilter",
    "MultipartConfigElement", "MultipartAutoConfiguration", "SessionRepository",
    "SecurityFilterChain", "HttpEntity", "ResponseEntity", "RequestEntity",
    "HttpHeaders", "HttpStatus", "MediaType", "ContentCachingRequestWrapper",
    "ContentCachingResponseWrapper", "CorsFilter", "OpenAPI", "OpenAPICustomizer",
    "SecurityFilterChain", "UserAgentAnalyzer", "HealthIndicator", "Health",
    "ShutdownEventListener", "ShutdownEndpoint",
    # Bean Validation
    "Validated", "Min", "Max", "ConstraintViolation", "Valid",
    "BindException", "ConstraintViolationException", "DataAccessException",
    "HttpMessageNotReadableException", "HttpRequestMethodNotAllowedException",
    "HttpRequestMethodNotSupportedException", "IllegalArgumentException",
    "IllegalStateException", "LocaleChangeInterceptor", "LocaleResolver",
    "MaxUploadSizeExceededException", "MethodArgumentNotValidException",
    "MethodArgumentTypeMismatchException", "MissingRequestHeaderException",
    "MissingServletRequestParameterException", "NoHandlerFoundException",
    "NullPointerException", "MessageFormat",
    # YDSZ 其他模块（core README 中不再出现，但其他模块 README 会引用）
    "TenantContextHolder", "TenantMdcFilter", "CoreHealthIndicator",
    "FilterIgnoreProperties", "FilterIgnoreConstant", "CacheConstants",
    "TokenConstants", "YdszMessageTopics", "TypeEnum", "DataScopeType",
    "IdentityType", "ServiceType", "SensitiveData", "SensitiveUtil",
    "SensitiveDataAdvice", "TraceFilter", "RequestContextCleanupFilter",
    "WebHealthIndicator", "TenantContextWebFilter", "SensitiveDataProcessor",
    "SensitiveDataSerializer", "SensitiveDataProperties", "SensitiveDataAdvice",
    "ColumnDesensitizationContext", "ColumnDesensitizationExecutor",
    "ColumnDesensitizationRule", "AuthContext", "AuthFilter", "BaseAuthFilter",
    "AbstractAuthHandler", "AuthHandler", "AuthenticationProvider", "AuthMetrics",
    "AuthMetricsCollector", "BaseAuthInfo", "BaseExceptionHandler",
    "MvcExceptionHandler", "ValidationExceptionHandler", "WebFluxExceptionHandler",
    "JdbcExceptionHandler", "DataIntegrityExceptionHandler", "BaseResponse",
    "RequestContext", "TraceIdGenerator", "RequestHolder", "RequestIdResolver",
    "RequestIdGenerator", "RequestIdResponseFilter", "TraceIdResponseFilter",
    "SecurityHeaderFilter", "ContentCachingFilter", "GlobalResponseAdvice",
    "BaseGlobalResponseAdvice", "WebAuthFilter", "WebAuthInfo", "WebAuthHandler",
    "BaseRequestIdResponseFilter", "BaseRequestLogInterceptor",
    "BaseHttpInterceptor", "RequestLogInterceptor", "AppRequestLogInterceptor",
    "AppAuthFilter", "AppGlobalResponseAdvice", "BaseMvcConfiguration",
    "BaseCorsProperties", "BaseI18nConfiguration", "BaseOpenApiConfiguration",
    "BaseTimezoneConfiguration", "BaseTraceProperties", "BaseSecurityHeadersProperties",
    "BaseFilterOrders", "DocConstants", "DocExporter", "DocAutoConfiguration",
    "DocProperties", "DocSecurityConfiguration", "Knife4jAutoConfiguration",
    "OpenApiAutoConfiguration", "BaseAutoConfiguration", "I18nAutoConfiguration",
    "AppMvcConfiguration", "AppTimezoneConfiguration", "FlowMetrics",
    "SafeHealthIndicator", "SnowflakeUtils", "YdszAuthInfo",
    "AbstractContentCachingFilter", "BaseRequestIdResponseFilter",
    "AbstractModuleMetrics", "AbstractModuleHealthIndicator",
    "CoreMetrics", "CoreMetricsCallback", "PageConstantsInitializer",
    # 内部类（以嵌套类形式存在，非独立 .java 文件）
    "DocBasicAuthFilter",
    # 框架接口（springdoc）
    "OpenApiCustomizer", "Knife4jOpenApiCustomizer",
    # Spring MVC HTTP 消息转换器
    "AbstractGenericHttpMessageConverter", "AbstractHttpMessageConverter",
    "TenantContext", "TenantStatus", "TenantMode", "MpBaseEntity",
    "InnerInterceptor", "InnerInterceptorProvider", "MybatisPlusInterceptor",
    "MybatisPlusConfiguration", "DynamicRoutingDataSource", "ParenthesedSelect",
    "SetOperationList", "TaskDecorator", "TenantIsolationException",
    "TenantRedisKeyPrefixer", "TenantDataSourceRouter", "TenantDataSourceFilter",
    "TenantContextFeignInterceptor", "TenantHealthIndicator", "TenantMetrics",
    "TenantRateLimiter", "TenantAwareRedisKey", "TenantContextWebFilter",
    "TenantLifecycleManager", "TenantConfigProvider", "TenantProperties",
    "TenantAutoConfiguration", "CacheIsolationStrategy", "TenantAuditLogger",
    "SystemTenantContextRunner", "TenantColumn", "TenantInterceptorProvider",
    "TenantIsolationInterceptor", "RequestInterceptor", "JsonConfigBean",
    "JsonGenerator", "ObjectMapper", "ThreadLocalSnapshot", "SpringFactory",
    # 其他
    "T", "PageQuery", "UserVO", "User", "OrderResultCode", "IPage",
    "FlowTaskOperateDTO", "FlowStartProcessDTO", "FlowInstanceViewDTO",
    "FlowCommentCreateDTO", "FlowAssigneeDTO", "EmbeddedApprovalViewDTO",
    "EmbeddedApprovalActionDTO", "FlowDelegateAuthPutDTO", "FlowDelegateAuthPostDTO",
    "MsgLog", "AppInfo", "UserAccount",
}

# Java / 常见关键词（README 中出现的非类名 token）
KEYWORDS = {
    "public", "private", "static", "class", "interface", "enum", "return",
    "true", "false", "null", "void", "new", "default", "extends", "implements",
    "try", "catch", "finally", "import", "package", "throw", "throws", "this",
    "A00000", "A01001", "A01002", "A10001", "A10002", "A10003", "A10004",
    "A10005", "A10101", "A10102", "A10103", "B10201", "B10202", "A10203",
    "A10301", "A10302", "C10401", "C10402", "C10403", "C10404", "C10405",
    "C10406", "A10501", "A10502", "A10601", "A10602", "A10603", "B20001",
    "B20002", "B20003", "C10501", "C10502", "C10503", "C10601", "C10701",
    "C10702", "A20001", "A20002", "A20003", "A20101", "A20102", "A20103",
    "A20104", "A20105", "A20108", "A20109", "A20110", "A20111", "B30001",
    "B30002", "B30003", "B30004", "B30005", "B30101", "B30201", "B70001",
    "B70002", "B70003", "C99999", "zh-CN", "en-US", "ok", "error",
    "ydsz", "core", "enabled", "max-page-size", "default-page-size", "L1",
    "xlsx", "traceId", "userId", "tenantId", "requestId", "language",
    "X-Trace-Id", "X-Access-Token", "X-User-Language", "X-Identity-Type",
    "X-Service-Type", "X-Data-Scope", "X-Tenant-Id", "X-Unique-Id",
    "X-Company-Ids", "X-Dept-Ids", "X-Project-Ids", "X-Region-Ids",
    "X-Visible-Columns", "X-Editable-Columns", "X-Request-Source",
    "X-Forwarded-For", "X-Distinct-Id", "response.success", "response.error",
    # 枚举值（README 描述枚举时反引号引用）
    "WEB_SERVICE", "APP_SERVICE", "TENANT", "GROUP", "COMPANY", "DEPT",
    "USER", "PROJECT", "REGION", "CUSTOM", "YDSZ", "VISITOR",
    "ACCEPT", "HEADER", "INFO", "DENY", "ALLOW", "BLOCK", "PASS", "THROW",
    "RETURN_MASKED", "RETURN_ORIGINAL", "API", "ARITHMETIC", "IMAGE",
    "SLIDER", "BLACKLIST", "WHITELIST", "IP", "HIGH", "MEDIUM", "LOW",
    "GLOBAL", "LOCAL", "CLUSTER", "SINGLE", "MULTI", "REDIS", "NONE",
    "ACTIVE", "DELETED", "SUSPENDED", "OFFLINE", "ISOLATE_DB", "REDIS_DB",
    "TENANT_AUDIT", "KEY_PREFIX", "FIXED_WINDOW", "SLIDING_WINDOW",
    "SLIDING_LOG", "TOKEN_BUCKET", "LEAKY_BUCKET", "COUNTER", "CONCURRENCY",
    "HOT_PARAM", "SYNCHRONIZER", "DOUBLE_SUBMIT", "Lax", "Strict", "None",
    "ANY", "AUTO", "DEFAULT", "DELEGATING", "IGNORE", "ERROR", "NAME",
    "PROPERTY", "PROPERTIES", "CLASS", "REF", "READ_ONLY", "READ_WRITE",
    "WRITE_ONLY", "WRAPPER_ARRAY", "WRAPPER_OBJECT", "SNAKE_CASE",
    "KEBAB_CASE", "LOWER_CAMEL_CASE", "UPPER_CAMEL_CASE", "MINIMAL_CLASS",
    "PUBLIC_ONLY", "PROTECTED_AND_PUBLIC", "TYPE_CHECK_CACHE", "AND", "OR",
    "PING", "CheckMode", "CheckType", "PreCheckMode", "CsrfMode",
    "A01052", "B01051", "C01051", "B01004", "B02001", "B02002",
    "DEFAULT_API_DOCS_PATH", "DEFAULT_API_VERSION", "DEFAULT_GROUP_NAME",
    "DEFAULT_KNIFE4J_PATH", "OPENAPI_VERSION", "LOWEST_PRECEDENCE",
    "INTERCEPTOR_REQUEST_LOG", "REQUEST_CONTEXT_CLEANUP",
}

# 只提取代码块之外的 backtick 类名，避免示例代码干扰
CODE_BLOCK_RE = re.compile(r"```.*?```", re.DOTALL)
BACKTICK_RE = re.compile(r"`([A-Za-z_][A-Za-z0-9_]*(?:\.[A-Za-z_][A-Za-z0-9_]*)?)`")
# 变更记录章节（历史类名允许引用已删除代码）
CHANGELOG_RE = re.compile(r"##\s*变更记录.*$", re.DOTALL)
# 错误码段位（A1xxxx / B2xxxx 等）
SEGMENT_RE = re.compile(r"^[ABC][0-9]x{4,}$")


def extract_readme_types(readme_text: str) -> set:
    """提取 README 正文（非代码块、非变更记录）中反引号包裹的标识符。"""
    text = CODE_BLOCK_RE.sub("", readme_text)
    text = CHANGELOG_RE.sub("", text)
    types = set()
    for m in BACKTICK_RE.finditer(text):
        name = m.group(1)
        # 取简单类名（去掉包前缀后的最后一段）
        simple = name.split(".")[-1]
        # 跳过：小写开头（字段名/参数名）、段位模式（A1xxxx）
        if simple[:1].islower() or SEGMENT_RE.match(simple):
            continue
        types.add(simple)
    return types


def collect_source_types(module_dir: pathlib.Path, repo_root: pathlib.Path | None) -> set:
    """收集源码树中的类名；--repo-root 时搜索全仓库（排除 target 目录）。"""
    types = set()
    if repo_root is not None:
        roots = [repo_root]
    else:
        roots = [module_dir / "src" / "main" / "java"]
    for root in roots:
        if not root.exists():
            continue
        for f in root.rglob("*.java"):
            if "target" in f.parts:
                continue
            types.add(f.stem)
    return types


def check_module(module_dir: pathlib.Path, repo_root: pathlib.Path | None) -> int:
    readme = module_dir / "README.md"
    if not readme.exists():
        print(f"[SKIP] {module_dir.name}: 无 README.md")
        return 0

    readme_types = extract_readme_types(readme.read_text(encoding="utf-8"))
    source_types = collect_source_types(module_dir, repo_root)

    ghosts = sorted(
        t for t in readme_types
        if t not in source_types
        and t not in EXTERNAL_TYPES
        and t not in KEYWORDS
    )

    if ghosts:
        print(f"[FAIL] {module_dir.name}: README 引用但源码不存在的类（幽灵类）:")
        for g in ghosts:
            print(f"    - {g}")
        return 1

    print(f"[PASS] {module_dir.name}: README 类引用与源码一致 "
          f"（引用 {len(readme_types)} 个，源码 {len(source_types)} 个）")
    return 0


def main() -> int:
    args = sys.argv[1:]
    repo_root = None
    if "--repo-root" in args:
        idx = args.index("--repo-root")
        repo_root = pathlib.Path(args[idx + 1]).resolve()
        args = args[:idx] + args[idx + 2:]

    if not args:
        print(__doc__)
        return 2

    rc = 0
    for arg in args:
        p = pathlib.Path(arg).resolve()
        if not p.exists():
            print(f"[ERROR] 路径不存在: {p}")
            rc = 2
            continue
        # 支持直接传 README.md 文件路径（lefthook staged_files 场景），自动推导模块目录
        if p.is_file() and p.name == "README.md":
            module_dir = p.parent
        else:
            module_dir = p
        rc |= check_module(module_dir, repo_root)
    return rc


if __name__ == "__main__":
    sys.exit(main())

