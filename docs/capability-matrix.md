# ydzs-common 公共能力矩阵（Capability Matrix）

> 创建日期：2026-09-25  
> 关联文档：[模块边界参考](common-module-boundary-reference.md) / [全局引用分析报告](reports/ydzs-common-global-reference-analysis.md)  
> 目的：一页纵览 31 个公共子模块的核心能力、引用热度与 SPI 扩展点，作为新需求落位与季度治理的索引。

---

## 一、L1 工具层

| 模块 | 引用数 | 核心能力 | SPI 扩展点 | 行业对标 | 成熟度 |
|------|--------|----------|-----------|----------|--------|
| ydsz-common-json | 10/10 | 高性能 JSON 序列化、树模型、自定义注解体系 | — | 自研替代 Jackson | A+ |
| ydsz-common-cache | 9/10 | 本地缓存 TinyLFU/装饰器/LoadingCache，无缝桥接 Spring Cache | — | Caffeine / Guava Cache | A |
| ydsz-common-excel | 3/10 | 超高速 SAX 流式 Excel/CSV 读写、模板导出、多 Sheet | — | EasyExcel / 自研 | A |
| ydsz-common-util | 10/10 | 加密/日期/集合/脱敏/雪花 ID/对象映射/重试/掩码 | PasswordStrengthChecker | Apache Commons / Hutool | A+ |

## 二、L2-L3 基类层

| 模块 | 引用数 | 核心能力 | SPI 扩展点 | 行业对标 | 成熟度 |
|------|--------|----------|-----------|----------|--------|
| ydsz-common-core | 10/10 | YdszResponse/PageResponse/RequestContext/TenantContextHolder/FeatureFlag | — | Spring Web基础 | A+ |
| ydsz-common-locales | 10/10（传递性） | 国际化 SPI、MessagesourceHolder、LocaleResolver | — | Spring MessageSource | A |
| ydsz-common-domain | 10/10 | TreeBuilder/PageQuery/DataPermissionContext/BaseQuery | — | DDD基础 | A |
| ydsz-common-exception | 10/10 | BusinessException/SysException/MvcExceptionHandler | — | Spring @ControllerAdvice | A |

## 三、L4 基础数据层

| 模块 | 引用数 | 核心能力 | SPI 扩展点 | 行业对标 | 成熟度 |
|------|--------|----------|-----------|----------|--------|
| ydsz-common-jdbc | 10/10 | 动态数据源/多租户拦截器/审计字段/安全防火墙/JSON TypeHandler | — | MyBatis Plus | A |
| ydsz-common-redis | 10/10 | Redis Hash/String 抽象/限流/多级缓存/失效事件 | Redisson 可选 | Lettuce增强 | A |
| ydsz-common-lock | 6/10 | 分布式锁公平锁/读写锁/信号量/看门狗续约 | CurrentUserIdResolver | Redisson | A |
| ydsz-common-thread | 9/10 | 线程池注册/监控/告警/热更新/虚拟线程支持 | — | 自研监控 | A |
| ydsz-common-tenant | 7/10 | Schema路由/数据源路由/异步透传/Redis Key前缀 | — | 自研多租户 | B+ |

## 四、L5 业务服务层（按引用数降序）

