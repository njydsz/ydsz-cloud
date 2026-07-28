package com.njydsz.project.web.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.core.response.PageResponse;
import com.njydsz.project.domain.entity.cost.CostAllocation;
import com.njydsz.project.server.service.CostAllocationService;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;
import com.njydsz.project.domain.converter.ProjectConverter;
import com.njydsz.project.domain.vo.CostAllocationVO;
import com.njydsz.project.domain.dto.post.CostAllocationPostDTO;
import com.njydsz.project.domain.dto.put.CostAllocationPutDTO;
import com.njydsz.common.lock.annotation.Idempotent;

/**
 * 成本分摊 Controller。
 *
 * <p>提供项目成本分摊记录的 REST API，是「项目管理 / 财务成本归集 / 项目利润核算」业务域的核心 Controller。
 * 对标大厂 PMIS / ERP（如 SAP CO-PA / 用友 NC / 金蝶 EAS）系统中的「项目成本归集 / 成本中心核算 /
 * 期间成本结转 / 成本要素分析」管理界面。
 *
 * <h3>核心能力</h3>
 * <ul>
 *   <li>多维度归集：按 {@code period × initiationId × costCategory} 三维度归集项目成本</li>
 *   <li>5 大成本类别：LABOR（人力）/ PURCHASE（采购）/ EXPENSE（费用）/ OUTSOURCE（外包）/ OTHER（其他）</li>
 *   <li>期间结转：按月（{@code yyyy-MM}）滚动归集，作为利润快照的输入数据</li>
 *   <li>预算对比：与 {@code ProjectBudgetItem} 实时对比，计算预算占用率与预警</li>
 *   <li>成本追溯：单条成本记录可追溯到原始单据（工时 / 采购单 / 报销单）</li>
 * </ul>
 *
 * <h3>归集流程</h3>
 * <pre>
 *  原始单据（工时 / 采购 / 报销） → 成本要素归集 → 期间分摊 → 成本中心确认 → 利润快照
 *          ↓                          ↓                ↓                ↓                ↓
 *   TimeEntryService          CostElementService  PeriodCloseJob   CostCenterService  ProfitSnapshotJob
 *   CostPurchaseService       LaborCostCalculator                    AllocationTrigger
 *   ProjectExpenseService
 * </pre>
 *
 * <h3>关键约束</h3>
 * <ul>
 *   <li>已结账（{@code status=CLOSED}）的成本分摊<b>严禁</b>修改，需走「反结账」流程</li>
 *   <li>同一项目同一期间同一类别<b>只能存在一条</b>有效分摊记录（唯一索引保证）</li>
 *   <li>成本数据是利润快照的唯一输入源，禁止手工抹账</li>
 *   <li>财务期末关账后，{@code CostAllocation} 同步进入「已锁」状态</li>
 * </ul>
 *
 * <h3>安全与稳定性</h3>
 * <ul>
 *   <li>所有写接口 {@code @Audit} 审计，写操作落 {@code ydsz_operation_log}</li>
 *   <li>分页查询受 {@code DataScopeInterceptor} 数据权限控制（财务 / 销售总监可见全量）</li>
 *   <li>成本归集加分布式锁（{@code ydsz:project:cost:allocation:lock}）防并发</li>
 *   <li>成本快照重算走预聚合表，避免大表实时 SQL 聚合</li>
 * </ul>
 *
 * <h3>接口路径</h3>
 * <pre>
 *   GET    /api/v1/project/cost/allocation/{id}   - 按 ID 查询
 *   GET    /api/v1/project/cost/allocation/page   - 分页查询
 *   POST   /api/v1/project/cost/allocation        - 创建分摊记录
 *   PUT    /api/v1/project/cost/allocation        - 更新分摊记录
 *   DELETE /api/v1/project/cost/allocation/{id}   - 删除分摊记录
 * </pre>
 *
 * <h3>架构位置</h3>
 * <pre>
 *   前端 (PC Web) → ydsz-gateway → ydsz-project-web (本 Controller)
 *                                          ↓
 *                              ydsz-project-server.CostAllocationService
 *                              ydsz-project-server.PeriodCloseJob (期间关账)
 *                              ydsz-project-server.CostElementService (要素归集)
 *                                          ↓
 *                              ydsz-project-infra.CostAllocationMapper
 *                                          ↓
 *                              ydsz_cost_allocation
 *                              ydsz_execution_time_entry (工时源)
 *                              ydsz_cost_purchase (采购源)
 *                              ydsz_project_expense (费用源)
 * </pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see com.njydsz.project.server.service.CostAllocationService 成本归集 Service
 * @see com.njydsz.project.domain.entity.cost.CostAllocation 成本归集体
 * @see ProjectProfitSnapshotController 利润快照 Controller（成本的下游消费者）
 * @see ProjectBudgetItemController 立项预算明细 Controller（成本对比基准）
 */
