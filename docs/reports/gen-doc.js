const fs = require('fs');
const { Document, Packer, Paragraph, TextRun, Table, TableRow, TableCell, Header, Footer, AlignmentType, HeadingLevel, BorderStyle, WidthType, ShadingType, PageNumber, PageBreak } = require('docx');

const FONT = 'SimSun';
const HFONT = 'SimHei';
const br = { style: BorderStyle.SINGLE, size: 1, color: '999999' };
const br_all = { top: br, bottom: br, left: br, right: br };

function hc(text, w) {
  return new TableCell({ borders: br_all, width: { size: w, type: 'dxa' }, shading: { fill: '305496', type: ShadingType.CLEAR }, verticalAlign: 'center', margins: { top: 60, bottom: 60, left: 100, right: 100 }, children: [new Paragraph({ alignment: AlignmentType.CENTER, children: [new TextRun({ text, bold: true, font: HFONT, size: 18, color: 'FFFFFF' })] })] });
}
function tc(text, w, opts = {}) {
  return new TableCell({ borders: br_all, width: { size: w, type: 'dxa' }, shading: opts.sh ? { fill: 'F2F7FB', type: ShadingType.CLEAR } : undefined, verticalAlign: 'center', margins: { top: 40, bottom: 40, left: 80, right: 80 }, children: [new Paragraph({ alignment: opts.c ? AlignmentType.CENTER : AlignmentType.LEFT, children: [new TextRun({ text: String(text), font: FONT, size: 18, bold: opts.b || false, color: opts.cl || '000000' })] })] });
}
function tbl(headers, rows, cw) {
  const tw = cw.reduce((a, b) => a + b, 0);
  return new Table({ width: { size: tw, type: 'dxa' }, columnWidths: cw, rows: [
    new TableRow({ children: headers.map((h, i) => hc(h, cw[i])) }),
    ...rows.map((row, ri) => new TableRow({ children: row.map((c, ci) => tc(c, cw[ci], { sh: ri % 2 === 1, b: ci === 0, c: ci > 0 && ci < row.length - 1 })) }))
  ]});
}
function h1(text) { return new Paragraph({ heading: HeadingLevel.HEADING_1, spacing: { before: 360, after: 200 }, children: [new TextRun({ text, font: HFONT, size: 36, bold: true, color: '1F4E79' })] }); }
function h2(text) { return new Paragraph({ heading: HeadingLevel.HEADING_2, spacing: { before: 280, after: 160 }, children: [new TextRun({ text, font: HFONT, size: 28, bold: true, color: '2E75B6' })] }); }
function h3(text) { return new Paragraph({ heading: HeadingLevel.HEADING_3, spacing: { before: 200, after: 120 }, children: [new TextRun({ text, font: HFONT, size: 24, bold: true, color: '404040' })] }); }
function p(text, opts = {}) { return new Paragraph({ spacing: { after: 120, line: 360 }, children: [new TextRun({ text, font: FONT, size: 21, ...opts })] }); }
function b(text, indent = 0) { return new Paragraph({ spacing: { after: 80, line: 360 }, indent: { left: 480 + indent * 360 }, children: [new TextRun({ text: '\u2022  ' + text, font: FONT, size: 20 })] }); }
function pb() { return new Paragraph({ children: [new PageBreak()] }); }

const C = [];
// cover
C.push(new Paragraph({ alignment: AlignmentType.CENTER, spacing: { before: 2400 }, children: [] }));
C.push(new Paragraph({ alignment: AlignmentType.CENTER, spacing: { after: 200 }, children: [new TextRun({ text: 'ydsz-common 全局引用分析与优化建议报告', font: HFONT, size: 48, bold: true, color: '1F4E79' })] }));
C.push(new Paragraph({ alignment: AlignmentType.CENTER, spacing: { after: 200 }, children: [new TextRun({ text: '对标行业竞品 利用率评估 重复造轮子扫描 可落地优化建议', font: FONT, size: 26, color: '666666' })] }));
C.push(new Paragraph({ alignment: AlignmentType.CENTER, spacing: { after: 600 }, children: [new TextRun({ text: '2026 年 9 月', font: FONT, size: 24, color: '888888' })] }));

// 1. exec summary
C.push(pb());
C.push(h1('一、执行摘要'));
C.push(p('本报告围绕 ydsz-common 全部 30 个子模块（L1-L6），对九大业务引擎的依赖声明与 Java import 引用热度进行定量扫描，对标 Spring/Guava/Hutool/Resilience4j 等主流竞品，识别出三大核心问题。', { bold: true }));

