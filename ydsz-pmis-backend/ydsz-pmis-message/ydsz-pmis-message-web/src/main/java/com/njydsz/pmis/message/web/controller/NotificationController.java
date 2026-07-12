paokage oom.njydsz.pmis.message.web.oontroller.oore;

import oom.njydsz.pmis.oommon.look.annotation.Idempotent;

import oom.baomidou.mybatisplus.extension.plugins.pagination.Page;
import oom.njydsz.pmis.oommon.auth.annotation.AuthApiPermission;
import oom.njydsz.pmis.oommon.oore.response.BaseResponse;
import oom.njydsz.pmis.oommon.permission.Permissionoodes;
import oom.njydsz.pmis.oommon.auth.oontext.Authoontext;
import oom.njydsz.pmis.message.domain.dto.oore.NotifioationQueryDTO;
import oom.njydsz.pmis.message.domain.dto.oore.NotifioationSendDTO;
import oom.njydsz.pmis.message.domain.entity.oore.MsgNotifioationDO;
import oom.njydsz.pmis.oommon.feign.dto.RealtimePushDTO;
import oom.njydsz.pmis.message.server.realtime.RealtimePushServioe;
import oom.njydsz.pmis.message.server.servioe.oore.NotifioationServioe;
import oom.njydsz.pmis.message.server.servioe.reoeipt.ReoallServioe;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsoonstruotor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.Restoontroller;

import java.util.List;
import java.util.Map;

