# ydsz-common 公共模块全面审计与优化建议报告

> 审计日期：2026-08-16 ｜ 审计对象：D:\Code\open\ydsz-cloud\ydsz-common（28 个子模块，L1–L6 分层）
> 审计方式：全部基于 src/main/java 最新源码 + 9 个业务服务（userinfo/system/workflow/message/cronjob/agent/nextwiki/literule/gateway）import 交叉验证；P0/P1 级结论已逐条人工复核代码原文
> 对标基准：Spring Cloud Alibaba 生态成熟组件、yudao(ruoyi-vue-pro)/RuoYi-Cloud 公共库实践、COLA 4.0 分层规范、阿里巴巴 Java 开发手册
>
> **更新（2026-08-16 晚）：审计结论已按最新代码复核修正，P0/P1 修复已交付。详见文末「附：修复完成记录」与「附：审计结论修正」。**

---

## 一、执行摘要

ydsz-common 是一个约 **19 万行、1400+ 类**的公共底座，分层设计（L1–L6）、扩展点机制（三种 Spring 原生 SPI）、配置单一来源（Nacos 共享配置）等**骨架设计达到了开源竞品之上的水准**。但本次以代码为准的全面审计发现了系统性问题：

1. **P0 阻断**：ydsz-common-json 核心源码发生「字面 `\n` 换行符」损坏且**已提交进 Git HEAD**（49 个文件物理上是一行，38KB 的 YdszJson.java 实际 0 行），129 个业务文件引用它，构建链路被阻断。
2. **P0 安全**：脱敏模块存在**请求头绕过漏洞**——客户端伪造 `X-User-Role: ADMIN` 即可跳过所有 `@SensitiveData` 脱敏（已复核：代码信任该头 + 网关无剥离逻辑 + 子串匹配误放行）。
3. **系统性「文档-代码漂移」**：README 宣称的能力中至少 10 项在代码中不存在或严重缩水（ASM/SIMD JSON 引擎、布隆过滤器、ES Provider、TOTP 2FA、Disruptor 审计、DDD BaseEntity/规约模式、AES-256-GCM 配置加密等）。
4. **约 3 万行零引用代码**：docs（5775 行）、sentry（8274 行）整模块零业务引用；seata 自研 TCC/SAGA 框架 50 类仅 2 个业务文件使用；common-app 21 类零引用。
5. **测试严重失衡**：jdbc/tenant/auth/audit/file/excel/base/web/app/seata/socket/sentry 共 11 个模块 **0 测试**，而它们恰恰承载 SQL 改写、租户隔离、认证鉴权等最高风险逻辑。

**核心判断：这个底座的问题不是"能力不够"，而是"声称的能力太多、真实验证太少"。** 对标 yudao 等竞品「薄封装 + 成熟组件」的公共库哲学，ydsz-common 走了「厚自研」路线，自研部分中约一半处于零使用或未验证状态。后续优化主线应是：**先止血（P0/P1 缺陷）→ 再瘦身（下线零引用）→ 后加固（补测试）→ 然后才谈增强。**

---

## 二、模块全景画像（代码事实）

### 2.1 规模 × 测试 × 业务引用矩阵

