# ADR-012: Seata 分布式事务 PoC 设计

| 属性 | 值 |
|------|-----|
| **状态** | ACCEPTED（待 PoC 验证） |
| **决策日期** | 2026-09-14 |
| **决策者** | ydsz-team |
| **相关** | ydzs-common-seata 模块、workflow 引擎、cronjob 引擎 |
| **编号变更** | 2026-09-15 由 `ADR-010` 重编号为 ADR-012 并移入 `adr/`（原编号与 `ADR-010-mq-usage-boundary.md` 撞号） |

## 背景

ydsz-common-seata 模块已就绪（`SeataAutoConfiguration` + `SeataProperties` + `SeataHealthIndicator`），但 8 个业务引擎中零业务使用 `@GlobalTransactional`。当前跨服务数据一致性依赖 Outbox 事件（最终一致性），存在数据不一致时间窗。

## 决策

对 **workflow → cronjob** 强一致性场景引入 **Seata AT 模式** 进行 PoC 验证。

## PoC 场景选型

### 场景：流程实例创建 + 任务定时触发联动

| 维度 | 说明 |
|------|------|
| **触发** | 用户提交审批 → workflow 引擎创建流程实例 |
| **联动** | 流程节点配置了定时任务 → cronjob 引擎同时注册 JobDetail |
| **一致性要求** | 两者必须同时成功/失败，否则流程状态与定时任务不一致 |

### 技术路径

```java
// ydsz-workflow-web：提交审批接口
@Service
public class FlowSubmissionService {

    @Resource
    private CronjobFeignClient cronjobFeignClient;

    /**
     * 提交审批（Seata AT 模式 PoC）
     *
     * <p>正常路径：流程实例落库 + cronjob 注册定时任务 → 两者都成功
     * <p>异常路径：cronjob 调用失败 → Seata 根据 undo_log 自动回滚已落库的流程实例
     *
     * @param request 提交请求
     * @return 流程实例 ID
     */
    @GlobalTransactional(name = "submit-flow-with-schedule", rollbackFor = Exception.class)
    public String submitFlow(FlowSubmissionRequest request) {
        // 1. workflow 模块本地落库（产生 undo_log 前镜像）
        FlowInstance instance = flowInstanceRepository.create(request);

        // 2. Feign 调用 cronjob 注册定时任务（XID 透传由 ydsz-common-feign 内置拦截器处理）
        cronjobFeignClient.registerTask(FlowTaskConverter.toScheduleRequest(instance));

        // 3. 若任何异常（RuntimeException / Seata 全局超时），Seata TC 协调两侧回滚
        return instance.getId();
    }
}
```

## 依赖配置

### workflow-web pom.xml（新增）

```xml
<!-- Seata 分布式事务（PoC 验证） -->
<dependency>
    <groupId>com.njydsz</groupId>
    <artifactId>ydsz-common-seata</artifactId>
</dependency>
```

### workflow-web application.yml

```yaml
ydsz:
  seata:
    enabled: ${SEATA_ENABLED:false}  # PoC 阶段默认关闭，按需开启
    application-id: ${spring.application.name}
    tx-service-group: default_tx_group
    data-source-proxy-mode: AT
    enable-auto-data-source-proxy: true
    undo-log:
      table-name: undo_log
      serialization: jackson
      only-care-update-columns: true
```

## 数据库变更

需要在 **workflow** 和 **cronjob** 数据库中各创建一张 `undo_log` 表：

```sql
-- docs/sql/V26.09.14__seata_undo_log_for_poc.sql
-- 适用于 workflow 和 cronjob 数据库

CREATE TABLE IF NOT EXISTS `undo_log` (
    `branch_id`     BIGINT       NOT NULL COMMENT 'branch transaction id',
    `xid`           VARCHAR(128) NOT NULL COMMENT 'global transaction id',
    `context`       VARCHAR(128) NOT NULL COMMENT 'undo_log context,such as serialization',
    `rollback_info` LONGBLOB     NOT NULL COMMENT 'rollback info',
    `log_status`    INT          NOT NULL COMMENT '0:normal status,1:defense status',
    `log_created`   DATETIME(6)  NOT NULL COMMENT 'create datetime',
    `log_modified`  DATETIME(6)  NOT NULL COMMENT 'modify datetime',
    UNIQUE KEY `ux_undo_log` (`xid`, `branch_id`)
) ENGINE = InnoDB
  AUTO_INCREMENT = 1
  DEFAULT CHARSET = utf8mb4
  COMMENT ='AT mode undo table for seata';
```

## 验证清单

| # | 验证项 | 预期结果 | 验证方式 |
|---|--------|---------|---------|
| 1 | 正常提交流程 + 注册定时任务 | 两者都成功，DB 可查询 | 单元测试 |
| 2 | cronjob 调用失败（模拟超时） | 流程实例自动回滚（undo_log 反向 SQL） | 集成测试（mock Feign 抛异常） |
| 3 | Seata Server 不可用（降级） | 业务降级为本地事务 + Outbox 事件（可配置） | 集成测试 |
| 4 | 并发提交（冲突 undo_log 锁） | 全局锁等待 / 超时重试 | 压力测试 |

## 风控措施

| 风险 | 缓解措施 |
|------|---------|
| Seata Server 单点不可用 | `application.yml` 默认 `enabled=false`，生产环境需独立 Seata Server 集群 |
| undo_log 性能开销 | `undo-log.table-name` 可按模块分表（如 `undo_log_workflow`） |
| 全局锁竞争 | 仅在强一致性场景开启，其他场景继续用 Outbox 降级 |
| 数据源代理兼容性 | 与 baomidou DynamicDataSource 共存测试（先验证 schema 隔离场景） |

## 推进路径

| 阶段 | 内容 | 工作量 | 负责域 |
|------|------|--------|--------|
| Phase 1 | DDL 脚本 + application.yml PoC 配置 + PoC 代码 | 2 人日 | 后端 |
| Phase 2 | 集成测试（模拟 Feign 异常 → 验证回滚） | 1 人日 | 后端 |
| Phase 3 | 性能基准（有/无 Seata 吞吐对比） | 1 人日 | 后端 |
| Phase 4 | 决策报告（是否推广到其他引擎） | 0.5 人日 | 架构 |
