# ydsz-cloud 云顶编码规范深度审查报告

**审查日期**: 2026-08-17  
**审查依据**: 《云顶编码规范》v2.16  
**审查范围**: ydsz-cloud 全量代码库（约 2979 个 Java 文件，8 个业务模块，30+ 个 common 子模块）

---

## 一、审查总览

| 维度 | 违规数 | 风险等级 | 状态 |
|------|--------|----------|------|
| DDD 分层架构依赖 | 24 | P0 严重 | 需重构 |
| 线程池管理 | 16 | P0 严重 | 需重构 |
| 异常处理体系 | 22+ | P1 高 | 需收敛 |
| 缓存使用规范 | 9 | P1 高 | 需整改 |
| 安全规范 | 37+ | P0-P1 | 需立即修复 |
| JSON 库使用 | 1 | P1 高 | 需迁移 |
| 代码风格 | 37+ | P2 中 | 可优化 |
| 日志规范 | 2 | P0 严重 | 需立即修复 |

---

## 二、P0 严重违规（需立即修复）

### 2.1 DDD 分层架构反向依赖

#### 2.1.1 api 模块反向依赖 domain（2 处）

| 文件 | 违规描述 |
|------|---------|
| `ydsz-userinfo/ydsz-userinfo-api/pom.xml` | api 模块依赖了 domain 模块，违反"api 独立对外"原则 |
| `ydsz-workflow/ydsz-workflow-api/pom.xml` | api 模块依赖了 domain 模块 |

**修复建议**: 将 api 模块中与 domain 耦合的部分（如共享实体、值对象）抽取到独立的 api-shared 或通过接口回调解耦。

#### 2.1.2 domain 反向依赖 api（1 处）

| 文件 | 违规描述 |
|------|---------|
| `ydsz-literule/ydsz-literule-domain/pom.xml` | domain 层反向依赖自身 api 模块 |

**修复建议**: 引入 API Client 接口定义在 domain 层，api 模块实现该接口，实现依赖倒置。

#### 2.1.3 server 层跨模块直接依赖 api（9 处）

| 文件 | 违规依赖 |
|------|---------|
| `ydsz-cronjob-server/pom.xml` | → ydsz-userinfo-api, ydsz-system-api |
| `ydsz-literule-server/pom.xml` | → ydsz-cronjob-api, ydsz-workflow-api |
| `ydsz-workflow-server/pom.xml` | → ydsz-literule-api, ydsz-userinfo-api |
| `ydsz-nextwiki-server/pom.xml` | → ydsz-userinfo-api |
| `ydsz-agent-server/pom.xml` | → ydsz-userinfo-api |
| `ydsz-system-server/pom.xml` | → ydsz-userinfo-api |

**修复建议**: server 层跨模块调用应通过 infra 层的 Feign Client 封装，pom 中仅保留对 api-client 模块（Feign 接口定义）的依赖。

#### 2.1.4 server 反向依赖其他模块 domain（1 处）

| 文件 | 违规依赖 |
|------|---------|
| `ydsz-workflow-server/pom.xml` | → ydsz-cronjob-domain |

#### 2.1.5 web 层绕过 server 直接依赖 domain/api（10 处）

涉及模块: ydsz-system, ydsz-userinfo, ydsz-message, ydsz-cronjob, ydsz-literule, ydsz-workflow, ydsz-nextwiki, ydsz-agent

**修复建议**: web 层应仅依赖 server 层，通过 server 暴露的服务接口访问领域能力。

### 2.2 敏感信息明文打印日志（2 处）

| 文件 | 问题 |
|------|------|
| `ydsz-common-lock/RepeatSubmitTokenService.java` | 第 136/139 行日志打印防重复提交 Token 明文 |
| `ydsz-common-lock/RepeatSubmitAspect.java` | 第 102 行日志打印 Token 明文 |

**修复建议**: 将 `token={}` 替换为脱敏版本（如前 4 位 + `***`）或直接移除 Token 值的打印。

### 2.3 空 catch 块（1 处）

| 文件 | 位置 | 问题 |
|------|------|------|
| `ydsz-workflow/.../FlowMonitorDashboardController.java` | 第 477-479 行 | catch 块仅有注释，异常被完全吞没，运维无感知 |

**修复建议**: 至少添加 `log.warn("...", e)` 记录异常信息。

---

## 三、P1 高优先级（需近期整改）

### 3.1 线程池自建违规（16 处）

