package com.njydsz.pmis.workflow.web.controller.instance;

import java.util.Map;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import com.njydsz.pmis.common.core.response.BaseResponse;
import com.njydsz.pmis.common.lock.annotation.IdempotentExempt;
import com.njydsz.pmis.workflow.domain.dto.InstanceMigrationDTO;
import com.njydsz.pmis.workflow.domain.dto.InstanceMigrationResultDTO;
import com.njydsz.pmis.workflow.server.service.FlowInstanceMigrationService;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 流程实例迁移 Controller
 *
 * <p>GAP-V2-09: 流程实例迁移接口（P1-10 从 FlowEngineController 拆分）。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@RestController
@Tag(name = "workflow-migration", description = "工作流实例迁移接口")
@RequestMapping("/workflow/engine")
@RequiredArgsConstructor
@Validated
public class FlowMigrationController {

    /** GAP-V2-09: 流程实例迁移服务（新版本部署后迁移运行中实例） */
    private final FlowInstanceMigrationService instanceMigrationService;

    /**
     * GAP-V2-09: 执行实例迁移 — 将源定义下运行中实例迁移到目标定义。
     *
     * <p>请求体 {@link InstanceMigrationDTO}：
     * <ul>
     *   <li>sourceDefinitionId / targetDefinitionId：源/目标定义 ID（必填）</li>
     *   <li>tenantId：租户 ID（可选，默认从上下文获取）</li>
     *   <li>nodeMapping：旧节点编码 -> 新节点编码 映射（可选）</li>
     *   <li>dryRun：是否试运行（可选，true 时仅模拟不落库）</li>
     * </ul>
     *
     * @param dto 迁移参数
     * @return 统一响应结果，包含迁移结果报告
     */
    @IdempotentExempt("查询/导出/预览/模拟语义接口，无需幂等")
    @PostMapping("/instance/migrate")
    public BaseResponse<InstanceMigrationResultDTO> migrateInstances(@RequestBody InstanceMigrationDTO dto) {
        return BaseResponse.ok(instanceMigrationService.migrate(dto));
    }

    /**
     * GAP-V2-09: 预览实例迁移（试运行 / dry run）— 不实际更新数据库，仅返回迁移报告。
     *
     * @param dto 迁移参数（dryRun 字段将被忽略，强制为试运行）
     * @return 统一响应结果，包含迁移结果报告
     */
    @IdempotentExempt("查询/导出/预览/模拟语义接口，无需幂等")
    @PostMapping("/instance/migrate/preview")
    public BaseResponse<InstanceMigrationResultDTO> previewMigration(@RequestBody InstanceMigrationDTO dto) {
        return BaseResponse.ok(instanceMigrationService.previewMigration(dto));
    }

    /**
     * GAP-V2-09: 自动映射节点编码 — 对比源/目标定义节点，按编码自动匹配。
     *
     * <p>返回的映射可作为 {@link InstanceMigrationDTO#setNodeMapping(Map)} 的预填值，
     * 编码不同的节点需人工补充映射。
     *
     * @param sourceDefinitionId 源定义 ID
     * @param targetDefinitionId 目标定义 ID
     * @return 统一响应结果，包含 旧节点编码 -> 新节点编码 的映射
     */
    @GetMapping("/instance/migrate/autoMap")
    public BaseResponse<Map<String, String>> autoMapNodes(
            @RequestParam Long sourceDefinitionId,
            @RequestParam Long targetDefinitionId) {
        return BaseResponse.ok(instanceMigrationService.autoMapNodes(sourceDefinitionId, targetDefinitionId));
    }
}
