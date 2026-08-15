package com.njydsz.workflow.web.controller.definition;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.lock.annotation.Idempotent;
import com.njydsz.common.permission.PermissionCodes;
import com.njydsz.common.safe.ratelimit.annotation.RateLimit;
import com.njydsz.workflow.domain.converter.WorkflowConverter;
import com.njydsz.workflow.domain.dto.FlowDeployProcessDTO;
import com.njydsz.workflow.domain.vo.FlowDefinitionVO;
import com.njydsz.workflow.server.service.FlowDefinitionService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.audit.enums.AuditType;
import com.njydsz.common.core.code.BaseResultCode;
/**
 * 流程定义部署与查询 Controller
 *
 * <p>提供流程定义的部署 / 发布 / 废弃 / 查询 / 预览等 REST 接口，
 * 是设计器、流程中心、运维控制台的数据入口。所有接口对标 Activiti / Flowable API 风格。
 *
 * <p><b>核心能力：</b>
 * <ul>
 *   <li><b>部署</b>：{@code POST /definition/deploy}（单条部署） /
 *       {@code POST /definition/batchDeployZip}（BPMN zip 批量部署）</li>
 *   <li><b>发布控制</b>：{@code POST /definition/{id}/publish}（发布，带版本兼容性校验） /
 *       {@code POST /definition/{id}/deprecate}（废弃）</li>
 *   <li><b>查询</b>：{@code GET /definition/code/{code}}（按编码查询已发布定义） /
 *       {@code GET /definition/page}（分页查询） /
 *       {@code GET /definition/{id}}（详情，含节点与跳转） /
 *       {@code GET /definition/{id}/preview}（只读预览）</li>
 * </ul>
 *
 * <p><b>权限模型：</b>所有接口通过 {@link AuthApiPermission} 注解配置
 * {@link PermissionCodes#WORKFLOW_DEFINITION_DEPLOY} 等权限码，与 RBAC 权限中心对接。
 *
 * <p><b>限流：</b>部署类接口通过 {@link RateLimit} 限流（{@code 50 QPS}），
 * 防止批量部署拖垮后端；幂等操作通过 {@link Idempotent} 注解保证
 * 「同一请求 5s 内只执行一次」。
 *
 * <p><b>设计原则：</b>本 Controller 仅做参数透传与权限校验，所有业务逻辑下沉到
 * {@link FlowDefinitionService}，符合「瘦 Controller / 胖 Service」规范。
 *
 * <p><b>拆分说明：</b>本类为原 {@code FlowDefinitionController} 拆分后的部署与查询部分。
 * 版本生命周期管理（切换 / 启停 / 版本历史 / 差异对比 / 回滚）见 {@link FlowDefinitionVersionController}；
 * 设计 / 导入导出 / 模拟 / 变更影响分析见 {@link FlowDefinitionDesignController}。
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see FlowDefinitionService 流程定义服务
 * @see FlowDeployProcessDTO 部署参数 DTO
 * @see FlowDefinitionVersionController 版本生命周期管理接口
 * @see FlowDefinitionDesignController 设计 / 导入导出 / 模拟接口
 */
@Slf4j
@RestController
@Tag(name = "workflow-definition", description = "工作流流程定义部署与查询接口")
@RequestMapping("/api/v1/workflow/engine")
@RequiredArgsConstructor
@Validated
public class FlowDefinitionController {

    /** 流程定义服务 */
    private final FlowDefinitionService definitionService;

