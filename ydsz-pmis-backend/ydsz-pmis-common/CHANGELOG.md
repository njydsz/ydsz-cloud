# ydsz-pmis-common 变更日志

## [3.5.0] - 2026-07-14

### P0 级修复

#### P0-1: 核心模块单元测试补充
- 补全 `util`、`redis`、`auth`、`exception` 等核心模块的单元测试
- 覆盖率提升至 60%+

#### P0-2: 事务性 Outbox 模式（新增 `ydsz-pmis-common-event` 模块）
- 新增 `OutboxMessage` 实体与 `OutboxStatus` 枚举
- 新增 `OutboxRepository` JDBC 仓储（不依赖 ORM）
- 新增 `OutboxService` 写入服务（`@Transactional` 保证原子性）
- 新增 `OutboxProcessor` 后台轮询处理器（指数退避 + 死信处理）
- 新增 `EventPublishGateway` SPI 接口 + `NoopEventPublishGateway` 降级
- 新增 `OutboxHealthIndicator` 健康检查（积压告警）
- 新增 DDL 脚本 `pm_event_outbox.sql`

#### P0-3: 敏感配置加密（新增 `ydsz-pmis-common-config` 模块）
- 新增 `ConfigEncryptor` AES-256-GCM 加密器
- 新增 `EncryptablePropertyResolver` 自动解密 `ENC()` 格式配置
- 新增 `ConfigAutoConfiguration` 早期解密（ApplicationContextInitialized 阶段）
- 密钥通过 `PMIS_CONFIG_ENCRYPT_KEY` 环境变量注入

#### P0-4: @SuppressWarnings 全量清理
- 清理 30+ 文件、60+ 处 `@SuppressWarnings` 注解
- `YdszExceptionBuilder` 引入 `self()` 抽象方法替代 CRTP unchecked cast
- `RedisRateLimiter` 引入 `castToLongList` 类型安全转换
- `RedisTransactionOps` 修复 `SessionCallback` 泛型定义
- `EnumConverter` 使用 `asSubclass(Enum.class)` 替代 raw cast
- `AbstractPmisException` 引入 `dataMap` 字段消除 Map cast

### P1 级增强

#### P1-5: 异步上下文传播（TTL 集成）
- 新增 `TtlTaskDecorator` 自动包装 `@Async` / `@Scheduled` 任务
- 新增 `TtlAsyncAutoConfiguration` 自动配置（`pmis.ttl.enabled` 开关）

#### P1-6: 线程池统一注册与监控
- 新增 `ThreadPoolRegistry` 集中管理所有线程池
- Micrometer 指标采集（活跃线程、队列大小、已完成任务等 7 个指标）
- `DisposableBean` 优雅停机（30 秒超时 + 强制关闭）
- 新增 `ThreadPoolRegistryAutoConfiguration` 自动配置

#### P1-7: 多维限流增强
- 新增 `MultiDimensionRateLimiter` 多维度组合限流
- Redis Lua 滑动窗口算法（ZSET + ZREMRANGEBYSCORE）
- 支持 IP / USER / API / GLOBAL 四维自由组合
- AND 语义：所有规则都通过才放行

#### P1-9: 分布式事务抽象（新增 `ydsz-pmis-common-seata` 模块）
- 新增 `DistributedTransactionManager` 统一接口
- 新增 `TccAction` / `TccContext` TCC 模式接口
- 新增 `TccTransactionManager` TCC 协调器（Try→Confirm→Cancel）
- 新增 `LocalTransactionManager` 本地降级实现
- 新增 `SeataProperties` 配置（支持 LOCAL / TCC / SEATA_AT / SAGA 切换）
- Seata AT 模式预留集成接口

### P2 级完善

#### P2-10: 模块文档补充
- 新增 `ydsz-pmis-common-event` README
- 新增 `ydsz-pmis-common-config` README
- 新增 `ydsz-pmis-common-seata` README

#### P2-11: 错误码统一管理
- 新增 `UnifiedErrorCode` 统一接口（code / key / httpStatus / description）
- 新增 `CommonErrorCode` 公共错误码枚举（40+ 标准错误码）
- 错误码格式：`PM[模块码][错误序号]`（如 PM01001 = 用户模块参数校验失败）

#### P2-12: 日志脱敏完善
- `EncryptablePropertyResolver` 日志输出自动脱敏属性名

#### P2-15: Bulkhead 隔离
- 新增 `BulkheadManager` 基于信号量的舱壁隔离
- 支持超时等待 + 快速失败
- `Ticket` 票据模式确保资源释放

#### P2-16: 健康检查聚合视图
- `OutboxHealthIndicator` 健康检查（PENDING 积压 + DEAD_LETTER 告警）
- `ThreadPoolRegistry` 状态快照 API

#### P2-17: 变更日志 CHANGELOG.md
- 本文件

### P3 级优化

#### P3-18: 声明式重试抽象
- 新增 `RetryTemplate` 灵活重试模板
- 支持最大重试次数、指数退避、异常谓词过滤、重试回调

#### P3-19: 特性开关管理增强
- 新增 `FeatureFlagManager` 增强版特性开关
- 支持静态开关、百分比灰度、白名单三种模式
- 一致性哈希保证灰度稳定性

#### P3-21: 统一日志上下文
- 新增 `UnifiedLogContext` 结构化日志上下文
- 8 个标准 MDC 字段（traceId / userId / tenantId / module / action 等）
- AutoCloseable + try-with-resources 支持

## [3.4.0] - 2026-07-13

### 迁移与品牌统一
- 第七批迁移：remi-comm 18 个子模块迁移到 ydsz-pmis-common
- 品牌标识统一替换：remi → ydsz
- P2-11 数据权限深度集成
- P2-9 DDD POM 优化
- P2-12 Gateway 模块增强
- 新增 ydsz-pmis-common-websocket 和 ydsz-pmis-common-netty
- 新增 ydsz-pmis-common-json 高性能 JSON 引擎（340 个测试通过）
- 新增 ydsz-pmis-common-cache 多策略本地缓存框架（106 个测试通过）

## [3.3.0] - 2026-07-12

### FQN 标准化工程
- P0 修复文档矛盾 + 重写检测脚本 + CI 集成
- P1 批量修复所有残留违规
- P2 引入 Spotless + Google Java Format + Pre-commit Hook
- P3 导出 IntelliJ IDEA 检查配置
- 建立 5 层工程化防线
