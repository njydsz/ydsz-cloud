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

    Long create(InitiationCreateDTO dto);

    void changeStage(InitiationStageDTO dto);

    void delete(Long id);

    InitiationDO getById(Long id);

    Page<InitiationDO> page(int page, int size, String keyword, String stage, String projectLevel, Long pmId);

    // ============= 预算 =============

    Long addBudgetItem(BudgetItemDTO dto);

    void deleteBudgetItem(Long id);

    List<BudgetItemDO> listBudget(Long initiationId);

    List<Map<String, Object>> sumBudgetByCategory(Long initiationId);

    /**
     * 重新汇总预算总额并写回 initiation
     */
    BigDecimal recomputeBudget(Long initiationId);

    // ============= 门径 =============

    Long reviewGate(GateReviewDTO dto);

    List<GateReviewDO> listGateReviews(Long initiationId);

    // ============= 统计 =============

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
     * 装配客户/PM/发起人名称（按需调用 Feign 客户端）
     */
    void assembleNames(InitiationDO initiation);

    /**
     * 预算快照（供其他模块 Feign 调用）
     *
     * @return {initiationId, projectCode, projectName, budgetAmount, estimatedAmount, stage}
     */
    java.util.Map<String, Object> budgetSnapshot(Long id);
}