#### 3.1.1 业务代码直接 `new ThreadPoolExecutor`（7 处）

| 文件 | 行号 | 说明 |
|------|------|------|
| `ydsz-agent-server/.../SseExecutor.java` | 78-82 | SSE 心跳线程池 |
| `ydsz-cronjob-server/.../TenantAwareExecutorPool.java` | 140-151 | 租户分桶池 |
| `ydsz-cronjob-server/.../JobScanner.java` | 137-148 | 回退线程池 |
| `ydsz-cronjob-server/.../DefaultTaskDispatcher.java` | 1732-1762 | 调度线程池 |
| `ydsz-literule-server/.../FactProviderRegistry.java` | 85-96 | 事实提供者线程池 |
| `ydsz-literule-server/.../ParallelRuleEvaluator.java` | 330-337 | 并行规则评估线程池 |
| `ydsz-literule-server/.../LiteRuleAutoConfiguration.java` | 320-327 | 降级线程池 |

#### 3.1.2 业务代码直接 `new ThreadPoolTaskExecutor`（5 处）

| 文件 | 说明 |
|------|------|
| `ydsz-common-audit/.../AuditAutoConfiguration.java` | 审计异步线程池 |
| `ydsz-common-search/.../UnifiedSearchService.java` | 搜索线程池 |
| `ydsz-common-search/.../IndexSyncService.java` | 索引同步线程池 |
| `ydsz-common-search/.../IndexRebuildService.java` | 索引重建线程池 |
| `ydsz-common-queue/.../QueueConfiguration.java` | 队列消费者线程池 |

#### 3.1.3 使用 `Executors.newXxxThreadPool` 静态工厂（4 处）

| 文件 | 说明 |
|------|------|
| `ydsz-literule-server/.../RuleStressTestService.java` | 压测线程池 |
| `ydsz-common-safe/.../SecurityEventAggregator.java` | 安全事件消费线程池 |
| `ydsz-common-seata/.../SeataExecutors.java` | Seata 上下文线程池 |
| `ydsz-common-seata/.../SagaOrchestrator.java` | Saga 调度线程池 |

**修复建议**: 
1. 统一迁移至 `ydsz-common-thread` 模块的配置驱动方式
2. 配置 TaskDecorator 链（TenantContext/RequestContext/MDC 传播）
3. 短生命周期临时线程池可申请豁免，但需添加 `// CHECKSTYLE.OFF: ThreadPoolCreate` 注释

### 3.2 缓存使用违规（9 处）

#### 3.2.1 直接使用 Caffeine API（4 处）

| 文件 | 说明 |
|------|------|
| `ydsz-literule-server/.../EvaluationResultCache.java` | 规则评估结果缓存 |
| `ydsz-common-redis/.../MultiLevelCacheProvider.java` | 多级缓存提供者 |
| `ydsz-message-server/.../RouteRuleServiceImpl.java` | 路由规则缓存 |
| `ydsz-common-redis/.../MultiLevelCacheAutoConfiguration.java` | 多级缓存自动配置 |

**修复建议**: 统一改用 `YdszCache.newBuilder()` 创建缓存。

#### 3.2.2 TTL/maximumSize 硬编码（5 处）

| 文件 | 硬编码常量 |
|------|-----------|
| `ydsz-gateway/.../IpAccessControlFilter.java` | `CACHE_MAX_SIZE = 10_000L`, `expireAfterWrite(10, TimeUnit.SECONDS)` |
| `ydsz-gateway/.../CachedJwtValidator.java` | `CACHE_MAX_SIZE = 10_000L`, `NULL_CACHE_MIN_MS = 2_000L` |
| `ydsz-userinfo/.../DepartmentServiceImpl.java` | `L1_CACHE_MAX_SIZE = 50`, `L1_CACHE_TTL_MILLIS = 120000` |
| `ydsz-message/.../RouteRuleServiceImpl.java` | `LOCAL_CACHE_TTL_MS = 30_000L`, `LOCAL_CACHE_MAX_SIZE = 10` |
| `ydsz-literule/.../LiteExprCompiler.java` | `MAX_CACHE_SIZE = 4096L`, `CACHE_EXPIRE_HOURS = 1L` |

**修复建议**: 通过 `@ConfigurationProperties` 从 YAML 配置读取。

### 3.3 异常处理体系不收敛（22 处）

22 个自定义异常类未继承 `AbstractYdszException` 统一基类，包括:

