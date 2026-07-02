package com.njydsz.pmis.execution.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.execution.entity.ProfitSimulationDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 利润模拟 Mapper
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Mapper
public interface ProfitSimulationMapper extends BaseMapper<ProfitSimulationDO> {

    ProfitSimulationDO selectByCode(@Param("code") String code);

    List<ProfitSimulationDO> selectByInitiation(@Param("initiationId") Long initiationId);

    /** 同项目下最大版本号 */
    Integer maxVersion(@Param("initiationId") Long initiationId);
}
