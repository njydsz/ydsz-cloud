# ydsz-backend 自研微服务框架后续优化完善建议

> 版本：v1.0（草案）
> 生成日期：2026-08-04
> 对标对象：RuoYi / Jeecg-Boot / Guns（内部快速开发框架）+ 互联网大厂（阿里/美团/字节等）研发规范
> 适用范围：ydsz-backend（ydsz-common 30 子模块 + 10 业务部署单元）及 ydsz-frontend
>
> **审计依据**：基于实际代码阅读（非仅 README），结合并发安全专项审查、测试/质量覆盖盘点、网关与可观测性盘点。

---

## 0. 框架前提与优化原则（再次明确）

本项目为内网自研开发框架，技术选型以**尽量不依赖外部框架**为前提，自研模块对标业界主流方案并深度定制，**不重复造轮子**。因此本报告全部优化建议遵循三条铁律：

1. **优化不替换**：不提议用 Jackson/FastJSON、XXL-Job、Drools、Flowable 等外部框架整体替换自研实现；
2. **内敛化优先**：聚焦自研模块内部的精简、去重、内敛化、测试补齐、性能提升、并发安全修复；
3. **基础设施例外**：Spring Boot/Cloud、Redis、PostgreSQL 等基础设施按业界标准演进，不属于"自研 vs 外部"讨论。

---

## 1. 现状评估摘要

项目整体成熟度**高于同类内部快速开发框架**（RuoYi/Jeecg/Guns）一个量级：自研 JSON/缓存/调度/规则/工作流五大引擎、ArchUnit 32 条架构规则、K8s + Helm + Argo Rollouts + HPA 部署体系、OTel/Jaeger 全链路追踪、CI 全流程质量门禁。**在"开箱即用、约定优于配置、降低认知负担"的核心价值上，框架本身已经交付了高完成度。**

但通过本次审计，仍发现了若干**高优先级、有据可依**的短板，集中在五个方面：

- **并发安全遗留缺陷**（common-json，2 个高危仍存在）；
- **测试覆盖结构性失衡**（4 个部署单元零测试、集成测试基类闲置）；
- **可观测性三支柱缺一**（Logs 无后端）与压测缺位；
- **Schema 管理处于占位态**（V1.0.0 为占位文件，CI 校验"宽松模式"）；
- **认知负担的二次收敛空间**（30 子模块 + 海量工具类仍有去重/内敛化余地）。

下文按**架构优化 → 功能增强 → 性能提升 → 体验改善**四维度展开，每条建议均给出**优先级（P0/P1/P2）、落地方向与预期收益**。

---

## 2. 架构优化

### 2.1 【P0】修复 common-json 并发安全遗留缺陷（缓存按 Class 隔离失效）

**现状**（已代码级确认）：

- `cache/SerializerCache.java:41/44` 的 `FIELD_META_CACHE`、`BEAN_SERIALIZER_CACHE` 均以 `Class<?>` 为唯一 Key；
- `provider/SerializationProvider.java:884-888` 首次加载时用**当前线程命名策略**烘焙 `FieldMeta.jsonName`（`final`，永久固化）；
- 不同 `JsonMapper`（不同 namingStrategy）共享同一全局缓存，**先加载者决定命名**，两个 Mapper 交错序列化时命名被污染。
- 另 `internal/JsonConfig.java:224` → `JsonParserUtil.setUseBigDecimal()` 写入**进程级全局 volatile static**，不受 `ThreadLocalSnapshot` 保护（`SerializationProvider.java:1093-1132` 保存/恢复字段中无 useBigDecimal），某 Mapper 开启 BigDecimal 会永久影响所有线程/所有 Mapper 的解析行为，与 `JsonConfig.java:254` 注释"天然线程安全"的承诺相悖。

> 注：记忆中的问题 (a)「ThreadLocalSnapshot 未覆盖 NAMING_STRATEGY」已在 `SerializationProvider.java:1115/1130` 修复，本次不再要求。

