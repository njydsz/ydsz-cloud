package com.njydsz.project.web.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.core.response.PageResponse;
import com.njydsz.project.domain.entity.project.ProjectBudgetItem;
import com.njydsz.project.server.service.ProjectBudgetItemService;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;
import com.njydsz.project.domain.converter.ProjectConverter;
import com.njydsz.project.domain.vo.ProjectBudgetItemVO;
import com.njydsz.project.domain.dto.put.ProjectBudgetItemPutDTO;
import com.njydsz.project.domain.dto.post.ProjectBudgetItemPostDTO;
import com.njydsz.common.lock.annotation.Idempotent;

/**
 * 立项预算明细 Controller。
 *
 * <p>提供项目立项预算明细的 REST API，是「项目管理 / 预算管控 / 成本控制」业务域的核心 Controller。
 * 对标大厂 PMIS / ERP（如 SAP CO / 用友 NC / 金蝶 EAS）系统中的「项目预算 / 成本预算 / 预算控制 / 预算占用」管理界面。
 *
 * <h3>核心能力</h3>
 * <ul>
 *   <li>预算分类：按 5 大类别（LABOR 人力 / PURCHASE 采购 / EXPENSE 费用 / OUTSOURCE 外包 / OTHER 其他）拆解预算</li>
 *   <li>预算占用：与实际成本表联动，实时计算「已发生 / 在途 / 剩余」预算</li>
 *   <li>预算预警：触发「预算占用率 80% 黄灯 / 95% 红灯」预警规则，自动推送预警通知</li>
 *   <li>预算变更：项目变更（{@code ProjectChange}）时联动调整预算明细</li>
 *   <li>预算对比：与历史项目预算、行业基准预算对比分析</li>
 * </ul>
 *
 * <h3>预算占用率计算</h3>
 * <pre>
 *   占用率 = 累计已发生成本 / 预算金额 × 100%
 *   剩余预算 = 预算金额 - 累计已发生成本 - 在途（已审批未付款）
 *   预警等级 = 0（绿灯） / 80% 黄灯 / 95% 红灯
 * </pre>
 *
 * <h3>关键约束</h3>
 * <ul>
 *   <li>项目立项后<b>必须</b>录入预算明细才能推进到执行阶段</li>
 *   <li>已审批通过的预算明细，修改需走「项目变更 / 预算调整」审批流程</li>
 *   <li>预算金额<b>不得</b>超过合同总金额（系统强校验）</li>
 *   <li>采购类预算需关联具体的采购计划（{@code ProjectCostPurchase}）</li>
 * </ul>
 *
 * <h3>安全与稳定性</h3>
 * <ul>
 *   <li>所有写接口 {@code @Audit} 审计</li>
 *   <li>分页查询受 {@code DataScopeInterceptor} 数据权限控制</li>
 *   <li>预算变更走工作流审批（{@code ydsz-workflow}）</li>
 *   <li>预算占用计算走预聚合表，避免大表实时 SQL 聚合</li>
 * </ul>
 *
 * <h3>接口路径</h3>
 * <pre>
 *   GET    /api/v1/project/project/budget/item/{id}   - 按 ID 查询
 *   GET    /api/v1/project/project/budget/item/page   - 分页查询
 *   POST   /api/v1/project/project/budget/item        - 创建预算明细
 *   PUT    /api/v1/project/project/budget/item        - 更新预算明细
 *   DELETE /api/v1/project/project/budget/item/{id}   - 删除预算明细
 * </pre>
 *
 * <h3>架构位置</h3>
 * <pre>
 *   前端 (PC Web) → ydsz-gateway → ydsz-project-web (本 Controller)
 *                                          ↓
 *                              ydsz-project-server.ProjectBudgetItemService
 *                              ydsz-project-server.BudgetOccupancyService
 *                              ydsz-project-server.BudgetAlertJob (预警定时任务)
 *                                          ↓
 *                              ydsz-project-infra.ProjectBudgetItemMapper
 *                              ydsz-project-infra.BudgetOccupancyMapper (预聚合)
 *                                          ↓
 *                              ydsz_project_budget_item
 *                              ydsz_cost_allocation (实际成本)
 * </pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see com.njydsz.project.server.service.ProjectBudgetItemService 预算 Service
 * @see com.njydsz.project.domain.entity.project.ProjectBudgetItem 预算明细实体
 * @see CostAllocationController 成本归集 Controller（实际成本来源）
 * @see ProjectChangeController 项目变更 Controller（预算调整入口）
 */
