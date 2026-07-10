package com.njydsz.pmis.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.njydsz.pmis.agent.dto.ToolMarketQueryDTO;
import com.njydsz.pmis.agent.dto.ToolRegisterDTO;
import com.njydsz.pmis.agent.entity.ToolMarketEntryDO;
import com.njydsz.pmis.agent.mapper.ToolMarketEntryMapper;
import com.njydsz.pmis.agent.service.ToolMarketService;
import com.njydsz.pmis.agent.tool.HttpApiTool;
import com.njydsz.pmis.agent.tool.OpenApiSpecParser;
import com.njydsz.pmis.agent.tool.ToolRegistry;
import com.njydsz.pmis.agent.tool.ToolResult;
import com.njydsz.pmis.common.api.PageResult;
import com.njydsz.pmis.common.exception.BizException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 工具市场服务实现（P2-12 落地）。
 *
 * <p>对标 Coze Plugin Store / Dify Tool Manager，实现 HTTP API 工具的全生命周期管理：
 * <ul>
 *   <li>手动注册 / OpenAPI 批量导入</li>
 *   <li>启用 / 禁用 / 注销</li>
 *   <li>测试调用</li>
 *   <li>应用启动时自动加载已启用工具到 {@link ToolRegistry}</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P2-12)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ToolMarketServiceImpl implements ToolMarketService {

    /** 工具市场条目 Mapper（CRUD） */
    private final ToolMarketEntryMapper mapper;
    /** 工具注册中心（内存注册表，供 Agent 运行时调用） */
    private final ToolRegistry toolRegistry;
    /** JSON 序列化/反序列化工具 */
    private final ObjectMapper objectMapper;

    /** 来源类型常量 */
    private static final String SOURCE_MANUAL = "MANUAL";
    private static final String SOURCE_OPENAPI = "OPENAPI";

    // ==================== 生命周期 ====================

    /**
     * 应用启动后自动加载已启用工具。
     */
    @PostConstruct
    public void init() {
        loadEnabledTools();
    }

    @Override
    public void loadEnabledTools() {
        List<ToolMarketEntryDO> enabledTools = mapper.selectAllEnabled();
        if (enabledTools == null || enabledTools.isEmpty()) {
            log.info("[ToolMarket] 无已启用工具需要加载");
            return;
        }
        int success = 0;
        int failed = 0;
        for (ToolMarketEntryDO entry : enabledTools) {
            try {
                HttpApiTool tool = buildToolFromEntry(entry);
                toolRegistry.register(tool);
                success++;
            } catch (Exception e) {
                log.warn("[ToolMarket] 加载工具失败: name={}, error={}", entry.getToolName(), e.getMessage());
                failed++;
            }
        }
        log.info("[ToolMarket] 启动加载完成: 成功={}, 失败={}, 总计={}", success, failed, enabledTools.size());
    }

    // ==================== 注册 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ToolMarketEntryDO register(ToolRegisterDTO dto) {
        // 校验工具名唯一性
        ToolMarketEntryDO existing = mapper.selectByToolName(dto.getToolName());
        if (existing != null) {
            throw new BizException("工具名称已存在: " + dto.getToolName());
        }

        ToolMarketEntryDO entry = new ToolMarketEntryDO();
        entry.setToolName(dto.getToolName());
        entry.setDisplayName(StringUtils.hasText(dto.getDisplayName()) ? dto.getDisplayName() : dto.getToolName());
        entry.setDescription(dto.getDescription());
        entry.setCategory(StringUtils.hasText(dto.getCategory()) ? dto.getCategory() : "default");
        entry.setSourceType(SOURCE_MANUAL);
        entry.setHttpMethod(dto.getHttpMethod().toUpperCase());
        entry.setEndpointUrl(dto.getEndpointUrl());
        entry.setHeaders(toJson(dto.getHeaders()));
        entry.setParamSchema(toJson(dto.getParamSchema()));
        entry.setBodyTemplate(dto.getBodyTemplate());
        entry.setPathParams(toJson(dto.getPathParams()));
        entry.setQueryParams(toJson(dto.getQueryParams()));
        entry.setTimeoutMs(dto.getTimeoutMs() != null ? dto.getTimeoutMs() : 30000L);
        entry.setRequiresApproval(Boolean.TRUE.equals(dto.getRequiresApproval()));
        entry.setEnabled(true);
        entry.setVersion(StringUtils.hasText(dto.getVersion()) ? dto.getVersion() : "1.0.0");
        entry.setTenantId("1");

        mapper.insert(entry);

        // 注册到 ToolRegistry
        try {
            HttpApiTool tool = buildToolFromEntry(entry);
            toolRegistry.register(tool);
            log.info("[ToolMarket] 工具注册成功: name={}", entry.getToolName());
        } catch (Exception e) {
            log.error("[ToolMarket] 工具注册到 ToolRegistry 失败: name={}, error={}",
                    entry.getToolName(), e.getMessage(), e);
        }

        return entry;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<ToolMarketEntryDO> registerFromOpenApi(String specUrl) {
        if (!StringUtils.hasText(specUrl)) {
            throw new BizException("OpenAPI 规范 URL 不能为空");
        }

        log.info("[ToolMarket] 开始从 OpenAPI 导入: url={}", specUrl);

        // 解析 OpenAPI 规范
        OpenApiSpecParser parser = new OpenApiSpecParser(objectMapper, null);
        List<ToolRegisterDTO> dtos;
        try {
            dtos = parser.parseFromUrl(specUrl);
        } catch (Exception e) {
            throw new BizException("OpenAPI 规范解析失败: " + e.getMessage());
        }

        if (dtos.isEmpty()) {
            log.warn("[ToolMarket] OpenAPI 规范中未找到可导入的操作");
            return List.of();
        }

        List<ToolMarketEntryDO> result = new ArrayList<>();
        for (ToolRegisterDTO dto : dtos) {
            try {
                // 检查是否已存在同名工具
                ToolMarketEntryDO existing = mapper.selectByToolName(dto.getToolName());
                if (existing != null) {
                    log.warn("[ToolMarket] 跳过已存在的工具: name={}", dto.getToolName());
                    continue;
                }

                ToolMarketEntryDO entry = new ToolMarketEntryDO();
                entry.setToolName(dto.getToolName());
                entry.setDisplayName(dto.getDisplayName());
                entry.setDescription(dto.getDescription());
                entry.setCategory(dto.getCategory());
                entry.setSourceType(SOURCE_OPENAPI);
                entry.setHttpMethod(dto.getHttpMethod().toUpperCase());
                entry.setEndpointUrl(dto.getEndpointUrl());
                entry.setParamSchema(toJson(dto.getParamSchema()));
                entry.setPathParams(toJson(dto.getPathParams()));
                entry.setQueryParams(toJson(dto.getQueryParams()));
                entry.setTimeoutMs(30000L);
                entry.setRequiresApproval(false);
                entry.setEnabled(true);
                entry.setVersion(dto.getVersion());
                entry.setOpenApiSpecUrl(specUrl);
                entry.setOpenApiOperationId(dto.getToolName());
                entry.setTenantId("1");

                mapper.insert(entry);

                // 注册到 ToolRegistry
                HttpApiTool tool = buildToolFromEntry(entry);
                toolRegistry.register(tool);

                result.add(entry);
                log.info("[ToolMarket] OpenAPI 工具导入成功: name={}", entry.getToolName());
            } catch (Exception e) {
                log.warn("[ToolMarket] OpenAPI 工具导入失败: name={}, error={}",
                        dto.getToolName(), e.getMessage());
            }
        }

        log.info("[ToolMarket] OpenAPI 导入完成: 成功={}, 总计={}", result.size(), dtos.size());
        return result;
    }

    // ==================== 注销 / 启停 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unregister(String toolName) {
        ToolMarketEntryDO entry = mapper.selectByToolName(toolName);
        if (entry == null) {
            throw new BizException("工具不存在: " + toolName);
        }

        // 从 ToolRegistry 移除
        toolRegistry.unregister(toolName);

        // 软删除
        mapper.deleteById(entry.getId());

        log.info("[ToolMarket] 工具已注销: name={}", toolName);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ToolMarketEntryDO enable(String toolName) {
        ToolMarketEntryDO entry = mapper.selectByToolName(toolName);
        if (entry == null) {
            throw new BizException("工具不存在: " + toolName);
        }

        entry.setEnabled(true);
        mapper.updateById(entry);

        // 注册到 ToolRegistry
        try {
            HttpApiTool tool = buildToolFromEntry(entry);
            toolRegistry.register(tool);
            log.info("[ToolMarket] 工具已启用: name={}", toolName);
        } catch (Exception e) {
            log.error("[ToolMarket] 工具启用失败: name={}, error={}", toolName, e.getMessage(), e);
            throw new BizException("工具启用失败: " + e.getMessage());
        }

        return entry;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ToolMarketEntryDO disable(String toolName) {
        ToolMarketEntryDO entry = mapper.selectByToolName(toolName);
        if (entry == null) {
            throw new BizException("工具不存在: " + toolName);
        }

        entry.setEnabled(false);
        mapper.updateById(entry);

        // 从 ToolRegistry 移除
        toolRegistry.unregister(toolName);

        log.info("[ToolMarket] 工具已禁用: name={}", toolName);
        return entry;
    }

    // ==================== 查询 ====================

    @Override
    public ToolMarketEntryDO getById(String id) {
        ToolMarketEntryDO entry = mapper.selectById(id);
        if (entry == null) {
            throw new BizException("工具不存在: " + id);
        }
        return entry;
    }

    @Override
    public PageResult<ToolMarketEntryDO> page(ToolMarketQueryDTO query) {
        LambdaQueryWrapper<ToolMarketEntryDO> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getToolName())) {
            wrapper.like(ToolMarketEntryDO::getToolName, query.getToolName());
        }
        if (StringUtils.hasText(query.getCategory())) {
            wrapper.eq(ToolMarketEntryDO::getCategory, query.getCategory());
        }
        if (StringUtils.hasText(query.getSourceType())) {
            wrapper.eq(ToolMarketEntryDO::getSourceType, query.getSourceType());
        }
        if (Objects.nonNull(query.getEnabled())) {
            wrapper.eq(ToolMarketEntryDO::getEnabled, query.getEnabled());
        }
        wrapper.orderByDesc(ToolMarketEntryDO::getCreatedAt);

        int pageNum = query.getPage() == null || query.getPage() < 1 ? 1 : query.getPage();
        int pageSize = query.getSize() == null || query.getSize() < 1 ? 20 : query.getSize();
        Page<ToolMarketEntryDO> page = new Page<>(pageNum, pageSize);
        return PageResult.ofPage(mapper.selectPage(page, wrapper));
    }

    // ==================== 测试 ====================

    @Override
    public ToolResult testTool(String toolName, Map<String, Object> parameters) {
        ToolMarketEntryDO entry = mapper.selectByToolName(toolName);
        if (entry == null) {
            throw new BizException("工具不存在: " + toolName);
        }

        HttpApiTool tool = buildToolFromEntry(entry);
        log.info("[ToolMarket] 测试工具调用: name={}, params={}", toolName, parameters);
        return tool.execute(parameters, null);
    }

    // ==================== 内部方法 ====================

    /**
     * 从持久化条目构建 HttpApiTool 实例。
     */
    private HttpApiTool buildToolFromEntry(ToolMarketEntryDO entry) {
        return HttpApiTool.builder()
                .toolName(entry.getToolName())
                .description(entry.getDescription())
                .httpMethod(entry.getHttpMethod())
                .endpointUrl(entry.getEndpointUrl())
                .headers(fromJson(entry.getHeaders(), new TypeReference<Map<String, String>>() {}))
                .paramSchema(fromJson(entry.getParamSchema(), new TypeReference<Map<String, Object>>() {}))
                .bodyTemplate(entry.getBodyTemplate())
                .pathParams(fromJson(entry.getPathParams(), new TypeReference<List<String>>() {}))
                .queryParams(fromJson(entry.getQueryParams(), new TypeReference<List<String>>() {}))
                .timeoutMs(entry.getTimeoutMs() != null ? entry.getTimeoutMs() : 30000L)
                .requiresApproval(Boolean.TRUE.equals(entry.getRequiresApproval()))
                .objectMapper(objectMapper)
                .build();
    }

    /**
     * 将对象序列化为 JSON 字符串。
     */
    private String toJson(Object obj) {
        if (obj == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            log.warn("[ToolMarket] JSON 序列化失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 将 JSON 字符串反序列化为指定类型。
     */
    private <T> T fromJson(String json, TypeReference<T> typeRef) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, typeRef);
        } catch (Exception e) {
            log.warn("[ToolMarket] JSON 反序列化失败: json={}, error={}", json, e.getMessage());
            return null;
        }
    }
}
