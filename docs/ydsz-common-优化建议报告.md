# ydsz-common 公共依赖模块全面分析与优化建议（v2 · 自主可控基准版）

> 分析范围：`ydsz-common` 全部 30 个子模块（约 1400+ 个 Java 文件），基于最新源码逐文件审读。
> 生成日期：2026-08-17 ｜ 版本：v2（已按「内网 + 少依赖 + 绝对自主可控」战略基准校准）

---

## 〇、评估基准校准（重要）

**战略前提**：本项目是自研内网项目，目标不是对标开源社区的性能与生态，而是 **「依赖最小化 + 绝对自主可控」**。这决定了评估尺子要从「对标互联网大厂 / 开源竞品」切换为：

1. **自研是手段不是目的** —— 自研组件本身是正确战略，不因「有成熟开源替代」而判错；
2. **真正要审查的是三件事**：
   - **硬伤**：算法/安全/数据完整性 bug（与是否自研无关，自研错了更要修好）；
   - **诚实性**：宣称的能力是否真实交付，空壳/半成品是否误导使用方；
   - **依赖纪律**：既然要「少依赖」，那已引入却无人用的依赖、重复的依赖就是违背战略的漏洞。
3. **不要求极致性能** → 之前「补 JMH 压测、换更快的库」类建议降级；「算法写错导致命中率劣化」「全量读内存 OOM」这类**正确性问题**仍是 P0。

**由此，v1 中「换成开源库」类建议全部反转**，重新归类如下。

---

## 一、执行摘要

**总体定位**：`ydsz-common` 分层清晰（L1-L6 + 构建期纯度检查）、版本收敛治理到位，事务性 Outbox、多租户数据权限 fail-closed、断点续传/秒传等硬能力真实落地，是一套**自研可控能力不错**的底座。

**真正的问题不在「自研」，而在三处**：
1. **自研组件的工程化不足** —— 自研 JSON/缓存/锁存在注释漂移、算法移植错误、虚假宣传（ASM/SIMD）、零测试佐证，**可控性打了折扣**；
2. **宣称 > 交付的空壳** —— TOTP 2FA、FeatureFlag、SCHEMA 租户隔离、OCR、ES 搜索、下级部门数据权限扩展，只有接口或空壳却已对外宣称；
3. **依赖纪律松弛** —— 既要少依赖，却又存在「自研 SAX + 强依赖 POI 双引擎」「搜索模块 es/solr/opensearch 死依赖」「Redis 15 种 ops 大半无人用」「分布式锁 6 种 4 种零消费」这类违背战略的冗余。

**建议主线（十六字）**：**「保留自研、修好硬伤、补齐空壳、严治依赖」**。

---

## 二、优先级总览（路线图）

| 阶段 | 主题 | 关键词 |
|------|------|--------|
| **P0（立即）** | 修正确性硬伤 + 安全缺陷 + 杜绝数据丢失 | XSS 架空、事件静默丢失、刷新锁 fail-open、缓存算法 bug、OOM、列名注入面 |
| **P1（本季度）** | 自研组件工程化 + 补齐空壳/诚实化 | 修算法注释、去虚假宣传、TOTP、SCHEMA 租户、OCR、ES、依赖裁剪 |
| **P2（下季度）** | 瘦身基座 + 依赖治理 + 体验 | 合并 base/web/app、锁/ops 裁剪、死依赖清理、注解收敛 |

---

## 三、五维度详细分析（按新基准重分类）

### 3.1 硬伤（正确性 / 安全 / 数据完整性）—— 与自研无关，必须修

