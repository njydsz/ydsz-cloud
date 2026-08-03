# ydsz-project

> 项目全生命周期管理服务（端口 9009）

承载项目全生命周期核心业务域：商机 → 立项 → 合同 → 执行 → 成本 → 收入 → 利润 → 开票 → 回款 → 结项 → 售后。是 ydsz-pmis 平台的「经营主轴」，对标大厂 PMIS / 项目管理系统（如 Primavera P6 / Microsoft Project）的核心业务能力，复用 common-web、common-audit、common-lock、common-cache、common-excel、common-notify、common-search 等公共模块。

## 模块定位

| 属性 | 值 |
|---|---|
| **类型** | 部署单元（独立启动） |
| **端口** | 9009 |
| **启动类** | `com.njydsz.project.web.ProjectApplication` |
| **作用** | 项目全生命周期管理：商机→立项→合同→执行→成本→收入→利润→开票→回款→结项→售后 |
| **依赖** | ydsz-common（通过 common-web / common-audit / common-lock / common-cache / common-excel / common-notify / common-search）+ ydsz-literule（规则引擎）+ ydsz-userinfo / ydsz-system（Feign） |
| **构建顺序** | api → domain → infra → server → web |
| **版本** | 1.0.0 |

## 5 层 DDD 架构

```
ydsz-project/
├── ydsz-project-api/       # API 层：Feign 客户端 + 跨服务调用 DTO + Fallback
├── ydsz-project-domain/    # 领域层：实体 + 值对象 + 领域服务接口 + Repository 接口 + VO + Converter
├── ydsz-project-infra/     # 基础设施层：Repository 实现 + MyBatis Mapper
├── ydsz-project-server/    # 服务层：应用服务 + 领域服务实现 + 配置 + 健康 + 指标 + 事件监听
└── ydsz-project-web/       # Web 层：Controller + 启动类 + bootstrap/application 配置
```

| 子模块 | 关键内容 |
|---|---|
| `ydsz-project-api` | `ProjectInfoClient`（Feign 客户端）+ `ProjectInfoClientFallback` |
| `ydsz-project-domain` | 34 个实体类（按业务域分包）+ 35 个 VO + DTO + `ProjectConverter` + `ProjectResultCode` |
| `ydsz-project-infra` | 34 个 Repository 实现 + 34 个 MyBatis Mapper |
| `ydsz-project-server` | 35 个 Service + `ProjectAutoConfiguration` + `ProjectProperties` + `ProjectHealthIndicator` + `ProjectMetrics` + `CrossModuleEventListener` + `FlowEventQueueSubscriber` + `ProjectSearchProvider` |
| `ydsz-project-web` | 37 个 Controller + `ProjectApplication` 启动类 |

## 核心业务域

### 1. 商机管理

| 项 | 值 |
|---|---|
| 数据库表 | `ydsz_project_opportunity`、`ydsz_project_opportunity_follow` |
| Controller | `ProjectOpportunityController`、`ProjectOpportunityFollowController` |
| 功能 | A/B/C 分级、跟进记录、赢单 / 丢单、商机转化立项 |

### 2. 立项管理

| 项 | 值 |
|---|---|
| 数据库表 | `ydsz_project_initiation`、`ydsz_project_budget_item` |
| Controller | `ProjectInitiationController`、`ProjectBudgetItemController` |
| 功能 | WBS 预算、立项审批、阶段推进、按 PM 查询 |

项目状态机：`PRE_INITIATION → INITIATION → CONTRACT → EXECUTION → CLOSURE`（预立项 → 立项 → 合同 → 执行 → 收尾）。

### 3. 合同管理

| 项 | 值 |
|---|---|
| 数据库表 | `ydsz_project_contract`、`ydsz_project_contract_supplement`、`ydsz_project_contract_change`、`ydsz_project_contract_template` |
| Controller | `ProjectContractController`、`ProjectContractSupplementController`、`ProjectContractChangeController`、`ProjectContractTemplateController` |
| 功能 | 合同模板、补充协议、合同变更、合同风险标记 |

### 4. 变更管理

| 项 | 值 |
|---|---|
| 数据库表 | `ydsz_project_change` |
| Controller | `ProjectChangeController` |
| 功能 | 5 类变更：范围变更 / 成本变更 / 合同变更 / 人员变更 / 进度变更 |

### 5. WBS 执行

