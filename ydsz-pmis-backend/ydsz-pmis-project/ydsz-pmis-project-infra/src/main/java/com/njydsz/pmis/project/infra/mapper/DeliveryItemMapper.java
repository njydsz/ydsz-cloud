package com.njydsz.pmis.project.infra.mapper;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.project.domain.entity.DeliveryItemDO;

/**
 * 交付项 Mapper
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Mapper
public interface DeliveryItemMapper extends BaseMapper<DeliveryItemDO> {

    /**
     * 按编码查询交付项
     *
     * @param code 交付项编码
     * @return 交付项对象，未找到返回 null
     */
    DeliveryItemDO selectByCode(@Param("code") String code);

    /**
     * 更新交付项状态
     *
     * @param id     交付项 ID
     * @param status 目标状态
     * @return 受影响行数
     */
    int updateStatus(@Param("id") String id, @Param("status") String status);

    /**
     * 更新完成标记
     *
     * @param id        交付项 ID
     * @param completed 是否完成（0/1）
     * @return 受影响行数
     */
    int updateTrCompleted(@Param("id") String id, @Param("completed") Integer completed);

    /**
     * 按立项 ID 查询交付项列表
     *
     * @param initiationId 立项 ID
     * @return 交付项列表
     */
    List<DeliveryItemDO> selectByInitiation(@Param("initiationId") String initiationId);

    /**
     * 按立项 + 阶段查询交付项列表
     *
     * @param initiationId 立项 ID
     * @param stage        阶段
     * @return 交付项列表
     */
    List<DeliveryItemDO> selectByStage(@Param("initiationId") String initiationId,
                                       @Param("stage") String stage);

    /**
     * 按状态聚合交付项计数
     *
     * @param initiationId 立项 ID
     * @return 状态聚合列表
     */
    List<Map<String, Object>> aggregateByStatus(@Param("initiationId") String initiationId);

    /**
     * 统计某阶段已验收的交付项数量
     *
     * @param initiationId 立项 ID
     * @param stage        阶段
     * @return 已验收数量
     */
    long countAcceptedByStage(@Param("initiationId") String initiationId,
                              @Param("stage") String stage);

    /**
     * 统计某阶段必选交付项数量
     *
     * @param initiationId 立项 ID
     * @param stage        阶段
     * @return 必选交付项数量
     */
    long countRequiredByStage(@Param("initiationId") String initiationId,
                              @Param("stage") String stage);
}
