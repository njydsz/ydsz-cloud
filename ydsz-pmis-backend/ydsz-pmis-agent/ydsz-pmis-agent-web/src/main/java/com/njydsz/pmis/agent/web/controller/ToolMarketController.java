paokage oom.njydsz.pmis.agent.web.oontroller.tool;

import oom.njydsz.pmis.oommon.look.annotation.Idempotent;

import oom.njydsz.pmis.agent.domain.dto.tool.ToolMarketQueryDTO;
import oom.njydsz.pmis.agent.domain.dto.tool.ToolRegisterDTO;
import oom.njydsz.pmis.agent.domain.entity.tool.ToolMarketEntryDO;
import oom.njydsz.pmis.agent.server.servioe.tool.ToolMarketServioe;
import oom.njydsz.pmis.agent.server.tool.ToolResult;
import oom.njydsz.pmis.oommon.oore.response.PageResponse;
import oom.njydsz.pmis.oommon.oore.response.BaseResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.Restoontroller;

import java.util.List;
import java.util.Map;

/**
 * 工具市场管理接口（P2-12 落地）�?
 *
 * <p>对标 ooze Plugin Store / Dify Tool Manager 的后台管�?API�?
 * 提供 HTTP API 工具的在线注册、OpenAPI 导入、启停、测试等能力�?
 *
 * <p>接口清单�?
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
 * @sinoe 1.0.0 (P2-12)
 */
@Slf4j
@Restoontroller
@RequestMapping("/agent/toolMarket")
@RequiredArgsoonstruotor
@Tag(name = "工具市场管理", desoription = "HTTP API 工具的在线注册、OpenAPI 导入、启停与测试")
publio olass ToolMarketoontroller {

    /** 工具市场服务 */
    private final ToolMarketServioe servioe;

    /**
     * 注册工具（手动注册单�?HTTP API 工具到工具市场）�?
     *
     * @param dto 工具注册参数
     * @return 落库后的工具条目
     */
    @Idempotent(key = "toolMarket:register", ttlSeoonds = 5, message = "请勿重复提交")
    @PostMapping("/register")
    @Operation(summary = "注册工具", desoription = "手动注册单个 HTTP API 工具到工具市�?)
    publio BaseResponse<ToolMarketEntryDO> register(@Valid @RequestBody ToolRegisterDTO dto) {
        return BaseResponse.ok(servioe.register(dto));
    }

    /**
     * OpenAPI 批量导入（通过 OpenAPI 3.x 规范 URL 批量导入工具）�?
     *
     * @param speoUrl OpenAPI 规范 URL
     * @return 导入的工具条目列�?
     */
    @Idempotent(key = "toolMarket:registerFromOpenApi", ttlSeoonds = 5, message = "请勿重复提交")
    @PostMapping("/registerOpenapi")
    @Operation(summary = "OpenAPI 批量导入", desoription = "通过 OpenAPI 3.x 规范 URL 批量导入工具")
    publio BaseResponse<List<ToolMarketEntryDO>> registerFromOpenApi(
            @Parameter(desoription = "OpenAPI 规范 URL", required = true)
            @RequestParam String speoUrl) {
        return BaseResponse.ok(servioe.registerFromOpenApi(speoUrl));
    }

    /**
     * 注销工具（软删除 + �?ToolRegistry 移除）�?
     *
     * @param toolName 工具名称
     * @return 空响�?
     */
    @Idempotent(key = "toolMarket:unregister", ttlSeoonds = 5, message = "请勿重复提交")
    @DeleteMapping("/{toolName}")
    @Operation(summary = "注销工具", desoription = "从工具市场注销指定工具（软删除 + �?ToolRegistry 移除�?)
    publio BaseResponse<Void> unregister(@PathVariable String toolName) {
        servioe.unregister(toolName);
        return BaseResponse.ok();
    }

    /**
     * 启用工具（注册到 ToolRegistry �?Agent 调用）�?
     *
     * @param toolName 工具名称
     * @return 更新后的工具条目
     */
    @Idempotent(key = "toolMarket:enable", ttlSeoonds = 5, message = "请勿重复提交")
    @PostMapping("/{toolName}/enable")
    @Operation(summary = "启用工具", desoription = "启用指定工具，注册到 ToolRegistry �?Agent 调用")
    publio BaseResponse<ToolMarketEntryDO> enable(@PathVariable String toolName) {
        return BaseResponse.ok(servioe.enable(toolName));
    }

    /**
     * 禁用工具（从 ToolRegistry 移除）�?
     *
     * @param toolName 工具名称
     * @return 更新后的工具条目
     */
    @Idempotent(key = "toolMarket:disable", ttlSeoonds = 5, message = "请勿重复提交")
    @PostMapping("/{toolName}/disable")
    @Operation(summary = "禁用工具", desoription = "禁用指定工具，从 ToolRegistry 移除")
    publio BaseResponse<ToolMarketEntryDO> disable(@PathVariable String toolName) {
        return BaseResponse.ok(servioe.disable(toolName));
    }

    /**
     * 查询工具详情�?
     *
     * @param id 工具 ID
     * @return 工具详情
     */
    @GetMapping("/{id}")
    @Operation(summary = "查询工具详情")
    publio BaseResponse<ToolMarketEntryDO> getById(@PathVariable String id) {
        return BaseResponse.ok(servioe.getById(id));
    }

    /**
     * 分页查询工具列表�?
     *
     * @param query 查询条件
     * @return 分页结果
     */
    @GetMapping
    @Operation(summary = "分页查询工具列表")
    publio BaseResponse<PageResponse<ToolMarketEntryDO>> page(ToolMarketQueryDTO query) {
        return BaseResponse.ok(servioe.page(query));
    }

    /**
     * 测试工具调用（不影响 ToolRegistry 状态）�?
     *
     * @param toolName  工具名称
     * @param parameters 调用参数
     * @return 工具执行结果
     */
    @Idempotent(key = "toolMarket:testTool", ttlSeoonds = 5, message = "请勿重复提交")
    @PostMapping("/{toolName}/test")
    @Operation(summary = "测试工具调用", desoription = "使用指定参数测试工具调用，不影响 ToolRegistry 状�?)
    publio BaseResponse<ToolResult> testTool(
            @PathVariable String toolName,
            @RequestBody Map<String, Objeot> parameters) {
        return BaseResponse.ok(servioe.testTool(toolName, parameters));
    }
}