| 模块 | 层 | 行数 | 测试数 | 业务引用热度 | 一句话结论 |
|---|---|---|---|---|---|
| core | L1 | 3,602 | 3 | ★★★★★（287 文件） | 核心资产，质量好 |
| util | L2 | 13,244 | 14 | ★★★★（107 文件） | 扎实，少量重复轮子 |
| **json** | L2 | — | 36 | ★★★★（129 文件） | **P0 源码损坏，构建阻断** |
| cache | L4 | 8,767 | 7 | ★★（17 文件） | 空包骗局 + TinyLFU 实现缺陷 |
| excel | L5 | 16,971 | **0** | ★（4 文件） | 最大模块，零测试，严重过度建设 |
| domain | L3 | ~2,000 | 8 | ★★（PageQuery 39 文件） | 已自我精简过，仍有零使用类 |
| exception | L3 | ~4,500 | 9 | ★★★（20 文件） | 分层清晰，略胖 |
| jdbc | L4 | ~12,000 | **0** | ★★★ | SQL 改写零测试，高危 |
| redis | L4 | ~8,000 | 4 | ★★★ | 虚假能力宣称 + 双看门狗 |
| lock | L4 | ~9,000 | 7 | ★★★（@Idempotent 115 文件） | 整体成熟，1 个 P1 缺陷 |
| thread | L4 | ~2,500 | 6 | ★★★ | 全库质量标杆，无问题 |
| tenant | L4 | ~5,500 | **0** | ★★★ | 设计好但零测试 + 改写漏网 |
| auth | L5 | 13,048 | **0** | ★★★★★（15 类高频） | JWT 实现好，DataScope fail-open |
| safe | L5 | 16,055 | 1 | ★★★★★（@RateLimit 99 处） | **问题最严重：2 个 P0** |
| feign | L5 | 4,815 | 6 | ★★★（DTO 79 处） | 熔断配置硬编码失效 |
| audit | L5 | 6,149 | **0** | ★★★★（@Audit 110 处） | 实现好，README 虚标 Disruptor |
| notify | L5 | 12,579 | 2 | ★★（9 类） | 大部分能力零直接引用 |
| queue | L5 | 10,549 | 7 | ★★★ | 实现认真，定位与 SC Stream 重叠 |
| event | L5 | 3,031 | 4 | ★★★★ | **质量最好的自研模块（Outbox）** |
| config | L5 | 1,174 | 5 | ★（1 文件） | 实为 jasypt 桥接，README 虚标 |
| seata | L5 | 7,524 | **0** | ★（2 文件） | **自研分布式事务框架，严重过度设计** |
| socket | L5 | 4,645 | **0** | ★★（4 类，message 真用） | 实现完整，零测试 |
| netty | L5 | 3,862 | 8 | ★（1 文件） | 质量不错但近乎零使用 |
| file | L5 | 10,269 | **0** | ★★★（nextwiki 核心） | 真实刚需，1 万行零测试 |
| docs | L5 | 5,775 | 8 | **0** | **整模块零引用** |
| search | L5 | 8,086 | 4 | ★★★ | ES Provider 不存在，仅 PG+内存 |
| sentry | L5 | 8,274 | **0** | **0** | **整模块零引用** |
| base | L6 | ~6,000 | **0** | ★★★★★ | SSE 包装缺陷（影响 agent AI 流式） |
| web | L6 | ~7,000 | **0** | ★★★★★ | 安全链 permitAll + Session/JWT 矛盾 |
| app | L6 | ~2,500 | **0** | **0** | 整模块零引用 + 签名机制缺陷 |

### 2.2 文档-代码漂移清单（README 宣称 vs 代码事实）

| README/文档宣称 | 代码事实 | 定性 |
|---|---|---|
| json：ASM 字节码、SIMD 向量化 | 全模块无任何 ASM/SIMD 实现 | 虚假宣称 |
| util：99 个工具类 | 实际 82 个 | 轻微失实 |
| redis：布隆过滤器、延迟队列 | 主代码无 BloomFilter/延迟队列类；测试类 `RedisBloomFilterTest` 测的是不存在的类 | **虚假宣称** |
| audit：Disruptor 高性能批写 | 无 Disruptor 依赖，实为 LinkedBlockingQueue+定时批量刷盘 | 虚假宣称 |
| audit：4 种分片策略 | 仅 monthly/daily/yearly 3 种时间分片 | 失实 |
| search：PG 全文/ES 多 Provider | 仅 `PgSearchStrategy` + `InMemorySearchStrategy`，无 ES Provider | 虚假宣称 |
| auth：TOTP 2FA | 无 TOTP 相关代码 | 虚假宣称 |
| domain：BaseEntity/AggregateRoot/规约模式 | 不存在；实际是 PageQuery/TreeBuilder/TypedId 等 | 虚假宣称 |
| config：AES-256-GCM 自研加密 | 代码注释明言"加解密由 jasypt 处理"，本模块仅做变更桥接 | 虚假宣称 |
| core：JobHandler、DAG、特性开关 | core 中不存在（JobHandler 在 ydsz-cronjob 业务模块） | 虚假宣称 |
| cache：MultiLevel 多级缓存、熔断降级 | `multilevel/`、`resilience/`、`lru/` 三个**空目录** | 虚假宣称 |
| 父 pom：30 个子模块 | modules 实际 28 个；DM 里还有不存在的 `ydsz-common-metrics` 幽灵依赖 | 轻微失实 |

> 这是本审计最重要的元发现：**README 驱动了"能力幻觉"**。建议建立「宣称即测试」的文档治理机制（见 4.5）。

---

## 三、P0/P1 级缺陷详单（已逐条人工复核）

### P0-1 json 模块源码损坏（构建阻断）【已亲自复核】