C.push(h3('1.1 核心数据'));
C.push(b('ydsz-common 共 30 子模块：L1 工具层 4 个、L2 核心响应基础 2 个、L3 领域基类 2 个、L4 数据基础 5 个、L5 业务服务 14 个、L6 应用基座 3 个'));
C.push(b('全员公共子模块（被 10/10 业务模块依赖）：json / core / exception / util / redis / thread / auth / audit / base / web 共 10 个'));
C.push(b('冷门子模块（全员引用 < 50 次）：app / config / netty / tenant / queue / notify / socket / file'));
C.push(b('重复造轮子案例 10 类，覆盖目标规则 YDIZ-COMMON-003、YDIZ-OOP-006、YDIZ-DB-001 等强制级'));
C.push(b('虚假依赖 4 处（pom 有依赖但 import=0）：userinfo 的 common-thread 和 common-tenant'));

C.push(h3('1.2 高优先级行动项'));
C.push(b('P0：清理 4 处虚假依赖，重构密码学入口（BCryptPasswordEncoder -> PwdUtils），修复 3 处 YDIZ-OOP-006 违规示例'));
C.push(b('P1：收敛脱敏工具至唯一归属（MaskUtils + SensitiveUtil），统一日期/文件/ID 工具入口，新增 YDIZ-COMMON-018/019/020/021 规则'));
C.push(b('P2：建立公共能力标准运维机制（持续巡检 + 新模块入驻评审），开启长期护盾/全员引用的潜在增量开关'));

// 2. 能力盘点
C.push(pb());
C.push(h1('二、ydsz-common 全域子模块能力盘点'));
C.push(h2('2.1 分层架构总览'));
C.push(tbl(
  ['层级', '定位', '数量', '子模块清单', '依赖约束'],
  [
    ['L1 工具层', '零外部依赖工具库', '4', 'json, util, cache, excel', '禁止依赖业务模块/Spring/L3+'],
    ['L2 核心响应与基础设施', '统一响应/分页/TraceId + i18n 基座', '2', 'core, locales', '仅依赖 L1'],
    ['L3 领域基类', 'DDD 基类/异常体系', '2', 'domain, exception', '仅依赖 L1-L2'],
    ['L4 数据基础', '持久化增强（JDBC/Redis/锁/线程/租户）', '5', 'jdbc, redis, lock, thread, tenant', '仅依赖 L1-L3'],
    ['L5 业务服务', '安全/认证/消息/事务/可观测等', '14', 'auth, safe, feign, audit, notify, queue, event, config, socket, netty, file, docs, search, sentry', '可依赖 L1-L4'],
    ['L6 应用基座', '终端基座（Web/App）', '3', 'base, app, web', '可依赖全部'],
  ],
  [1400, 2400, 600, 3200, 2000]
));

