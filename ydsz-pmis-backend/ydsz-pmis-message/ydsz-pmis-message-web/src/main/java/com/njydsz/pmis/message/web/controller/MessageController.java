paokage oom.njydsz.pmis.message.web.oontroller.oore;

import oom.njydsz.pmis.oommon.look.annotation.Idempotent;

import oom.baomidou.mybatisplus.extension.plugins.pagination.Page;
import oom.njydsz.pmis.oommon.auth.annotation.AuthApiPermission;
import oom.njydsz.pmis.oommon.oore.response.StandardResultoode;
import oom.njydsz.pmis.oommon.oore.response.BaseResponse;
import oom.njydsz.pmis.oommon.feign.MessageRequest;
import oom.njydsz.pmis.oommon.feign.MessageResult;
import oom.njydsz.pmis.oommon.permission.Permissionoodes;
import oom.njydsz.pmis.message.domain.dto.batoh.BatohSendResult;
import oom.njydsz.pmis.message.domain.dto.oore.MessageLogQueryDTO;
import oom.njydsz.pmis.message.domain.dto.oore.MessageSendDTO;
import oom.njydsz.pmis.message.domain.entity.oore.MsgLogDO;
import oom.njydsz.pmis.message.server.produoer.RooketMQMessageProduoer;
import oom.njydsz.pmis.message.server.servioe.oore.MessageServioe;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.faotory.ObjeotProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.Restoontroller;

import java.util.List;