| 项 | 值 |
|---|---|
| 数据库表 | `ydsz_execution_wbs_task`、`ydsz_execution_time_entry` |
| Controller | `ExecutionWbsTaskController`、`ExecutionTimeEntryController` |
| 功能 | WBS 任务分解、工时填报、采购挂载、费用归集 |

### 6. EVM 挣值管理

| 项 | 值 |
|---|---|
| 数据库表 | `ydsz_evm_measure` |
| Controller | `EvmMeasureController` |
| 功能 | PV / EV / AC 三量 + CPI / SPI 绩效指数 + EAC / ETC / VAC 趋势预测 + S 曲线 |

关键计算公式：

```
SV  = EV - PV           （进度偏差；SV > 0 提前，SV < 0 滞后）
CV  = EV - AC           （成本偏差；CV > 0 节约，CV < 0 超支）
SPI = EV / PV           （进度绩效指数；SPI >= 1.0 正常）
CPI = EV / AC           （成本绩效指数；CPI >= 1.0 正常）
EAC = BAC / CPI         （完工估算；基于当前 CPI 推算总成本）
ETC = EAC - AC          （完工尚需估算）
VAC = BAC - EAC         （完工偏差；正数表示预算节约）
```

约束：已锁定（`status=LOCKED`）的 EVM 测量记录禁止修改；PV / EV / AC 三量由系统计算，禁止手工录入；同一项目同一周期仅允许一条有效测量记录（唯一索引保证）。

### 7. 成本管理

| 项 | 值 |
|---|---|
| 数据库表 | `ydsz_cost_allocation`、`ydsz_cost_purchase` |
| Controller | `CostAllocationController`、`CostPurchaseController` |
| 功能 | 成本归集（人工 / 采购 / 费用）、成本分摊、成本预警 |

### 8. 收入确认

| 项 | 值 |
|---|---|
| 数据库表 | `ydsz_project_revenue`、`ydsz_project_invoice`、`ydsz_project_payment` |
| Controller | `ProjectRevenueController`、`ProjectInvoiceController`、`ProjectPaymentController` |
| 功能 | 终验确认 / 里程碑确认 / 月结确认、开票管理、回款管理 |

### 9. 风险管理

| 项 | 值 |
|---|---|
| 数据库表 | `ydsz_execution_risk` |
| Controller | `ExecutionRiskController` |
| 功能 | 风险登记、风险评估、风险应对、风险等级（高 / 中 / 低） |

### 10. 费率管理

| 项 | 值 |
|---|---|
| 数据库表 | `ydsz_rate_card`、`ydsz_rate_internal` |
| Controller | `RateCardController`、`RateInternalController` |
| 功能 | 对外报价费率（RateCard）+ 对内成本费率（RateInternal）双费率体系 |

### 11. 利润核算

| 项 | 值 |
|---|---|
| 数据库表 | `ydsz_project_profit_simulation`、`ydsz_project_profit_snapshot` |
| Controller | `ProjectProfitSimulationController`、`ProjectProfitSnapshotController` |
| 功能 | 多版本利润模拟、利润快照、利润趋势分析 |

### 12. 客户信用

| 项 | 值 |
|---|---|
| 数据库表 | `ydsz_project_customer_credit` |
| Controller | `ProjectCustomerCreditController` |
| 功能 | A / B / C / D 四级客户信用评级、信用额度管控 |

### 13. 交付管理

| 项 | 值 |
|---|---|
| 数据库表 | `ydsz_execution_delivery_item`、`ydsz_execution_delivery_standard`、`ydsz_project_gate_review` |
| Controller | `ExecutionDeliveryItemController`、`ExecutionDeliveryStandardController`、`ProjectGateReviewController` |
| 功能 | CD1-CD5 门径评审、8 类交付物标准、交付验收 |

### 14. 项目结项

| 项 | 值 |
|---|---|
| 数据库表 | `ydsz_execution_closure` |
| Controller | `ExecutionClosureController` |
| 功能 | 正式结项、预结项、强制结项 |

### 15. 售后管理

| 项 | 值 |
|---|---|
| 数据库表 | `ydsz_warranty`、`ydsz_ops_ticket`、`ydsz_satisfaction` |
| Controller | `WarrantyController`、`OpsTicketController`、`SatisfactionController` |
| 功能 | 质保期管理、运维工单、客户满意度回访 |

### 16. 资源管理

