package com.njydsz.pmis.project.service.initiation;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.project.dto.execution.BudgetItemDTO;
import com.njydsz.pmis.project.dto.initiation.GateReviewDTO;
import com.njydsz.pmis.project.dto.initiation.InitiationCreateDTO;
import com.njydsz.pmis.project.dto.initiation.InitiationStageDTO;
import com.njydsz.pmis.project.entity.execution.BudgetItemDO;
import com.njydsz.pmis.project.entity.initiation.GateReviewDO;
import com.njydsz.pmis.project.entity.initiation.InitiationDO;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 立项服务接口
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public interface InitiationService {

    /**
     * 提交立项申请。
     *
     * @param dto 立项申请参数
     * @return 立项 ID
     */
    String create(InitiationCreateDTO dto);

    /**
     * 立项阶段迁移（遵循 InitiationStage 状态机）。
     *
     * @param dto 阶段迁移参数
     */
    void changeStage(InitiationStageDTO dto);

    /**
     * 删除立项（逻辑删除）。
     *
     * @param id 立项 ID
     */
    void delete(String id);

    /**
     * 根据立项 ID 查询立项详情。
     *
     * @param id 立项 ID
     * @return 立项实体；不存在返回 null
     */
    InitiationDO getById(String id);

    /**
     * 分页查询立项列表。
     *
     * @param page         页码（从 1 开始）
     * @param size         每页大小
     * @param keyword      关键词（项目编号/名称模糊匹配），可空
     * @param stage        阶段码，可空
     * @param projectLevel 项目级别（A/B/C），可空
     * @param pmId         项目经理 ID，可空
     * @return 分页结果
     */
    Page<InitiationDO> page(int page, int size, String keyword, String stage, String projectLevel, String pmId);

    // ============= 预算 =============

    /**
     * 新增预算明细。
     *
     * @param dto 预算明细参数
     * @return 预算明细 ID
     */
    String addBudgetItem(BudgetItemDTO dto);

    /**
     * 删除预算明细。
     *
     * @param id 预算明细 ID
     */
    void deleteBudgetItem(String id);

    /**
     * 查询立项的所有预算明细。
     *
     * @param initiationId 立项 ID
     * @return 预算明细列表
     */
    List<BudgetItemDO> listBudget(String initiationId);

    /**
     * 按预算大类汇总金额。
     *
     * @param initiationId 立项 ID
     * @return 每个大类对应的金额汇总列表
     */
    List<Map<String, Object>> sumBudgetByCategory(String initiationId);

    /**
     * 重新汇总预算总额并写回 initiation。
     *
     * @param initiationId 立项 ID
     * @return 汇总后的预算总额
     */
    BigDecimal recomputeBudget(String initiationId);

    // ============= 门径 =============

    /**
     * 提交门径评审决策。
     *
     * @param dto 评审参数
     * @return 评审记录 ID
     */
    String reviewGate(GateReviewDTO dto);

    /**
     * 查询立项的所有门径评审记录。
     *
     * @param initiationId 立项 ID
     * @return 评审记录列表
     */
    List<GateReviewDO> listGateReviews(String initiationId);

    // ============= 统计 =============

    /**
     * 按阶段聚合计数。
     *
     * @param tenantId 租户 ID，可空
     * @return 每种阶段对应的数量列表
     */
    List<Map<String, Object>> aggregateByStage(String tenantId);

    // ============= 流程集成 =============

    /**
     * 启动立项审批流，并将流程实例 ID 写回 initiation
     *
     * @param id 立项 ID
     * @param initiatorId 发起人 ID
     * @return 流程实例 ID（启动失败时返回 null）
     */
    String startProcess(String id, String initiatorId);

    /**
     * 装配客户/PM/发起人名称（按需调用 Feign 客户端）。
     *
     * @param initiation 立项实体，为 null 时安全返回
     */
    void assembleNames(InitiationDO initiation);

    /**
     * 预算快照（供其他模块 Feign 调用）。
     *
     * @param id 立项 ID
     * @return {initiationId, projectCode, projectName, budgetAmount, estimatedAmount, stage}
     */
    Map<String, Object> budgetSnapshot(String id);

    // ============= 流程状态联动（供 workflow 模块 Feign 调用） =============

    /**
     * 标记立项为审批中（APPROVING）。
     *
     * @param id 立项 ID
     * @throws BizException 立项不存在时抛出
     */
    void markProcessing(String id);

    /**
     * 标记立项为已批准（APPROVED），并设置门径为 CD1。
     *
     * @param id 立项 ID
     * @throws BizException 立项不存在时抛出
     */
    void markApproved(String id);

    /**
     * 标记立项为已驳回（REJECTED）。
     *
     * @param id     立项 ID
     * @param reason 驳回原因（可空）
     * @throws BizException 立项不存在时抛出
     */
    void markRejected(String id, String reason);
}
