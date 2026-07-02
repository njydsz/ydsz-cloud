package com.njydsz.pmis.project.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.project.dto.BudgetItemDTO;
import com.njydsz.pmis.project.dto.GateReviewDTO;
import com.njydsz.pmis.project.dto.InitiationCreateDTO;
import com.njydsz.pmis.project.dto.InitiationStageDTO;
import com.njydsz.pmis.project.entity.BudgetItemDO;
import com.njydsz.pmis.project.entity.GateReviewDO;
import com.njydsz.pmis.project.entity.InitiationDO;

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
    Long create(InitiationCreateDTO dto);

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
    void delete(Long id);

    /**
     * 根据立项 ID 查询立项详情。
     *
     * @param id 立项 ID
     * @return 立项实体；不存在返回 null
     */
    InitiationDO getById(Long id);

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
    Page<InitiationDO> page(int page, int size, String keyword, String stage, String projectLevel, Long pmId);

    // ============= 预算 =============

    /**
     * 新增预算明细。
     *
     * @param dto 预算明细参数
     * @return 预算明细 ID
     */
    Long addBudgetItem(BudgetItemDTO dto);

    /**
     * 删除预算明细。
     *
     * @param id 预算明细 ID
     */
    void deleteBudgetItem(Long id);

    /**
     * 查询立项的所有预算明细。
     *
     * @param initiationId 立项 ID
     * @return 预算明细列表
     */
    List<BudgetItemDO> listBudget(Long initiationId);

    /**
     * 按预算大类汇总金额。
     *
     * @param initiationId 立项 ID
     * @return 每个大类对应的金额汇总列表
     */
    List<Map<String, Object>> sumBudgetByCategory(Long initiationId);

    /**
     * 重新汇总预算总额并写回 initiation。
     *
     * @param initiationId 立项 ID
     * @return 汇总后的预算总额
     */
    BigDecimal recomputeBudget(Long initiationId);

    // ============= 门径 =============

    /**
     * 提交门径评审决策。
     *
     * @param dto 评审参数
     * @return 评审记录 ID
     */
    Long reviewGate(GateReviewDTO dto);

    /**
     * 查询立项的所有门径评审记录。
     *
     * @param initiationId 立项 ID
     * @return 评审记录列表
     */
    List<GateReviewDO> listGateReviews(Long initiationId);

    // ============= 统计 =============

    /**
     * 按阶段聚合计数。
     *
     * @param tenantId 租户 ID，可空
     * @return 每种阶段对应的数量列表
     */
    List<Map<String, Object>> aggregateByStage(Long tenantId);

    // ============= 流程集成 =============

    /**
     * 启动立项审批流，并将流程实例 ID 写回 initiation
     *
     * @param id 立项 ID
     * @param initiatorId 发起人 ID
     * @return 流程实例 ID（启动失败时返回 null）
     */
    String startProcess(Long id, Long initiatorId);

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
    java.util.Map<String, Object> budgetSnapshot(Long id);
}