| 项 | 值 |
|---|---|
| 数据库表 | `ydsz_billable_utilization_snapshot` |
| Controller | `BillableUtilizationSnapshotController` |
| 功能 | 资源池管理、Bench 资源管理、利用率快照 |

### 17. 对账管理

| 项 | 值 |
|---|---|
| 数据库表 | `ydsz_project_reconcile_daily` |
| Controller | `ProjectReconcileDailyController` |
| 功能 | 每日对账、差异标记、对账归档 |

### 18. 费用报销

| 项 | 值 |
|---|---|
| 数据库表 | `ydsz_project_expense` |
| Controller | `ProjectExpenseController` |
| 功能 | 项目费用报销、费用分摊到 WBS、报销审批 |

### 19. 预警管理

| 项 | 值 |
|---|---|
| 数据库表 | `ydsz_alert_dispatch` |
| Controller | `AlertDispatchController` |
| 功能 | 红 / 黄 / 绿三色阈值告警、告警分发、告警归档 |

### 20. 报表分析

| 项 | 值 |
|---|---|
| Controller | `ProjectSearchController`、`ProjectExcelController`、`ProjectFileController` |
| 功能 | 项目立项全文检索、高级报表、Dashboard 数据导出、项目附件管理 |

`ProjectSearchController` 基于 `UnifiedSearchService` 提供项目立项数据的全文检索能力，仅检索 `project` 域，适合项目管理后台的精细化搜索；支持高亮、模糊匹配、过滤、分页、排序，按当前用户角色 / 部门 / 租户下推权限。

## 数据库表清单

共 34 张表，按业务域分组：

### 商机与立项

| 表名 | 实体类 | 说明 |
|---|---|---|
| `ydsz_project_opportunity` | `ProjectOpportunity` | 商机主表 |
| `ydsz_project_opportunity_follow` | `ProjectOpportunityFollow` | 商机跟进记录 |
| `ydsz_project_initiation` | `ProjectInitiation` | 项目立项主表 |
| `ydsz_project_budget_item` | `ProjectBudgetItem` | 立项预算项（WBS） |

### 合同与变更

| 表名 | 实体类 | 说明 |
|---|---|---|
| `ydsz_project_contract` | `ProjectContract` | 合同主表 |
| `ydsz_project_contract_supplement` | `ProjectContractSupplement` | 合同补充协议 |
| `ydsz_project_contract_change` | `ProjectContractChange` | 合同变更 |
| `ydsz_project_contract_template` | `ProjectContractTemplate` | 合同模板 |
| `ydsz_project_change` | `ProjectChange` | 5 类项目变更 |

### 执行与交付

| 表名 | 实体类 | 说明 |
|---|---|---|
| `ydsz_execution_wbs_task` | `ExecutionWbsTask` | WBS 任务 |
| `ydsz_execution_time_entry` | `ExecutionTimeEntry` | 工时填报 |
| `ydsz_execution_risk` | `ExecutionRisk` | 执行风险 |
| `ydsz_execution_delivery_item` | `ExecutionDeliveryItem` | 交付物 |
| `ydsz_execution_delivery_standard` | `ExecutionDeliveryStandard` | 交付物标准 |
| `ydsz_execution_closure` | `ExecutionClosure` | 项目结项 |
| `ydsz_project_gate_review` | `ProjectGateReview` | CD1-CD5 门径评审 |

### 成本与利润

| 表名 | 实体类 | 说明 |
|---|---|---|
| `ydsz_cost_allocation` | `CostAllocation` | 成本归集 |
| `ydsz_cost_purchase` | `CostPurchase` | 采购成本 |
| `ydsz_project_profit_simulation` | `ProjectProfitSimulation` | 利润模拟 |
| `ydsz_project_profit_snapshot` | `ProjectProfitSnapshot` | 利润快照 |
| `ydsz_project_expense` | `ProjectExpense` | 费用报销 |

### 收入与对账

| 表名 | 实体类 | 说明 |
|---|---|---|
| `ydsz_project_revenue` | `ProjectRevenue` | 收入确认 |
| `ydsz_project_invoice` | `ProjectInvoice` | 开票 |
| `ydsz_project_payment` | `ProjectPayment` | 回款 |
| `ydsz_project_reconcile_daily` | `ProjectReconcileDaily` | 每日对账 |

### EVM 与费率