| # | 问题 | 证据 | 处理 |
|---|------|------|------|
| H1 🔴 | **XSS 用自定义 700 行正则 HTMLFilter，架空 OWASP sanitizer** | `safe/XssFilter:159`、`XssHttpServletRequestWrapper:73/137` 调 `EscapeUtils.clean()` 走正则；`OwaspXssCleaner` 已引入却未用 | 统一改用已引入的 OWASP sanitizer（**注意：这不算「增依赖」，是「用对已引入的依赖」**） |
| H2 🔴 | **刷新锁 fail-open** | `auth/TokenBlacklistService:178-181` Redis 异常 `return true` 放行并发刷新 | 改 fail-closed（拒绝 + 告警） |
| H3 🔴 | **领域事件静默丢失** | `event/NoopEventPublishGateway:32-39` 返回 true，未投递消息被标记 SENT | 返回 false + 强制 `fail-on-noop=true` |
| H4 🔴 | **自研缓存算法移植错误（命中率劣化）** | `cache/lfu/FrequencySketch:138` 用 `(index & 3) << counterShift` 定位，`maximumSize:98` 用 `maximum/64`，与 Count-Min Sketch 标准不符 | 修正为正确计数定位（`(index & 15) << 2` / `index >>> 4`，表大小按 `maximum/16` 采样），**保留自研** |
| H5 🔴 | **文件上传全量读内存 OOM** | `file/AbstractFileStorage:450-456` 用 `file.getBytes()` | 改流式，配合既有分片能力 |
| H6 🟠 | **数据权限列名未白名单校验（注入面）** | `jdbc/DataScopeHelper:79-116` 仅对值 escape，`deptColumn/userColumn/tableAlias` 直接拼接 | 列名/表名白名单校验 |
| H7 🟠 | **requestId/traceId 语义 bug** | `exception/BaseExceptionHandler:449` `setProperty("requestId", traceId)` 误赋值 | 修正映射 |
| H8 🟡 | **app 模块 POM 重复声明（构建 bug）** | `ydsz-common/pom.xml:106` 默认 modules 仍列 app，与 :117-121 profile 重复 | 从默认 modules 移除 |

### 3.2 自研组件工程化（保留自研，但要把"可控"做实）

> 自研的意义是可控，而可控的前提是**代码可信、算法正确、文档诚实**。以下组件保留，但需补工程化。

| 组件 | 现状问题 | 建议 |
|------|---------|------|
| **自研 JSON（85 文件）** | 注释漂移（ASM 已移除仍残留"供 ASM 序列化器"注释）；README 称"SIMD 向量化"实为 8 字符手动展开；性能表无 JMH 佐证 | ① 清理遗留注释，代码与文档对齐；② 去掉 ASM/SIMD 虚假宣传，如实标注"零依赖轻量序列化"；③ 明确其定位是**零依赖场景专用**，不必对标 Jackson 全兼容 |
| **自研缓存 W-TinyLFU** | FrequencySketch 算法错（H4）；`evictOnce` 非真 TinyLFU admission | 修 H4 后，核对 window/probation admission 逻辑与论文一致；补单元测试（命中率/淘汰顺序） |
| **自研 Excel** | 双引擎并存：自研 SAX + 仍强依赖 POI（`excel/pom.xml:25-32`），与"少依赖"矛盾；`ASMFieldAccessor` 实为 MethodHandle（命名误导） | **二选一**：若坚持少依赖，则真正移除 POI 依赖、纯自研；否则保留 EasyExcel 路径并停掉自研 SAX。当前"两边都占"最违背战略 |
| **自研分布式锁（6 种）** | FAIR/MULTI/RW/SEMAPHORE 零外部消费；`RedisMultiLock:435` 裸 `expire` 自造续期与看门狗重复 | 裁剪到实际消费的 REENTRANT + 幂等；续期统一走看门狗；**不引入 Redisson**（那才是违背少依赖） |
| **自研可观测（sentry）** | `OtelSdkBuilder/YdszOpenTelemetry/YdszSpan` 自研 OTel SDK；`OtelTraceInfoExtractor` 用反射适配 | 保留自研，但**简化**：反射式 OTel 适配改为直接依赖 `opentelemetry-api`（一个稳定小依赖，换取无反射），或干脆去掉 OTel 兼容层、专注自有 traceId 体系 |
| **seata 模块** | 已引入 Seata 依赖，又自研 TCC/SAGA 协调器，重复 | **二选一消除重复**：若自主可控优先，则彻底自研并**移除 Seata 依赖**；若保留 Seata，则删除自研 TCC/SAGA/XID 签名器。不要"依赖 + 自研"并存 |

