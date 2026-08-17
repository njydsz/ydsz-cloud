# ydsz-system 模块全面分析与优化建议

> 对标：若依 RuoYi、Pig、SpringBlade、JeecgBoot、maku-boot 等开源快速开发平台，以及 Apollo/Nacos 配置中心、阿里 Java 开发手册、Google Java Style Guide 等业界标准。
> 分析基线：`D:\Code\open\ydsz-cloud\ydsz-system` 当前最新源码（5 个 DDD 子模块，约 92 个源文件）。

---

## 〇、现状总览

| 维度 | 现状 | 评价 |
|------|------|------|
| 技术栈 | Java 21 + Spring Boot 4.1 + MyBatis-Plus 3.5.16 + PostgreSQL | 前沿，领先竞品一个身位 |
| 分层 | 严格 api/domain/infra/server/web 五层 | 结构完整，但领域层被架空（贫血模型） |
| 业务域 | 配置 / 字典 / 应用注册 / 变量 / 版本 / 多租户 / 全局搜索 | 覆盖面广，但多项「半成品」 |
| 工程规范 | checkstyle + spotless + enforcer 已配置 | 但 `checkstyle.skip=true`、spotless 未绑定构建，实际未生效 |
| 测试 | **0 个单元测试**（`src/test` 目录不存在） | 与 README「mvn verify 自动执行测试」严重不符 |
| 可观测 | Micrometer + Health + Sentry | 指标定义齐全，但命中率埋点一半未接线 |

**总体结论**：模块在「广度」上对标甚至超过主流竞品（配置+字典+变量+版本+租户+搜索），但在「深度」与「闭环」上存在明显短板——多租户物理隔离、读写分离、全局搜索等宣称能力未真正落地，且存在 **1 个数据越权缓存风险**、**多个缓存一致性缺陷**、**0 测试** 等硬伤。建议按 P0→P1→P2 分级收口。

---

## 一、架构优化

### 1.1 Repository 形同虚设，领域模型贫血（P1）
`ConfigRepository` / `DictRepository` 本质是 Mapper 的透传壳：
```java
public ConfigMapper getConfigMapper() { return configMapper; }
```
Service 层直接拿 Mapper 写 `QueryWrapper`，DDD 的 Repository 抽象没有形成，`domain` 层被架空。实体全是 `@Data + SuperBuilder` 的纯贫血对象，无任何领域行为与不变量。

**对标**：竞品多为传统 MVC 三层，本项目 DDD 是差异化优势，但当前实现是「三层架构套了 DDD 的壳」。

**建议**：
- Repository 应封装查询语义（如 `ConfigRepository.findByGroupAndStatus(group, status)`），而不是暴露 Mapper。
- 关键不变量下沉到实体/领域服务：配置值类型校验、字典项 `(typeCode,itemCode)` 唯一性、租户到期状态推导等，收口到 domain 层，避免各 Service 重复实现。

### 1.2 「上帝 Service」，职责过载（P1）
`ConfigServiceImpl` 单类承载 7 类职责：CRUD + 缓存 + 值校验 + 版本快照 + 事件发布 + 搜索索引同步 + 指标埋点，近 560 行。

**建议**：按职责拆分（应用服务编排 + 领域服务 + 横切关注点交给 AOP/事件）：
- 缓存失效、事件发布、索引同步、指标埋点抽取为「可插拔的横切切面」或领域事件订阅者，而非堆在写方法里。
- 版本快照逻辑已由 `EntityVersionService` 抽出，但触发点仍散落在 3 个 ServiceImpl，可统一为 `@Around` 切面或领域事件。

### 1.3 多租户物理隔离（ISOLATE_DB）未落地（P1）
`Tenant.datasourceKey` 字段已存在，注释声称支持 ISOLATE_DB 独立库路由，但代码中**没有任何动态数据源路由逻辑**。根 pom 引入 `dynamic-datasource 4.3.1` 也标注「读写分离 P2-3」未启用。

**对标**：SpringBlade 的租户管理 + 动态数据源是成熟方案；大厂 SaaS 普遍支持独立库/独立 Schema 隔离。

**建议**：要么明确「当前仅支持共享表 + tenant_id 逻辑隔离，ISOLATE_DB 为规划项」并收敛注释，要么补齐 `@DS` 路由 + 租户建库初始化流程。

### 1.4 数据库脚本缺失，交付不闭环（P0）
README 与根 pom 明确要求 `ydsz-system-web/src/main/resources/sql/init.sql`，但 `find ydsz-system -name "*.sql"` 返回空。9 张表（`ydsz_config` 等）无任何 DDL。

