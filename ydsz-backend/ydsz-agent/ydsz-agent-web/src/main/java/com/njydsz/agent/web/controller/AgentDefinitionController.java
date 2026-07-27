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

import com.njydsz.agent.domain.agent.AgentDefinition;
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
import com.njydsz.agent.domain.dto.post.AgentDefinitionDOPostDTO;
import com.njydsz.agent.domain.dto.put.AgentDefinitionDOPutDTO;
import com.njydsz.agent.domain.vo.AgentDefinitionDOVO;
import com.njydsz.agent.domain.vo.AgentDefinitionVO;

/**
 * Agent 定义 REST API
 *
 * <p>提供 Agent 定义的 CRUD 管理接口：
 * <ul>
 *   <li>{@code GET /agent/definitions} — 列出所有活跃 Agent 定义</li>
 *   <li>{@code GET /agent/definitions/{id}} — 获取单个 Agent 定义</li>
 *   <li>{@code GET /agent/definitions/code/{code}} — 按 code 获取 Agent 定义</li>
 *   <li>{@code POST /agent/definitions} — 创建 Agent 定义</li>
 *   <li>{@code PUT /agent/definitions} — 更新 Agent 定义</li>
 *   <li>{@code DELETE /agent/definitions/{id}} — 删除 Agent 定义</li>
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

    /** Agent 定义服务 */
    private final AgentDefinitionService agentDefinitionService;

    @GetMapping
    public BaseResponse<List<AgentDefinitionDOVO>> list() {
        return BaseResponse.success(AgentConverter.INSTANT.agentDefinitionListToVO(agentDefinitionService.listActive()));
    }

    @GetMapping("/{id}")
    public BaseResponse<AgentDefinitionDOVO> getById(@PathVariable String id) {
        AgentDefinitionDO entity = agentDefinitionService.getById(id);
        if (entity == null) {
            return BaseResponse.error("Agent not found: " + id);
        }
        return BaseResponse.success(entity);
    }

    @GetMapping("/code/{code}")
    public BaseResponse<AgentDefinitionVO> getByCode(@PathVariable String code) {
        AgentDefinitionDO entity = agentDefinitionService.getByCode(code);
        if (entity == null) {
            return BaseResponse.error("Agent not found: " + code);
        }
        return BaseResponse.success(agentDefinitionService.toDomain(entity));
    }

    @Audit(module = "Agent定义", type = AuditType.OPERATION, action = AuditAction.CREATE, content = "'create'")
    @Idempotent(key = "ydsz:agent:AgentDefinitionController:create:lock", ttlSeconds = 5)
    @RateLimit(resource = "agent.agentdefinition.create", threshold = 50)
    @PostMapping
    public BaseResponse<AgentDefinitionDOVO> create(@Valid @RequestBody AgentDefinitionDOPostDTO dto) {
        AgentDefinitionDO entity = AgentConverter.INSTANT.postDtoToEntity(dto);
        return BaseResponse.success(AgentConverter.INSTANT.entityToVO(agentDefinitionService.create(entity)));
    }

    @Audit(module = "Agent定义", type = AuditType.OPERATION, action = AuditAction.UPDATE, content = "'update'")
    @RateLimit(resource = "agent.agentdefinition.update", threshold = 50)
    @Idempotent(key = "ydsz:agent:AgentDefinitionController:update:lock", ttlSeconds = 5)
    @PutMapping
    public BaseResponse<AgentDefinitionDOVO> update(@Valid @RequestBody AgentDefinitionDOPutDTO dto) {
        AgentDefinitionDO entity = AgentConverter.INSTANT.putDtoToEntity(dto);
        return BaseResponse.success(AgentConverter.INSTANT.entityToVO(agentDefinitionService.update(entity)));
    }

    @Audit(module = "Agent定义", type = AuditType.OPERATION, action = AuditAction.DELETE, content = "'delete'")
    @Idempotent(key = "ydsz:agent:AgentDefinitionController:delete:lock", ttlSeconds = 5)
    @RateLimit(resource = "agent.agentdefinition.delete", threshold = 50)
    @DeleteMapping("/{id}")
    public BaseResponse<Boolean> delete(@PathVariable String id) {
        return BaseResponse.success(agentDefinitionService.removeById(id));
    }
}