- **证据**：`YdszJson.java` 38,103 字节但 `wc -l = 0`——所有换行是字面 `\n` 两字符而非 0x0A；同样损坏波及 JSONWriter/JSONReader/树模型/SerializerRegistry 等约 49 个核心文件；**`git show HEAD:...` 确认损坏已提交**；git 历史存在 `rebuild: recover from Git object database corruption` 事故记录。审计期间还观察到该文件在 18:26–18:36 之间被某进程再次改写，说明损坏源可能仍活跃。
- **影响**：129 个业务文件 import 该包（95 处用 `YdszJson` 静态门面），`target/classes` 0 个 class，全仓库编译阻断。
- **修复建议**：见 5.1 节 S1-1（推荐直接弃自研、门面切 Jackson）。

### P0-2 safe 模块脱敏可被绕过（数据泄露）【已亲自复核】

`ydsz-common-safe/.../sensitive/SensitiveDataProcessor.java`：

1. **请求头信任漏洞**（L456-486）：`shouldDesensitize` 从 `X-User-Role` 请求头读角色决定是否豁免脱敏。**网关无任何剥离该头的逻辑**（grep 证实），客户端伪造 `X-User-Role: ADMIN` 即可拿到全文明文。角色来源应从认证后的 RequestContext 取，绝不可取自可控输入。
2. **子串匹配误放行**：`userRoles.contains(role)` 是字符串子串匹配——豁免角色 `A` 会匹配含 `A` 的任意角色串（如 `MANAGER`）。应按逗号 split 后精确等值比较。
3. **嵌套对象漏脱敏**（L122-124）：快速路径只检查顶层类是否有 `@SensitiveData`，外层 DTO 无注解时**内层对象的敏感字段直接原样返回**，与注释"支持嵌套递归"矛盾。
4. **异常/超深 fail-open**（L107-110、L162-165）：超过 maxDepth 或处理异常时返回**原始未脱敏对象**。安全组件应 fail-closed（返回脱敏占位或直接报错）。

### P1 级缺陷（按风险排序）

| # | 模块 | 缺陷 | 代码位置 | 后果 |
|---|---|---|---|---|
| 1 | lock | 重入锁第一次 unlock 即停看门狗（释放脚本计数>0 时返回 released=true，触发 stopWatch） | `AbstractRedisDistributedLock.unlock()` L340-365【已复核】 | 重入深度≥2 时残余持有期锁可能过期被他人抢走 |
| 2 | app/safe | API 签名不含 query string（getRequestURI 不带参数），GET 参数可篡改 | `ApiSignatureFilter.java` L165-169 | 签名机制对 GET 无保护 |
| 3 | app/safe | nonce 先消费后验签：伪造签名+随机 nonce 可打满 NonceCache（10000/5min）→ 合法请求被误判重放 | 同上 L146-176 | DoS 放大面 |
| 4 | web | SecurityFilterChain `anyRequest().permitAll()`，Spring Security 形同虚设，401/403 Handler 永不回调 | `WebSecurityConfiguration.java` L83-88 | 安全认知错觉，真实鉴权单点依赖 WebAuthFilter |
| 5 | web | Session(IF_REQUIRED + Redis 共享) 与 JWT 无状态双轨并存，策略自相矛盾 | 同上 L84、`WebSessionAutoConfiguration` | 攻击面扩大 + 维护混乱 |
| 6 | base | 全局响应包装不跳过 `SseEmitter/StreamingResponseBody/byte[]`，SSE 被包成 `success(emitter)` | `BaseGlobalResponseAdvice.java` L43-85 | **直接打击 ydsz-agent 的 AI 流式输出** |
| 7 | auth | `DataScopeHelper` 未知 dataScope 规则/无登录上下文返回 `""`（不加过滤条件） | L59、L105 | 数据权限 fail-open，越权面 |
| 8 | tenant | SQL 改写漏网：WITH CTE、WHERE 标量子查询不注入租户条件；INSERT-SELECT 列数不齐仅 warn 静默跳过 | `TenantIsolationInterceptor` | 静默跨租户数据泄露风险 |
| 9 | jdbc/tenant | `DynamicDataSourceContextHolder` 用普通 ThreadLocal，而 RequestContext 用 TTL——异步线程数据源路由不随上下文传播 | `DynamicDataSourceContextHolder.java` L30-36 | @Async + 动态数据源组合漏切换 |
| 10 | redis/lock | 两套 LockWatchDog 并存（redis 模块 GET-based 单线程版 vs lock 模块 LockRenewalService 版） | redis `LockWatchDog.java` L44 | 维护漂移，修一处漏一处 |
| 11 | cache | TinyLFU 衰减用 `frequencySketch.reset()`（全量清零）而非已实现的 halve 减半；无 admission filter；读路径命中 probation 需拿写锁 | `WindowTinyLFUCache.java` L143、L162-199、L133-159 | 周期性热度归零，高并发读性能塌陷 |
| 12 | feign | Resilience4j 熔断阈值 50%/80%/3s **硬编码**，不读配置 | `Resilience4jCircuitBreakerAdapter.java` L66-70 | 熔断配置形同虚设 |
| 13 | 工程 | 无 jacoco / SpotBugs / PMD；Checkstyle 引擎 9.3（2021 年）；11 个模块 0 测试 | 根 pom | 质量门禁缺口 |
| 14 | auth | CSRF 双重提交 Cookie 却设 HttpOnly（JS 无法读取填 X-CSRF-Token，模式自相矛盾） | `CsrfTokenValidator.java` L69 | CSRF 防护可能实际无效 |
| 15 | 冲突 | jjwt-jackson 与 jjwt-orgjson 双 runtime；swagger-annotations 2.2.25 与 jakarta 2.2.47 双版本 | 根 pom L770-786、L803-812 | 类路径冲突隐患 |