| 表名 | 实体类 | 说明 |
|---|---|---|
| `ydsz_evm_measure` | `EvmMeasure` | EVM 挣值测量 |
| `ydsz_rate_card` | `RateCard` | 对外报价费率 |
| `ydsz_rate_internal` | `RateInternal` | 对内成本费率 |

### 客户与资源

| 表名 | 实体类 | 说明 |
|---|---|---|
| `ydsz_project_customer_credit` | `ProjectCustomerCredit` | 客户信用 |
| `ydsz_billable_utilization_snapshot` | `BillableUtilizationSnapshot` | 资源利用率快照 |
| `ydsz_alert_dispatch` | `AlertDispatch` | 预警分发 |

### 售后

| 表名 | 实体类 | 说明 |
|---|---|---|
| `ydsz_warranty` | `Warranty` | 质保 |
| `ydsz_ops_ticket` | `OpsTicket` | 运维工单 |
| `ydsz_satisfaction` | `Satisfaction` | 客户满意度 |

> 注：所有实体类继承 `MpBaseEntity<String>`，主键为 String 类型（雪花算法）。实体类遵循项目规范，不以 `DO` 为后缀。

## 接入方式

### 1. POM 引入依赖

本模块为部署单元，不对外暴露 starter；如需跨服务调用项目信息，引入 API 子模块：

```xml
<dependency>
    <groupId>com.njydsz</groupId>
    <artifactId>ydsz-project-api</artifactId>
</dependency>
```

### 2. bootstrap.yml 配置

```yaml
server:
  port: 9009
  servlet:
    context-path: /

spring:
  application:
    name: ydsz-project
  profiles:
    active: ${SPRING_PROFILES_ACTIVE:dev}
  cloud:
    nacos:
      discovery:
        enabled: true
        server-addr: ${NACOS_SERVER_ADDR:127.0.0.1:8848}
        namespace: ${NACOS_NAMESPACE:ydsz}
      config:
        enabled: true
        file-extension: yaml
        shared-configs:
          - data-id: ydsz-common.yaml
            group: ${spring.profiles.active}
            refresh: true
          - data-id: ydsz-project.yml
            group: ${spring.profiles.active}
            refresh: true
```

### 3. Feign 客户端调用

```java
import com.njydsz.project.api.client.ProjectInfoClient;
import com.njydsz.common.core.response.BaseResponse;

@FeignClient(name = "ydsz-project", contextId = "projectInfoClient")
public interface ProjectInfoClient {

    @GetMapping("/api/v1/project/info/name")
    BaseResponse<String> getProjectName(@RequestParam String projectId);

    @GetMapping("/api/v1/project/info/status")
    BaseResponse<String> getProjectStatus(@RequestParam String projectId);
}
```

调用方启用 Feign 扫描：

```java
import com.njydsz.common.feign.annotation.EnableYdszFeign;

@EnableYdszFeign(basePackages = {"com.njydsz.project.api"})
```

## 配置项

| 配置 | 默认值 | 说明 |
|---|---|---|
| `ydsz.project.enabled` | true | 是否启用项目模块自动配置 |
| `ydsz.project.default-initial-stage` | `PRE_INITIATION` | 项目立项默认初始阶段 |
| `ydsz.project.default-status` | `DRAFT` | 项目立项默认初始状态 |
| `ydsz.project.default-level` | `C` | 项目立项默认等级（A/B/C/D） |
| `ydsz.project.gate-review-required` | false | 阶段推进是否需要门审 |
| `ydsz.project.cache-definition-ttl-minutes` | 30 | 项目定义缓存 TTL（分钟） |
| `ydsz.project.cache-max-size` | 1000 | 项目本地缓存最大条目数 |
| `ydsz.project.query-cache-ttl-seconds` | 60 | 查询结果缓存 TTL（秒） |
| `ydsz.project.export-max-rows` | 10000 | 导出 Excel 最大行数限制 |
| `ydsz.project.attachment-max-size-mb` | 50 | 项目附件最大上传大小（MB） |
| `ydsz.project.outbox-enabled` | false | 是否启用 Outbox 事件发布 |
| `ydsz.project.notify-enabled` | true | 是否启用项目通知 |
| `ydsz.project.cache.definition-ttl-minutes` | 30 | 项目定义缓存 TTL（子属性） |
| `ydsz.project.cache.max-size` | 1000 | 项目本地缓存最大条目数（子属性） |
| `ydsz.project.notify.on-created` | false | 立项创建时是否发送通知 |
| `ydsz.project.notify.on-stage-changed` | false | 阶段变更时是否发送通知 |
| `ydsz.project.notify.on-closed` | false | 项目关闭时是否发送通知 |
| `ydsz.project.notify.gate-reminder-hours` | 24 | 门审提醒提前小时数 |

