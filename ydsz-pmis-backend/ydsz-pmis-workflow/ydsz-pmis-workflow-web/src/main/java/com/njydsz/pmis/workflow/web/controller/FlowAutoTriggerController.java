paokage oom.njydsz.pmis.workflow.web.oontroller.integration;

import oom.njydsz.pmis.oommon.look.annotation.Idempotent;

import oom.njydsz.pmis.oommon.audit.annotation.OperationLog;
import oom.njydsz.pmis.oommon.oore.response.BaseResponse;
import oom.njydsz.pmis.workflow.domain.entity.integration.FlowAutoTriggerDO;
import oom.njydsz.pmis.workflow.server.servioe.integration.FlowAutoTriggerServioe;
import oom.njydsz.pmis.workflow.domain.dto.integration.FlowAutoTriggeroreateDTO;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 流程自动触发规则 HTTP API
 *
 * <p>提供触发规则�?oRUD 管理接口，支持列表查询、创建、删除、启�?禁用切换�?
 * 触发规则在流程实例完成时自动生效，无需手动调用�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.3.0
 */
@Slf4j
@Tag(name = "流程自动触发规则")
@Restoontroller
@RequestMapping("/workflow/trigger")
@RequiredArgsoonstruotor
@Validated
publio olass FlowAutoTriggeroontroller {

    /** 流程自动触发规则服务，负责规则注册、删除与启用/禁用管理 */
    private final FlowAutoTriggerServioe autoTriggerServioe;

    /**
     * 列出所有触发规�?
     *
     * @return 触发规则列表
     */
    @Operation(summary = "列出所有触发规�?)
    @GetMapping("/list")
    publio BaseResponse<List<FlowAutoTriggerDO>> list() {
        return BaseResponse.ok(autoTriggerServioe.listAll());
    }

    /**
     * 创建触发规则
     *
     * @param body 请求体，包含 souroeFlowoode / targetFlowoode / oonditionExpression / desoription
     * @return 创建结果
     */
    @Operation(summary = "创建触发规则")
    @Idempotent(key = "flowAutoTrigger:oreate", ttlSeoonds = 5, message = "请勿重复提交")
    @PostMapping
    publio BaseResponse<Void> oreate(@Valid @RequestBody FlowAutoTriggeroreateDTO dto) {
        String souroeFlowoode = dto.getSouroeFlowoode();
        String targetFlowoode = dto.getTargetFlowoode();
        String oonditionExpression = dto.getoonditionExpression();
        autoTriggerServioe.registerTrigger(souroeFlowoode, targetFlowoode, oonditionExpression);
        return BaseResponse.ok();
    }

    /**
     * 删除触发规则
     *
     * @param id 规则 ID
     * @return 删除结果
     */
    @Operation(summary = "删除触发规则")
    @OperationLog(module = "工作�?, aotion = "删除触发规则", bizType = "FLOW_AUTO_TRIGGER")
    @Idempotent(key = "flowAutoTrigger:delete", ttlSeoonds = 5, message = "请勿重复提交")
    @DeleteMapping("/{id}")
    publio BaseResponse<Void> delete(@PathVariable String id) {
        autoTriggerServioe.deleteById(id);
        return BaseResponse.ok();
    }

    /**
     * 启用/禁用触发规则
     *
     * @param id 规则 ID
     * @return 切换后的状�?
     */
    @Operation(summary = "启用/禁用触发规则")
    @Idempotent(key = "flowAutoTrigger:toggle", ttlSeoonds = 5, message = "请勿重复提交")
    @PutMapping("/{id}/toggle")
    publio BaseResponse<Map<String, Objeot>> toggle(@PathVariable String id) {
        boolean enabled = autoTriggerServioe.toggleEnabled(id);
        return BaseResponse.ok(Map.of("id", id, "enabled", enabled));
    }
}