| 模块 | 引用数 | 核心能力 | SPI 扩展点 | 行业对标 | 成熟度 |
|------|--------|----------|-----------|----------|--------|
| ydsz-common-auth | 10/10 | JWT/RBAC/DataScope/OIDC/API Key/布隆过滤器 | — | Spring Security | A |
| ydsz-common-safe | 10/10 | 限流/熔断/CSRF/XSS/SSRF/字段加密/幂等/安全告警 | **DesensitizeProvider** ✅ | Resilience4j | A |
| ydsz-common-feign | 7/10 | 响应解包/熔断适配器/重试/隔离/限流压缩 | — | OpenFeign增强 | A |
| ydsz-common-audit | 7/10 | 操作审计切面/JDBC存储/变更存储/脱敏/查询 | — | 自研 | A |
| ydsz-common-notify | 6/10 | 12渠道通知中心/模板热加载/重试去重 | — | 自研 | A |
| ydsz-common-socket | 6/10 | WebSocket推送/STOMP/SSE/多设备/集群同步/在线用户 | — | Spring WebSocket | A |
| ydsz-common-lock | 6/10 | 见 L4 | — | — | — |
| ydsz-common-redis | 10/10 | 见 L4 | — | — | — |
| ydsz-common-queue | 6/10 | MQ统一API 6引擎/死信/投递语义 | — | Spring Messaging | A |
| ydsz-common-event | 6/10 | Outbox/领域事件/Saga编排/多网关适配 | — | Spring Events + Outbox | A |
| ydsz-common-search | 4/10 | ES/PG/内存多引擎/中文分词/排序 | — | Spring Data | B+ |
| ydsz-common-file | 2/10 | 对象存储适配7平台/断点续传/去重/生命周期 | **ImageProcessor** ✅ | 自研 | A |
| ydsz-common-netty | 3/10 | Netty Server/Client/RPC/编解码/零拷贝 | — | 原生 Netty | A |
| ydsz-common-docs | 2/10 | 多格式解析/OCR/PII检测/安全扫描 | **TemplateEngine** 预留 ✅, **PreviewRenderer** 预留 ✅ | Apache Tika / PDFBox | B+ |
| ydsz-common-sentry | 9/10 | OTel/Tracing/SLA/告警收敛/日志发布 | — | Micrometer + OTel | A+ |
| ydsz-common-config | 3/10 | 配置热更新/Nacos桥接/变更监听/CLI工具 | — | 自研 | B |
| ydsz-common-seata | 0/10 | Seata XID透传 *(已 @Deprecated)* | — | Seata | D- |

## 五、L6 应用基座

| 模块 | 引用数 | 核心能力 | 成熟度 |
|------|--------|----------|--------|
| ydsz-common-base | 10/10 | 全局装配/Trace/安全头/I18n/OpenAPI | A+ |
| ydsz-common-app | 7/10 | 移动端/HTTP摘要/请求ID/鉴权拦截/日志脱敏 | B+ |
| ydsz-common-web | 待确认 | PC Web层/过滤链/签名校验/Webhook | B |

---

## 六、SPI 扩展点注册表

| SPI | 模块 | 默认实现 | 启用方式 | 配置键 |
|-----|------|----------|----------|--------|
| DesensitizeProvider | common-safe | DefaultDesensitizeProvider (@Component @Primary) | 直接注入；自定义 @Primary 覆盖 | — |
| ImageProcessor | common-file | DefaultImageProcessor (@Component @Primary) | 直接注入；自定义 @Primary 覆盖 | — |
| TemplateEngine | common-docs | NoOpTemplateEngine (@ConditionalOnMissingBean) | 引入 poi-tl/pdfbox + @Bean @Primary | — |
| PreviewRenderer | common-docs | NoOpPreviewRenderer (@ConditionalOnMissingBean) | 引入 pdfbox/libreoffice + @Bean @Primary | — |

---

## 七、能力成熟度评级

| 等级 | 含义 | 模块列表 |
|------|------|----------|
| A+ | 成熟/覆盖度超出行业基准 | json, core, sentry, base, util, auth |
| A | 成熟/对标行业标准 | cache, excel, domain, exception, jdbc, redis, lock, thread, safe, feign, audit, notify, socket, queue, event, netty, file, search |
| B+ | 成熟但有优化空间 | tenant, config, docs, app, web |
| B | 设计合理但能力偏薄 | locales 利用模式待治理 |
| C | 规划中 | — |
| D- | 已废弃 | seata |

---

## 八、季度治理追踪（KPI）

| 指标 | 基线 (26.09.25) | 目标 (26.12) |
|------|-----------------|-------------|
| L1 纯度（无业务反向依赖） | 100% | 100% |
| P0 反模式依赖 | 3 处 | 0 处 |
| 零引用模块数 | 2（含假零） | ≤1（仅 attic 中归档） |
| SPI 扩展点数 | 4 | 8（新增行为分析、幂等等） |
| 模块能力矩阵文档化 | 100% 完成 | 持续同步 |
| @Deprecated 迁移完成率 | seata 已标记 | seata 归档至 attic |

---

*本文档由 ydzz-common 深度分析流程生成；随模块演进按季度刷新。*
