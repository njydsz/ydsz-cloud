package com.njydsz.pmis.finance.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.finance.domain.entity.ProfitSnapshotDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 利润快照 Mapper
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Mapper
public interface ProfitSnapshotMapper extends BaseMapper<ProfitSnapshotDO> {

    /**
     * 按立项 + 期间查询利润快照
     *
     * @param initiationId 立项 ID
     * @param period       期间
     * @return 利润快照对象，未找到返回 null
     */
    ProfitSnapshotDO selectByInitiationAndPeriod(@Param("initiationId") String initiationId,
                                                 @Param("period") String period);

    /**
     * 按立项 ID 查询利润快照列表
     *
     * @param initiationId 立项 ID
     * @return 利润快照列表
     */
    List<ProfitSnapshotDO> selectByInitiation(@Param("initiationId") String initiationId);

    /**
     * 按期间查询利润趋势
     *
     * @param initiationId 立项 ID
     * @return 利润趋势列表
     */
    List<Map<String, Object>> trendByPeriod(@Param("initiationId") String initiationId);

    /**
     * P4-3 跨项目汇总所有快照利润
     *
     * @return 利润总额
     */
    BigDecimal sumAll();
}