    /**
     * 部署流程定义
     *
     * @param dto 流程部署参数
     * @return 统一响应结果，包含流程定义 ID
     */
    @Idempotent(key = "ydsz:workflow:FlowDefinitionController:deploy:lock", ttlSeconds = 5)
    @RateLimit(resource = "workflow.flowdefinition.deploy", threshold = 50)
    @PostMapping("/definition/deploy")
    @Audit(module = "流程定义", type = AuditType.OPERATION, action = AuditAction.ENABLE, content = "'deploy'")
    @Operation(summary = "部署流程定义")
    @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_DEFINITION_DEPLOY)
    public BaseResponse<String> deploy(@Valid @RequestBody FlowDeployProcessDTO dto) {
        String id = definitionService.deploy(dto);
        return BaseResponse.success(id);
    }

    /**
     * GAP-P1-6: BPMN 部署包 .zip 批量导入流程定义。
     *
     * <p>对标 Activiti/Flowable 的 zip 部署能力。上传 .zip 文件，遍历其中的
     * {@code .bpmn} / {@code .bpmn20.xml} 文件逐个部署，单个失败不影响其他文件。
     *
     * @param file     zip 文件（multipart/form-data）
     * @return 统一响应结果，包含 successCount / failedItems
     */
    @Idempotent(key = "ydsz:workflow:FlowDefinitionController:batchDeployFromZip:lock", ttlSeconds = 5)
    @PostMapping(value = "/definition/batchDeployZip", consumes = "multipart/form-data")
    @Operation(summary = "BPMN 部署包 .zip 批量导入")
    @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_DEFINITION_DEPLOY)
    public BaseResponse<Map<String, Object>> batchDeployFromZip(
            @RequestParam("file")
            MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return BaseResponse.error(BaseResultCode.VALIDATION_FAILED, "zip 文件不能为空");
        }
        try {
            return BaseResponse.success(definitionService.batchDeployFromZip(file.getBytes(), null));
        } catch (IOException e) {
            return BaseResponse.error(BaseResultCode.BAD_REQUEST, "读取 zip 文件失败: " + e.getMessage());
        }
    }

    /**
     * 发布流程定义（带版本兼容性校验）。
     *
     * <p>P1-4: 发布前自动检测同 flowCode 激活版本的在途实例是否会因节点删除而卡死。
     * HIGH 风险时默认阻断，可通过 {@code force=true} 强制发布（需管理员权限）。
     *
     * @param id    流程定义 ID
     * @param force 是否强制发布（跳过 HIGH 风险阻断），默认 false
     * @return 统一响应结果
     */
    @Idempotent(key = "ydsz:workflow:FlowDefinitionController:publish:lock", ttlSeconds = 5)
    @PostMapping("/definition/{id}/publish")
    @Audit(module = "流程定义", type = AuditType.OPERATION, action = AuditAction.ENABLE, content = "'publish'")
    @Operation(summary = "发布流程定义（带版本兼容性校验）")
    @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_DEFINITION_PUBLISH)
    public BaseResponse<Void> publish(@PathVariable String id,
                                      @RequestParam(defaultValue = "false") boolean force) {
        definitionService.publish(id, force);
        return BaseResponse.success();
    }

    /**
     * 废弃流程定义
     *
     * @param id 流程定义 ID
     * @return 统一响应结果
     */
    @Idempotent(key = "ydsz:workflow:FlowDefinitionController:deprecate:lock", ttlSeconds = 5)
    @RateLimit(resource = "workflow.flowdefinition.deprecate", threshold = 50)
    @PostMapping("/definition/{id}/deprecate")
    @Audit(module = "流程定义", type = AuditType.OPERATION, action = AuditAction.CREATE, content = "'deprecate'")
    @Operation(summary = "废弃流程定义")
    @AuthApiPermission(apiCodes = PermissionCodes.WORKFLOW_DEFINITION_PUBLISH)
    public BaseResponse<Void> deprecate(@PathVariable String id) {
        definitionService.deprecate(id);
        return BaseResponse.success();
    }

    /**
     * 按编码查询已发布流程定义
     *
     * @param code      流程编码
     * @param version   版本号（可选）
     * @param tenantId  租户 ID（可选）
     * @return 统一响应结果，包含流程定义
     */
    @GetMapping("/definition/code/{code}")
    @Operation(summary = "按编码查询已发布流程定义")
    public BaseResponse<FlowDefinitionVO> getByCode(@PathVariable String code,
                                          @RequestParam(required = false) String version,
                                          @RequestParam(required = false) String tenantId) {
        return BaseResponse.success(WorkflowConverter.INSTANT.entityToVO(definitionService.getPublished(code, version, tenantId)));
    }

    /**
     * 分页查询流程定义
     *
     * @param pageNo   页码
     * @param pageSize 每页大小
     * @param category 分类（可选）
     * @param flowCode 流程编码（可选）
     * @return 统一响应结果，包含流程定义列表
     */
    @GetMapping("/definition/page")
    @Operation(summary = "分页查询流程定义")
    public BaseResponse<List<FlowDefinitionVO>> page(@RequestParam(defaultValue = "1") @Min(1) int pageNo,
                                          @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize,
                                          @RequestParam(required = false) String category,
                                          @RequestParam(required = false) String flowCode) {
        return BaseResponse.success(WorkflowConverter.INSTANT.flowDefinitionListToVO(definitionService.page(pageNo, pageSize, category, flowCode)));
    }

    /**
     * P2-21: 流程定义详情查询（含节点 + 跳转）
     *
     * @param id 流程定义 ID
     * @return 统一响应结果，包含 definition / nodes / skips
     */
    @GetMapping("/definition/{id}")
    @Operation(summary = "查询流程定义详情（含节点与跳转）")
    public BaseResponse<Map<String, Object>> getDefinitionDetail(@PathVariable String id) {
        return BaseResponse.success(definitionService.getDetail(id));
    }

    /**
     * P2-8 (GAP-53): 流程定义预览 — 只读模式返回定义详情 + readOnly 标记
     *
     * <p>前端用 bpmn-js 以只读模式渲染（禁用编辑 palette），展示流程全貌。
     * 数据与 {@link #getDefinitionDetail} 一致，额外携带 {@code readOnly=true} 标志。
     */
    @GetMapping("/definition/{id}/preview")
    @Operation(summary = "流程定义预览（只读）")
    public BaseResponse<Map<String, Object>> getDefinitionPreview(@PathVariable String id) {
        Map<String, Object> detail = definitionService.getDetail(id);
        detail.put("readOnly", true);
        return BaseResponse.success(detail);
    }
}
