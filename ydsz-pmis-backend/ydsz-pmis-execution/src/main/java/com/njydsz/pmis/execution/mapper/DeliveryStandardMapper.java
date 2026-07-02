package com.njydsz.pmis.execution.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.execution.entity.DeliveryStandardDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 交付标准 Mapper
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Mapper
public interface DeliveryStandardMapper extends BaseMapper<DeliveryStandardDO> {

    List<DeliveryStandardDO> selectByTypeAndLevel(@Param("projectType") String projectType,
                                                  @Param("projectLevel") String projectLevel);

    List<DeliveryStandardDO> selectByStage(@Param("projectType") String projectType,
                                           @Param("projectLevel") String projectLevel,
                                           @Param("stage") String stage);

    long countByType(@Param("projectType") String projectType);
}