**建议落地路径**：
1. 缓存 Key 由 `Class<?>` 改为 `(Class, namingStrategy)` 复合键（或缓存实例内带命名策略标签），使不同策略 Mapper 隔离；
2. 将 `JsonParserUtil.useBigDecimal` 由全局 volatile static 改为 ThreadLocal，或并入 `SerializationContext.CONTEXT`，并在 `ThreadLocalSnapshot` 中保存/恢复；
3. 补充并发交错序列化测试用例（两个命名策略 Mapper 交替执行，断言字段名与 BigDecimal 行为互不污染）。

**预期收益**：消除多 Mapper 并发下的隐性命名污染与解析行为漂移，修复注释承诺与实际行为不一致的隐患。这是自研 JSON 引擎作为框架核心能力的**信誉基石**。

### 2.2 【P1】建立 Schema 一致性"强校验"闭环（替换占位态）

**现状**：`deploy/sql/README.md` 明确禁止 Flyway/Liquibase，采用"版本化脚本 + CI diff 校验"策略——方向正确、无需替换。但 `V1.0.0__init.sql` 当前为**占位文件**（真实 DDL 未从库导出），`verify/schema_check.sh` 处于"宽松模式"（仅查可执行性/语法/版本递增），**Schema 一致性实际尚未被真正守护**。`STATUS.md` 仅 V1.0.1 READY。

**建议落地路径**：
1. 从生产/开发库执行 `pg_dump --schema-only` 导出真实 DDL 回填 `V1.0.0__init.sql`（表数 ≥ 126、视图 ≥ 5 为验收线）；
2. 将 `schema_check.sh` 的 diff 校验由"宽松"切换为"严格"（比对期望 Schema，不一致即 CI 失败），并在 CI 中强制执行；
3. 为种子脚本建立"幂等性 + 数量断言"（字典/菜单/租户/超管条目数基线）。

**预期收益**：让"Schema 唯一事实来源"从口号变成真实守护，杜绝测试/生产环境漂移。这是内部框架**跨环境可迁移性的关键**。

### 2.3 【P1】网关与可观测性补齐"三支柱"（Logs 后端）

**现状**：`deploy/observability` 三支柱中 Metrics（Prometheus）+ Traces（OTel→Jaeger）齐全，**Logs 无后端**（otel-collector 无 logs pipeline，无 Loki/ELK）；Prometheus 仅静态配置 gateway/project 两个 job；无服务网格；APM 依赖自研 OTel 接入。

**建议落地路径**：
1. 引入 **Loki + Promtail**（或自研日志聚合）补齐 Logs pipeline，otel-collector 增加 logs 通道，与 trace_id/span_id 关联实现 **log↔trace 串联**；
2. Prometheus 抓取改为**Pod 自动发现**（K8s 注解/service monitor 模式），覆盖全部 10 个服务，而非仅 gateway/project；
3. 将既有 `SkyWalkingTraceContext`（feign 模块）的后端启用闭环，与 OTel/Jaeger 二选一并统一到一套 APM 面板，避免双栈维护；
4. 服务网格（Istio）视团队运维能力作为 P2 远期项，不强制。

**预期收益**：三支柱补齐后，跨服务排障（一条请求的 trace + 关联日志 + 指标）方可闭环，达到互联网大厂可观测性基线。

### 2.4 【P2】服务间调用契约统一与跨模块去重

**现状**：10 个服务间 Feign 调用已有 Fallback 全覆盖（良好），但 `docs`/`system` 等模块职责边界仍存在重叠（如统一文件管理、统一消息中心被多模块依赖），认知负担随模块数线性增长。

**建议落地路径**：
1. 对 30 个 common 子模块做一次"依赖/API 使用频率"盘点，识别**低使用率 + 高维护成本**的模块（如 thread/socket/netty 仅⭐⭐⭐），评估内敛合并或降级为可选 profile；
2. 将跨服务 Feign DTO 收敛到独立的 `*-api` 契约层，统一版本与兼容策略（接口变更显式版本化）；
3. 对齐大厂"BFF/网关聚合"实践，对网关层补充必要的响应聚合与协议编排能力，减少前端多次串行调用。

**预期收益**：降低公共库维护面，收敛跨服务契约，控制框架演进期的认知负载。

---

## 3. 功能增强

### 3.1 【P0】补齐测试覆盖结构性缺口