C.push(h2('2.2 30 个子模块能力清单'));
const modTable = [
  ['ydsz-common-json', 'L1', '自研 JSON 引擎，零 Jackson 运行时依赖，支持多态化/树模型/AOT', 'YdszJson, JsonMapper, JsonNode', '10/10'],
  ['ydsz-common-util', 'L1', '通用工具集（字符串/日期/加密/雪花ID/HTTP/正则/集合/Bean映射/国密SM2-4/AES-GCM）', 'StringUtils, DateUtils, SnowflakeIdGenerator, PwdUtils, DigestUtils, MaskUtils, BeanMapper', '10/10'],
  ['ydsz-common-cache', 'L1', '多策略本地缓存（Window-TinyLFU/Striped/Loading/Async/Expirate/WriteThrough 装饰器）+ @YdszCacheable AOP + Actuator + Micrometer', 'Cache, YdszCache, LoadingCache, CacheBuilder, MultiLevelCacheTemplate', '9/10'],
  ['ydsz-common-excel', 'L1', '自研 OOXML 字节流 Excel 引擎（零 POI 依赖），SAX 流式读写、HSSF 兼容、公式注入防护', 'ExcelFacade, ExcelReader, ExcelWriter, SuperFastExcelReader, SuperFastExcelWriter, FormulaInjectionGuard', '5/10'],
  ['ydsz-common-core', 'L2', '统一响应上下文（YdszResponse/PageResponse/ResultCode）+ TenantContextHolder/RequestContext/TraceId/FeatureFlag', 'YdszResponse, PageResponse, TenantContextHolder, TraceIdGenerator, CoreAutoConfiguration', '10/10'],
  ['ydsz-common-locales', 'L2', 'i18n 基座（MessageSource/LocaleResolver/I18nContext 传播 SPI/翻译缺失严格性控制）', 'I18n, I18nMessages, Locales, MessageSourceHolder, LocalesAutoConfiguration', '1/10'],
  ['ydsz-common-domain', 'L3', '领域基础模型（PageQuery/TreeBuilder/Specification 规范模式/TypedId/枚举/数据权限上下文）', 'PageQuery, TreeBuilder, TreeNode, Specification, DataScopeContextHolder, TypedId', '10/10'],
  ['ydsz-common-exception', 'L3', '公共异常体系（BizException/SysException/ExceptionCode 注册表 + MVC+WebFlux 全局处理器 + OpenAPI 错误码扩展）', 'AbstractYdszException, BusinessException, SysException, YdszExceptionHandlerAutoConfiguration, MvcExceptionHandler', '10/10'],
  ['ydsz-common-jdbc', 'L4', 'MyBatis-Plus 增强（MpBaseEntity 基类/动态数据源路由/JSqlParser 行列级数据权限/SQL 防火墙/JsonTypeHandler）', 'MpBaseEntity, MpBaseIdEntity, DynamicRoutingDataSource, DataPermissionInnerInterceptor, SqlFirewallInnerInterceptor', '9/10'],
  ['ydsz-common-redis', 'L4', 'Redis 操作封装（String/Hash/List/Set/ZSet/Stream/PubSub/Geo/Bitmap/Pipeline/RateLimiter）+ Reactive + Key 命名规范', 'RedisStringOps, RedisHashOps, RedisStreamOps, RedisRateLimiter, YdszJsonRedisSerializer, TenantRedisKeyPrefixer', '10/10'],
  ['ydsz-common-lock', 'L4', '分布式锁（Redis 可重入/公平/读写锁/Semaphore/MultiLock）+ @YdszDistributedLock AOP + WatchDog + @DistributedScheduled', 'DistributedLocker, LockTemplate, RedisReentrantLock, RedisFairLock, RedisReadWriteLock, LockWatchDog', '9/10'],
  ['ydsz-common-thread', 'L4', '统一线程池治理（编程式工厂/按业务隔离/VirtualThread/Micrometer/慢任务告警/动态热更新/Actuator）', 'InternalExecutorFactory, ExecutorUtils, ThreadPoolProperties, ThreadPoolRegistry, VirtualExecutorCreator, AlarmNotifier', '10/10'],
  ['ydsz-common-tenant', 'L4', '多租户隔离（Header WebFilter/MyBatis 拦截器/Feign 传播/异步 TaskDecorator/Redis Key 前缀/数据源路由/RateLimit）', 'TenantAutoConfiguration, TenantContextWebFilter, TenantIsolationInterceptor, TenantContextFeignInterceptor, TenantDataSourceRouter', '5/10'],
  ['ydsz-common-auth', 'L5', '认证授权（JWT/Token Blacklist/RBAC 权限树/权限预检/AuthFilter/ColumnDesensitization/Session/OIDC/InternalHeader/API Key）', 'JwtTokenService, TokenService, RbacPermissionEvaluator, AuthPermissionAspect, AuthContextUtils, LoginUser, InternalHeaderSigner', '10/10'],
  ['ydsz-common-safe', 'L5', '安全（XSS/CSRF/TokenBucket+Redis 限流/Resilience4j 熔断/API 签名/SRP/IP 黑白名单/验证码/字段加密+脱敏/EncryptTypeHandler）', 'XssFilter, CsrfFilter, RateLimitAspect, TokenBucketLimiter, SafeCircuitBreaker, SensitiveUtil, FieldEncryptionService', '9/10'],
  ['ydsz-common-feign', 'L5', '企业级 OpenFeign（请求拦截/Trace 透传/Bulkhead/熔断/限流/GZIP/错误解码/重试/Resilience4j 适配/Fallback）', 'YdszFeignErrorDecoder, TraceRequestInterceptor, FeignCircuitBreakerGuard, JsonEncoder, GzipRequestCompressInterceptor', '9/10'],
  ['ydsz-common-audit', 'L5', '审计日志（@Audit 注解+AOP/异步记录/JDBC 存储/敏感字段掩码/检索/分表/Micrometer）', 'Audit, AuditAspect, AuditRecorder, JdbcAuditStorage, AuditLog, AuditQueryService, DiffSnapshotHelper', '10/10'],
  ['ydsz-common-notify', 'L5', '统一通知（邮件/短信/站内消息/企微/钉钉/飞书；模板引擎；重试队列；DKIM 签名；熔断保护；脱敏；聚合防扰）', 'NotifyService, AsyncNotifyService, NotifyTemplate, EmailNotifySender, NotifyDedupService, TemplateHotLoader', '4/10'],
  ['ydsz-common-queue', 'L5', 'MQ 抽象层（Jedis/Rabbit/Kafka/RocketMQ/RedisStream 五引擎适配；IMessagePublisher/Subscriber；DLQ；PEL；去重；延迟消息）', 'IMessagePublisher, IMessageSubscriber, QueueManager, KafkaMQ, RedisStreamMQ, DeadLetterQueueService', '4/10'],
  ['ydsz-common-event', 'L5', '事务性 Outbox 领域事件（DomainEvent/OutboxService/OutboxProcessor/多网关 Kafka+RocketMQ+Saga/归档/幂等消费）', 'DomainEvent, DomainEventPublisher, OutboxService, OutboxRepository, EventPublishGateway, AbstractSaga', '8/10'],
  ['ydsz-common-config', 'L5', 'Jasypt 加密增强 + 配置热加载（ConfigChangeListener 回调桥/Logback 审计日志/EncryptHealthIndicator）', 'ConfigProperties, ConfigChangeBridge, ConfigChangeListener, ConfigCliTool', '3/10'],
  ['ydsz-common-socket', 'L5', 'WebSocket 实时通信（STOMP + 集群 Redis 广播 + 在线/离线补偿 + 心跳 + ACL + 去重 + DLQ + SSE 适配）', 'RealtimePushTemplate, WebSocketHandler, WebSocketPresenceService, RedisOfflineMessageStore', '4/10'],
  ['ydsz-common-netty', 'L5', 'Netty 网络通信（AbstractNettyServer/Client + JSON 编解码 + SSL + 连接认证 + 断线重连 + 零拷贝 + RPC）', 'AbstractNettyServer, JsonMessageEncoder, NettyRpcClient, ReconnectHandler, SslContextFactory', '3/10'],
  ['ydsz-common-file', 'L5', '文件存储抽象（IFileStorage 统一接口；OSS/MinIO/S3/COS/OBS/Qiniu/Rust/Local 8 大平台；断点续传/分片/VirusScan/Magic）', 'IFileStorage, AbstractFileStorage, DefaultStorageFactory, OssStorage, MinioStorage, FileOps, FileTypeValidator', '2/10'],
  ['ydsz-common-docs', 'L5', '文档内容解析与合规（PDF/Word/Excel/PPT/HTML/CSV；Tika MIME；jsoup；OWASP XSS；PII 检测；OCR 扩展接口）', 'ExcelDocumentParser, WordDocumentParser, TikaMimeTypeDetector, XssSanitizer, PiiDetector', '2/10'],
  ['ydsz-common-search', 'L5', '搜索引擎 SPI 抽象（PG tsvector 全文检索 + 内存引擎 + ES/Solr 预留扩展；SearchProvider/SearchCache/Rebuild/Analytics）', 'SearchProvider, UnifiedSearchService, SearchRequest, PgSearchStrategy, InMemorySearchStrategy, SearchEngineRegistry', '7/10'],
  ['ydsz-common-sentry', 'L5', '统一可观测（Otel SDK + SkyWalking 双链路追踪；ELK/Loki 双日志；Micrometer+JVM 指标；SLA @SlaMetricAspect；告警收敛）', 'SentryFacade, SentryService, OtelAutoConfiguration, SlaMetricAspect, ElkLogPublisher, LokiLogPublisher, AlertConverger', '9/10'],
  ['ydsz-common-base', 'L6', 'HTTP 公共基座（CORS/时区/安全头/TraceFilter/请求日志/OpenAPI-Knife4j/文档导出/YdszHealth/WebFlux/ArchUnit）', 'YdszAutoConfiguration, BaseGlobalResponseAdvice, TraceFilter, SecurityHeadersFilter, DocExporter', '10/10'],
  ['ydsz-common-app', 'L6', '移动端 App 基座（AppAuthFilter/AppAuthHandler/AppGlobalResponseAdvice/RequestId/健康检查/OpenAPI 配置）', 'AppGlobalResponseAdvice, AppAuthFilter, AppAuthAppAuthHandler, AppHealthIndicator', '8/10'],
  ['ydsz-common-web', 'L6', 'PC Web 基座（完整 MVC 栈/WebAuthFilter/WebAuthHandler/TenantMdcFilter/TraceId/ContentCaching/SecurityHeaders/Yauaa）', 'GlobalResponseAdvice, WebAuthFilter, WebAuthHandler, WebMvcConfiguration, TenantMdcFilter, TraceIdResponseFilter', '10/10'],
];
C.push(tbl(
  ['子模块', '层级', '核心能力', '关键工具类/服务', '依赖模块数'],
  modTable,
  [1900, 600, 2800, 3200, 1100]
));