**建议**：补齐 `deploy/sql/init.sql`（含建表 + 索引 + 初始数据），否则模块无法独立初始化运行。

### 1.5 文档与代码不一致（P2）
- README 实体清单同时列出 `DictVersion.java` 与 `EntityVersion.java`，实际代码只有 `EntityVersion`（统一版本服务替代了三套独立版本服务）。
- 端口：README 正文写 **9001**，根 README 模块表写 **9002**，`bootstrap.yml` 待核。服务端口需统一。

---

## 二、功能增强

### 2.1 多租户能力「半成品」（P1）
- `TenantPlan` 实体仅 4 个字段（planCode/planName/description/sortOrder），**没有配额字段**；但注释反复提及「菜单配额 / 功能开关 / `TenantQuotaAspect` 配额拦截」，这些在代码中**均不存在**。
- 租户「到期自动降级/锁定」无任何调度任务实现（`@EnableScheduling` 已开但无相关 Job）。
- `TenantPlanMenu` 只有 `menuId`，无「按钮/数据权限/功能点」维度。
- `TenantServiceImpl.removeById` 直接逻辑删除，**不校验租户下是否有用户/数据**，会产生孤儿数据。

**对标**：JeecgBoot/SpringBlade 的租户管理含配额、到期、菜单权限绑定。

**建议**：补齐套餐配额字段与租户到期调度任务；删除租户前做关联校验；`TenantPlanMenu` 扩展功能点/按钮权限维度。

### 2.2 Config 与 Variable 职责重叠（P2，见「过度设计」）

### 2.3 AppInfo 字段语义混乱 + OAuth2 字段缺失（P1）
- `appCode` / `appKey` / `appSecret` 三个字段语义重复混乱：实体注释说 `appCode` 是 client_id，`appKey` 又是「应用密钥」；而 `selectEnabledByAppKey` 却用 `appKey` 做查询。唯一索引 `uk_app_code` 与 `appKey` 查询不一致，容易踩坑。
- 作为「OAuth2 应用注册」，缺 `grantTypes`、`accessTokenValidity`、`refreshTokenValidity`、`authorizationCodeTimeout` 等标准字段，无法真正支撑 OAuth2 授权码/客户端凭证流程。

**建议**：统一命名（建议 `clientId` / `clientSecret`），补齐 OAuth2 标准字段，或明确边界（仅做密钥校验，不做完整授权服务器）。

### 2.4 版本快照能力不完善（P2）
- 版本号用 `"v" + System.currentTimeMillis()`，并发下可能重复、不可读。
- 快照为**全量** JSON，无 diff；字典回滚采用「物理删除 + 全量重建」，大数据量下有风险。
- `listByResourceTypeAndKey` 无分页，版本历史膨胀后是隐患。

**建议**：版本号改为可读的递增语义（如 `v1/v2` 或 `yyyyMMdd-HHmmss` + 序号）；版本历史加分页 + 上限清理策略；快照考虑增量 diff 或压缩。

### 2.5 全局搜索只覆盖 Config（P1）
`SystemSearchProvider` 仅实现 Config 的搜索，Dict/AppInfo/Variable/Tenant 均未注册 SearchProvider。README 声称「聚合各模块」，实际系统模块内只能搜到配置。

**建议**：补齐 Dict/Variable 的 SearchProvider，或明确搜索覆盖范围，避免「宣称能力 vs 实际能力」错位。

---

## 三、性能提升

### 3.1 缓存失效粒度过粗（P0，性能隐患）
所有写操作统一 `@CacheEvict(allEntries = true)`：改一个配置 → 清空整个配置缓存；改一个字典项 → 清空整个字典缓存。配置/字典量大时，一次写操作引发大量回源 DB 的「缓存击穿」。

**建议**：改为按 key 精准失效：
```java
@CacheEvict(value = SYSTEM_CONFIG_CACHE, key = "@cacheKeyBuilder.configValue(#vo.configKey)")
@CacheEvict(value = SYSTEM_CONFIG_CACHE, key = "@cacheKeyBuilder.configGroup(#vo.configGroup)")
```
字典项同理按 `typeCode` 精准清 `dictList`/`dictItem`。

### 3.2 跨实例缓存一致性链路脆弱（P1）
`CacheConfig` 明确使用 `ydsz-common-cache` **本地缓存**（进程内），跨实例失效依赖 `Outbox 事件 → CrossModuleEventListener.clear()`，这是**最终一致**，存在窗口期：A 实例写配置后，B 实例在事件到达前仍返回旧值。且 `InternalApiController`/README 注释说「走 Redis 二级缓存」，与实际本地缓存实现矛盾。

