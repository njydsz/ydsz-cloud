package com.njydsz.pmis.agent.service;

import com.njydsz.pmis.agent.dto.ToolMarketQueryDTO;
import com.njydsz.pmis.agent.dto.ToolRegisterDTO;
import com.njydsz.pmis.agent.entity.ToolMarketEntryDO;
import com.njydsz.pmis.agent.tool.ToolResult;
import com.njydsz.pmis.common.api.PageResult;

import java.util.List;
import java.util.Map;

/**
 * 工具市场服务接口（P2-12 落地）。
 *
 * <p>对标 Coze Plugin Store / Dify Tool Manager，提供 HTTP API 工具的
 * 注册、导入、启停、测试等全生命周期管理能力。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P2-12)
 */
public interface ToolMarketService {

    /**
     * 手动注册单个 HTTP API 工具。
     *
     * @param dto 工具注册信息
     * @return 持久化后的工具条目
     */
    ToolMarketEntryDO register(ToolRegisterDTO dto);

    /**
     * 通过 OpenAPI 规范 URL 批量导入工具。
     *
     * @param specUrl OpenAPI 3.x 规范 URL
     * @return 导入的工具条目列表
     */
    List<ToolMarketEntryDO> registerFromOpenApi(String specUrl);

    /**
     * 注销工具（软删除 + 从 ToolRegistry 移除）。
     *
     * @param toolName 工具名称
     */
    void unregister(String toolName);

    /**
     * 启用工具（注册到 ToolRegistry）。
     *
     * @param toolName 工具名称
     * @return 更新后的条目
     */
    ToolMarketEntryDO enable(String toolName);

    /**
     * 禁用工具（从 ToolRegistry 移除）。
     *
     * @param toolName 工具名称
     * @return 更新后的条目
     */
    ToolMarketEntryDO disable(String toolName);

    /**
     * 根据 ID 查询工具详情。
     *
     * @param id 条目 ID
     * @return 工具条目
     */
    ToolMarketEntryDO getById(String id);

    /**
     * 分页查询工具市场。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<ToolMarketEntryDO> page(ToolMarketQueryDTO query);

    /**
     * 测试工具调用（不影响 ToolRegistry）。
     *
     * @param toolName  工具名称
     * @param parameters 测试参数
     * @return 调用结果
     */
    ToolResult testTool(String toolName, Map<String, Object> parameters);

    /**
     * 应用启动时加载所有已启用工具到 ToolRegistry。
     */
    void loadEnabledTools();
}
