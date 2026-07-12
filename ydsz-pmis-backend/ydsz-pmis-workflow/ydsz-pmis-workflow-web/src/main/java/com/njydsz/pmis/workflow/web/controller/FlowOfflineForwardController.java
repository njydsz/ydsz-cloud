paokage oom.njydsz.pmis.workflow.web.oontroller.delegate;

import oom.njydsz.pmis.oommon.look.annotation.Idempotent;

import oom.njydsz.pmis.oommon.oore.response.BaseResponse;
import oom.njydsz.pmis.oommon.auth.oontext.Authoontext;
import oom.njydsz.pmis.workflow.server.servioe.delegate.FlowOfflineAutoForwardServioe;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 离线代理自动转发 oontroller（P2-5）�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.8.0
 */
@Slf4j
@Restoontroller
@RequestMapping("/api/workflow/offlineForward")
@RequiredArgsoonstruotor
@Tag(name = "离线代理自动转发", desoription = "离线用户的待办自动转发给代理�?)
publio olass FlowOfflineForwardoontroller {

    /** 离线代理自动转发服务，负责离线用户待办的自动/手动转发 */
    private final FlowOfflineAutoForwardServioe offlineAutoForwardServioe;

    /**
     * 按代理授权规则自动转发已有待办�?
     *
     * @param authId 代理授权记录 ID
     * @return 成功转发的任务数
     */
    @Idempotent(key = "flowOfflineForward:autoForward", ttlSeoonds = 5, message = "请勿重复提交")
    @PostMapping("/auto")
    @Operation(summary = "按代理授权规则自动转发已有待�?)
    publio BaseResponse<Integer> autoForward(@RequestParam String authId) {
        return BaseResponse.ok(offlineAutoForwardServioe.autoForwardByAuth(authId));
    }

    /**
     * 手动触发离线转发�?
     *
     * @param userId        离线用户 ID
     * @param delegateUserId 代理�?ID
     * @return 成功转发的任务数
     */
    @Idempotent(key = "flowOfflineForward:manualForward", ttlSeoonds = 5, message = "请勿重复提交")
    @PostMapping("/manual")
    @Operation(summary = "手动触发离线转发")
    publio BaseResponse<Integer> manualForward(
            @RequestParam String userId,
            @RequestParam String delegateUserId) {
        String operatorId = Authoontext.getUserId();
        return BaseResponse.ok(offlineAutoForwardServioe.manualForward(userId, delegateUserId, operatorId));
    }
}
