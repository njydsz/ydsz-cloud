package com.njydsz.pmis.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.workflow.entity.FlowCcDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 流程抄送 Mapper
 *
 * <p>P0-3: 抄送中心（对标钉钉/飞书的"抄送我的"独立 Tab）。
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
@Mapper
public interface FlowCcMapper extends BaseMapper<FlowCcDO> {

    /**
     * 查"抄送我的"（分页）
     *
     * @param tenantId   租户 ID
     * @param ccUserId   抄送接收人 ID
     * @param readStatus 已读状态过滤（可空）
     * @param flowCode   流程编码过滤（可空）
     * @param offset     分页偏移
     * @param limit      每页大小
     */
    List<FlowCcDO> selectCcByUserPage(@Param("tenantId") Long tenantId,
                                     @Param("ccUserId") Long ccUserId,
                                     @Param("readStatus") String readStatus,
                                     @Param("flowCode") String flowCode,
                                     @Param("offset") int offset,
                                     @Param("limit") int limit);

    /**
     * 统计"抄送我的"总数
     */
    long countCcByUser(@Param("tenantId") Long tenantId,
                       @Param("ccUserId") Long ccUserId,
                       @Param("readStatus") String readStatus,
                       @Param("flowCode") String flowCode);

    /**
     * 统计"抄送我的"未读数
     */
    long countCcUnreadByUser(@Param("tenantId") Long tenantId,
                             @Param("ccUserId") Long ccUserId);

    /**
     * P2-3: 统计全局未读抄送数（Prometheus Gauge 监控指标）
     *
     * <p>无 tenant/ccUser 过滤，统计 pmis_flow_cc 表所有未读记录数。
     */
    long countUnread();

    /**
     * 标记抄送为已读
     */
    int markRead(@Param("id") Long id,
                 @Param("ccUserId") Long ccUserId,
                 @Param("readAt") LocalDateTime readAt);

    /**
     * 全部标记为已读
     */
    int markAllRead(@Param("tenantId") Long tenantId,
                    @Param("ccUserId") Long ccUserId,
                    @Param("readAt") LocalDateTime readAt);

    /**
     * 查实例的抄送列表
     */
    List<FlowCcDO> selectByInstanceId(@Param("tenantId") Long tenantId,
                                      @Param("instanceId") Long instanceId);
}