**现状**（已盘点）：10 个部署单元中 **gateway / system / cronjob / nextwiki 四个完全无测试**；`AbstractIntegrationTest`（Testcontainers，自动拉起 PG16 + Redis7）已存在但**无任何类实际继承**；JMH 仅覆盖 cache；`lefthook.yml` 的 pre-push **不跑后端测试**（仅前端 type-check/unit）。

**建议落地路径**：
1. 为 gateway/system/cronjob/nextwiki 补齐**最小冒烟级单测**（关键 Filter / 核心 Service / 路由规则），而非追求覆盖率数字；
2. 让 `AbstractIntegrationTest` 实际投入使用：至少为 jdbc（读写分离、数据权限 SQL 改写）、redis（多级缓存失效广播）、queue（RocketMQ 收发）各建 1-2 个真实容器集成用例；
3. 在 `lefthook.yml` 的 pre-push 中补充后端增量测试（`mvn test -Dtest=...` 或按变更模块跑），将质量左移到本地；
4. 为 json（核心引擎）、literule（规则引擎）、workflow（流程引擎）补充 JMH 基准，建立性能基线。

**预期收益**：把"ArchUnit 架构守护强、运行态单测弱"的结构性失衡纠正过来，避免核心引擎缺陷在运行时才暴露。

### 3.2 【P1】规则/工作流/调度引擎的可观测与治理增强

**现状**：自研 literule（30+ SPI 依赖反转）、workflow（BPMN 2.0）、cronjob（Leader/DAG/分片/自愈）功能完善，但对外暴露的是能力而非治理视角。

**建议落地路径**：
1. 为三个引擎补充**运行态治理 API**：规则命中率/分支覆盖率、流程实例卡点与超时、任务执行耗时分布，全部接入既有 metrics；
2. 将 `@DistributedScheduled` / 调度任务纳入统一的"任务血缘 + 幂等 + 告警"体系，与 notify/sentry 打通（已有基础）；
3. 规则引擎补充分片执行/并行评估能力（对标 Drools 高并发评估场景），支撑大数据量规则集。

**预期收益**：三个"自研引擎"从"能跑"进阶为"可治理、可观测、可优化"，提升对业务方的交付信心。

### 3.3 【P2】安全能力纵深（已强，补充前沿项）

**现状**：安全已非常强（XSS/SQLi/CSRF/脱敏18种/限流7算法/签名/TOTP 2FA/自动封禁/字段加密）。对标大厂可补充：

- **供应链安全**：`dependency-check`（OWASP）已接入，建议补充 SBOM 生成（CycloneDX）与依赖定期巡检，CI 增加 `-DskipDependencyCheck=false` 默认开启的阈值；
- **AI Agent 安全**（ydsz-agent）：LLM 输出注入防护、prompt 注入检测、Agent 工具调用的权限最小化与审计（目前 agent 已有护栏，建议外化评估项）；
- **密钥管理**：将字段加密/API 签名密钥从配置外置到 KMS/密钥中心（如阿里云 KMS），实现密钥轮换。

**预期收益**：安全能力从"防御性"升级为"纵深 + 合规可审计"，适配信创/等保场景。

---

## 4. 性能提升

### 4.1 【P0】JSON 引擎并发安全修复后的缓存命中率优化（承接 2.1）

修复 2.1 后，进一步优化 `SerializerCache`：引入**按命名策略分桶 + 无锁（ConcurrentHashMap per-bucket）+ 二级缓存**（首级策略无关字段、次级策略相关字段），把多策略场景下重复烘焙的字段元数据复用起来，避免修复后缓存翻倍带来的内存与首包开销。

### 4.2 【P1】多级缓存与 DB 访问层的压测基线

**现状**：cache 模块实现 11 种策略 + 多级缓存（Redis + 本地）+ JMH 基准，但**未落库到 CI/压测基线**；jdbc 有读写分离 + 4 种负载策略。

**建议落地路径**：
1. 将 cache JMH 基准固化为 CI 性能门禁（与历史基线 diff，超标即告警）；
2. 为读写分离、数据权限 SQL 改写建立 **k6/gatling** 全链路压测场景（当前无任何端到端压测配置），校准 HPA 阈值（当前 CPU 70% 触发）；
3. 对齐大厂"容量评估 + SLO 校准"：为关键接口（项目全生命周期、EVM、审批流）设定 P99 延迟目标并接入告警。