## 使用示例

### 1. 创建商机

```java
import com.njydsz.project.domain.dto.post.ProjectOpportunityPostDTO;
import com.njydsz.project.server.service.ProjectOpportunityService;

@RequiredArgsConstructor
public class OpportunityDemo {

    private final ProjectOpportunityService projectOpportunityService;

    public String createOpportunity() {
        ProjectOpportunityPostDTO dto = new ProjectOpportunityPostDTO();
        dto.setCustomerName("某集团有限公司");
        dto.setOpportunityName("ERP 升级项目");
        dto.setLevel("A");
        dto.setEstimatedAmount(new BigDecimal("5000000"));
        return projectOpportunityService.save(dto);
    }
}
```

### 2. 发起立项审批

```java
import com.njydsz.project.domain.dto.post.ProjectInitiationPostDTO;
import com.njydsz.project.server.service.ProjectInitiationService;

public String initiateProject(ProjectInitiationPostDTO dto) {
    // 商机赢单后，由销售创建预立项，PM 完善信息后推进阶段
    String projectId = projectInitiationService.save(dto);
    // 推进阶段：PRE_INITIATION → INITIATION（提交立项审批）
    projectInitiationService.advanceStage(projectId);
    return projectId;
}
```

### 3. EVM 计算

EVM 计算为高耗时操作，由 `EvmMeasureService` 内部加分布式锁（`ydsz:project:evm:calculate:lock`）防并发；趋势预测结果存缓存（`ydsz:project:evm:trend:cache:projectId`，TTL 1h）。

```java
import com.njydsz.project.server.service.EvmMeasureService;
import com.njydsz.project.domain.vo.EvmMeasureVO;

// 系统按周期自动计算 PV / EV / AC 三量，并派生 SV / CV / SPI / CPI / EAC / ETC / VAC
EvmMeasureVO measure = evmMeasureService.calculateAndSnapshot(projectId, period);
if (measure.getCpi().compareTo(BigDecimal.ONE) < 0) {
    // CPI < 1.0：成本超支，触发预警
    alertDispatchService.dispatch(projectId, "CPI_BELOW_THRESHOLD", measure.getCpi());
}
```

### 4. 利润模拟

```java
import com.njydsz.project.server.service.ProjectProfitSimulationService;

// 多版本模拟：基于不同的成本假设（如费率调整 / 工时上浮）生成多个模拟版本
String simulationId = projectProfitSimulationService.createSimulation(
        projectId, "保守版本", assumptions);
// 模拟结果落 ydsz_project_profit_simulation；可对当前实际数据快照到 ydsz_project_profit_snapshot
projectProfitSimulationService.snapshot(simulationId);
```

## 跨模块事件订阅

`CrossModuleEventListener` 订阅其他模块的领域事件，实现跨模块解耦：

| 事件类型 | 触发动作 |
|---|---|
| `FLOW_INSTANCE_APPROVED` | 工作流审批通过后，按 businessType（INITIATION / CHANGE / CLOSEOUT）更新项目 / 合同状态 |
| `FLOW_INSTANCE_REJECTED` | 工作流审批驳回后，回滚项目状态 |
| `USER_LOGIN` | 用户登录时预热项目缓存 |
| `CONFIG_CHANGED` | 系统配置变更时刷新项目参数缓存 |

监听器使用 `@Async` 异步处理，失败仅记录日志不抛出，避免阻塞事件总线。`FlowEventQueueSubscriber` 订阅 Outbox 队列，保证事件至少投递一次。

## SPI 扩展点

| 扩展点 | 用途 | 实现方 |
|---|---|---|
| `IProjectInitiationRepository` | 项目立项仓储接口，供健康检查探针使用 | `ProjectInitiationRepository`（infra 层） |
| `ProjectSearchProvider` | 项目搜索数据提供者，向 `UnifiedSearchService` 注册项目立项索引 | `ydsz-project-server` 内置实现 |
| `ProjectMetrics` | 项目模块 Micrometer 指标采集器，可由业务方覆盖 | `ProjectAutoConfiguration` 注册默认实现 |

