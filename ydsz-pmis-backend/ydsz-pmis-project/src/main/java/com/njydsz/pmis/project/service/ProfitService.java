package com.njydsz.pmis.project.service;

import com.njydsz.pmis.project.dto.ProfitSnapshotDTO;
import com.njydsz.pmis.project.entity.ProfitSnapshotDO;

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
    String generateSnapshot(ProfitSnapshotDTO dto);

    /**
     * 查询项目某月快照
     */
    ProfitSnapshotDO getByInitiationAndPeriod(String initiationId, String period);

    /**
     * 项目所有快照
     */
    List<ProfitSnapshotDO> listByInitiation(String initiationId);

    /**
     * 趋势分析
     */
    List<Map<String, Object>> trendByPeriod(String initiationId);

    /**
     * 项目健康度评分
     */
    int healthScore(String initiationId, String period);
}
