# ydsz-system 模块全面分析报告

> 分析基准：最新代码（2026-08，73 个主类 / 5 个 DDD 子模块）
> 对标对象：若依 RuoYi、Pig、maku-boot、SpringBlade、JeecgBoot（README 声明竞品）；阿里《Java 开发手册》、美团编码规约、Google Java Style；配置中心/字典中心的大厂实践（Nacos Config、Apollo、飞书/字节字典平台）

---

## 一、现状概览

### 1.1 模块规模与分层

| 子模块 | 类数 | 职责 | 评价 |
|---|---|---|---|
| api | 4 | Feign Client + Fallback | 仅 Config/Dict 两个客户端，AppInfoClient 缺失（README 引用但无文件） |
| domain | 24 | Entity/DTO/VO/Query/Enum/Converter | 实体直接继承 `MpBaseEntity`（MyBatis-Plus），非纯领域模型 |
| infra | 11 | Mapper + Repository | Repository 为纯透传（getMapper），形同虚设 |
| server | 20 | Service + Config/Health/Metrics/Search/Listener | 业务核心，缓存/事件/指标集中在 Service 层 |
| web | 10 | Controller + 启动类 | 写接口统一加了 @Idempotent/@RateLimit/@Audit，规范度高 |

### 1.2 能力矩阵（业务域 × 已实现能力）

| 业务域 | CRUD | 缓存 | 版本/回滚 | 事件 | 搜索 | 多租户 | 结论 |
|---|---|---|---|---|---|---|---|
| 系统配置 Config | ✅ | ✅ 穿透防护 | ❌ | ✅ Outbox | ✅ | ⚠️ 缓存键缺 tenant | 主体可用，有硬伤 |
| 数据字典 DictItem/DictType | ✅ | ✅ | ⚠️ 快照语义反 | ❌ | ❌ | ⚠️ 同上 | 版本机制有正确性缺陷 |
| 系统变量 Variable | ✅ | ✅ | ❌ | ❌ | ❌ | ⚠️ 同上 | 与 Config 高度重复 |
| 应用注册 AppInfo | ✅ | ❌ | ❌ | ❌ | ❌ | ⚠️ | 密钥校验无防护 |
| 租户 Tenant/Plan/Menu | ❌ 仅实体+Mapper | ❌ | ❌ | ❌ | ❌ | ❌ | **规格书级死代码** |
| 全局搜索 | — | — | — | — | ✅ | ✅ | 仅覆盖 config 域 |

### 1.3 总体评价

**优点（保持）**：
- 写接口安全规范落地好：`@Idempotent`（Redis SET NX EX）+ `@RateLimit` + `@Audit` 三件套在 5 个 Controller 全覆盖，是大厂口径。
- 缓存穿透防护（`__NULL__` 哨兵 + 短 TTL）在 Config/Dict/Variable 三处统一落地，模式正确。
- 缓存失效用 SCAN 替代 KEYS，避免 Redis 阻塞，意识到位。
- `SystemMetrics` 继承 `AbstractModuleMetrics` 统一指标前缀，消除了样板代码。
- `SystemHealthIndicator` 用占位符探针而非 COUNT(*)，健康检查不伤性能。
- BCrypt 存 appSecret、VO 不暴露密钥哈希，安全边界清晰。

**核心问题**：**存在 6 个 P0 级正确性/安全缺陷**（Feign 契约不一致、多租户缓存串味、字典版本快照语义反了、getConfigValue 忽略分组、@Async 失效、SQL/客户端文件缺失），以及 2 类"文档声称但未实现"的能力（多租户套餐、Caffeine 缓存）。**"文档写得比代码多"** 是本模块最大工程债。

---

## 二、五维度分析

### 2.1 架构优化

