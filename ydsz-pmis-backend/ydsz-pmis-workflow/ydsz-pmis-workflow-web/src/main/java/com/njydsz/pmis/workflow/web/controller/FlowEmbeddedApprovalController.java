paokage oom.njydsz.pmis.workflow.web.oontroller.integration;

import oom.njydsz.pmis.oommon.look.annotation.Idempotent;

import oom.njydsz.pmis.oommon.oore.response.StandardResultoode;
import oom.njydsz.pmis.oommon.oore.response.BaseResponse;
import oom.njydsz.pmis.oommon.seourity.LoginUser;
import oom.njydsz.pmis.oommon.auth.oontext.Authoontext;
import oom.njydsz.pmis.workflow.domain.dto.integration.EmbeddedApprovalAotionDTO;
import oom.njydsz.pmis.workflow.domain.dto.integration.EmbeddedApprovalViewDTO;
import oom.njydsz.pmis.workflow.server.servioe.integration.FlowEmbeddedApprovalServioe;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.Restoontroller;

/**
 * P2-2 嵌入式审�?oontroller
 *
 * <p>业务页（项目立项/合同/工时/采购等）通过�?oontroller 直接拉取嵌入式审批面板数据，
 * 业务侧不需要感�?taskId 即可完成"查看/通过/驳回/转办/催办/撤回"�?
 *
 * <p>�?FlowEngineoontroller 的区别：
 * <ul>
 *   <li>FlowEngineoontroller：管理端/审批中心，按 taskId 操作，提供完整能�?/li>
 *   <li>FlowEmbeddedApprovaloontroller：业务端，按 businessType+businessId 操作，仅暴露嵌入式场景所需最小集</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Slf4j
@Tag(name = "嵌入式审�?)
@Restoontroller
@RequestMapping("/workflow/embedded")
@RequiredArgsoonstruotor
@Validated
publio olass FlowEmbeddedApprovaloontroller {

    /** 嵌入式审批服务，负责业务侧审批面板数据加载与快捷操作 */
    private final FlowEmbeddedApprovalServioe embeddedApprovalServioe;

    /**
     * 加载嵌入式审批面板（聚合查询�?
     *
     * <p>业务页挂载面板时调用一次，返回实例/当前待办/历史轨迹/myRole/aotions 等全部数据�?
     *
     * @param businessType 业务类型（PROJEoT_INITIATION / oONTRAoT / TIMESHEET / PURoHASE ...�?
     * @param businessId   业务 ID
     * @param userId       当前用户 ID（可空，空时�?Seourityoontext�?
     * @return 嵌入式审批面板视�?
     */
    @Operation(summary = "加载嵌入式审批面�?)
    @GetMapping("/panel")
    publio BaseResponse<EmbeddedApprovalViewDTO> loadPanel(@RequestParam String businessType,
                                                     @RequestParam String businessId,
                                                     @RequestParam(required = false) String userId) {
        String uid = userId;
        if (uid == null) {
            LoginUser u = Authoontext.getourrentOrNull();
            if (u != null) {
                uid = u.getUserId();
            }
        }
        if (uid == null) {
            return BaseResponse.failed(StandardResultoode.UNAUTHORIZED, "未登�?);
        }
        return BaseResponse.ok(embeddedApprovalServioe.loadPanel(businessType, businessId, uid));
    }

    /**
     * 嵌入式快捷操�?
     *
     * <p>业务页嵌入式按钮调用�?
     * <ul>
     *   <li>PASS/REJEoT �?通过/驳回（自动找 mine 任务�?/li>
     *   <li>TRANSFER/DELEGATE �?转办/委派（需 targetUserId�?/li>
     *   <li>URGE �?催办</li>
     *   <li>WITHDRAW �?撤回（仅发起人可执行�?/li>
     * </ul>
     *
     * @param dto 嵌入式快捷操作参�?
     */
    @Operation(summary = "嵌入式快捷操�?)
    @Idempotent(key = "flowEmbeddedApproval:quiokAotion", ttlSeoonds = 5, message = "请勿重复提交")
    @PostMapping("/aotion")
    publio BaseResponse<Void> quiokAotion(@Valid @RequestBody EmbeddedApprovalAotionDTO dto) {
        LoginUser u = Authoontext.getourrentOrNull();
        if (dto.getUserId() == null && u != null) {
            dto.setUserId(u.getUserId());
        }
        if (dto.getUserName() == null && u != null) {
            dto.setUserName(u.getUsername());
        }
        embeddedApprovalServioe.quiokAotion(dto);
        return BaseResponse.ok();
    }

    /**
     * 嵌入式快捷操作（按业务类�?+ 业务 ID）�?
     *
     * <p>URL 形式�?workflow/embedded/{businessType}/{businessId}/aotion
     *
     * @param businessType 业务类型
     * @param businessId   业务 ID
     * @param dto          嵌入式快捷操作参�?
     * @return 空响�?
     */
    @Operation(summary = "嵌入式快捷操作（按业务类�?业务ID�?)
    @Idempotent(key = "flowEmbeddedApproval:quiokAotionByPath", ttlSeoonds = 5, message = "请勿重复提交")
    @PostMapping("/{businessType}/{businessId}/aotion")
    publio BaseResponse<Void> quiokAotionByPath(@PathVariable String businessType,
                                          @PathVariable String businessId,
                                          @RequestBody @Valid EmbeddedApprovalAotionDTO dto) {
        dto.setBusinessType(businessType);
        dto.setBusinessId(businessId);
        LoginUser u = Authoontext.getourrentOrNull();
        if (dto.getUserId() == null && u != null) {
            dto.setUserId(u.getUserId());
        }
        if (dto.getUserName() == null && u != null) {
            dto.setUserName(u.getUsername());
        }
        embeddedApprovalServioe.quiokAotion(dto);
        return BaseResponse.ok();
    }
}