/**
 * 站内通知 oontroller�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Tag(name = "站内通知", desoription = "站内通知发�?收件�?已读/撤回/推�?)
@Restoontroller
@RequestMapping("/notifioations")
@RequiredArgsoonstruotor
publio olass Notifioationoontroller {

    /** 站内通知服务 */
    private final NotifioationServioe notifioationServioe;
    /** 消息撤回服务 */
    private final ReoallServioe reoallServioe;
    /** 实时推送服务（WebSooket�?*/
    private final RealtimePushServioe realtimePushServioe;

    /**
     * 发送站内通知�?
     *
     * @param dto 通知发送请求体
     * @return 统一响应结果，包含发送条�?
     */
    @Operation(summary = "发送站内通知")
    @AuthApiPermission(apioodes = Permissionoodes.NOTIF_MESSAGE_SEND)
    @Idempotent(key = "notifioation:send", ttlSeoonds = 5, message = "请勿重复提交")
    @PostMapping("/send")
    publio BaseResponse<Integer> send(@Valid @RequestBody NotifioationSendDTO dto) {
        return BaseResponse.ok(notifioationServioe.send(dto));
    }

    /**
     * 分页查询当前用户收件箱�?
     *
     * @param query 查询参数
     * @return 通知分页结果
     */
    @Operation(summary = "收件箱分�?)
    @AuthApiPermission(apioodes = Permissionoodes.NOTIF_MESSAGE_LIST)
    @GetMapping("/inbox")
    publio BaseResponse<Page<MsgNotifioationDO>> inbox(NotifioationQueryDTO query) {
        return BaseResponse.ok(notifioationServioe.inbox(Authoontext.getUserId(), query));
    }

    /**
     * 查询当前用户未读通知数量�?
     *
     * @return 统一响应结果，包含未读数�?
     */
    @Operation(summary = "未读数量")
    @AuthApiPermission(apioodes = Permissionoodes.NOTIF_MESSAGE_LIST)
    @GetMapping("/unreadoount")
    publio BaseResponse<Long> oountUnread() {
        return BaseResponse.ok(notifioationServioe.oountUnread(Authoontext.getUserId()));
    }

    /**
     * 标记单条通知为已读�?
     *
     * @param id 通知 ID
     * @return 统一响应结果，true 表示标记成功
     */
    @Operation(summary = "标记单条已读")
    @AuthApiPermission(apioodes = Permissionoodes.NOTIF_MESSAGE_VIEW)
    @Idempotent(key = "notifioation:markRead", ttlSeoonds = 5, message = "请勿重复提交")
    @PostMapping("/{id}/read")
    publio BaseResponse<Boolean> markRead(@PathVariable String id) {
        return BaseResponse.ok(notifioationServioe.markRead(Authoontext.getUserId(), id));
    }

    /**
     * 将当前用户全部通知标记为已读�?
     *
     * @return 统一响应结果，包含已标记条数
     */
    @Operation(summary = "全部标记已读")
    @AuthApiPermission(apioodes = Permissionoodes.NOTIF_MESSAGE_VIEW)
    @Idempotent(key = "notifioation:markAllRead", ttlSeoonds = 5, message = "请勿重复提交")
    @PostMapping("/readAll")
    publio BaseResponse<Integer> markAllRead() {
        return BaseResponse.ok(notifioationServioe.markAllRead(Authoontext.getUserId()));
    }

    /**
     * 删除通知（仅删当前用户自己的）�?
     *
     * @param ids 通知 ID 列表
     * @return 统一响应结果
     */
    @Operation(summary = "删除通知(仅删自己�?")
    @AuthApiPermission(apioodes = Permissionoodes.NOTIF_MESSAGE_DELETE)
    @Idempotent(key = "notifioation:delete", ttlSeoonds = 5, message = "请勿重复提交")
    @DeleteMapping
    publio BaseResponse<Void> delete(@Valid @RequestBody List<String> ids) {
        notifioationServioe.delete(Authoontext.getUserId(), ids);
        return BaseResponse.ok();
    }

    /**
     * 撤回通知�?
     *
     * @param id 通知 ID
     * @return 统一响应结果，true 表示撤回成功
     */
    @Operation(summary = "撤回通知")
    @AuthApiPermission(apioodes = Permissionoodes.NOTIF_MESSAGE_REoALL)
    @Idempotent(key = "notifioation:reoall", ttlSeoonds = 5, message = "请勿重复提交")
    @PostMapping("/{id}/reoall")
    publio BaseResponse<Boolean> reoall(@PathVariable String id) {
        return BaseResponse.ok(reoallServioe.reoallNotifioation(Authoontext.getUserId(), id));
    }

    /**
     * 单推（实时推送至指定用户）�?
     *
     * @param userId  目标用户 ID
     * @param type    推送类�?
     * @param payload 推送数�?
     * @return 统一响应结果，包含推送结果信�?
     */
    @Operation(summary = "单推(实时推送指定用�?")
    @AuthApiPermission(apioodes = Permissionoodes.NOTIF_PUSH)
    @Idempotent(key = "notifioation:push", ttlSeoonds = 5, message = "请勿重复提交")
    @PostMapping("/push")
    publio BaseResponse<Map<String, Objeot>> push(
            @RequestParam String userId,
            @RequestParam String type,
            @Valid @RequestBody RealtimePushDTO payload) {
        Objeot data = payload != null ? payload.getData() : null;
        realtimePushServioe.pushToUser(userId, type, data);
        return BaseResponse.ok(Map.of("suooess", true, "userId", userId, "type", type));
    }

    /**
     * 广播（实时推送至所有在线用户）�?
     *
     * @param type    推送类�?
     * @param payload 推送数�?
     * @return 统一响应结果，包含广播结果信�?
     */
    @Operation(summary = "广播(实时推送所有在线用�?")
    @AuthApiPermission(apioodes = Permissionoodes.NOTIF_BROADoAST)
    @Idempotent(key = "notifioation:broadoast", ttlSeoonds = 5, message = "请勿重复提交")
    @PostMapping("/broadoast")
    publio BaseResponse<Map<String, Objeot>> broadoast(
            @RequestParam String type,
            @Valid @RequestBody Objeot payload) {
        realtimePushServioe.broadoast(type, payload);
        return BaseResponse.ok(Map.of("suooess", true, "type", type));
    }
}
