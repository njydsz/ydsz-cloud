package com.njydsz.pmis.message.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.feign.MessageRequest;
import com.njydsz.pmis.common.feign.MessageResult;
import com.njydsz.pmis.message.dto.MessageLogQueryDTO;
import com.njydsz.pmis.message.dto.MessageSendDTO;
import com.njydsz.pmis.message.entity.MsgLogDO;

/**
 * 消息发送服务
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public interface MessageService {

    /**
     * 基于跨模块共享请求发送消息
     *
     * @param request 消息发送请求
     * @return 发送结果
     */
    MessageResult send(MessageRequest request);

    /**
     * 直接发送消息(走本模块 DTO)
     *
     * @param dto 发送参数
     * @return 发送结果
     */
    MessageResult sendDirect(MessageSendDTO dto);

    /**
     * 分页查询消息发送日志
     *
     * @param query 查询参数
     * @return 分页结果
     */
    Page<MsgLogDO> pageLog(MessageLogQueryDTO query);
}