**A1（P0）Feign 响应契约与服务端实现不一致，跨服务调用必然失败**
- `InternalApiController` 三个内部端点返回**裸类型**：`getConfig` 返回 `String`、`getDictItem` 返回 `DictItemVO`、`listDictItems` 返回 `List<String>`、`validateClient` 返回 `boolean`。
- 而 `ConfigClient.getConfig` 声明 `BaseResponse<String>`、`DictClient.getDictItem` 声明 `BaseResponse<String>`（连内层类型都对不上，服务端是 DictItemVO）、`listDictItems` 声明 `BaseResponse<List<String>>`。
- 后果：Feign 反序列化 `"abc"` 到 `BaseResponse` 时抛 `MismatchedInputException`，或拿到错误结构。**工作流/用户模块调用字典/配置即失败**。
- 修复：统一为「内部 API 也返回 `BaseResponse<T>`」，Client 与服务端对齐；或内部 API 走独立响应包装，二选一，不要混。

**A2（P0）多租户缓存键缺失 tenantId，跨租户缓存串味（数据泄露 + 正确性）**
- `ConfigServiceImpl`：`system:config:value:{configKey}`、`system:config:group:{configGroup}`、`system:config:public` 均无 `tenantId` 前缀。
- `DictItemServiceImpl`：`system:dict:item:{typeCode}:{itemCode}`、`system:dict:list:{typeCode}` 无 tenantId。
- `VariableServiceImpl`：`system:variable:value:{variableKey}` 无 tenantId。
- 后果：租户 A 写入配置后，租户 B 同 key 命中 A 的缓存值 → **跨租户数据泄露**。`system:config:public` 甚至全局共享一个 key。
- 修复：所有缓存键统一加 `{tenantId}:` 前缀，采用 `TenantContext` 拼接；evict 时同步按租户维度失效。

**A3（P0）getConfigValue 按 key 查询但唯一约束是 (tenant, group, key)**
- `ConfigMapper.selectByConfigKey` 只 `WHERE config_key = ? LIMIT 1`，忽略 `config_group`。
- 但唯一索引是 `uk_tenant_group_key`（group+key 联合）。同名 key 跨分组时，`getConfigValue("x")` 会**随机返回某一分组的 x**。
- 修复：要么 `getConfigValue` 增加 group 参数，要么 DB 层把唯一约束改为 key 全局唯一；二选一，当前语义自相矛盾。

**A4（P1）domain 层泄漏基础设施，DDD 名不副实**
- `domain/pom.xml` 依赖 `mybatis-plus-annotation` + `spring-security-crypto`；所有实体继承 `MpBaseEntity`、标 `@TableName`。
- 实体即持久化模型，`domain` 退化为 "annotated POJO 层"，领域逻辑（唯一性校验、值类型校验）反而全在 `server` 层手写。这与 README 宣称的"严格 DDD 五层、依赖单向收敛"不符。
- 建议：短期接受现状但写明"贫血模型 + 事务脚本"定位；中期把 `valueType` 校验、字典唯一性规则下沉为领域方法。

**A5（P1）Repository 是纯透传空壳**
- `ConfigRepository`/`DictRepository` 只有 `getConfigMapper()/getDictTypeMapper()` 两个 getter，Service 层直接 `configRepository.getConfigMapper().selectPage(...)`。
- 既无仓储语义（无聚合、无领域方法），又增加一层无意义间接。要么补上真正的仓储方法（`findByGroup`、`findByKey` 等），要么删除直接注入 Mapper。

**A6（P1）@Async 失效（无 @EnableAsync）**
- `CrossModuleEventListener.onConfigChanged` 标 `@Async`，但 `SystemApplication` 及 common 全量无 `@EnableAsync`，注解是 no-op，监听器在事件发布线程同步执行。
- 修复：补 `@EnableAsync` + 专用线程池，或去掉 `@Async` 避免误导。

### 2.2 功能增强

**F1（P0）字典版本快照语义颠倒，回滚无法还原"变更前"**
- `DictItemServiceImpl.save/updateById/removeById` 在 `mapper.insert/update/delete` **之后**调用 `createSnapshotVersion`，内部 `listEnabledByTypeCode(typeCode)` 取到的是**变更后**状态。
- 但 `DictVersion` 实体、`DictVersionService`、`rollbackTo` 的 javadoc 全部声称 snapshotJson 是"变更前快照"。
- 后果：回滚到 v2 会把"变更后"数据原样重放，无法回到变更前。这是版本管理功能的**根本性正确性缺陷**。
- 修复：把快照抓取移到写操作**之前**（`save` 前先 `listEnabledByTypeCode` 生成 snapshotJson，再 insert）。

