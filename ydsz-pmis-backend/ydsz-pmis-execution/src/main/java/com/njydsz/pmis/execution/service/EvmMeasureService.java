package com.njydsz.pmis.execution.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.execution.dto.EvmMeasureCreateDTO;
import com.njydsz.pmis.execution.entity.EvmMeasureDO;

import java.util.List;
import java.util.Map;

/**
 * EVM 挣值测量服务
 */
public interface EvmMeasureService {

    /** 录入或更新 EVM 测量（按 initiation+wbs+period 唯一） */
    Long save(EvmMeasureCreateDTO dto);

    EvmMeasureDO getById(Long id);

    List<EvmMeasureDO> listByInitiation(Long initiationId);

    List<EvmMeasureDO> listByWbs(Long wbsTaskId);

    /** WBS 节点级偏差趋势 */
    List<Map<String, Object>> trend(Long initiationId);

    /** 项目 EVM 健康汇总（最新一期） */
    Map<String, Object> dashboard(Long initiationId);

    Page<EvmMeasureDO> page(int page, int size, Long initiationId, String alertLevel);

    void delete(Long id);

    /**
     * 项目变更触发的 EVM 基线重算
     *
     * <p>由 ProjectChangeExecutedEvent 监听器调用, 根据最新 BAC/工期/范围,
     * 标记该项目 EVM 待重算并刷新基线版本号, 后续新录入的测量自动使用新基线.
     *
     * @param initiationId 项目立项 ID
     * @param reason       重算原因 (如 "PROJECT_CHANGE: changeCode")
     * @return 重算结果 (baselineVersion / affectedMeasures)
     */
    Map<String, Object> recalculateBaseline(Long initiationId, String reason);

    /**
     * 查询项目当前 EVM 基线版本号, 不存在返回 0
     */
    int currentBaselineVersion(Long initiationId);
}
