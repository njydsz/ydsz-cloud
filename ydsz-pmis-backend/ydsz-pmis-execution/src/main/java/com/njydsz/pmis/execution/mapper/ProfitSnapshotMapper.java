package com.njydsz.pmis.execution.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.execution.entity.ProfitSnapshotDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface ProfitSnapshotMapper extends BaseMapper<ProfitSnapshotDO> {

    ProfitSnapshotDO selectByInitiationAndPeriod(@Param("initiationId") Long initiationId,
                                                 @Param("period") String period);

    List<ProfitSnapshotDO> selectByInitiation(@Param("initiationId") Long initiationId);

    List<Map<String, Object>> trendByPeriod(@Param("initiationId") Long initiationId);

    /** P4-3 跨项目汇总所有快照利润 */
    java.math.BigDecimal sumAll();
}
    /** P6 每日对账：跨项目汇总所有快照利润 */
    BigDecimal sumAll();
}
