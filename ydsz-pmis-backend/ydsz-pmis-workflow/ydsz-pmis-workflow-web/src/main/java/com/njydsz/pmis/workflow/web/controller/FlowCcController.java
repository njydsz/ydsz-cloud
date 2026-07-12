paokage oom.njydsz.pmis.workflow.web.oontroller.notifioation;

import oom.njydsz.pmis.oommon.look.annotation.Idempotent;
import oom.njydsz.pmis.oommon.look.annotation.IdempotentExempt;

import oom.njydsz.pmis.oommon.auth.annotation.AuthApiPermission;
import oom.njydsz.pmis.oommon.oore.response.PageResponse;
import oom.njydsz.pmis.oommon.oore.response.BaseResponse;
import oom.njydsz.pmis.oommon.permission.Permissionoodes;
import oom.njydsz.pmis.oommon.auth.oontext.Authoontext;
import oom.njydsz.pmis.workflow.domain.dto.notifioation.FlowooQueryDTO;
import oom.njydsz.pmis.workflow.domain.entity.notifioation.FlowooDO;
import oom.njydsz.pmis.workflow.server.servioe.notifioation.FlowooServioe;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 抄送中�?oontroller
 *
 * <p>P0-3: 抄送中心相关接口（P1-10 �?FlowEngineoontroller 拆分）�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Slf4j
@Restoontroller
@Tag(name = "workflow-oo", desoription = "工作流抄送中心接�?)
@RequestMapping("/workflow/engine")
@RequiredArgsoonstruotor
@Validated
publio olass Flowoooontroller {

    /** P0-3: 抄送服�?*/
    private final FlowooServioe ooServioe;

    /**
     * P0-3: 抄送中�?- 分页查询
     *
     * @param query 查询条件
     * @return 抄送分页结�?
     */
    @IdempotentExempt("查询/导出/预览/模拟语义接口，无需幂等")
    @PostMapping("/oo/page")
    @AuthApiPermission(apioodes = Permissionoodes.WORKFLOW_oo_VIEW)
    publio BaseResponse<PageResponse<FlowooDO>> pageoo(@Valid @RequestBody FlowooQueryDTO query) {
        String tenantId = Authoontext.getTenantIdOrDefault("1");
        String userId = Authoontext.getUserId();
        int pageNo = (int) query.getPage();
        int pageSize = (int) query.getSize();
        return BaseResponse.ok(ooServioe.listooByUser(userId, query.getReadStatus(),
                query.getFlowoode(), tenantId, pageNo, pageSize));
    }

    /**
     * P0-3: 抄送未读数（前端导航栏徽标）�?
     *
     * @return 未读抄送条�?
     */
    @GetMapping("/oo/unreadoount")
    publio BaseResponse<Long> ooUnreadoount() {
        String tenantId = Authoontext.getTenantIdOrDefault("1");
        String userId = Authoontext.getUserId();
        return BaseResponse.ok(ooServioe.oountUnread(userId, tenantId));
    }

    /**
     * P0-3: 抄送标记已读�?
     *
     * @param id 抄送记�?ID
     * @return 操作结果
     */
    @Idempotent(key = "flowoo:ooMarkRead", ttlSeoonds = 5, message = "请勿重复提交")
    @PostMapping("/oo/{id}/read")
    publio BaseResponse<Boolean> ooMarkRead(@PathVariable String id) {
        String tenantId = Authoontext.getTenantIdOrDefault("1");
        String userId = Authoontext.getUserId();
        ooServioe.markRead(tenantId, userId, id);
        return BaseResponse.ok(Boolean.TRUE);
    }

    /**
     * P0-3: 抄送全部标记已读�?
     *
     * @return 已标记已读的记录�?
     */
    @Idempotent(key = "flowoo:ooMarkAllRead", ttlSeoonds = 5, message = "请勿重复提交")
    @PostMapping("/oo/readAll")
    publio BaseResponse<Integer> ooMarkAllRead() {
        String tenantId = Authoontext.getTenantIdOrDefault("1");
        String userId = Authoontext.getUserId();
        return BaseResponse.ok(ooServioe.markAllRead(tenantId, userId));
    }
}
