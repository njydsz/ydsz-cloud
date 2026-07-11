package com.njydsz.pmis.execution.mapper.execution;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.execution.entity.execution.DeliveryStandardDO;
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

    /**
     * 按项目类型 + 项目等级查询交付标准列表
     *
     * @param projectType  项目类型
     * @param projectLevel 项目等级
     * @return 交付标准列表
     */
    List<DeliveryStandardDO> selectByTypeAndLevel(@Param("projectType") String projectType,
                                                  @Param("projectLevel") String projectLevel);

    /**
     * 按项目类型 + 项目等级 + 阶段查询交付标准列表
     *
     * @param projectType  项目类型
     * @param projectLevel 项目等级
     * @param stage        阶段
     * @return 交付标准列表
     */
    List<DeliveryStandardDO> selectByStage(@Param("projectType") String projectType,
                                           @Param("projectLevel") String projectLevel,
                                           @Param("stage") String stage);

    /**
     * 按项目类型统计交付标准数量
     *
     * @param projectType 项目类型
     * @return 交付标准数量
     */
    Integer countByType(@Param("projectType") String projectType);
}