**F2（P1）应用密钥校验无任何防护（爆破/DoS 向量）**
- `validateClient` 每次调用执行 BCrypt `matches()`（strength=10 约 100ms），无 Redis 缓存、无失败锁定、无服务层限流。
- 接口文档明确声称"需配合 @RateLimit + 失败锁定 + `AppInfo.lockedUntil` 字段"，但 `AppInfo` 实体**无 lockedUntil 字段**、Controller 层内部 API 的 `@RateLimit` 仅 50QPS 且不防单 key 爆破。
- 修复：加 Redis 缓存（`ydsz:app:validate:{appKey}` TTL 5min，校验成功缓存、失败不缓存）+ 失败计数锁定（`app:{appKey}:fail` 连续 5 次锁 30min）。

**F3（P1）多租户套餐（Tenant/TenantPlan/TenantPlanMenu）是规格书级死代码**
- 三个实体 + 三个 Mapper 存在，但**无 Service、无 Controller、无任何调用**。
- 实体 javadoc 引用的 `TenantService`、`TenantQuotaAspect`、`TenantQuotaExceededException` 均不存在。
- 修复：要么落地租户套餐 CRUD + 配额校验切面（对标若依/Pig 的租户套餐），要么删除并在 README 注明"规划中"，避免误导使用者。

**F4（P1）缓存能力"声称有、实现无"**
- `DictService.listAll()`、`DictItemService.list()`、`DictTypeService.listAll()` 的 javadoc 声称"走 Caffeine 缓存"，但实现是裸 `selectList(null)`，**无任何缓存**。
- 修复：补 `@YdszCacheable` 或本地 Caffeine，或修正文档。

**F5（P2）公开配置接口语义与 DataScope 冲突**
- `listPublicConfigs()` 是 `/api/v1/config/public` 的"无需鉴权"接口，却在 Service 上标 `@DataScope(deptColumn="dept_id", userColumn="created_by")`；而 `Config` 实体/表**无 dept_id 列**。匿名场景下 AuthContext 为空时切面可能 NPE 或注入不存在的列导致 SQL 错误。**需按 AuthRowPermissionAspect 实现复核**。

**F6（P2）InternalApiIpFilter 声称支持 CIDR/通配符，实际仅精确匹配**
- `SystemProperties.internalApiIpWhitelist` javadoc 称"支持 CIDR 与通配符"，但 `doFilter` 用 `whitelist.contains(clientIp)` 精确匹配。
- 修复：接入已有 `CidrUtils`/`IpValidator`（ydsz-common-util 已具备），或删除"支持 CIDR"的表述。

### 2.3 性能提升

**P1-1 批量新增字典项 N+1 + 每项全量快照，500 条是灾难**
- `DictItemBatchServiceImpl.batchSave` 循环调用 `dictItemService.save()`，每项执行：`selectCount`（唯一性）+ `insert` + `evictCache`（SCAN）+ `createSnapshotVersion`（`listEnabledByTypeCode` 全量查询 + `insert` 版本）。500 条 ≈ 2000+ 次 SQL + 500 次 SCAN + 500 个全量快照。
- 修复：批量内去重后，一次 `insertBatchSomeColumn`，**整个批量只生成一个版本快照**（快照抓取在批量前）。

**P1-2 validateClient 的 BCrypt 无缓存（同 F2）**
- 每次校验 ~100ms CPU，无缓存；网关同步链路高频调用即打满 CPU。

**P1-3 缓存键无 tenantId 导致命中率被错误抬升/串味（同 A2）**
- 不同租户共享缓存，看似命中率上升，实为脏数据。

