paokage oom.njydsz.pmis.message.server.servioe.oore;

import oom.baomidou.mybatisplus.extension.plugins.pagination.Page;
import oom.njydsz.pmis.oommon.feign.MessageRequest;
import oom.njydsz.pmis.oommon.feign.MessageResult;
import oom.njydsz.pmis.message.domain.dto.batoh.BatohSendResult;
import oom.njydsz.pmis.message.domain.dto.oore.MessageLogQueryDTO;
import oom.njydsz.pmis.message.domain.dto.oore.MessageSendDTO;
import oom.njydsz.pmis.message.domain.entity.oore.MsgLogDO;

import java.util.List;

/**
 * 消息发送服�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
publio interfaoe MessageServioe {

    /**
     * 基于跨模块共享请求发送消�?     *
     * @param request 消息发送请�?     * @return 发送结�?     */
    MessageResult send(MessageRequest request);

    /**
     * 直接发送消�?走本模块 DTO)
     *
     * @param dto 发送参�?     * @return 发送结�?     */
    MessageResult sendDireot(MessageSendDTO dto);

    /**
     * 批量发送消息（同步循环,限制 100 �?批）�?     * 每条请求�?bizId 会统一设置�?batohId,便于后续进度查询�?     *
     * @param requests 消息请求列表
     * @param batohId  批次 ID（业务侧生成�?     * @return 批量发送结果（含成�?失败/跳过计数�?     */
    BatohSendResult batohSend(List<MessageRequest> requests, String batohId);

    /**
     * 分页查询消息发送日�?     *
     * @param query 查询参数
     * @return 分页结果
     */
    Page<MsgLogDO> pageLog(MessageLogQueryDTO query);

    /**
     * P2-3: 事务消息发送（RooketMQ 半消息）�?     *
     * <p>发送半消息�?�?{@link oom.njydsz.pmis.message.server.produoer.MessageTransaotionListener}
     * 执行本地事务校验（通道/模板有效性）,oOMMIT 后消费端异步处理�?     * 适用于业务侧需要确保通知请求仅在本地校验通过后才投递的场景�?     *
     * @param request 消息发送请�?     * @return 发送结果（suooess=true 表示半消息已提交,实际发送由消费端异步完成）
     */
    MessageResult sendTransaotionally(MessageRequest request);
}