// 3. 热度矩阵
C.push(pb());
C.push(h1('三、业务模块 x ydsz-common 引用热度矩阵'));
C.push(p('扫描面：9 个业务模块全量 src/ 下所有 .java 文件的 import com.njydsz.common.{module}.* 语句数。'));

C.push(h3('3.1 业务模块对关键子模块的引用次数'));
C.push(tbl(
  ['业务模块', 'core', 'safe', 'auth', 'util', 'json', 'exception', 'audit', '模块总 import'],
  [
    ['message', '132', '78', '50', '43', '35', '30', '49', '600'],
    ['workflow', '110', '55', '51', '20', '50', '60', '40', '490'],
    ['userinfo', '102', '68', '50', '19', '25', '53', '61', '538'],
    ['cronjob', '69', '35', '54', '14', '43', '29', '49', '435'],
    ['system', '80', '37', '28', '10', '12', '25', '36', '319'],
    ['nextwiki', '49', '22', '60', '41', '5', '27', '31', '368'],
    ['agent', '34', '23', '31', '33', '64', '35', '34', '351'],
    ['literule', '47', '34', '7', '17', '17', '7', '59', '307'],
    ['gateway', '10', '14', '10', '8', '4', '2', '1', '84'],
  ],
  [1400, 800, 800, 800, 800, 900, 800, 1400]
));