**P1-4 字典版本快照对大字典是 O(N) 全量序列化**
- 行政区划等大字典（>1 万项）每次写操作全量 `YdszJson.toJson` + 落库，写放大严重。建议：快照改为 diff（变更前/后差异）或对大字典关闭逐次快照，改为定时全量快照。

**P2-5 杂项**
- `page()` 返回后 Controller 又做一次 `PageResponse.success(...)` 二次包装（Config/Dict Controller），可简化。
- `ConfigController` 重复 import `PageResponse`（第 9、10 行）、`DictVersionServiceImpl` 重复 import `SystemConverter`（第 24、29 行）——checkstyle 应能拦到，说明门禁未生效或未提交。

### 2.4 体验改善（开发者体验 / 文档 / 测试）

**E1（P0）SQL 初始化脚本缺失，无法按 README 部署**
- 根 README 要求执行 `ydsz-system/ydsz-system-web/src/main/resources/sql/init.sql`，但全模块**无任何 .sql 文件**。9 张表无 DDL，无法初始化数据库。
- 修复：补齐 `deploy/sql/V1.0.0.sql`（含 9 张表 + 索引 + CHECK 约束），并在 README 对齐路径。

**E2（P1）文档与实现大面积漂移（建议 CI 加 doc-link 校验）**
- 端口：根 README 表写 `system:9002`，实际 bootstrap 为 9001（userinfo 才是 9002）。
- AppInfoClient/AppInfoClientFallback：README 引用，文件不存在。
- 缓存 TTL：接口 javadoc 写 30min，实现 5/10min；多处 `@Cacheable`/Caffeine 声称未实现。
- `DictController.remove` 文档称"级联删除字典项"，实现明确不级联——**误导且危险**。
- 建议：一次性校正 + 关键契约（Feign 返回类型、缓存键、快照语义）补单元测试锁定。

**E3（P1）测试覆盖缺口**
- 模块内未发现单测类。核心正确性逻辑（缓存穿透、快照语义、Feign 契约、多租户缓存隔离）恰恰最需要测试。
- 建议：优先补 4 类测试：①多租户缓存隔离（同 key 不同 tenant 不串）；②字典版本"变更前快照"语义；③Feign 响应契约；④`getConfigValue` 跨分组。

**E4（P2）告警式校验是半吊子**
- `validateConfigValue` 校验失败仅 `log.warn` + 打指标，不阻止保存（`CONFIG_VALUE_VALIDATION_WARNING` 错误码）。既然有校验，建议对 `NUMBER/BOOLEAN/JSON` 的**非法值在写入口直接拒绝**（`CONFIG_VALUE_VALIDATION_WARNING` 保留用于存量数据清洗期）。

### 2.5 过度设计（收敛与下线）

**OD1（P1）Variable 与 Config 高度同构，双 KV 体系并存**
- `VariableServiceImpl` 与 `ConfigServiceImpl` 的缓存穿透、哨兵、evict、toEntity 逻辑几乎逐行复制（仅 cacheKey 前缀不同），且 `Variable` 复用 `ConfigValueType`。两套 KV 增加维护成本与认知负担。
- 建议：短期抽公共父类 `AbstractCachedKvService`；长期评估是否合并（Variable 的"跨服务 Feign 查询"诉求其实 Config 也能满足）。

**OD2（P1）ConfigRepository/DictRepository 空壳透传**
- 无任何领域语义，纯 getMapper。删除或补真仓储方法（见 A5）。

**OD3（P1）Tenant/TenantPlan/TenantPlanMenu 实体+Mapper 无消费方**
- 见 F3，属"先建表后写代码"的规格书式过度设计。

**OD4（P2）@Idempotent + @RateLimit + @Audit 三件套堆砌于写接口**
- 本身是好实践，但 `@Idempotent` 的 key 用固定字符串（`ydsz:system:ConfigController:save:lock`），**5 秒内所有用户的写操作互斥**（A 用户建配置会锁住 B 用户），而不是按"用户+资源"隔离。应改为 `...:save:{userId}` 或基于请求体指纹，避免误伤并发写。