@RestController
@RequestMapping("/api/v1/project/project/budget/item")
@RequiredArgsConstructor
public class ProjectBudgetItemController {

    private final ProjectBudgetItemService service;

    /**
     * 按 ID 查询预算明细。
     *
     * <p>返回预算明细实体 + 富化的项目名称 / 预算科目名称 / 责任人等外键字段。
     *
     * @param id 预算明细主键 ID
     * @return 预算明细视图对象
     */
    @GetMapping("/{id}")
    public BaseResponse<ProjectBudgetItemVO> getById(@PathVariable String id) { return BaseResponse.success(ProjectConverter.INSTANT.entityToVO(service.getById(id))); }

    /**
     * 分页查询预算明细列表。
     *
     * <p>支持按项目、预算类别（LABOR/PURCHASE/...）、预警等级等多维度筛选。
     *
     * @param p 当前页码（默认 1）
     * @param s 每页条数（默认 10）
     * @return 分页预算明细视图对象
     */
    @GetMapping("/page")
    public PageResponse<ProjectBudgetItemVO> page(@RequestParam(defaultValue="1") int p, @RequestParam(defaultValue="10") int s) {
        IPage<ProjectBudgetItem> r = service.page(p, s);
        return PageResponse.success(ProjectConverter.INSTANT.projectBudgetItemListToVO(r.getRecords()), r.getTotal(), (int)r.getCurrent(), (int)r.getSize());
    }

    /**
     * 创建预算明细。
     *
     * <p>通常从项目立项阶段批量导入；创建后自动校验：
     * <ol>
     *   <li>所有明细金额合计 ≤ 项目合同金额</li>
     *   <li>采购类预算（{@code category=PURCHASE}）需填写供应商信息</li>
     *   <li>触发预算占用率初值计算</li>
     * </ol>
     *
     * @param dto 预算明细创建入参（项目 ID、预算类别、金额、责任人等）
     * @return 是否创建成功
     */
    @Idempotent(key = "ydsz:project:ProjectBudgetItemController:save:lock", ttlSeconds = 5)
    @PostMapping
    @Audit(action=AuditAction.CREATE, module="PROJECT", content="Create ProjectBudgetItem")
    public BaseResponse<Boolean> save(@RequestBody ProjectBudgetItemPostDTO dto) { return BaseResponse.success(service.save(ProjectConverter.INSTANT.postDtoToEntity(dto))); }

    /**
     * 更新预算明细。
     *
     * <p>已发生成本的预算明细<b>严禁</b>直接修改金额；预算调整必须通过
     * {@link ProjectChangeController} 走变更流程。
     *
     * @param dto 预算明细更新入参
     * @return 是否更新成功
     */
    @Idempotent(key = "ydsz:project:ProjectBudgetItemController:update:lock", ttlSeconds = 5)
    @PutMapping
    @Audit(action=AuditAction.UPDATE, module="PROJECT", content="Update ProjectBudgetItem")
    public BaseResponse<Boolean> update(@RequestBody ProjectBudgetItemPutDTO dto) { return BaseResponse.success(service.updateById(ProjectConverter.INSTANT.putDtoToEntity(dto))); }

    /**
     * 按 ID 删除预算明细。
     *
     * <p>采用<b>逻辑删除</b>；已发生成本或被采购单关联的预算明细<b>严禁</b>删除。
     *
     * @param id 预算明细主键 ID
     * @return 是否删除成功
     */
    @Idempotent(key = "ydsz:project:ProjectBudgetItemController:remove:lock", ttlSeconds = 5)
    @DeleteMapping("/{id}")
    @Audit(action=AuditAction.DELETE, module="PROJECT", content="Delete ProjectBudgetItem")
    public BaseResponse<Boolean> remove(@PathVariable String id) { return BaseResponse.success(service.removeById(id)); }
}
