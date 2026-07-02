package com.njydsz.pmis.execution.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.execution.entity.DeliveryItemDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 交付项 Mapper
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Mapper
public interface DeliveryItemMapper extends BaseMapper<DeliveryItemDO> {

    DeliveryItemDO selectByCode(@Param("code") String code);

    int updateStatus(@Param("id") Long id, @Param("status") String status);

    int updateTrCompleted(@Param("id") Long id, @Param("completed") Integer completed);

    List<DeliveryItemDO> selectByInitiation(@Param("initiationId") Long initiationId);

    List<DeliveryItemDO> selectByStage(@Param("initiationId") Long initiationId,
                                       @Param("stage") String stage);

    List<Map<String, Object>> aggregateByStatus(@Param("initiationId") Long initiationId);

    long countAcceptedByStage(@Param("initiationId") Long initiationId,
                              @Param("stage") String stage);

    long countRequiredByStage(@Param("initiationId") Long initiationId,
                              @Param("stage") String stage);
}