**OD5（P2）`allow-bean-definition-overriding: true`**
- bootstrap.yml 开启 Bean 覆盖，属掩盖 Bean 冲突的坏味道（BCryptPasswordEncoder 与全局 auth PasswordEncoder 可能冲突的伏笔），应定位并消除覆盖。

---

## 三、落地路线图

### P0（本迭代，正确性/安全收口）

| # | 事项 | 验证方式 |
|---|---|---|
| 1 | 统一 Feign 契约：内部 API 返回 `BaseResponse<T>`，Client 对齐（含 getDictItem 内层类型 DictItemVO） | 跨服务 Feign 调用单测/联调通过 |
| 2 | 缓存键全部加 `{tenantId}:` 前缀，evict 按租户失效 | 双租户同 key 不串缓存集成测试 |
| 3 | `getConfigValue` 修正分组语义（或改唯一约束为 key 全局唯一） | 跨分组同名 key 用例 |
| 4 | 字典版本快照改为"变更前抓取"，回滚可真正还原 | 版本回滚单测：v2 回滚后数据 == v1 快照 |
| 5 | 补 `@EnableAsync` + 线程池（或去掉 @Async） | 事件监听异步执行 |
| 6 | 补齐 `deploy/sql/V1.0.0.sql`（9 张表 DDL）与 AppInfoClient（或删 README 引用） | `mvn` 后可初始化 DB |

### P1（下个迭代，能力补齐与体验）

| # | 事项 |
|---|---|
| 1 | validateClient 加 Redis 校验缓存 + 失败锁定，落 AppInfo.lockedUntil 字段 |
| 2 | 多租户套餐落地（Tenant/Plan/Menu 的 Service+Controller+配额切面）或正式下线 |
| 3 | batchSave 改批量插入 + 单次版本快照，修正返回契约 `{successCount, failCount, versionId}` |
| 4 | 补 Caffeine/`@YdszCacheable` 到 listAll/list；校正 TTL 文档；核对 DataScope 在无 dept_id 表上的行为 |
| 5 | 大字典快照改 diff 或定时全量，降低写放大 |
| 6 | 修正内部 API IP 白名单 CIDR 支持；补核心单测（4 类见 E3） |

### P2（长期治理）

| # | 事项 |
|---|---|
| 1 | domain 去 MyBatis 化评估：贫血模型明确定位或下沉领域规则 |
| 2 | Variable/Config 抽 `AbstractCachedKvService` 或合并 |
| 3 | Repository 补真仓储语义或删除；`allow-bean-definition-overriding` 消除 |
| 4 | @Idempotent key 改为按用户/资源隔离；告警式校验转为写入口强校验 |
| 5 | 建立"文档-代码契约"CI 校验（Feign 返回类型、缓存键、端口、SQL 文件存在性） |

---

## 附：关键证据位置

- Feign 契约不一致：`InternalApiController.java`（返回裸 String/DictItemVO/List/boolean）vs `ConfigClient.java`/`DictClient.java`（声明 BaseResponse）
- 多租户缓存键缺 tenant：`ConfigServiceImpl.java:89-93`、`DictItemServiceImpl.java:114-116`、`VariableServiceImpl.java:107`
- getConfigValue 忽略分组：`ConfigMapper.java:44`（`WHERE config_key = ? LIMIT 1`）
- 快照语义颠倒：`DictItemServiceImpl.java:325/350/379`（insert/update/delete 之后调 createSnapshotVersion）
- @Async 无 @EnableAsync：`CrossModuleEventListener.java:31`；全模块无 `@EnableAsync`
- SQL 缺失：`find ydsz-system -name "*.sql"` = 空；AppInfoClient 缺失同理
- 端口漂移：根 README（system:9002）vs `bootstrap.yml:12`（9001）；userinfo `bootstrap.yml:12`（9002）
- 死代码：`Tenant/TenantPlan/TenantPlanMenu` 实体+Mapper 无 Service/Controller
- 空壳仓储：`ConfigRepository.java`/`DictRepository.java`（仅 getMapper）
- 精确匹配白名单：`InternalApiIpFilter.java:105`（`whitelist.contains(clientIp)`）