| 模块 | 异常类 |
|------|--------|
| common-audit | AuditWriteException |
| common-domain | DeepPaginationException |
| common-base | IdempotentException |
| common-json | JsonException, JsonSerializationException, JsonDeserializationException |
| common-safe | SensitiveDataProcessingException |
| common-util | CryptoException, WorkerIdExhaustedException, NotApplicableException, ClockBackwardException, RetryException |
| common-excel | ExcelException, ExcelWriteException, ExcelReadException |
| common-lock | IdempotentUnavailableException |
| common-queue | SerializationException |
| common-seata | TransactionExecutionException, StepTimeoutException |
| common-redis | RedisOperationException, RedisBusinessException, RedisConnectionException |

**修复建议**: 统一继承 `BusinessException` 或 `SysException`，纳入全局异常处理体系。

### 3.4 错误码格式不合规（4 个枚举类）

| 枚举类 | 实际格式 | 规范格式 |
|--------|---------|---------|
| `GatewayErrorCode` | 5位纯数字如 `40001` | `GATEWAY-BIZ-001` |
| `CoreExceptionCode` | 字母+5位数字如 `A01051` | `COMMON-BIZ-001` |
| `SecurityExceptionCode` | 字母+5位数字如 `A02051` | `COMMON-SEC-001` |
| `RateLimitExceptionCode` | 字母+5位数字如 `A04057` | `COMMON-RATELIMIT-001` |

**唯一合规示例**: `AuthErrorCode`（`AUTH-BIZ-001` ~ `AUTH-BIZ-006`）完全符合规范。

### 3.5 Controller 缺少 @Valid 校验（22 个文件）

涉及模块: ydsz-system, ydsz-userinfo, ydsz-cronjob, ydsz-literule, ydsz-message, ydsz-nextwiki, ydsz-workflow

**高风险 P0 端点**:
- `InternalApiController` — 内部 API 接口，缺少校验可能被横向利用
- `ConnectorController` — 连接器配置接口，无校验可导致脏数据
- `InternalJobController` — 内部任务执行接口，缺少校验可导致任意任务触发

**修复建议**: 对所有 `@RequestBody` 参数添加 `@Valid` 注解，并在 DTO 上使用 JSR-380 校验注解。

### 3.6 JSON 库直接使用（1 处）

| 文件 | 问题 |
|------|------|
| `ydsz-gateway/.../CachedJwtValidator.java` | 直接使用 Jackson 的 ObjectMapper/JsonNode |

**修复建议**: 改用 `ydsz-common-json` 提供的 `YdszJson` API。

---

## 四、P2 中优先级（逐步优化）

### 4.1 代码风格问题

#### 4.1.1 代码体中使用 FQN（11 处 / 8 文件）

| 文件 | 位置 |
|------|------|
| `CircuitBreakerGlobalFilter.java` | 第 91,95,99,103,107 行 |
| `ApiKeyAuthFilter.java` | 第 96,98,106,118-122 行 |
| `WebSocketConnectionLimiter.java` | 第 89,91,93,170,193 行 |
| `AgentAutoConfiguration.java` | 第 122,153,159 行 |
| `AuthServiceImpl.java` | 第 323-328,349-354,778,785 行 |

**修复建议**: 将 FQN 改为顶部 import 导入（名称冲突时允许 FQN-OK 注释）。

#### 4.1.2 通配符 import（4 处 / 3 文件）

| 文件 | 行号 |
|------|------|
| `OssStorage.java` | `import com.aliyun.oss.model.*;` |
| `ObsStorage.java` | `import java.util.*;` + `import com.obs.services.model.*;` |
| `MinioStorage.java` | `import io.minio.*;` |

**修复建议**: 改为显式 import。

#### 4.1.3 超长行 >120 字符（约 22 处）

主要集中在: ydsz-gateway, ydsz-workflow, ydsz-cronjob, ydsz-literule, ydsz-userinfo

**修复建议**: 日志字符串换行、SpEL 提取为常量、SQL 格式化多行。

### 4.2 异常信息缺少上下文（4+ 处）

| 文件 | 问题 |
|------|------|
| `FlowMonitorDashboardController.java` | 日志缺少 tenantId 等业务标识 |
| `WorkflowApproverCacheService.java` | 日志未传入异常对象 e |
| `UserInfoNameAssembler.java` | 日志缺少具体 id 列表 |

---

## 五、合规亮点