### 3.3 空壳与诚实性（宣称了就要交付，否则撤下）

| # | 缺口 | 证据 | 处理 |
|---|------|------|------|
| G1 | **TOTP 2FA 完全缺失** | 全仓 grep 无实现，仅 README 提及 | 自研 TOTP（HMAC-SHA1 时间窗，几十行可实现）或撤下宣称 |
| G2 | **FeatureFlag 缺失** | 仅 `CoreProperties` 灰度雏形 | 自研轻量开关（配置中心驱动）或撤下宣称 |
| G3 | **SCHEMA 租户模式空壳（等同零隔离）** | `TenantContextWebFilter:110-111` 仅写 schema 名，无 `search_path` 下发 | 实现 PG `search_path` 真实切换，否则删除该模式 |
| G4 | **OCR 半成品** | `OcrEngine` 仅接口，无引擎 | 自研/集成离线 OCR 或撤下宣称 |
| G5 | **搜索缺 ES + 死依赖残留** | `search/engine/` 仅 pg/memory；`pom.xml:63-81` 保留 es/solr/opensearch 依赖未清理 | 补 ES 或**删除死依赖与死配置**（后者是纯收益） |
| G6 | **DataScopeIdExpander 空壳** | 仅接口无实现（下级部门展开） | 补实现或标注暂不支持 |
| G7 | **AliyunSmsProvider 伪实现** | 自制 HMAC 头非官方协议，`queryBalance()` 硬编码 -1 | 改为真实协议或降级改名"通用 HTTP 短信" |
| G8 | **ActiveMQ 空目录** | `mq/active/` 空目录，却宣称 5 种 MQ | 补实现或从文档移除 |
| G9 | **统一存储能力落差** | `QiniuStorage:499`/COS/OBS 部分方法静默返回 null/空 | 补齐或显式抛"不支持"，禁止静默空返回 |

### 3.4 依赖治理（「少依赖」战略的落地纪律）

> 这是本次校准后**新增的重点维度**：既然要少依赖，就要有量化治理，否则"少依赖"只是口号。

| # | 问题 | 证据 | 建议 |
|---|------|------|------|
| D1 | **无依赖白名单与审计机制** | 全仓依赖靠人工收敛，无 CI 门禁 | 建立 `dependency-check`（OWASP）+ 依赖白名单，CI 拦截新增依赖 |
| D2 | **死依赖未清理** | 搜索 es/solr/opensearch、`additional-spring-configuration-metadata.json` 死配置 | 每季度 `dependency:analyze` 清理 unused/undeclared |
| D3 | **重复依赖面** | Redis 15 种 ops 大半无人用、分布式锁 6 种 4 种零消费、safe 模块 XSS 3 套/限流 3 套/CSRF 2 套 | 各收敛为单一路径，缩减公共面 |
| D4 | **自研与依赖并存冲突** | Excel 自研 SAX + POI 并存；seata 依赖 + 自研 TCC/SAGA 并存 | 见 3.2，各二选一 |
| D5 | **供应链安全** | 内网无仓库镜像/CVE 扫描 | 建内网 Maven 镜像 + 定期 CVE 扫描，版本统一收敛已有 |

### 3.5 体验改善（保留，与自研无关）

| # | 问题 | 证据 | 建议 |
|---|------|------|------|
| E1 | 统一响应过度 | `BaseResponse` 545 行、重载 10+、3 个 volatile 懒加载字段 | 收敛 `code/message/data/traceId`，`msg→message` |
| E2 | 注解碎片化 | `@AuthRowPermission` 与 `@DataScope` 语义重叠 | 收敛为统一入口 |
| E3 | 配置项爆炸 | `FeignProperties` 11 组、`SeataProperties`~250、`SentryProperties`~400 行默认全开 | 提供"默认即安全"开关，重型能力默认关 |
| E4 | 反射脆弱实现 | `TenantContextWebFilter:253-266` 反射拼 `invokeGetPermissionIds` | 改类型化 SPI |
| E5 | 文档夸大 | "30 倍/3-5 倍"无佐证、`ASMFieldAccessor`/`sentry` 命名误导 | 如实标注，去夸大 |