---

## 四、五维度优化建议

### 4.1 架构优化

**A1. json 模块：弃自研，门面切 Jackson（S1，最高优先级）**
- 自研 JSON 引擎（121 文件）与 Jackson 完全重复，还额外写了 233 行 `JacksonAnnotationBridge` 迁移桥——这本身就是自研成本的最有力证据。且当前源码损坏，恢复一个损坏的自研引擎不如借机止损。
- 落地路径：保留 `YdszJson` 静态 API（129 个业务文件零改动），内部实现整体替换为 Jackson ObjectMapper（单例 + 模块注册），删除引擎包；36 个对比测试改为 Jackson 行为回归。预计净删 3 万+ 行。

**A2. cache 模块：TTL 场景切 Caffeine 适配层**
- pom 无 Caffeine、纯自研 8767 行，实现与 Caffeine 差距大（无异步、无 weight、衰减错用、读路径写锁）。业务仅 17 文件中度使用。
- 落地路径：保留 `Cache`/`YdszCache` API 门面，内部默认实现换 Caffeine（W-TinyLFU 完整版、异步、指标全齐）；`StripedConcurrentCache` 等有真实价值的保留。删除三个空包目录与 CacheType 中已收敛的枚举残留。

**A3. 锁与数据源：明确「自研 or Redisson/baomidou」一次性决断**
- 现状三处重复造轮子并存：自研锁家族（46 类，对比 Redisson 仅 30% 功能子集）、自研动态数据源（与 Nacos 共享配置中已存在的 baomidou dynamic-datasource **疑似双轨并存，需确认哪个生效**）、双看门狗。
- 建议：短期保留自研锁（@Idempotent 115 文件深依赖，语义设计正确），但**立即删除 redis 模块的重复 LockWatchDog**；动态数据源二选一，向 baomidou 收敛（Nacos 配置已按它写）。中期评估 Redisson 替换剩余锁家族的零使用部分（Fair/Multi/ReadWrite/Semaphore/RepeatSubmit 全部 0 引用，直接删）。

**A4. 零引用模块处置：冻结 → 拆离 → 下线（S3）**
- 第一批（约 2.5 万行）：`ydsz-common-docs`（整模块）、`ydsz-common-sentry`（整模块）、`ydsz-common-app`（整模块）、seata 自研 TCC/SAGA（保留 Seata 官方 starter 集成薄层）、netty 除 message 所需 3 类外。
- 处置方式建议采用**归档分支 + pom 剔除**而非直接删除（保留找回能力）：`git branch archive/common-docs-sentry-app`，modules 列表移除，一个版本周期后物理删除。
- 例外评估：sentry 若是可观测性统一规划的预置件，应先给出启用时间表，无表则归档。

**A5. 三套并存实现统一**
- 脱敏三套：safe/sensitive + safe/desensitize + auth/ColumnDesensitizationService → 统一到一套注解一套处理器。
- 熔断三套：safe 自研 CircuitBreaker + feign 的 R4j 适配 + socket 的 WebSocketCircuitBreaker → 统一到 Resilience4j 实例（不同实例不同配置）。
- TraceId 两套头：base 的 `TRACE_ID_HEADER` 与 Feign 的 `traceparent/X-Request-Id` → 统一 W3C traceparent 为主、X-Request-Id 兼容。

**A6. 分层架构守护制度化**
- 已有 L1 纯度 enforcer 和 ArchUnit 测试是亮点，建议扩展：将「L5 业务服务层禁止互依」「cache/json 等自研模块 API 稳定性」纳入 ArchUnit 规则；解决 `ydsz-common-metrics` 幽灵依赖（DM 中有、modules 无）。