C.push(h2('3.2 冷门模块与过时模块'));
C.push(b('ydsz-common-app：8/10，仅 ydzs-agent 等含 App 主程序的模块使用，其他模块塞入仅靠传递依赖'));
C.push(b('ydsz-common-tenant：5/10，主要由 base/web 传递，业务代码直接 import 不多'));
C.push(b('ydsz-common-netty：3/10，主要用于 message/nextwiki 长连接推送，ydsz-agent 零 import'));
C.push(b('ydsz-common-queue / notify / socket：分别 4/10、4/10、4/10，集中于 message/cronjob 的消息推送业务'));
C.push(b('ydsz-common-config：3/10，仅 system/literule 少量使用 @ConfigListener SPI'));

// 4. 利用率
C.push(pb());
C.push(h1('四、利用率问题分析'));
C.push(h2('4.1 虚假依赖（pom 有依赖但 Java import = 0）'));
C.push(tbl(
  ['业务模块', '虚假依赖子模块', 'pom 位置', 'Java import 次数', '风险等级'],
  [
    ['ydsz-userinfo', 'ydsz-common-thread', 'server pom', '0', '低（可直接删除）'],
    ['ydsz-userinfo', 'ydsz-common-tenant', 'server pom', '0', '低（由 infra 传递）'],
    ['ydsz-system', 'ydsz-common-locales', 'server pom', '1', '低（用 Spring 原生即可）'],
    ['ydsz-workflow', 'ydsz-common-event', 'infra pom', '1', '低（无 event 场景）'],
  ],
  [1600, 2000, 1600, 1400, 2000]
));

C.push(h2('4.2 低利用率依赖（依赖有但 import < 10）'));
C.push(p('以下场景 import 较少但可能属于基础设施代码少量使用（健康检查、配置等）'));
C.push(tbl(
  ['业务模块', '低利用 common 子模块 (import 次数)'],
  [
    ['system', 'cache (3), redis (4), locales (1), file (3), json (12)'],
    ['userinfo', 'locales (0), thread (0), tenant (0), excel (6), config (1)'],
    ['workflow', 'thread (1), event (1), sentry (1), search (3), redis (10)'],
    ['cronjob', 'tenant (2), notify (1), sentry (1), excel (4), locales (5)'],
    ['nextwiki', 'event (3), json (5)'],
  ],
  [1800, 7200]
));

C.push(h2('4.3 传递依赖风险'));
C.push(p('以下场景代码已使用 common API，但仅靠传递链接入，不符合 YDIZ-ARCH-001。建议在使用层显式声明。'));
C.push(tbl(
  ['业务模块', '使用的 common 能力', '当前隐蔽依赖链路', '建议'],
  [
    ['cronjob', 'common-jdbc (MpBaseEntity)', 'domain pom 存在，server/web 传递', '可保留，server/web 若直接引用 jdbc API 则应显式声明'],
    ['literule', 'common-jdbc', '同上', '同上'],
    ['agent', 'common-locales', 'infra pom 显式声明，server 未声明', 'infra 已够，无需上提'],
  ],
  [1600, 2200, 2200, 2200]
));

// 5. 重复造轮子
C.push(pb());
C.push(h1('五、重复造轮子扫描结果'));
C.push(h2('5.1 密码学/加密'));
C.push(tbl(
  ['问题类型', '具体文件:Line', '违反规则', '涉及文件数', '建议动作'],
  [
    ['MessageDigest.getInstance 直接调用', 'ydsz-common-auth/.../oidc/JwksEndpoint.java:115', 'YDIZ-COMMON-003 (P1)', '1', '改用 DigestUtils.sha256()'],
    ['new BCryptPasswordEncoder() 直接 new', 'ydsz-userinfo/.../provision/ProvisionOrchestrator.java:191', 'YDIZ-COMMON-016 (P0)', '1', '改用 PwdUtils / 注入 PasswordEncoder Bean'],
    ['UUID.randomUUID() 作为业务主键', 'CaptchaController/SocialAuthService/LdapOrgSyncService 等 6 处', 'YDIZ-COMMON-017 (P0)', '6', '改用 SnowflakeIdGenerator.nextId()'],
  ],
  [3200, 2800, 1700, 1300, 2200]
));

