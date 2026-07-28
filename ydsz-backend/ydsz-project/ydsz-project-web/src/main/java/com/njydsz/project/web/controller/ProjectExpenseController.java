package com.njydsz.project.web.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.core.response.PageResponse;
import com.njydsz.project.domain.entity.project.ProjectExpense;
import com.njydsz.project.server.service.ProjectExpenseService;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;
import com.njydsz.project.domain.converter.ProjectConverter;
import com.njydsz.project.domain.vo.ProjectExpenseVO;
import com.njydsz.project.domain.dto.put.ProjectExpensePutDTO;
import com.njydsz.project.domain.dto.post.ProjectExpensePostDTO;
import com.njydsz.common.lock.annotation.Idempotent;

/**
 * 项目费用 Controller
 *
 * <p>提供项目费用报销 / 成本归集的 REST API，是「项目管理 / 成本管理」业务域的核心 Controller 之一。
 * 对标大厂 PMIS / 财务共享中心 / 费控系统中的「项目费用 / 报销单 / 成本归集」管理界面。
 *
 * <p><b>费用类型：</b>
 * <ul>
 *   <li><b>人工成本</b>：项目成员的工时投入，按工时 × 人天费率自动归集</li>
 *   <li><b>差旅费用</b>：项目成员出差产生的交通 / 住宿 / 餐饮 / 杂费</li>
 *   <li><b>采购成本</b>：项目采购的软硬件 / 服务费用（与 {@code CostPurchase} 关联）</li>
 *   <li><b>外包成本</b>：外包人员 / 外包服务的费用支出</li>
 *   <li><b>其他费用</b>：会议费、办公费、咨询费等非典型支出</li>
 * </ul>
 *
 * <p><b>业务流程：</b>
 * <pre>
 *  报销申请 → 部门审批 → 财务审核 → 凭证生成 → 成本归集 → 预算扣减
 *       ↑           ↑           ↑          ↑          ↑          ↑
 *   ProjectExpense  ProjectExpense ProjectExpense ProjectExpense  ProjectExpense  ProjectBudget
 *     .save()       .approve()    .audit()    .post()      .allocate()  .deduct()
 * </pre>
 *
 * <p><b>关键约束：</b>
 * <ul>
 *   <li>费用报销 <b>必须</b> 关联到具体项目（{@code projectId}），不允许「无项目费用」</li>
 *   <li>单笔费用金额 <b>不得</b> 超过项目剩余预算（由 {@code ProjectBudgetService} 校验）</li>
 *   <li>已审批 / 已入账的费用 <b>严禁</b> 物理删除，必须走「红冲」流程</li>
 *   <li>涉及发票的费用必须上传发票影像，参见 {@link ProjectFileController}</li>
 * </ul>
 *
 * <p><b>权限控制：</b>
 * <ul>
 *   <li>查询：项目成员 / 部门负责人 / 财务 / PMO 可见</li>
 *   <li>创建：项目成员可创建本人名下的费用</li>
 *   <li>审批：部门负责人 / 财务 拥有审批权限</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v1/project/project/expense")
@RequiredArgsConstructor
public class ProjectExpenseController {

    private final ProjectExpenseService service;

    /**
     * 按 ID 查询费用详情
     *
     * <p>返回费用实体 + 关联的项目名称、报销人姓名、审批人姓名等富化字段。
     * 字典字段（费用类型、支付方式等）通过 {@code NameAssembler} 翻译为可读文本。
     *
     * @param id 费用主键 ID
     * @return 费用视图对象（含富化的外键名称）
     */
    @GetMapping("/{id}")
    public BaseResponse<ProjectExpenseVO> getById(@PathVariable String id) { return BaseResponse.success(ProjectConverter.INSTANT.entityToVO(service.getById(id))); }

    /**
     * 分页查询费用列表
     *
     * <p>支持按项目、报销人、费用类型、审批状态、日期范围等条件筛选（由 Query 扩展）。
     * 当前骨架实现为最简分页，复杂条件由后续 {@code ProjectExpensePageQuery} 扩展。
     *
     * @param p 当前页码（默认 1）
     * @param s 每页条数（默认 10）
     * @return 分页费用视图对象
     */
    @GetMapping("/page")
    public PageResponse<ProjectExpenseVO> page(@RequestParam(defaultValue="1") int p, @RequestParam(defaultValue="10") int s) {
        IPage<ProjectExpense> r = service.page(p, s);
        return PageResponse.success(ProjectConverter.INSTANT.projectExpenseListToVO(r.getRecords()), r.getTotal(), (int)r.getCurrent(), (int)r.getSize());
    }

    /**
     * 创建费用报销
     *
     * <p>保存后触发 {@code ExpenseCreatedEvent} 领域事件，联动：
     * <ol>
     *   <li>预算预占（{@code ProjectBudgetService.occupy}）</li>
     *   <li>成本归集（{@code CostAllocationService.allocate}）</li>
     *   <li>审批工作流启动（{@code WorkflowServiceClient.startExpenseApproval}）</li>
     * </ol>
     *
     * @param dto 费用创建入参（包含费用类型、金额、项目 ID、发票影像等）
     * @return 是否创建成功
     */
    @Idempotent(key = "ydsz:project:ProjectExpenseController:save:lock", ttlSeconds = 5)
    @PostMapping
    @Audit(action=AuditAction.CREATE, module="PROJECT", content="Create ProjectExpense")
    public BaseResponse<Boolean> save(@RequestBody ProjectExpensePostDTO dto) { return BaseResponse.success(service.save(ProjectConverter.INSTANT.postDtoToEntity(dto))); }

    /**
     * 更新费用
     *
     * <p>仅允许更新「待审批 / 已驳回」状态的费用；已审批的费用必须通过红冲流程。
     * 更新成功后 <b>必须</b> 同步刷新预算占用和成本归集快照。
     *
     * @param dto 费用更新入参
     * @return 是否更新成功
     */
    @Idempotent(key = "ydsz:project:ProjectExpenseController:update:lock", ttlSeconds = 5)
    @PutMapping
    @Audit(action=AuditAction.UPDATE, module="PROJECT", content="Update ProjectExpense")
    public BaseResponse<Boolean> update(@RequestBody ProjectExpensePutDTO dto) { return BaseResponse.success(service.updateById(ProjectConverter.INSTANT.putDtoToEntity(dto))); }

    /**
     * 按 ID 删除费用
     *
     * <p>采用<b>逻辑删除</b>；已审批 / 已入账的费用 <b>严禁</b> 删除（由 Service 层校验）。
     * 删除后自动释放预算占用（{@code ProjectBudgetService.release}）。
     *
     * @param id 费用主键 ID
     * @return 是否删除成功
     */
    @Idempotent(key = "ydsz:project:ProjectExpenseController:remove:lock", ttlSeconds = 5)
    @DeleteMapping("/{id}")
    @Audit(action=AuditAction.DELETE, module="PROJECT", content="Delete ProjectExpense")
    public BaseResponse<Boolean> remove(@PathVariable String id) { return BaseResponse.success(service.removeById(id)); }
}