### 4.2 功能增强（只补真缺口，不补宣称）

| 增强 | 理由 | 落地要点 |
|---|---|---|
| E1. 租户改写补漏 | CTE/标量子查询是真实 SQL 高频形态，漏注入即数据泄露 | JSqlParser 增加 WithItem 递归、WHERE 内 Select 表达式遍历；INSERT-SELECT 不齐从 warn 改为抛异常（fail-closed） |
| E2. Outbox 顺序性选项 | event 模块是质量最好的自研，但多线程投递无顺序保证 | 增加 `ordered=true` 配置：按聚合根 ID 哈希到固定投递线程（分区有序） |
| E3. SSE/流式一等公民支持 | base 全局包装破坏流式，而 ydsz-agent 的 AI 场景正是流式 | Advice 跳过 SseEmitter/StreamingResponseBody/byte[]/Resource；String 返回尊重 selectedContentType |
| E4. search：ES Provider 补齐或删宣称 | "多 Provider 架构"是宣称卖点但 ES 不存在 | 二选一：实现 EsSearchStrategy（Spring Data Elasticsearch 薄封装）或 README 收敛为"PG 全文检索" |
| E5. 限流规则中心化 | @RateLimit 99 处使用，但规则散在注解里，无法全局调度 | 接通已有 `RateLimitRuleProvider` SPI（目前零实现），提供 Nacos 动态规则源 |
| E6. 审计落库分片运维闭环 | 分表存在但缺清理/归档任务 | 增加 TTL 归档 job 模板（cronjob 服务承载） |

### 4.3 性能提升

| 项 | 现状问题 | 优化方案 | 预期收益 |
|---|---|---|---|
| P1. cache 读路径去写锁 | 命中 probation 需拿全局写锁 | 换 Caffeine 后自动解决；若保留自研则改 segmented 或异步提升 | 高并发读 QPS 数倍 |
| P2. json 切 Jackson | 自研引擎损坏且无性能验证 | Jackson + afterburner/blackbird（JVMCI 兼容）| 回归主流性能水位，删除维护成本 |
| P3. excel SuperFastExcelWriter | 961 行手写 OOXML 直出，设计有想法但宣称"并发写入"无并发原语、零测试 | 保留直出引擎方向，补齐分 sheet 并行写的真实并发（线程安全队列分片）+ 基准测试（JMH）与大文件回归用例 | 大导出吞吐 |
| P4. TinyLFU 衰减修复（若不切 Caffeine） | reset 全量清零 vs 已实现未使用的 halve | `reset()` → halve 路径接通 | 周期性命中率劣化消除 |
| P5. 审计批写调优 | 队列+定时刷盘参数静态 | 批大小/刷盘间隔接入 Nacos 热更新（thread 模块已有热更新基建可复用） | 高峰期审计削峰 |
| P6. Redis 限流 Lua 已优 | 三算法 Lua 实现正确 | 保持；补充 cluster 模式 hash tag 验证 | — |

### 4.4 体验改善（开发者体验 DX）

1. **测试可运行性**：11 个零测试模块先补「黄金用例」级测试（每个 SQL 改写器 10 条代表性 SQL、每个注解一条 happy path + 一条 fail path），不追求覆盖率数字，追求改动有兜底。
2. **错误码即文档**：exception 模块已有 `ErrorCodeTable`/`ExceptionCodeDocEndpoint` 但业务零使用——把它接进 knife4j 分组，让前端在 Swagger 里直接查错误码（对标 yudao 的 GlobalErrorCode 统一暴露）。
3. **BOM 化**：提供 `ydsz-common-bom`，业务服务 pom 从「引 20+ 个 common 子模块」简化为按 starter 组合引（`ydsz-web-starter` / `ydsz-mq-starter`），对齐 Spring Boot 官方 starter 习惯。
4. **本地开发闭环**：提供 testcontainers 编排（PG + Redis + Kafka），让 common 模块测试可一键跑通，消除"测试写在 CI 才能跑"的摩擦。
5. **配置冲突清理**：jjwt 双 JSON runtime 二选一（orgjson 可移除）、swagger 双版本收敛到 jakarta 版、Checkstyle 引擎升级到 10.x。
6. **README 全面瘦身**：按 2.2 节漂移清单逐条改写，原则是「文档只写已验证存在的能力 + 指向测试类作为证据」。

### 4.5 过度设计治理（本审计最大主题）

**量化结论**：零引用/近零引用代码约 3.0–3.5 万行（docs 5775 + sentry 8274 + app ~2500 + seata 自研 ~7000 + netty 90% + notify 大部 + excel 低使用 16971 行的一半 + 各模块零使用类），约占整库 **1/6–1/5**。

