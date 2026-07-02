package com.njydsz.pmis.message.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.feign.MessageRequest;
import com.njydsz.pmis.common.feign.MessageResult;
import com.njydsz.pmis.message.entity.MessageLogDO;
import com.njydsz.pmis.message.entity.MessageTemplateDO;

/**
 * 消息服务接口
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public interface MessageService {

    /**
     * 发送消息（可走模板）
     *
     * @param request 消息请求（含通道、接收人、模板编码、参数等）
     * @return 发送结果（含供应商侧追踪 ID）
     * @throws com.njydsz.pmis.common.exception.BizException 请求参数非法、模板不存在/已停用、通道不支持时抛出
     */
    MessageResult send(MessageRequest request);

    /**
     * 直接发送内容（不走模板）
     *
     * @param request 消息请求（templateCode 会被忽略）
     * @return 发送结果
     */
    MessageResult sendDirect(MessageRequest request);

    /**
     * 分页查询发送日志
     *
     * @param page    页码（从 1 开始）
     * @param size    每页条数
     * @param channel 通道过滤（可空）
     * @param bizType 业务类型过滤（可空）
     * @param status  状态过滤（可空）
     * @return 日志分页结果
     */
    Page<MessageLogDO> pageLog(int page, int size, String channel, String bizType, String status);

    /**
     * 加载模板（带租户隔离）
     *
     * @param templateCode 模板编码
     * @param channel      通道（大写）
     * @param tenantId     租户 ID（为空时默认 1L）
     * @return 模板实体，不存在返回 null
     */
    MessageTemplateDO loadTemplate(String templateCode, String channel, Long tenantId);
}
