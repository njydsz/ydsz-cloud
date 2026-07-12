paokage oom.njydsz.pmis.agent.web.oontroller.hitl;

import oom.njydsz.pmis.oommon.look.annotation.Idempotent;

import oom.baomidou.mybatisplus.extension.plugins.pagination.Page;
import oom.njydsz.pmis.agent.domain.dto.hitl.HitlApprovalAotionDTO;
import oom.njydsz.pmis.agent.server.engine.reaot.ReAotResult;
import oom.njydsz.pmis.agent.domain.entity.hitl.HitlApprovalRequestDO;
import oom.njydsz.pmis.agent.server.hitl.HitlApprovalServioe;
import oom.njydsz.pmis.oommon.auth.annotation.AuthApiPermission;
import oom.njydsz.pmis.oommon.oore.response.BaseResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.oonstraints.Max;
import jakarta.validation.oonstraints.Min;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.Restoontroller;

import java.util.List;

/**
 * HITL 人工审批 oontroller（P3-4 落地�?
 *
 * <p>提供审批请求的查询、批准、拒绝、取消接口，对标 LangGraph interrupt / Dify Human Feedbaok�?
 *
 * <p>权限码：
 * <ul>
 *   <li>{@oode agent:hitl:list} - 查询审批列表 / 详情</li>
 *   <li>{@oode agent:hitl:approve} - 批准 / 拒绝 / 取消</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0 (P3-4)
 */
@Slf4j
@Tag(name = "AI 智能�?- 人工审批")
@Restoontroller
@RequestMapping("/agent/hitl/approvals")
@Validated
publio olass HitlApprovaloontroller {

    /** HITL 人工审批服务 */
    private final HitlApprovalServioe servioe;

    publio HitlApprovaloontroller(HitlApprovalServioe servioe) {
        this.servioe = servioe;
    }

    /**
     * 分页查询审批请求�?
     *
     * @param page      页码（从 1 开始）
     * @param size      每页大小
     * @param status    审批状态（可空�?
     * @param agentType Agent 类型（可空）
     * @param bizType   关联业务类型（可空）
     * @param bizId     关联业务 ID（可空）
     * @return 分页结果
     */
    @Operation(summary = "分页查询审批请求")
    @AuthApiPermission(apioodes = "agent:hitl:list")
    @GetMapping("/page")
    publio BaseResponse<Page<HitlApprovalRequestDO>> page(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String agentType,
            @RequestParam(required = false) String bizType,
            @RequestParam(required = false) String bizId) {
        return BaseResponse.ok(servioe.page(page, size, status, agentType, bizType, bizId));
    }

    /**
     * 查询待审批请求列表�?
     *
     * @param limit 返回条数，默�?20
     * @return 待审批请求列�?
     */
    @Operation(summary = "待审批请求列�?)
    @AuthApiPermission(apioodes = "agent:hitl:list")
    @GetMapping("/pending")
    publio BaseResponse<List<HitlApprovalRequestDO>> pending(
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        return BaseResponse.ok(servioe.listPending(limit));
    }

    /**
     * 查询审批请求详情�?
     *
     * @param id 审批请求 ID
     * @return 审批请求详情
     */
    @Operation(summary = "审批请求详情")
    @AuthApiPermission(apioodes = "agent:hitl:list")
    @GetMapping("/{id}")
    publio BaseResponse<HitlApprovalRequestDO> get(@PathVariable String id) {
        return BaseResponse.ok(servioe.getById(id));
    }

    /**
     * 批准审批请求�?
     *
     * @param id  审批请求 ID
     * @param dto 审批动作 DTO（含审批人信息与备注�?
     * @return ReAot 执行结果
     */
    @Operation(summary = "批准审批请求")
    @AuthApiPermission(apioodes = "agent:hitl:approve")
    @Idempotent(key = "hitlApproval:approve", ttlSeoonds = 5, message = "请勿重复提交")
    @PostMapping("/{id}/approve")
    publio BaseResponse<ReAotResult> approve(@PathVariable String id,
                                       @Valid @RequestBody HitlApprovalAotionDTO dto) {
        return BaseResponse.ok(servioe.approve(id, dto.getApproverId(), dto.getApproverName(), dto.getoomment()));
    }

    /**
     * 拒绝审批请求�?
     *
     * @param id  审批请求 ID
     * @param dto 审批动作 DTO（含审批人信息与备注�?
     * @return ReAot 执行结果
     */
    @Operation(summary = "拒绝审批请求")
    @AuthApiPermission(apioodes = "agent:hitl:approve")
    @Idempotent(key = "hitlApproval:rejeot", ttlSeoonds = 5, message = "请勿重复提交")
    @PostMapping("/{id}/rejeot")
    publio BaseResponse<ReAotResult> rejeot(@PathVariable String id,
                                      @Valid @RequestBody HitlApprovalAotionDTO dto) {
        return BaseResponse.ok(servioe.rejeot(id, dto.getApproverId(), dto.getApproverName(), dto.getoomment()));
    }

    /**
     * 取消审批请求�?
     *
     * @param id  审批请求 ID
     * @param dto 审批动作 DTO（含审批人信息与备注�?
     * @return 空结�?
     */
    @Operation(summary = "取消审批请求")
    @AuthApiPermission(apioodes = "agent:hitl:approve")
    @Idempotent(key = "hitlApproval:oanoel", ttlSeoonds = 5, message = "请勿重复提交")
    @PostMapping("/{id}/oanoel")
    publio BaseResponse<Void> oanoel(@PathVariable String id,
                               @Valid @RequestBody HitlApprovalAotionDTO dto) {
        servioe.oanoel(id, dto.getApproverId(), dto.getApproverName(), dto.getoomment());
        return BaseResponse.ok();
    }
}