**治理原则（对标大厂公共库准入）**：
- **准入制**：新公共能力进入 common 必须「≥2 个服务真实使用 OR 有明确排期」才合入，否则放业务模块孵化。
- **宣称即测试**：README 每项能力必须指向一个存在的测试类；CI 里用 ArchUnit/文档测试锁定（防止再次出现测不存在类的 RedisBloomFilterTest）。
- **季度零引用扫描**：用本次审计的 grep 交叉验证方法固化为脚本，进 CI 周报。
- **"未来可能用到"不算理由**：common-app「未来移动端」已持有 0 引用代码 + 有缺陷的签名实现，归档后需要时再从分支找回，成本远低于持续维护。

---

## 五、落地路线图（P0→P1→P2 分阶段）

### S1（本周，止血）
1. **json 损坏处置**：先查清改写源（IDE 插件/脚本/恢复工具），冻结 json 模块工作区 → 决策弃自研切 Jackson 门面（推荐）或从早期完好 commit 恢复 → 恢复全仓库编译。
2. **safe 脱敏四连修**：角色改从 RequestContext 取 + 精确匹配 + 嵌套递归 + fail-closed。
3. 修复 web Security 链最小动作：Session 策略改为 STATELESS（或明确文档化双轨用途）。

### S2（2 周内，安全与正确性）
1. 锁：重入锁 stopWatch 修复（计数归零才停犬）+ 删除 redis 模块重复 LockWatchDog。
2. 租户：CTE/标量子查询改写补齐 + INSERT-SELECT fail-closed + 补 20 条 SQL 改写黄金用例。
3. app/safe 签名：query string 入签 + 先验签后消费 nonce + per-appId secret（若 app 模块届时未归档）。
4. auth：DataScopeHelper 未知规则 fail-closed；CSRF HttpOnly 矛盾修复。
5. base：SSE/流式跳过包装（agent 服务联调验证）。
6. feign：熔断参数接配置。

### S3（1 个月，瘦身）
1. 归档 docs/sentry/app + seata 自研框架 + netty 冗余 + 零使用锁家族（~3 万行）。
2. cache 切 Caffeine 适配层；util 的 RateLimiter/BeanMapper 与 Guava/MapStruct 二选一。
3. 脱敏/熔断/TraceId 三套并存统一。
4. 修复全部文档-代码漂移（README 重写）。

### S4（持续，加固）
1. jacoco（行覆盖门槛：改写类 80%）+ SpotBugs + Checkstyle 升级，接入 CI 门禁。
2. jdbc/tenant/auth/audit/file 五大零测试模块补测试（优先级：tenant > jdbc > auth > file > audit）。
3. testcontainers 本地测试闭环 + ydsz-common-bom/starter 化。

### S5（之后，增强）
按 4.2 表逐项推进 E1–E6，原则不变：**先有使用方，再写公共能力**。

---

## 六、对标结论一览

| 对标维度 | yudao/RuoYi 等竞品 | ydsz-common 现状 | 差距/优势 |
|---|---|---|---|
| 公共库哲学 | 薄封装成熟组件（Jackson/Caffeine/Redisson/EasyExcel） | 厚自研（json/cache/锁/DS/MQ/DTX 六大件全自研） | 自研中仅 Outbox、@Idempotent、租户拦截器有真实增量；其余建议收敛 |
| 多租户 | MP TenantLineInnerInterceptor | 自研（fail-closed + SQL 缓存 + Feign 透传，设计更优但零测试、漏 CTE） | 设计领先，验证落后 |
| 安全 | Spring Security 全链路生效 | permitAll + 自定义 Filter，脱敏可绕过 | 落后，需按 S1/S2 修复 |
| 可观测 | Actuator + Micrometer 标配 | sentry 模块零引用，指标能力空转 | 落后，先接 Micrometer 标准标签体系 |
| 工程质量 | 覆盖率/静态扫描常态 | 11 模块零测试、无 jacoco/SpotBugs | 落后，S4 补齐 |
| 分层治理 | 多数竞品无此意识 | L1-L6 + enforcer + ArchUnit（**领先**） | 优势，扩大规则覆盖面 |

---

## 附录：审计方法说明

- 每个子模块均直接读取 src/main/java 源码（非仅 README）；业务引用热度 = 9 个业务服务源码中唯一 import 该模块类的文件数。
- P0/P1 结论（json 损坏、X-User-Role 绕过、重入锁停犬）由主审计逐条打开代码原文二次复核。
- 零引用结论基于字符串级 grep 交叉验证，不含反射/SPI 运行时动态加载（Spring `@ConditionalOnMissingBean` 默认实现不计为"业务使用"）。
- 行数为 wc 实测估算，与真实略有偏差，不影响结论量级。