**对标**：Apollo/Nacos 配置中心采用「本地 Caffeine 一级 + 远端二级 + 变更长轮询/推送」。

**建议**：
- 统一为「本地 Caffeine 一级缓存 + Redis 二级缓存 + 变更通知」标准结构。
- 至少先统一注释与实现的一致性，明确一致性语义（最终一致窗口期告知业务方）。

### 3.3 N+1 与逐条查询（P1）
- `DictItemBatchServiceImpl.validateDbUniqueness` 逐条 `selectCount`，500 条 = 500 次 SQL（类注释自称「2000+ → ~10 SQL」并不准确，唯一性校验仍是 N 次）。
- `TenantPlanMenuServiceImpl.updatePlanMenus` 用 `entities.forEach(mapper::insert)` 逐条插入，应改批量。
- `DictServiceImpl.removeById` 的子项 count 校验多一次查询。

**建议**：唯一性校验改为 `IN` 批量查询 + 内存比对；菜单关联改批量 INSERT；合并冗余查询。

### 3.4 读写分离未落地（P2）
pom 引入 `dynamic-datasource 4.3.1`（标注「读写分离 P2-3」），但 system 模块无任何 `@DS` 使用。

**建议**：配置读接口（`getConfigValue` / `listEnabledByTypeCode` 等）走从库 `@DS("slave")`，写接口走主库。

### 3.5 深度分页防护不统一（P2）
`TenantController` / `VariableController` 有 `MAX_PAGE_SIZE=500` 上限，但 Config/Dict/AppInfo 的分页走 `getEffectivePageSize()`，上限策略需统一；且均为 offset 分页，无游标/keyset 分页，深度翻页会退化。

---

## 四、体验改善

### 4.1 i18n 形同虚设（P2）
`messages_en_US.properties` / `messages_zh_CN.properties` 存在，但业务代码几乎全硬编码中文（异常码、日志、`"更新配置: "` 等），i18n 未接入。

**建议**：要么接入 i18n 框架（异常码/文案走 key），要么删除空资源文件，避免误导。

### 4.2 日志与异常码风格不统一（P2）
- `log.warn` 直接拼中文，无 traceId/结构化字段（虽引入 Logstash encoder）。
- `AuditAdminController` 混用 `BaseResponse.error(BaseResultCode.NOT_FOUND.getCode(), "...")` 硬编码，其余走 `BusinessException.of(SystemExceptionCode.xxx)`。

**建议**：统一异常码枚举 + 统一日志规范（结构化 key=value，含 traceId）。

### 4.3 API 版本管理不统一（P2）
`@ApiVersion("1")` 仅 `AuditAdminController` 使用，其余未用；`/api/v1/` 路径前缀与注解版本并存。

### 4.4 代码卫生（P1，低成本高收益）
- 多处重复 import：`ConfigServiceImpl` 中 `ConfigVO` import 两次、`DictItemServiceImpl` 中 `DictItemVO` 两次、`TenantPlanServiceImpl` 中 `TenantPlanVO` 两次、`VariableServiceImpl` 中 `VariableVO` 两次。
- 根因：`checkstyle.skip=true` + spotless 未绑定构建阶段，导致静态检查形同虚设。

**建议**：将 `checkstyle.skip` 改回 `false`，spotless 绑定到 `validate` 阶段，一次 `mvn spotless:apply` 批量修复。

---

## 五、过度设计（建议收敛）

### 5.1 Variable 与 Config 双轨制（P2，收敛）
两者字段几乎一致（key/value/valueType/description），仅注释定位不同（Config 面向后端、Variable 面向业务侧）。**功能重复度 >90%**，维护成本翻倍。对标大厂（Apollo/Nacos）通常一个配置中心即可。

**建议**：合并为一个「配置中心」，通过 `scope`（SYSTEM/BIZ）或 `public` 标记区分，而非两套实体+两套缓存+两套版本+两套回滚。

### 5.2 版本快照复杂度 vs 价值（P2）
Config/Dict/Variable 三套都做「版本快照 + 一键回滚」，但系统配置/字典回滚是低频需求，且当前实现（时间戳版本号 + 全量快照）偏重。建议：保留 Config 的版本回滚（有实际价值），Dict/Variable 可降级为「变更审计日志」，砍掉全量回滚复杂度。

### 5.3 指标埋点一半未接线（P2）
`SystemMetrics.recordConfigCacheHit()` / `recordDictCacheHit()` / `recordVariableCacheHit()` **从未被调用**，命中率统计恒为 0（只有 miss 被埋）。命中率看板是空的。