C.push(h2('5.2 日期工具散见'));
C.push(p('共 10+ 文件使用 DateTimeFormatter.ofPattern 自定义格式化，从 ydzz-workflow/FlowEfficiencyServiceImpl 3 个 private static final 到 ydsz-generator/VelocityDateTool 每次 ofPattern 等。目前未被任何规则显式禁止，建议新增 YDIZ-COMMON-018。'));

C.push(h2('5.3 上传文件 Files.write 直接调用'));
C.push(p('cronjob/JobArtifactService、generator/CodeGenService 等 8 处 Files.write 或 Files.writeString 直接调用。建议对持久化写入路径引入 ydsz-common-file 的 FileOps 统一入口。'));

C.push(h2('5.4 两套脱敏工具并存'));
C.push(p('项目内存在 MaskUtils (ydsz-common-util) 和 SensitiveUtil (ydsz-common-safe) 两套脱敏实现，一个面向通用 PII，一个面向安全等保。建议合并至唯一归属模块。'));

C.push(h2('5.5 缓存自实现残留'));
C.push(p('ydsz-agent/llm/CachedLlmClient 已迁移至 YdszCache#getWithProtection，但注释中保留历史 ConcurrentHashMap+Future 记录。建议清理注释以免误导。'));

C.push(h2('5.6 其他使用（未检出问题的维度）'));
C.push(b('字符串工具：未发现业务代码直接 import Spring/Commons StringUtils，已统一走 ydsz-common-util。'));
C.push(b('序列化工具：未发现 new Gson() 或 new ObjectMapper() 直接实例化，已统一走 YdszJson。'));
C.push(b('Apache POI 控制：仅 common-docs 内部使用 POI 实现文档解析，业务层禁止直接使用。'));
C.push(b('i18n 资源骨架：已按 YDIZ-I18N-003 在 8 引擎部署至少 5 key 的 messages.properties。'));

// 6. 规范不统一
C.push(pb());
C.push(h1('六、规范不统一扫描结果'));
C.push(tbl(
  ['问题维度', '具体文件:Line', '违反规则', '严重程度', '建议动作'],
  [
    ['Java 布尔字段缺 is 前缀', 'ydsz-userinfo/.../controller/TokenExchangeController.java:265 (boolean valid)', 'YDIZ-OOP-006 (P0)', '高', '命名 isValid + @JsonProperty("旧 is_valid")'],
    ['Java 布尔字段缺 is 前缀', 'ydsz-message/.../template/TemplateVariableDef.java (boolean required)', 'YDIZ-OOP-006 (P0)', '高', '命名 isRequired'],
    ['Boolean 包装类型语义模糊', 'ydsz-agent/.../text2sql/Text2SqlStateContext.java (Boolean validResult)', 'YDIZ-OOP-006 (P0)', '中', '重命名 isValidResult'],
    ['API 路径混用 v1 与无版本', 'ydsz-system/.../MonitorReportController.java:110', '版本规范', '低', '二选一或添加兼容注释'],
    ['SQL DDL sort_order 列名', 'cronjob.sql / workflow.sql 部分表', 'YDIZ-DB-001 (P0)', '高（需补扫）', '根据实际应用情况决定是否 DDL 改造'],
  ],
  [2800, 2800, 1400, 1100, 2700]
));

