package com.njydsz.pmis.workflow.service;

import com.njydsz.pmis.workflow.entity.FlowNotifyChannelDO;

import java.util.List;

/**
 * 通知通道配置服务
 *
 * <p>管理各通知通道（站内信/邮件/短信/Webhook/钉钉/企业微信）的配置，
 * 供管理员在后台增删改查，并供通知服务按通道类型查询配置。
 *
 * @author ydsz-pmis-team
 * @since 1.3.0
 */
public interface FlowNotifyChannelService {

    /**
     * 查询租户下所有通知通道配置
     *
     * @param tenantId 租户 ID
     * @return 通道配置列表
     */
    List<FlowNotifyChannelDO> listChannels(Long tenantId);

    /**
     * 查询租户下所有启用的通知通道配置
     *
     * @param tenantId 租户 ID
     * @return 启用的通道配置列表
     */
    List<FlowNotifyChannelDO> listEnabledChannels(Long tenantId);

    /**
     * 新增或更新通知通道配置
     *
     * @param dto 通道配置（id 为空时新增，非空时更新）
     * @return 保存后的通道配置（含 ID）
     */
    FlowNotifyChannelDO saveChannel(FlowNotifyChannelDO dto);

    /**
     * 启用/停用通知通道
     *
     * @param id      通道配置 ID
     * @param enabled 是否启用
     */
    void toggleChannel(Long id, Boolean enabled);

    /**
     * 删除通知通道配置（逻辑删除）
     *
     * @param id 通道配置 ID
     */
    void deleteChannel(Long id);

    /**
     * 按通道类型查询配置 JSON 字符串
     *
     * @param channelType 通道类型（IN_APP/EMAIL/SMS/WEBHOOK/DINGTALK/WECHAT）
     * @param tenantId    租户 ID
     * @return 配置 JSON 字符串，未配置时返回 null
     */
    String getConfig(String channelType, Long tenantId);
}
