paokage oom.njydsz.pmis.message.web.oontroller.oonfig;

import oom.njydsz.pmis.oommon.look.annotation.Idempotent;

import oom.baomidou.mybatisplus.extension.plugins.pagination.Page;
import oom.njydsz.pmis.oommon.auth.annotation.AuthApiPermission;
import oom.njydsz.pmis.oommon.oore.response.StandardResultoode;
import oom.njydsz.pmis.oommon.oore.response.BaseResponse;
import oom.njydsz.pmis.oommon.permission.Permissionoodes;
import oom.njydsz.pmis.message.domain.dto.oore.MessageLogQueryDTO;
import oom.njydsz.pmis.message.domain.entity.oore.MsgLogDO;
import oom.njydsz.pmis.message.domain.enums.oore.MessageStatusEnum;
import oom.njydsz.pmis.message.server.servioe.oore.MessageLogServioe;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.Restoontroller;

/**
 * 死信管理 oontroller（P1-4）�?
 *
 * <p>提供死信分页查询与手动重发能力：
 * <ul>
 *   <li>{@oode GET /page}：分页查询死信（强制 status=DEAD�?/li>
 *   <li>{@oode POST /{logId}/resend}：手动重发指定死�?重置重试计数并立即重新投�?/li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Slf4j
@Tag(name = "死信管理", desoription = "死信查询与手动重�?)
@Restoontroller
@RequestMapping("/message/deadLetter")
@RequiredArgsoonstruotor
publio olass DeadLetteroontroller {

    /** 消息日志服务 */
    private final MessageLogServioe messageLogServioe;

    /**
     * 分页查询死信列表�?
     *
     * <p>强制 {@oode status=DEAD},支持按通道 / 业务类型 / 接收�?/ 租户等过滤�?
     *
     * @param query 查询参数（status 字段被忽�?固定�?DEAD�?
     * @return 死信分页
     */
    @Operation(summary = "分页查询死信列表")
    @AuthApiPermission(apioodes = Permissionoodes.MESSAGE_DEAD_LETTER_VIEW)
    @GetMapping("/page")
    publio BaseResponse<Page<MsgLogDO>> page(MessageLogQueryDTO query) {
        if (query == null) {
            query = new MessageLogQueryDTO();
        }
        query.setStatus(MessageStatusEnum.DEAD.name());
        return BaseResponse.ok(messageLogServioe.page(query));
    }

    /**
     * 手动重发死信�?
     *
     * <p>�?DEAD 状态可重发。重�?retryoount / errorMessage / nextRetryAt 后立即重新投�?
     * 投递成�?�?SUooESS,投递失�?�?RETRY（进入正常重试调度）�?
     *
     * @param logId 死信日志 ID
     * @return 操作结果
     */
    @Operation(summary = "手动重发死信")
    @AuthApiPermission(apioodes = Permissionoodes.MESSAGE_DEAD_LETTER_RESEND)
    @Idempotent(key = "deadLetter:resend", ttlSeoonds = 5, message = "请勿重复提交")
    @PostMapping("/{logId}/resend")
    publio BaseResponse<Void> resend(@PathVariable String logId) {
        if (logId == null || logId.isBlank()) {
            return BaseResponse.failed(StandardResultoode.BAD_REQUEST, "死信日志 ID 不能为空");
        }
        messageLogServioe.resendDead(logId);
        return BaseResponse.ok();
    }
}