### 4.3 【P2】数据库层演进

- 大表（audit/消息/日志）分片策略已内置于 audit 模块（日/月/年分片），建议在 jdbc 层统一暴露分片配置，避免各模块重复实现；
- 评估 PostgreSQL 18 的新特性（增量物化视图、`MERGE`、`EXPLAIN` 工具）在查询层复用；
- 冷热数据分层（归档表 + 分区表）纳入 schema 版本管理。

---

## 5. 体验改善

### 5.1 【P1】开发者体验（DX）——降低认知负担

项目核心价值是"开箱即用、约定优于配置、降低认知负担"，因此 DX 是重要抓手：

1. **一键生成脚手架**：对标 Jeecg 的代码生成器，提供基于 DDD 五层架构的"API/领域/基础设施/Web"代码生成器（已有 gen-app.mjs 前端侧，后端侧建议对齐），从模板生成到路由/菜单/权限一键串联；
2. **统一错误码 & 调试面板**：`/actuator/exception-codes` 已提供错误码文档端点，建议联动前端错误提示，实现"报错即自查"；
3. **本地启动体验**：`dev-start.sh` + docker-compose 已具备，建议补充"一键启动全部 10 服务"的脚本编排与端口健康预检，减少手工按序启动的摩擦。

### 5.2 【P1】文档与工程规范的可发现性

- 丰富 `CODE_COMMENT_STANDARD.md` / `PROJECT_CAPABILITY_MODEL.md` 的索引，将 `.trae/rules` 的编码规范沉淀为**可被 AI/IDE 读取的机器化规则**（已具备），并补充"从零到一搭一个业务模块"的端到端实操教程；
- 将各模块 README 能力清单与代码实际保持同步（能力模型已注明"基于代码审计"，建议建立 README 自动核对的 CI 检查）。

### 5.3 【P2】多环境与发布体验

- 灰度：`GrayLoadBalancer`（请求头）+ Argo Rollouts（流量）双机制并存，建议**统一为一个入口**（以 Argo Rollouts 为主，应用层灰度退化为可选项），避免双套维护与规则冲突；
- 发布：补充**蓝绿模板**（当前仅金丝雀），适配不同服务类型的发布诉求；
- 回滚：结合 schema "只追加不修改 + ROLLBACK 注释"，提供一键回滚脚本与版本回滚联动。

---

## 6. 优先级总览（Roadmap）

| 优先级 | 主题 | 模块/范围 | 建议条目 |
|---|---|---|---|
| **P0** | JSON 并发安全修复 | common-json | 2.1（缓存按策略隔离 + useBigDecimal 线程化） |
| **P0** | 测试结构性补齐 | gateway/system/cronjob/nextwiki + IT 基座 | 3.1 |
| **P0** | Schema 强校验闭环 | deploy/sql | 2.2 |
| **P1** | 可观测性三支柱补齐 | deploy/observability | 2.3 |
| **P1** | 性能压测基线 + 容量校准 | cache/jdbc/CI | 4.2 |
| **P1** | 开发者体验（脚手架/DX） | 全栈 | 5.1 |
| **P1** | 引擎治理增强 | literule/workflow/cronjob | 3.2 |
| **P2** | 契约收敛与模块内敛 | common 30 子模块 | 2.4 |
| **P2** | 安全纵深（SBOM/密钥管理） | 全局 | 3.3 |
| **P2** | 数据库演进 + 发布体验 | jdbc/deploy | 4.3 / 5.3 |

**建议启动节奏**：P0 三项为**当前迭代**必做（尤其 JSON 并发修复，直接关系框架核心能力可信度）；P1 按季度排入研发效能/稳定性迭代；P2 作为框架演进 backlog。

---

## 7. 一句话总结

框架已完成从"能用的内部脚手架"到"工程化完善的微服务基座"的跃迁，**当前最紧迫的不是新增能力，而是**：(1) 修复核心 JSON 引擎的并发安全漏洞、(2) 补足测试与 Schema 守护的结构性空缺、(3) 打通日志可观测性支柱。在此基础上，再以开发者体验与性能基线为抓手，向"产品级内部研发平台"演进。