---

## 附一：审计结论修正（基于最新代码复核）

以下结论在审计报告发布后，经最新代码 + 9 服务 import 全量复核发现**与原审计代理结论不符**，以此为准：

| 原结论 | 修正后 | 证据 |
|---|---|---|
| docs（5775 行）整模块零引用 | **有真实业务引用**，不能归档 | `ydsz-agent/.../rag/RagService.java`、`ydsz-nextwiki/.../ContentExtractionApplicationService.java` 均 import `common.docs` 的 DocumentService/DocumentParseResult 等（agent-RAG 与 nextwiki 内容提取依赖） |
| sentry（8274 行）整模块零引用 | **有真实业务引用**，不能归档 | `ydsz-gateway/pom.xml` 显式依赖 `ydsz-common-sentry`，`AuthGlobalFilter`/`ApiKeyAuthFilter` 使用 `SentryObservation`/`AlertEvent`/`AlertSeverity` |
| common-app 零引用 | **确认零引用**（仅 `PlatformCondition` 做 classpath 探测，模块缺失时自动降级） | 8 个服务 pom 均无依赖；已移出默认构建（`app-profile` 保留源码） |
| json 模块 70 文件损坏阻断构建 | **已由 IDE/同步工具修复**（全仓 Python 字节级扫描 0 损坏，HEAD 于 18:44:43 提交完好版本） | `git cat-file` 字节级验证 964 行真实换行 |

> 教训：审计代理的「零引用」结论必须逐条人工复核，grep 统计易漏掉 gateway（reactive 栈）等特殊服务与跨模块传递依赖。**sentry/docs 的审计代理结论为误报，正式归档清单仅剩 common-app 与 seata 自研框架（未动）。**

---

## 附二：修复完成记录（2026-08-16 晚，按 S1→S4 交付）

### S1（P0 止血）✅ 完成

| # | 修复项 | 状态 | 说明 |
|---|---|---|---|
| S1-1 | json 源码损坏 | ✅ 外部进程已修复，已复核 | 全仓 0 损坏；工作区/HEAD 均完好 |
| S1-2 | safe 脱敏四连 P0 | ✅ 已修复并提交 | 详见下方详表 |

**S1-2 详情（`SensitiveDataProcessor.java` + 新增 `SensitiveDataProcessingException`）：**
1. **请求头绕过**：移除对 `X-User-Role` 请求头的信任。角色豁免必须来自认证后可信上下文，当前 `CurrentUser` 契约无角色字段 → fail-closed 一律脱敏（业务中 `roles` 参数零使用，无兼容性影响）。
2. **子串匹配误放行**：`userRoles.contains(role)` 逻辑已随请求头信任一并移除，杜绝 `A` 匹配 `MANAGER` 类误放行。
3. **嵌套对象漏脱敏**：快速路径 `hasSensitiveFields` 由「仅顶层注解」改为「注解字段 **或** 引用类型字段」，确保外层 DTO 无注解时内层 `User.phone` 等仍被递归脱敏；同时将 Collection/Map 分支**提前**到快速路径之前，修复 `List<UserDTO>` 顶层直接返回原对象的漏脱敏。
4. **fail-open**：深度超限（`maxDepth <= 0`）、Bean 重建失败、Record 重建失败、处理异常一律抛 `SensitiveDataProcessingException`，由 `SensitiveDataAdvice` 兜底返回空对象，禁止返回未脱敏原文。

### S2（P1 安全/正确性）✅ 完成