// 7. 对标竞品
C.push(pb());
C.push(h1('七、对标行业竞品差距分析'));
C.push(p('本节将 ydsz-common 与 Spring 生态、Apache Commons、Guava、Hutool、Resilience4j、Micrometer 等主流竞品框架进行对标。'));
const compRows = [
  ['JSON 引擎', 'YdszJson, JsonNode 自研，零 Jackson 运行时', 'Jackson / Gson / Fastjson2', '自主可控、可定制化多态接波/权限安全过滤', '生态兼容性不足，开发者学习成本高'],
  ['本地缓存', 'Window-TinyLFU/Striped/Loading/Async/@YdszCacheable AOP', 'Caffeine/Guava Cache', '策略组合丰富、AOP 注解更简洁', '维护成本高，算法潜在 bug 风险'],
  ['Excel 引擎', 'OOXML 自研字节流引擎（零 POI），SAX 流式读写', 'EasyExcel/Apache POI', '单机性能明显优于 POI', '复杂标准/公式/图表覆盖不足'],
  ['加密/哈希', 'CryptoUtils + PwdUtils（BCrypt/SM2-4）', 'Spring Security Crypto / Apache Commons Codec', '支持国密（SM2/SM3/SM4），符合等保要求', '从 Spring 生态离树，安全审计需自行维护'],
  ['分布式锁', 'RedisReentrantLock + TokenBucket + MultiLock + WatchDog', 'Redisson/Curator', '接口更简、更轻', '场景覆盖不如 Redisson 丰富'],
  ['Feign', 'TraceRequestInterceptor + FeignCircuitBreakerFallbackFactory', 'Spring Cloud OpenFeign + Resilience4j', '已基于 OpenFeign，支持 CircuitBreaker（YDIZ-SAFE）', '数量更少、实用性已足'],
  ['线程池', 'InternalExecutorFactory + Micrometer 指标', 'DynamicThreadPool/Netty FastThreadLocal', '集成 Micrometer + 动态热更新', '线程数管理与 Spring 默认可线程池未统一'],
  ['i18n', 'I18n/I18nMessages/Locales + MessageSourceHolder', 'MessageSource / ICU4J', '统一入口 + 强制注册底座', '数据表异常处理需自行实现'],
  ['安全追踪', 'RequestContextUtils + TraceIdGenerator + TracePropagation', 'SLF4J MDC + OpenTelemetry Context', '统一自研 + 与 Otel 双链路兼容', '线程池跨线程传播与原生 M 方式一致'],
  ['数据库', 'MpBaseEntity + DataPermissionInterceptor + SqlFirewall', 'MyBatis-Plus（国内）', '增强约束逻辑，包含隔离设计', '自定义异常档需要维护'],
  ['Redis', 'YdszJsonRedisSerializer + TenantRedisKeyPrefixer + RedisRateLimiter', 'Redisson/Spring Data Redis', '并发安全性更强、支持库数拦截', '数据库标准化维护成本陡增'],
  ['审计', 'AuditAspect + JdbcAuditLog + DiffSnapshotHelper', 'Spring Data JPA Auditing / Javers', '数据库自存储 + 变更快照', '需要定期维护分表策略'],
];
C.push(tbl(
  ['能力域', 'ydsz-自研实现', '行业标准对标', '优势/差异', '风险'],
  compRows,
  [1400, 2600, 2400, 1800, 1400]
));

// 8. 后续优化
C.push(pb());
C.push(h1('八、可落地的后续优化建议'));
C.push(h2('8.1 驱动目标'));
C.push(p('基于以上分析，提出以下统一方向：基于 ydsz-common 的全面标准化运维、避免重复造轮子、提升开发者利用率。'));

C.push(h2('8.2 P0 高优先级修复'));
C.push(h3('8.2.1 清理虚假依赖（预计 1-2 小时）'));
C.push(b('ydsz-userinfo 的 common-thread 和 common-tenant：在 server pom 删除，由 infra/web 传递依赖要求即可正常编译。'));
C.push(b('ydsz-system 的 common-locales：仅 1 个 import，可直接用 Spring 原生 MessageSource 替代。'));

C.push(h3('8.2.2 修复 3 处 YDIZ-OOP-006 示例'));
C.push(b('ydsz-userinfo TokenExchangeController 的 boolean valid -> isValid，加 @JsonProperty("旧")'));
C.push(b('ydsz-message TemplateVariableDef 的 boolean required -> isRequired'));
C.push(b('ydsz-agent Text2SqlStateContext 的 Boolean validResult -> isValidResult'));

C.push(h3('8.2.3 重构密码学入口'));
C.push(b('ydsz-common-auth JwksEndpoint 的 MessageDigest.getInstance -> DigestUtils.sha256'));
C.push(b('ydsz-userinfo ProvisionOrchestrator 的 new BCryptPasswordEncoder() -> 注入 PasswordEncoder Bean 或调用 PwdUtils.hashPasswordBCrypt'));

C.push(h2('8.3 P1 中优先级改造'));
C.push(h3('8.3.1 补充规则（YDIZ-COMMON-018、019）'));
C.push(b('新增 YDIZ-COMMON-018：所有日期格式化/解析必须通过 ydsz-common-util 的 DateUtils；禁止业务代码直接 new SimpleDateFormat 或频繁 DateTimeFormatter.ofPattern。'));
C.push(b('YDIZ-COMMON-019：所有持久化文件写入必须通过 ydsz-common-file；禁止业务代码直接调用 Files.write。'));

C.push(h3('8.3.2 收敛脱敏工具'));
C.push(b('合并 MaskUtils (ydsz-common-util) 和 SensitiveUtil (ydsz-common-safe) 至唯一归属模块（建议纳入 ydsz-common-safe（等保域）），指定一个统一入口。'));

C.push(h3('8.3.3 统一 ID 生成入口'));
C.push(b('6 处 UUID.randomUUID() 作为业务主键均被 YDIZ-COMMON-017 禁止；建议改用 SnowflakeIdGenerator.nextId()。'));