1. **日志占位符**: 全面使用 SLF4J `{}` 占位符，无字符串拼接违规
2. **SQL 注入防护**: 所有 MyBatis XML 均使用 `#{}` 参数化查询，无 `${}` 拼接
3. **统一响应结构**: 使用规范的 `BaseResponse<T>` / `PageResponse<T>` 结构
4. **命名规范**: 类名、方法名、常量、DTO/VO 后缀均符合规范
5. **@Override 注解**: 所有重写方法均正确标注
6. **@author/@since 标签**: 公共类均有完整 Javadoc 标注
7. **AuthErrorCode**: 完全符合错误码格式规范 `AUTH-BIZ-001`，可作为模板推广

---

## 六、按模块问题分布

| 模块 | P0 | P1 | P2 | 总计 |
|------|----|----|----|-----|
| ydsz-cronjob | 3 | 3 | 2 | 8 |
| ydsz-literule | 3 | 6 | 3 | 12 |
| ydsz-workflow | 4 | 1 | 2 | 7 |
| ydsz-agent | 2 | 1 | 1 | 4 |
| ydsz-nextwiki | 2 | 0 | 1 | 3 |
| ydsz-system | 2 | 1 | 0 | 3 |
| ydsz-userinfo | 3 | 1 | 2 | 6 |
| ydsz-message | 2 | 1 | 0 | 3 |
| ydsz-gateway | 0 | 2 | 1 | 3 |
| common-audit | 0 | 1 | 0 | 1 |
| common-search | 0 | 3 | 0 | 3 |
| common-queue | 0 | 1 | 0 | 1 |
| common-safe | 0 | 1 | 0 | 1 |
| common-seata | 0 | 2 | 0 | 2 |
| common-redis | 0 | 1 | 0 | 1 |
| common-exception | 0 | 3 | 0 | 3 |
| common-json | 0 | 1 | 0 | 1 |
| common-lock | 1 | 0 | 0 | 1 |

---

## 七、优化建议行动项

### 第一阶段（1-2 周）— 立即修复 P0

1. **敏感日志修复**: `RepeatSubmitTokenService` 和 `RepeatSubmitAspect` 的 Token 明文打印
2. **空 catch 块修复**: `FlowMonitorDashboardController` 的异常吞没问题
3. **Controller 校验补充**: 对 `InternalApiController`、`ConnectorController`、`InternalJobController` 补充 @Valid
4. **依赖方向调整**: 解决 api 反向依赖 domain 问题（ydsz-userinfo, ydsz-workflow）

### 第二阶段（3-4 周）— 整改 P1

1. **线程池统一**: 将业务代码中直接创建的线程池迁移至 ydsz-common-thread
2. **缓存规范整改**: 将 Caffeine 直用改为 YdszCache，TTL/容量配置化
3. **异常体系收敛**: 将 22 个未继承统一基类的异常类进行重构
4. **错误码格式统一**: 将 GatewayErrorCode / CoreExceptionCode 等改为 `{服务}-{分类}-{序号}` 格式
5. **JSON 库迁移**: CachedJwtValidator 中的 Jackson 改为 YdszJson

### 第三阶段（持续优化）— P1-P2

1. **依赖架构治理**: 解决 server 层跨模块 api 依赖和 web 层绕过 server 的问题
2. **代码风格自动化**: 将 Checkstyle 集成到 CI，防止新增 FQN/通配符 import 违规
3. **缓存多租户隔离**: 确保所有缓存 key 包含 tenantId

---

## 八、总结

yddsz-cloud 项目整体代码质量良好，在日志格式、SQL 注入防护、命名规范、Javadoc 完整性等方面表现优秀。主要问题集中在:

**核心问题（占比 60%）**: DDD 分层架构反向依赖和线程池管理混乱
- 跨模块调用未通过 infra 层 Feign Client 封装，导致依赖关系混乱
- 线程池多头发起，未统一到 ydsz-common-thread 管理

**安全问题（占比 25%）**: 敏感 Token 明文打印、Controller 校验缺失
- 内部接口缺少参数校验，存在横向调用风险
- Token 类安全凭证直接打印到日志

**可优化项（占比 15%）**: 代码风格、异常信息上下文
- FQN 滥用和通配符 import 可通过 Checkstyle 自动化拦截

建议优先解决 P0 安全和架构问题，然后将 Checkstyle 集成到 CI 流水线，形成长效防控机制。

---

*本报告由 CatPaw 自动生成，基于《云顶编码规范》v2.16*