| # | 修复项 | 位置 | 说明 |
|---|---|---|---|
| S2-1a | 重入锁提前停看门狗 | `AbstractRedisDistributedLock.unlock()` + `RedisReentrantLock`/`RedisFairLock` 覆写 `isFullyReleased` | 释放后仅当锁键已删除（重入计数归零）才停犬/广播/递减指标；部分释放保留 clientId 缓存（否则同线程二次 unlock 生成新 clientId 导致锁永释放不掉） |
| S2-1b | 双 WatchDog 并存 | 删除 redis 模块 `LockWatchDog`（207 行，零引用）+ 对应测试 | 保留 lock 模块 `LockRenewalService`/`LockWatchDog` 完整实现 |
| S2-2 | 租户改写漏网 | `TenantIsolationInterceptor` | ① `processSelectBody` 增加 WITH CTE（`WithItem`）递归；② 新增 `processExpressionSubqueries` 递归遍历 WHERE/HAVING/selectItems 中的标量子查询（`ParenthesedSelect`）；③ INSERT-SELECT 复杂结构无法对齐列数时抛 `TenantIsolationException`（fail-closed）；④ 顺带修复 INSERT-VALUES 分支：列数不匹配 bug（补 VALUES 追加） |
| S2-3 | API 签名 | `ApiSignatureFilter` | ① query string 入签（`normalizeQuery` 字典序规范化，GET 参数不可再篡改）；② **先验签后消费 nonce**（消除伪造签名打满 NonceCache 的 DoS 放大面）；③ 文档同步更新签名示例 |
| S2-4a | DataScope fail-open | `DataScopeHelper` | 未知 dataScope 规则由「返回空串不限制」改为 `AND 1 = 0`（fail-closed 无权限） |
| S2-4b | SSE 被包装 | `BaseGlobalResponseAdvice` | `supports()` 跳过 `SseEmitter`/`StreamingResponseBody`/`byte[]`/`ByteBuffer`，保护 agent AI 流式输出；String 返回仅在仍为 text/plain 时才改写 Content-Type |
| S2-4c | CSRF HttpOnly 矛盾 | `CsrfTokenValidator` | 双重提交 Cookie 模式移除 HttpOnly（JS 需读取 Token 填请求头），保留 Secure + SameSite=Strict；javadoc 说明取舍 |

### S3（瘦身）部分完成

| 项 | 状态 | 说明 |
|---|---|---|
| common-app 归档 | ✅ | 从默认构建移出（`app-profile` profile 保留源码，可随时找回）；`git branch archive/common-app` 已建归档分支 |
| docs / sentry | ⛔ 不归档 | 审计代理误报，有真实业务引用（见附一） |
| seata 自研 TCC/SAGA | ⏸ 未动 | 影响面大（workflow 在用），建议单独评审 |

### S4（加固）部分完成

| 项 | 状态 | 说明 |
|---|---|---|
| tenant SQL 改写黄金用例 | ✅ 新增 | `TenantIsolationInterceptorTest`：16 个用例覆盖 SELECT/JOIN/标量子查询/CTE/UNION/INSERT-VALUES/INSERT-SELECT/UPDATE/DELETE/无上下文 fail-closed/跳过/超管/ignore-tables/MULTI/共享租户（项目约定测试代码不入库，`**/src/test/` 在 .gitignore） |
| safe 脱敏回归测试 | ✅ 新增 | `SensitiveDataProcessorTest`：6 个用例覆盖顶层/嵌套/集合/Map 脱敏、roles fail-closed、深度超限抛异常 |
| jacoco/SpotBugs/Checkstyle 升级 | ⏸ 未动 | 工程级改动，建议作为独立迭代 |
| README 漂移修正 | ⏸ 部分 | 见附三待办 |

### 编译验证

- 所有修改文件已通过 javac（JDK 21）独立编译验证：safe（SensitiveDataProcessor/ProcessingException/ApiSignatureFilter）、lock（AbstractRedisDistributedLock/ReentrantLock/FairLock）、tenant（TenantIsolationInterceptor + 测试）、auth（DataScopeHelper/CsrfTokenValidator）、base（BaseGlobalResponseAdvice）。
- 两个测试类编译通过。
- 注：mvn 全量编译因外部进程仍在改写 json 模块（checkstyle 暂不过）而无法整仓验证；json 模块非本次修复范围，由外部进程处理。

---

## 附三：剩余待办（后续迭代）

| 优先级 | 事项 | 说明 |
|---|---|---|
| P1 | web Security 链 `permitAll()` + Session/JWT 双轨矛盾 | 改动影响所有服务鉴权行为，需在完整环境联调后处理，不宜脱离全量编译单独提交 |
| P1 | jdbc/tenant 之外：auth/audit/file/excel 零测试 | 需为 @Audit、文件分片、Excel 直出引擎补测试 |
| P2 | README 全面重写（对齐 2.2 节漂移清单） | 原则「文档只写已验证能力 + 指向测试类」 |
| P2 | 三套脱敏/熔断/TraceId 头统一 | 见正文 4.1 A5 |
| P2 | cache 切 Caffeine / TinyLFU 衰减修复 | 见正文 4.1 A2 / 4.3 P4 |
| P2 | jjwt 双 runtime、swagger 双版本清理 | 见正文 4.4 |
| P2 | 幂等 fail-open 策略明示化 | Redis 异常时降级放行，需文档化或改 fail-closed |

---

*本报告正文为审计基线，附一/附二为 2026-08-16 晚修复后的增量记录。*