---

## 四、安全专项（最高优先级，不变）

| 级别 | 问题 | 证据 | 建议 |
|------|------|------|------|
| 🔴 高危 | XSS 正则架空 OWASP | `XssFilter:159` 走自定义正则 | 改用已引入的 OWASP sanitizer |
| 🔴 高危 | 刷新锁 fail-open | `TokenBlacklistService:178-181` | 改 fail-closed |
| 🟠 中危 | 列名未白名单校验 | `DataScopeHelper:79-116` | 列名/表名白名单 |
| 🟠 中危 | Feign 头透传信任链 | `FeignRequestInterceptor:56-66` | 下游强制重验 JWT，头仅作上下文 |
| 🟡 低 | 审计日志静默丢弃 | `AsyncAuditRecorder:179-185` DISCARD_OLDEST | 改 CALLER_RUNS/磁盘兜底 |

---

## 五、落地路线图（v2）

### P0（2-4 周，正确性 + 安全 + 数据完整性）
1. XSS 统一改用已引入的 OWASP sanitizer（`safe`）
2. 刷新锁 fail-open → fail-closed（`auth`）
3. `NoopEventPublishGateway` 返回 false + 强制 fail-on-noop（`event`）
4. 修复 `FrequencySketch` 计数器定位算法（`cache`，**保留自研**）
5. 文件上传 `getBytes()` → 流式（`file`）
6. 列名/表名白名单校验（`jdbc`）
7. 修复 requestId/traceId 赋值 bug（`exception`）
8. app 模块 POM 重复声明修正（`ydsz-common/pom.xml`）

### P1（本季度，自研工程化 + 空壳补齐/诚实化）
1. 自研 JSON 清注释、去 ASM/SIMD 虚假宣传、明确零依赖定位
2. 自研缓存补 admission 逻辑核对 + 单元测试
3. Excel 二选一：去 POI 纯自研 / 停自研 SAX 用 EasyExcel
4. 自研 TOTP 2FA（或撤下宣称）
5. 自研 FeatureFlag（或撤下宣称）
6. 修复/删除 SCHEMA 租户模式
7. OCR 集成或撤下宣称
8. 搜索死依赖 es/solr/opensearch 清理
9. seata 二选一消除重复（去自研 TCC/SAGA 或去 Seata 依赖）
10. 分布式锁裁剪到 REENTRANT + 幂等，续期统一看门狗
11. XSS/限流/CSRF 各收敛单一路径
12. 补 DataScopeIdExpander 实现或标注
13. AliyunSmsProvider 真实化/改名
14. JWT 解析加本地缓存 + 布隆过滤器

### P2（下季度，依赖治理 + 基座瘦身）
1. 建立依赖白名单 + OWASP dependency-check CI 门禁
2. 合并 base/web/app，删除无消费方的 app 模块
3. 合并 socket/netty 韧性基建
4. 统一响应收敛四字段、RBAC 注解收敛
5. 清理死代码（MeteredThreadPoolExecutor、Redis 无人 ops、util 冗余包）
6. 配置项瘦身 + "默认即安全"
7. 限流桶 TTL 淘汰 + 竞态修复
8. 审计队列策略改 CALLER_RUNS
9. 类型化 SPI 替换反射调用
10. 文档去夸大、能力如实声明
11. 补齐/标注统一存储能力落差
12. 内网 Maven 镜像 + CVE 扫描接入

---

## 六、一句话结论

**自研这条路是对的，错的是「自研没做扎实」和「依赖没管严」。** 把精力从「换成开源库」转移到「把自研组件的算法写对、把空壳补上、把文档说真话、把依赖白名单立起来」，才是内网自主可控项目该走的路。

---

*报告完（v2 · 自主可控基准版）*