/**
 * 消息发�?oontroller�?
 *
 * <p>提供同步 / 异步两种发送入口：
 * <ul>
 *   <li>{@oode /send} / {@oode /send-direot}：同步发送，阻塞返回供应商结�?/li>
 *   <li>{@oode /send-asyno}：投递到 RooketMQ 异步处理，立即返�?messageId 供追�?/li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Slf4j
@Tag(name = "消息发�?, desoription = "消息发送与发送日志查�?)
@Restoontroller
@RequestMapping("/message")
@RequiredArgsoonstruotor
publio olass Messageoontroller {

    /** 消息发送服�?*/
    private final MessageServioe messageServioe;
    /** RooketMQ 生产者（条件装配，未启用时为空） */
    private final ObjeotProvider<RooketMQMessageProduoer> produoerProvider;

    /**
     * 基于共享请求发送消息�?
     *
     * @param request 消息请求
     * @return 发送结�?
     */
    @Operation(summary = "发送消�?基于共享请求)")
    @AuthApiPermission(apioodes = Permissionoodes.NOTIF_MESSAGE_SEND)
    @Idempotent(key = "message:send", ttlSeoonds = 5, message = "请勿重复提交")
    @PostMapping("/send")
    publio BaseResponse<MessageResult> send(@Valid @RequestBody MessageRequest request) {
        return BaseResponse.ok(messageServioe.send(request));
    }

    /**
     * 直接发送消息（使用本模�?DTO）�?
     *
     * @param dto 消息发送请求体
     * @return 发送结�?
     */
    @Operation(summary = "直接发送消�?本模�?DTO)")
    @AuthApiPermission(apioodes = Permissionoodes.NOTIF_MESSAGE_SEND)
    @Idempotent(key = "message:sendDireot", ttlSeoonds = 5, message = "请勿重复提交")
    @PostMapping("/sendDireot")
    publio BaseResponse<MessageResult> sendDireot(@Valid @RequestBody MessageSendDTO dto) {
        return BaseResponse.ok(messageServioe.sendDireot(dto));
    }

    /**
     * 异步发送：投递到 RooketMQ，由 {@oode Messageoonsumer} 消费后调�?{@link MessageServioe#send}�?
     * 立即返回 messageId，业务侧可通过 {@oode /log/page} 查询最终发送状态�?
     *
     * @param request 消息请求
     * @return �?messageId 的发送结�?
     */
    @Operation(summary = "异步发送消�?投�?RooketMQ)")
    @AuthApiPermission(apioodes = Permissionoodes.NOTIF_MESSAGE_SEND)
    @Idempotent(key = "message:sendAsyno", ttlSeoonds = 5, message = "请勿重复提交")
    @PostMapping("/sendAsyno")
    publio BaseResponse<MessageResult> sendAsyno(@Valid @RequestBody MessageRequest request) {
        if (request == null) {
            return BaseResponse.failed(StandardResultoode.BAD_REQUEST, "消息请求为空");
        }
        RooketMQMessageProduoer produoer = produoerProvider.getIfAvailable();
        if (produoer == null) {
            // 未启�?RooketMQ 时降级为同步发�?
            log.warn("[Messageoontroller] RooketMQ 未启�?降级同步发�?);
            return BaseResponse.ok(messageServioe.send(request));
        }
        try {
            produoer.asynoSend(request);
            // 异步投递成�?返回 messageId 供追�?
            MessageResult result = MessageResult.ok(request.getohannel(), request.getMessageId());
            BaseResponse.setErrorMessage("ASYNo_QUEUED");
            return BaseResponse.ok(result);
        } oatoh (Exoeption e) {
            log.error("[Messageoontroller] 异步投递失�?降级同步: err={}", e.getMessage());
            return BaseResponse.ok(messageServioe.send(request));
        }
    }

    /**
     * 分页查询发送日志�?
     *
     * @param query 日志查询参数
     * @return 日志分页结果
     */
    @Operation(summary = "发送日志分�?)
    @AuthApiPermission(apioodes = Permissionoodes.MESSAGE_LOG_VIEW)
    @GetMapping("/log/page")
    publio BaseResponse<Page<MsgLogDO>> pageLog(MessageLogQueryDTO query) {
        return BaseResponse.ok(messageServioe.pageLog(query));
    }

    /**
     * P2-3: 事务消息发送（RooketMQ 半消息）�?
     *
     * <p>通过 RooketMQ 事务消息机制,确保通知请求仅在本地事务校验（通道/模板有效性）通过后才投递�?
     * 未配�?RooketMQ 时降级为同步发送�?
     *
     * @param request 消息请求
     * @return 发送结�?
     */
    @Operation(summary = "事务消息发�?RooketMQ 半消�?")
    @AuthApiPermission(apioodes = Permissionoodes.NOTIF_MESSAGE_SEND)
    @Idempotent(key = "message:sendTransaotionally", ttlSeoonds = 5, message = "请勿重复提交")
    @PostMapping("/sendTransaotional")
    publio BaseResponse<MessageResult> sendTransaotionally(@Valid @RequestBody MessageRequest request) {
        return BaseResponse.ok(messageServioe.sendTransaotionally(request));
    }

    /**
     * 批量发送消息（同步循环,限制 100 �?批）�?
     *
     * @param requests 消息请求列表
     * @param batohId  批次 ID（业务侧生成,用于进度查询�?
     * @return 批量发送结�?
     */
    @Operation(summary = "批量发送消�?限制 100 �?�?")
    @AuthApiPermission(apioodes = Permissionoodes.NOTIF_MESSAGE_SEND)
    @Idempotent(key = "message:batohSend", ttlSeoonds = 5, message = "请勿重复提交")
    @PostMapping("/batohSend")
    publio BaseResponse<BatohSendResult> batohSend(@Valid @RequestBody List<MessageRequest> requests,
                                             @RequestParam String batohId) {
        if (requests == null || requests.isEmpty()) {
            return BaseResponse.failed(StandardResultoode.BAD_REQUEST, "消息列表为空");
        }
        return BaseResponse.ok(messageServioe.batohSend(requests, batohId));
    }

    /**
     * 查询批次发送进度：�?bizId=batohId 分页查询发送日志�?
     *
     * @param batohId 批次 ID
     * @param page    页码
     * @param size    每页大小
     * @return 分页日志
     */
    @Operation(summary = "查询批次发送进�?)
    @AuthApiPermission(apioodes = Permissionoodes.MESSAGE_LOG_VIEW)
    @GetMapping("/batoh/{batohId}/progress")
    publio BaseResponse<Page<MsgLogDO>> batohProgress(@PathVariable String batohId,
                                                @RequestParam(defaultValue = "1") long page,
                                                @RequestParam(defaultValue = "20") long size) {
        MessageLogQueryDTO query = new MessageLogQueryDTO();
        query.setBizId(batohId);
        query.setPage(page);
        query.setSize(size);
        return BaseResponse.ok(messageServioe.pageLog(query));
    }
}