@RestController
@RequestMapping("/api/v1/project/cost/allocation")
@RequiredArgsConstructor
public class CostAllocationController {

    private final CostAllocationService service;

    /**
     * 按 ID 查询成本分摊。
     *
     * <p>返回成本分摊实体 + 富化的项目名称 / 成本科目名称 / 责任人 / 期间等外键字段。
     *
     * @param id 分摊记录主键 ID
     * @return 分摊记录视图对象
     */
    @GetMapping("/{id}")
    public BaseResponse<CostAllocationVO> getById(@PathVariable String id) { return BaseResponse.success(ProjectConverter.INSTANT.entityToVO(service.getById(id))); }

    /**
     * 分页查询成本分摊列表。
     *
     * <p>支持按项目、期间、成本类别、状态等多维度筛选；按期间倒序返回。
     *
     * @param p 当前页码（默认 1）
     * @param s 每页条数（默认 10）
     * @return 分页分摊记录视图对象
     */
    @GetMapping("/page")
    public PageResponse<CostAllocationVO> page(@RequestParam(defaultValue="1") int p, @RequestParam(defaultValue="10") int s) {
        IPage<CostAllocation> r = service.page(p, s);
        return PageResponse.success(ProjectConverter.INSTANT.costAllocationListToVO(r.getRecords()), r.getTotal(), (int)r.getCurrent(), (int)r.getSize());
    }

    /**
     * 创建成本分摊（手动触发）。
     *
     * <p>通常由定时任务（{@code PeriodCloseJob}，每月 1 号凌晨）自动调用；本接口用于「数据修正」场景下手动归集。
     * 创建时自动：
     * <ol>
     *   <li>按项目 ID + 期间 + 类别汇总原始成本（工时 / 采购 / 费用）</li>
     *   <li>校验项目存在性与期间有效性</li>
     *   <li>检查唯一约束（项目 + 期间 + 类别）</li>
     * </ol>
     *
     * @param dto 分摊记录创建入参（项目 ID、期间、成本类别、金额等）
     * @return 是否创建成功
     */
    @Idempotent(key = "ydsz:project:CostAllocationController:save:lock", ttlSeconds = 5)
    @PostMapping
    @Audit(action=AuditAction.CREATE, module="PROJECT", content="Create CostAllocation")
    public BaseResponse<Boolean> save(@RequestBody CostAllocationPostDTO dto) { return BaseResponse.success(service.save(ProjectConverter.INSTANT.postDtoToEntity(dto))); }

    /**
     * 更新成本分摊。
     *
     * <p>已结账（{@code status=CLOSED}）的分摊记录<b>严禁</b>修改；错账处理：{@code CostAllocationService.unlockPeriod} 走「反结账」流程。
     *
     * @param dto 分摊记录更新入参
     * @return 是否更新成功
     */
    @Idempotent(key = "ydsz:project:CostAllocationController:update:lock", ttlSeconds = 5)
    @PutMapping
    @Audit(action=AuditAction.UPDATE, module="PROJECT", content="Update CostAllocation")
    public BaseResponse<Boolean> update(@RequestBody CostAllocationPutDTO dto) { return BaseResponse.success(service.updateById(ProjectConverter.INSTANT.putDtoToEntity(dto))); }

    /**
     * 按 ID 删除成本分摊。
     *
     * <p>采用<b>逻辑删除</b>；已结账或被利润快照引用的分摊记录<b>严禁</b>删除。
     *
     * @param id 分摊记录主键 ID
     * @return 是否删除成功
     */
    @Idempotent(key = "ydsz:project:CostAllocationController:remove:lock", ttlSeconds = 5)
    @DeleteMapping("/{id}")
    @Audit(action=AuditAction.DELETE, module="PROJECT", content="Delete CostAllocation")
    public BaseResponse<Boolean> remove(@PathVariable String id) { return BaseResponse.success(service.removeById(id)); }
}
