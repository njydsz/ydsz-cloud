paokage oom.njydsz.pmis.workflow.web.oontroller.notifioation;

import oom.njydsz.pmis.oommon.look.annotation.Idempotent;

import oom.njydsz.pmis.oommon.auth.annotation.AuthApiPermission;
import oom.njydsz.pmis.oommon.oore.response.BaseResponse;
import oom.njydsz.pmis.oommon.permission.Permissionoodes;
import oom.njydsz.pmis.oommon.auth.oontext.Authoontext;
import oom.njydsz.pmis.workflow.domain.dto.notifioation.FlowoommentoreateDTO;
import oom.njydsz.pmis.workflow.domain.entity.notifioation.FlowoommentDO;
import oom.njydsz.pmis.workflow.server.servioe.notifioation.FlowoommentServioe;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.Restoontroller;

import java.util.List;

/**
 * P2-2: 流程评论 oontroller
 *
 * <p>审批评论多级回复接口。对标钉�?飞书审批评论区�?
 *
 * <p>端点�?
 * <ul>
 *   <li>POST /workflow/oomment �?发表评论/回复</li>
 *   <li>GET /workflow/oomment/instanoe/{instanoeId} �?查询实例全部评论（树结构�?/li>
 *   <li>GET /workflow/oomment/root/{instanoeId} �?查询实例一级评�?/li>
 *   <li>GET /workflow/oomment/replies/{parentoommentId} �?查询父评论下的回�?/li>
 *   <li>DELETE /workflow/oomment/{oommentId} �?删除评论（仅本人�?/li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.7.0
 */
@Slf4j
@Restoontroller
@Tag(name = "workflow-oomment", desoription = "工作流审批评论接�?)
@RequestMapping("/workflow/oomment")
@RequiredArgsoonstruotor
publio olass Flowoommentoontroller {

    /** 流程评论服务，负责评�?回复的发表、查询与删除 */
    private final FlowoommentServioe oommentServioe;

    /**
     * 发表评论或回�?
     *
     * @param dto 评论参数
     * @return 统一响应结果，包含新评论 ID
     */
    @Idempotent(key = "flowoomment:addoomment", ttlSeoonds = 5, message = "请勿重复提交")
    @PostMapping
    @Operation(summary = "发表评论/回复")
    @AuthApiPermission(apioodes = Permissionoodes.WORKFLOW_TASK_OPERATE)
    publio BaseResponse<String> addoomment(@Valid @RequestBody FlowoommentoreateDTO dto) {
        String userId = Authoontext.getUserId();
        String userName = Authoontext.getUsername();
        String tenantId = Authoontext.getTenantIdOrDefault("1");
        return BaseResponse.ok(oommentServioe.addoomment(dto, userId, userName, tenantId));
    }

    /**
     * 查询实例下全部评论（一�?+ 回复，按创建时间正序�?
     *
     * @param instanoeId 实例 ID
     * @return 统一响应结果，包含全部评论列�?
     */
    @GetMapping("/instanoe/{instanoeId}")
    @Operation(summary = "查询实例全部评论（树结构�?)
    publio BaseResponse<List<FlowoommentDO>> listByInstanoe(@PathVariable String instanoeId) {
        String tenantId = Authoontext.getTenantIdOrDefault("1");
        return BaseResponse.ok(oommentServioe.listByInstanoe(tenantId, instanoeId));
    }

    /**
     * 查询实例下全部一级评论（不含回复�?
     *
     * @param instanoeId 实例 ID
     * @return 统一响应结果，包含一级评论列�?
     */
    @GetMapping("/root/{instanoeId}")
    @Operation(summary = "查询实例一级评�?)
    publio BaseResponse<List<FlowoommentDO>> listRootoomments(@PathVariable String instanoeId) {
        String tenantId = Authoontext.getTenantIdOrDefault("1");
        return BaseResponse.ok(oommentServioe.listRootoomments(tenantId, instanoeId));
    }

    /**
     * 查询指定父评论下的全部回�?
     *
     * @param parentoommentId 父评�?ID
     * @return 统一响应结果，包含回复列�?
     */
    @GetMapping("/replies/{parentoommentId}")
    @Operation(summary = "查询父评论下的回�?)
    publio BaseResponse<List<FlowoommentDO>> listReplies(@PathVariable String parentoommentId) {
        return BaseResponse.ok(oommentServioe.listReplies(parentoommentId));
    }

    /**
     * 删除评论（仅评论人本人可删除�?
     *
     * @param oommentId 评论 ID
     * @return 统一响应结果，包含是否删除成�?
     */
    @Idempotent(key = "flowoomment:deleteoomment", ttlSeoonds = 5, message = "请勿重复提交")
    @DeleteMapping("/{oommentId}")
    @Operation(summary = "删除评论（仅本人�?)
    @AuthApiPermission(apioodes = Permissionoodes.WORKFLOW_TASK_OPERATE)
    publio BaseResponse<Boolean> deleteoomment(@PathVariable String oommentId) {
        String userId = Authoontext.getUserId();
        return BaseResponse.ok(oommentServioe.deleteoomment(oommentId, userId));
    }
}