## 健康检查

| 端点 | 说明 | 检查项 |
|---|---|---|
| `/actuator/health/project` | 项目模块健康检查 | ① `ydsz_project_initiation` 表可达性（count 探针）；② Micrometer 指标注册状态 |

健康检查由 `ProjectHealthIndicator` 实现，继承 `AbstractModuleHealthIndicator` 统一基类，使用模板方法模式。任意一项失败，整体健康状态降级为 `DOWN`，但不会中断后续检查项。

## 监控指标

| 指标 | 类型 | 说明 |
|---|---|---|
| `project.initiation.created` | Counter | 项目立项创建计数 |
| `project.initiation.updated` | Counter | 项目立项更新计数 |
| `project.initiation.deleted` | Counter | 项目立项删除计数 |
| `project.initiation.query.duration` | Timer | 项目立项查询耗时（毫秒） |

指标由 `ProjectMetrics` 采集（继承 `AbstractModuleMetrics`），通过 `/actuator/prometheus` 端点暴露，供 Grafana / Prometheus 抓取。

## 启用注解

启动类 `ProjectApplication` 通过以下注解组合启用 ydsz 公共能力：

| 注解 | 来源 | 作用 |
|---|---|---|
| `@SpringBootApplication` | Spring Boot | 自动配置 + 组件扫描（`com.njydsz.project` + `com.njydsz.common`） |
| `@EnableDiscoveryClient` | Spring Cloud | Nacos 服务发现 |
| `@EnableYdszAuth` | common-auth | 鉴权拦截 + 权限校验 |
| `@EnableYdszAudit` | common-audit | 审计日志落 `ydsz_operation_log` |
| `@EnableYdszSafe` | common-safe | SQL 防火墙 + XSS 防护 |
| `@EnableYdszNotify` | common-notify | 通知发送（IM / 邮件） |
| `@EnableYdszFeign` | common-feign | Feign 客户端扫描（`project.api` + `userinfo.api` + `system.api`） |
| `@MapperScan` | MyBatis | 扫描 `com.njydsz.project.infra.mapper` |
| `@EnableScheduling` | Spring | 定时任务（对账 / 快照 / 预警扫描） |

## 注意事项

1. **EVM 数据完整性**：PV / EV / AC 三量必须由系统计算，禁止手工录入；已锁定的测量记录不可修改；同一项目同一周期仅允许一条有效测量记录（唯一索引保证）。
2. **项目状态机不可逆**：阶段推进（`PRE_INITIATION → INITIATION → CONTRACT → EXECUTION → CLOSURE`）单向流动，回退需走变更流程；`advanceStage` 默认受 `gateReviewRequired` 门控。
3. **数据权限**：分页查询受 `DataScopeInterceptor` 数据权限控制，PM 仅可见自己负责的项目；管理员通过 `@DataPermissionIgnore` 跳过限制。
4. **跨模块事件容错**：`CrossModuleEventListener` 使用 `@Async` 异步处理，异常仅记录日志不抛出，避免阻塞事件总线；Outbox 队列保证至少一次投递，业务方需做幂等。
5. **利润快照与模拟分离**：`ydsz_project_profit_simulation` 存多版本模拟假设，`ydsz_project_profit_snapshot` 存某时刻的实际数据冻结，二者不可混用。
6. **附件大小限制**：项目附件上传受 `ydsz.project.attachment-max-size-mb`（默认 50MB）限制，超出由 `ProjectFileController` 拒绝。
7. **Excel 导出限流**：导出受 `ydsz.project.export-max-rows`（默认 10000 行）限制，防止 OOM；超出建议分批导出或走异步任务。
8. **实体命名规范**：所有实体类继承 `MpBaseEntity<String>`，主键为 String（雪花算法）；实体类不以 `DO` 为后缀，直接使用业务名称（如 `ProjectInitiation`、`Warranty`）。

## 变更记录

- **v1.0.0**（2026-08-02）：初始版本。覆盖商机 / 立项 / 合同 / 变更 / WBS 执行 / EVM / 成本 / 收入 / 风险 / 费率 / 利润 / 客户信用 / 交付 / 结项 / 售后 / 资源 / 对账 / 费用 / 预警 / 报表共 20 个业务域；34 张数据库表；37 个 Controller；接入方式、配置项、使用示例、跨模块事件订阅、SPI 扩展点、健康检查、监控指标、注意事项等章节齐备。
