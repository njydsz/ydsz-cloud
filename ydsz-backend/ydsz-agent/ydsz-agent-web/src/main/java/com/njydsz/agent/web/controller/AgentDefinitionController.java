package com.njydsz.agent.web.controller;

import java.util.List;

import com.njydsz.common.safe.ratelimit.annotation.RateLimit;
import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.agent.domain.entity.AgentDefinitionDO;
import com.njydsz.agent.server.agent.AgentDefinitionService;
import com.njydsz.common.core.response.BaseResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.audit.enums.AuditType;
import com.njydsz.common.lock.annotation.Idempotent;
import com.njydsz.agent.domain.converter.AgentConverter;
import com.njydsz.agent.domain.dto.post.AgentDefinitionPostDTO;
import com.njydsz.agent.domain.dto.put.AgentDefinitionPutDTO;
import com.njydsz.agent.domain.vo.AgentDefinitionVO;
import com.njydsz.agent.domain.enums.AgentResultCode;

/**
 * Agent 定义管理 REST API Controller。
 *
 * <p>提供 Agent 定义的 CRUD 管理接口，是 Agent 能力扩展的配置入口：
 * <ul>
 *   <li>{@code GET /agent/definitions} - 列出所有活跃 Agent 定义</li>
 *   <li>{@code GET /agent/definitions/{id}} - 按主键 ID 获取 Agent 定义</li>
 *   <li>{@code GET /agent/definitions/code/{code}} - 按业务 code 获取 Agent 定义（推荐入口）</li>
 *   <li>{@code POST /agent/definitions} - 创建 Agent 定义</li>
 *   <li>{@code PUT /agent/definitions} - 更新 Agent 定义</li>
 *   <li>{@code DELETE /agent/definitions/{id}} - 删除 Agent 定义</li>
 * </ul>
 *
 * <h3>核心概念</h3>
 * <ul>
 *   <li>{@code AgentDefinition} - Agent 静态定义（system prompt / 工具列表 / 能力配置 / LLM 偏好）</li>
 *   <li>{@code agentCode} - 业务侧 Agent 唯一编码（如 {@code order-analysis-agent}）</li>
 *   <li>Agent 定义与执行实例解耦：定义存储为配置，运行时由 {@link AgentFactory} 按需加载</li>
 * </ul>
 *
 * <h3>安全与稳定性</h3>
 * <ul>
 *   <li>所有写操作均加 {@link Idempotent} 防重（5s TTL）</li>
 *   <li>所有写操作均加 {@link RateLimit} 限流（50 QPS）</li>
 *   <li>所有写操作均加 {@link Audit} 异步落库审计日志</li>
 *   <li>查询 {@code list} 仅返回 {@code isActive=true} 的活跃定义（{@code listActive} 内部过滤）</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/agent/definitions")
@RequiredArgsConstructor
public class AgentDefinitionController {

    /** Agent 定义服务（封装 DB CRUD + 业务校验） */
    private final AgentDefinitionService agentDefinitionService;

    /**
     * 列出所有活跃的 Agent 定义。
     *
     * <p>仅返回 {@code isActive=true} 的 Agent 定义，按业务约定排序（通常按 created_at 倒序）。
     * 供前端 Agent 列表页/下拉选择器使用。
     *
     * @return 统一响应结果，data 为 Agent 定义 VO 列表
     */
    @Audit(module = "Agent定义", type = AuditType.OPERATION, action = AuditAction.QUERY, content = "'list'")
    @GetMapping
    public BaseResponse<List<AgentDefinitionVO>> list() {
        return BaseResponse.success(AgentConverter.INSTANT.agentDefinitionListToVO(agentDefinitionService.listActive()));
    }

    /**
     * 按主键 ID 获取 Agent 定义详情。
     *
     * @param id Agent 定义主键（雪花算法字符串）
     * @return 统一响应结果，data 为 Agent 定义 VO；不存在时返回 error 响应
     */
    @Audit(module = "Agent定义", type = AuditType.OPERATION, action = AuditAction.QUERY, content = "'getById: ' + #id")
    @GetMapping("/{id}")
    public BaseResponse<AgentDefinitionVO> getById(@PathVariable String id) {
        AgentDefinitionDO entity = agentDefinitionService.getById(id);
        if (entity == null) {
            return BaseResponse.error(AgentResultCode.AGENT_NOT_FOUND, "Agent not found: " + id);
        }
        return BaseResponse.success(AgentConverter.INSTANT.entityToVO(entity));
    }

    /**
     * 按业务编码 code 获取 Agent 定义详情。
     *
     * <p>{@code code} 是 Agent 在业务侧的稳定标识（不随主键变化），推荐作为外部调用入口。
     * 与 {@link #getById} 的区别：code 是用户/调用方可见的语义编码，id 是系统内部的主键。
     *
     * @param code Agent 业务编码（全局唯一）
     * @return 统一响应结果，data 为 Agent 定义 VO；不存在时返回 error 响应
     */
    @Audit(module = "Agent定义", type = AuditType.OPERATION, action = AuditAction.QUERY, content = "'getByCode: ' + #code")
    @GetMapping("/code/{code}")
    public BaseResponse<AgentDefinitionVO> getByCode(@PathVariable String code) {
        AgentDefinitionDO entity = agentDefinitionService.getByCode(code);
        if (entity == null) {
            return BaseResponse.error(AgentResultCode.AGENT_NOT_FOUND, "Agent not found: " + code);
        }
        return BaseResponse.success(AgentConverter.INSTANT.entityToVO(entity));
    }

    /**
     * 创建 Agent 定义。
     *
     * <p>将 DTO 转换为 Entity 后持久化，返回创建后的完整 Agent 定义（带 id / created_at 等）。
     * 业务校验（如 code 唯一性、enabledTools 合法性）由 Service 层完成。
     *
     * @param dto Agent 定义创建请求体
     * @return 统一响应结果，data 为创建后的 Agent 定义 VO
     */
    @Audit(module = "Agent定义", type = AuditType.OPERATION, action = AuditAction.CREATE, content = "'create'")
    @Idempotent(key = "ydsz:agent:AgentDefinitionController:create:lock", ttlSeconds = 5)
    @RateLimit(resource = "agent.agentdefinition.create", threshold = 50)
    @PostMapping
    public BaseResponse<AgentDefinitionVO> create(@Valid @RequestBody AgentDefinitionPostDTO dto) {
        AgentDefinitionDO entity = AgentConverter.INSTANT.postDtoToEntity(dto);
        return BaseResponse.success(AgentConverter.INSTANT.entityToVO(agentDefinitionService.create(entity)));
    }

    /**
     * 更新 Agent 定义。
     *
     * <p>全量更新指定 ID 的 Agent 定义（PUT 语义），必须传入完整定义。
     * 局部更新场景建议用 PATCH 接口（暂未提供，必要时基于此方法扩展）。
     *
     * @param dto Agent 定义更新请求体（必须含 id）
     * @return 统一响应结果，data 为更新后的 Agent 定义 VO
     */
    @Audit(module = "Agent定义", type = AuditType.OPERATION, action = AuditAction.UPDATE, content = "'update'")
    @RateLimit(resource = "agent.agentdefinition.update", threshold = 50)
    @Idempotent(key = "ydsz:agent:AgentDefinitionController:update:lock", ttlSeconds = 5)
    @PutMapping
    public BaseResponse<AgentDefinitionVO> update(@Valid @RequestBody AgentDefinitionPutDTO dto) {
        AgentDefinitionDO entity = AgentConverter.INSTANT.putDtoToEntity(dto);
        return BaseResponse.success(AgentConverter.INSTANT.entityToVO(agentDefinitionService.update(entity)));
    }

    /**
     * 删除 Agent 定义（逻辑删除）。
     *
     * <p>调用 {@link AgentDefinitionService#removeById} 删除指定 ID 的 Agent 定义；
     * 是否为物理删除由 Service 层决定（推荐使用逻辑删除 + 状态字段标记）。
     *
     * @param id Agent 定义主键
     * @return 统一响应结果，data 为 true 表示删除成功，false 表示记录不存在
     */
    @Audit(module = "Agent定义", type = AuditType.OPERATION, action = AuditAction.DELETE, content = "'delete'")
    @Idempotent(key = "ydsz:agent:AgentDefinitionController:delete:lock", ttlSeconds = 5)
    @RateLimit(resource = "agent.agentdefinition.delete", threshold = 50)
    @DeleteMapping("/{id}")
    public BaseResponse<Boolean> delete(@PathVariable String id) {
        return BaseResponse.success(agentDefinitionService.removeById(id));
    }
}
