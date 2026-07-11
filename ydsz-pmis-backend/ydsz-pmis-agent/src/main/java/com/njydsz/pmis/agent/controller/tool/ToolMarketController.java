package com.njydsz.pmis.agent.controller.tool;

import com.njydsz.pmis.common.annotation.Idempotent;

import com.njydsz.pmis.agent.dto.tool.ToolMarketQueryDTO;
import com.njydsz.pmis.agent.dto.tool.ToolRegisterDTO;
import com.njydsz.pmis.agent.entity.tool.ToolMarketEntryDO;
import com.njydsz.pmis.agent.service.tool.ToolMarketService;
import com.njydsz.pmis.agent.tool.ToolResult;
import com.njydsz.pmis.common.api.PageResult;
import com.njydsz.pmis.common.api.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 工具市场管理接口（P2-12 落地）。
 *
 * <p>对标 Coze Plugin Store / Dify Tool Manager 的后台管理 API，
 * 提供 HTTP API 工具的在线注册、OpenAPI 导入、启停、测试等能力。
 *
 * <p>接口清单：
 * <ul>
 *   <li>POST   /agent/tool-market/register          - 手动注册单个工具</li>
 *   <li>POST   /agent/tool-market/register-openapi  - 通过 OpenAPI 规范批量导入</li>
 *   <li>DELETE /agent/tool-market/{toolName}        - 注销工具</li>
 *   <li>POST   /agent/tool-market/{toolName}/enable - 启用工具</li>
 *   <li>POST   /agent/tool-market/{toolName}/disable- 禁用工具</li>
 *   <li>GET    /agent/tool-market/{id}              - 查询工具详情</li>
 *   <li>GET    /agent/tool-market                   - 分页查询工具列表</li>
 *   <li>POST   /agent/tool-market/{toolName}/test   - 测试工具调用</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P2-12)
 */
@Slf4j
@RestController
@RequestMapping("/agent/toolMarket")
@RequiredArgsConstructor
@Tag(name = "工具市场管理", description = "HTTP API 工具的在线注册、OpenAPI 导入、启停与测试")
public class ToolMarketController {

    /** 工具市场服务 */
    private final ToolMarketService service;

    /**
     * 注册工具（手动注册单个 HTTP API 工具到工具市场）。
     *
     * @param dto 工具注册参数
     * @return 落库后的工具条目
     */
    @Idempotent(key = "toolMarket:register", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/register")
    @Operation(summary = "注册工具", description = "手动注册单个 HTTP API 工具到工具市场")
    public Result<ToolMarketEntryDO> register(@Valid @RequestBody ToolRegisterDTO dto) {
        return Result.ok(service.register(dto));
    }

    /**
     * OpenAPI 批量导入（通过 OpenAPI 3.x 规范 URL 批量导入工具）。
     *
     * @param specUrl OpenAPI 规范 URL
     * @return 导入的工具条目列表
     */
    @Idempotent(key = "toolMarket:registerFromOpenApi", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/registerOpenapi")
    @Operation(summary = "OpenAPI 批量导入", description = "通过 OpenAPI 3.x 规范 URL 批量导入工具")
    public Result<List<ToolMarketEntryDO>> registerFromOpenApi(
            @Parameter(description = "OpenAPI 规范 URL", required = true)
            @RequestParam String specUrl) {
        return Result.ok(service.registerFromOpenApi(specUrl));
    }

    /**
     * 注销工具（软删除 + 从 ToolRegistry 移除）。
     *
     * @param toolName 工具名称
     * @return 空响应
     */
    @Idempotent(key = "toolMarket:unregister", ttlSeconds = 5, message = "请勿重复提交")
    @DeleteMapping("/{toolName}")
    @Operation(summary = "注销工具", description = "从工具市场注销指定工具（软删除 + 从 ToolRegistry 移除）")
    public Result<Void> unregister(@PathVariable String toolName) {
        service.unregister(toolName);
        return Result.ok();
    }

    /**
     * 启用工具（注册到 ToolRegistry 供 Agent 调用）。
     *
     * @param toolName 工具名称
     * @return 更新后的工具条目
     */
    @Idempotent(key = "toolMarket:enable", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/{toolName}/enable")
    @Operation(summary = "启用工具", description = "启用指定工具，注册到 ToolRegistry 供 Agent 调用")
    public Result<ToolMarketEntryDO> enable(@PathVariable String toolName) {
        return Result.ok(service.enable(toolName));
    }

    /**
     * 禁用工具（从 ToolRegistry 移除）。
     *
     * @param toolName 工具名称
     * @return 更新后的工具条目
     */
    @Idempotent(key = "toolMarket:disable", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/{toolName}/disable")
    @Operation(summary = "禁用工具", description = "禁用指定工具，从 ToolRegistry 移除")
    public Result<ToolMarketEntryDO> disable(@PathVariable String toolName) {
        return Result.ok(service.disable(toolName));
    }

    /**
     * 查询工具详情。
     *
     * @param id 工具 ID
     * @return 工具详情
     */
    @GetMapping("/{id}")
    @Operation(summary = "查询工具详情")
    public Result<ToolMarketEntryDO> getById(@PathVariable String id) {
        return Result.ok(service.getById(id));
    }

    /**
     * 分页查询工具列表。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    @GetMapping
    @Operation(summary = "分页查询工具列表")
    public Result<PageResult<ToolMarketEntryDO>> page(ToolMarketQueryDTO query) {
        return Result.ok(service.page(query));
    }

    /**
     * 测试工具调用（不影响 ToolRegistry 状态）。
     *
     * @param toolName  工具名称
     * @param parameters 调用参数
     * @return 工具执行结果
     */
    @Idempotent(key = "toolMarket:testTool", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/{toolName}/test")
    @Operation(summary = "测试工具调用", description = "使用指定参数测试工具调用，不影响 ToolRegistry 状态")
    public Result<ToolResult> testTool(
            @PathVariable String toolName,
            @RequestBody Map<String, Object> parameters) {
        return Result.ok(service.testTool(toolName, parameters));
    }
}