**建议**：要么在 `@Cacheable` 命中路径接线（如自定义 CacheInterceptor 回调），要么删除无用方法，避免误导运维。

### 5.4 注释自述与实现不符（P2）
多处注释夸大实现（如 DictItemBatch 的 SQL 优化自述、多租户 ISOLATE_DB 自述、读写分离自述）。建议以「代码为准」重写注释，或补齐实现。

---

## 六、风险与缺陷清单（P0 必须处理）

| # | 问题 | 风险等级 | 位置 | 建议 |
|---|------|---------|------|------|
| 1 | `@DataScope` + `@Cacheable` 组合：`getConfigsByGroup`/`listPublicConfigs` 缓存 key 仅含 `configGroup`/`public`，未含用户/部门维度，不同数据权限用户可能命中同一缓存 → **数据越权** | **P0 安全** | `ConfigServiceImpl` | 缓存 key 纳入数据权限维度；或数据权限场景禁用缓存；核实 `Config`/`AppInfo` 是否存在 `dept_id` 列（实体未声明该字段，注解疑似拷贝残留） |
| 2 | 回滚后本地缓存不失效：`ConfigServiceImpl.rollbackTo`/`VariableServiceImpl.rollbackTo`/`DictItemServiceImpl.rollbackTo` 均无 `@CacheEvict`，回滚后读到旧值 | **P0 一致性** | 三个 `rollbackTo` | 回滚成功后主动 `@CacheEvict` 或调用缓存失效 |
| 3 | SQL 脚本缺失，9 张表无 DDL，无法初始化 | **P0 交付** | `deploy/sql/` | 补齐 init.sql |
| 4 | 租户删除无关联校验，产生孤儿数据 | P1 数据 | `TenantServiceImpl.removeById` | 删除前校验关联 |
| 5 | `checkDuplicateKey` / `checkDuplicateTypeCode` 先查后插，并发下竞态（虽有唯一索引兜底，但会抛 SQL 异常而非友好提示） | P1 | 各 ServiceImpl | 依赖唯一索引 + 捕获 `DuplicateKeyException` 转业务异常 |
| 6 | 0 个单元测试，`mvn verify` 无测试可跑 | P1 质量 | 全模块 | 至少覆盖 Config/Dict 的核心校验与缓存逻辑 |

---

## 七、可落地优化路线（按 P0→P1→P2）

### P0（安全/正确性，立即，1 个迭代）
1. 修复 `@DataScope`+`@Cacheable` 数据越权缓存（缓存 key 纳入权限维度 / 权限场景禁缓存）。
2. 三个 `rollbackTo` 回滚后补缓存失效。
3. 补齐 `deploy/sql/init.sql`（建表 + 索引 + 初始数据）。

### P1（架构/功能/性能，S2–S3）
4. Repository 去透传化，封装查询语义；实体下沉领域不变量。
5. 拆分「上帝 Service」：缓存失效/事件/索引/指标抽为横切切面或事件订阅者。
6. 多租户补课：套餐配额字段、租户到期调度、删除关联校验、ISOLATE_DB 明确收敛或补齐路由。
7. 缓存失效改为按 key 精准失效（消除 allEntries 击穿）。
8. 补齐 Dict/Variable 的 SearchProvider；统一缓存结构（本地一级 + Redis 二级 + 变更通知）。
9. N+1 治理：批量唯一性校验、菜单批量插入、合并冗余查询。
10. 修复重复 import、开启 checkstyle + spotless、补核心单元测试。
11. AppInfo 命名统一 + 补齐 OAuth2 标准字段（或明确边界）。

### P2（体验/收敛/清理，S4+）
12. Variable 与 Config 合并（scope 区分）或明确双轨理由。
13. 版本快照降级：Dict/Variable 回滚砍为审计日志，版本号可读化 + 历史分页。
14. 指标埋点接线或删除无用方法；读写分离落地。
15. i18n 接入或删除空资源；日志/异常码/API 版本统一。

---

## 八、一句话总结

`ydsz-system` 模块「广度有余、深度不足」：五层 DDD 骨架与业务覆盖领先竞品，但多租户物理隔离、读写分离、全局搜索、i18n、测试等宣称能力尚未真正落地；同时存在 1 个数据越权缓存风险、多个缓存一致性缺陷与 0 测试的硬伤。建议优先按 P0 清单修复安全与正确性问题，再以 P1 收口架构深度，P2 清理过度设计，最终形成「代码、文档、测试、可观测」四对齐的交付闭环。
