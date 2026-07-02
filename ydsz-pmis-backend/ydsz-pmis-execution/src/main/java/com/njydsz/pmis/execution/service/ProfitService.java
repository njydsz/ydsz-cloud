package com.njydsz.pmis.execution.service;

import com.njydsz.pmis.execution.dto.ProfitSnapshotDTO;
import com.njydsz.pmis.execution.entity.ProfitSnapshotDO;

import java.util.List;
import java.util.Map;

/**
 * 利润核算服务
 *
 * <p>提供项目月度利润快照生成、趋势分析及健康度评估能力。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public interface ProfitService {

    /**
     * 生成/更新项目月度利润快照
     */
    Long generateSnapshot(ProfitSnapshotDTO dto);

    /**
     * 查询项目某月快照
     */
    ProfitSnapshotDO getByInitiationAndPeriod(Long initiationId, String period);

    /**
     * 项目所有快照
     */
    List<ProfitSnapshotDO> listByInitiation(Long initiationId);

    /**
     * 趋势分析
     */
    List<Map<String, Object>> trendByPeriod(Long initiationId);

    /**
     * 项目健康度评分
     */
    int healthScore(Long initiationId, String period);
}