C.push(h2('8.4 P2 低优先级 / 优化'));
C.push(h3('8.4.1 开启公共能力巡检'));
C.push(b('建立在线清单或 CI 挂钩，每次 PR 自动分析新增的 import 是否达到 YDIZ-COMMON-014-019 规则级别下之入口。'));
C.push(b('整体建议新增 4 条新规则 (YDIZ-COMMON-018~021) 同步到 shared-rules.yaml。'));

C.push(h3('8.4.2 下放低利用率依赖'));
C.push(b('ydsz-system 可考虑将 ydsz-common-yxxx 利用率很低的子模块从 server 层下放到 infra 层，减轻 server 的依赖重量。'));

C.push(h3('8.4.3 竞品对标反思'));
C.push(b('YdszJson 是否需要打包成独立开源包？当前仅内部用，若继续部署但不开源，是一批开发成本。或考虑迁移到 Jackson + 指定插件。'));
C.push(b('ydsz-common-netty 的 RPC 能力是否需要广泛使用：如未未来发展 RPC 服务，可考虑移除。'));
C.push(b('Caffeine 的稳定性已经很高，ydsz-common-cache 是否需要停止自研而放弃？'));

C.push(h2('8.5 规则修订建议'));
C.push(tbl(
  ['规则 ID', '等级', '描述', '触发场景'],
  [
    ['YDIZ-COMMON-018', 'P1', '日期格式化必须通过 DateUtils；禁止直接 new SimpleDateFormat 或频繁 ofPattern', '业务代码日期操作'],
    ['YDIZ-COMMON-019', 'P1', '持久化文件写入必须通过 ydsz-common-file；禁止直接 Files.write', '业务代码文件操作'],
    ['YDIZ-COMMON-020', 'P2', '跨服务超时配置必须外部化到 ydsz-common-config；禁止在业务代码中有变量值硬编码时间单位', '业务超时配置'],
    ['YDIZ-COMMON-021', 'P2', '脱敏接口必须通过 ydsz-common-safe 的 MaskUtils 或 SensitiveUtil；禁用 substring/replaceAll 自实现', '数据返回前接口处理'],
  ],
  [1400, 700, 4400, 2400]
));

C.push(h2('8.6 下一步行动'));
C.push(b('1、确认 P0/P1 优先级修复清单任务拆分到具体 owner。'));
C.push(b('2、更新 shared-rules.yaml 添加 4 条新规则并同步到 catpaw-always.md。'));
C.push(b('3、开启 4 小时课题讨论：是否基于 ydsz-common 与 Spring 标准化档案合并。'));
C.push(b('4、持续追踪利用率指标，每月检查新增模块对 ydsz-common 的引用是否合规。'));

// Build doc
const doc = new Document({
  styles: {
    default: { document: { run: { font: FONT, size: 21 } } },
    paragraphStyles: [
      { id: 'Heading1', name: 'Heading 1', basedOn: 'Normal', next: 'Normal', quickFormat: true, run: { size: 36, bold: true, font: HFONT, color: '1F4E79' }, paragraph: { spacing: { before: 360, after: 200 }, outlineLevel: 0 } },
      { id: 'Heading2', name: 'Heading 2', basedOn: 'Normal', next: 'Normal', quickFormat: true, run: { size: 28, bold: true, font: HFONT, color: '2E75B6' }, paragraph: { spacing: { before: 280, after: 160 }, outlineLevel: 1 } },
      { id: 'Heading3', name: 'Heading 3', basedOn: 'Normal', next: 'Normal', quickFormat: true, run: { size: 24, bold: true, font: HFONT, color: '404040' }, paragraph: { spacing: { before: 200, after: 120 }, outlineLevel: 2 } },
    ]
  },
  sections: [{
    properties: { page: { size: { width: 11906, height: 16838 }, margin: { top: 1440, right: 1440, bottom: 1440, left: 1440 } } },
    headers: { default: new Header({ children: [new Paragraph({ alignment: AlignmentType.RIGHT, children: [new TextRun({ text: 'ydsz-common 全局依赖分析报告', font: FONT, size: 16, color: '999999', italics: true })] })] }) },
    footers: { default: new Footer({ children: [new Paragraph({ alignment: AlignmentType.CENTER, children: [new TextRun({ text: '第 ', font: FONT, size: 16, color: '666666' }), new TextRun({ children: [PageNumber.CURRENT], font: FONT, size: 16, color: '666666' }), new TextRun({ text: ' 页', font: FONT, size: 16, color: '666666' })] })] }) },
    children: C
  }]
});

Packer.toBuffer(doc).then(buffer => {
  const out = 'D:/Code/open/ydsz-cloud/docs/reports/ydsz-common-global-reference-analysis.docx';
  fs.writeFileSync(out, buffer);
  console.log('OK:', out, (buffer.length / 1024).toFixed(1) + ' KB');
});
