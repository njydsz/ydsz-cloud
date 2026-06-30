package com.njydsz.pmis.message.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.message.channel.MessageRequest;
import com.njydsz.pmis.message.channel.MessageResult;
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
     */
    MessageResult send(MessageRequest request);

    /**
     * 直接发送内容（不走模板）
     */
    MessageResult sendDirect(MessageRequest request);

    /**
     * 分页查询发送日志
     */
    Page<MessageLogDO> pageLog(int page, int size, String channel, String bizType, String status);

    /**
     * 加载模板（带租户隔离）
     */
    MessageTemplateDO loadTemplate(String templateCode, String channel, Long tenantId);
}
