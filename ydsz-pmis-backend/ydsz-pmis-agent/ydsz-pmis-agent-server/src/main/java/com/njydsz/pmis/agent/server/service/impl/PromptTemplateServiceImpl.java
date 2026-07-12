package com.njydsz.pmis.agent.server.service.impl.tool;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.agent.domain.dto.tool.PromptTemplateCreateDTO;
import com.njydsz.pmis.agent.domain.dto.tool.PromptTemplateQueryDTO;
import com.njydsz.pmis.agent.server.engine.prompt.PromptTemplateRegistry;
import com.njydsz.pmis.agent.domain.entity.agent.AgentPromptTemplateDO;
import com.njydsz.pmis.agent.infra.mapper.agent.AgentPromptTemplateMapper;
import com.njydsz.pmis.agent.server.service.tool.PromptTemplateService;
import com.njydsz.pmis.common.core.response.StandardResultCode;
import com.njydsz.pmis.common.core.response.PageResponse;
import com.njydsz.pmis.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Objects;

/**
 * Prompt 模板管理服务实现（P2-2 落地）。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P2-2)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PromptTemplateServiceImpl implements PromptTemplateService {

    /** Prompt 模板 Mapper（CRUD） */
    private final AgentPromptTemplateMapper mapper;
    /** Prompt 模板注册中心（内存缓存 + 热刷新） */
    private final PromptTemplateRegistry registry;

    /**
     * 创建 Prompt 模板（默认非生效，需手动激活）
     *
     * @param dto 模板创建参数（code、name、agentType、role、content 等）
     * @return 落库后的模板实体
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public AgentPromptTemplateDO create(PromptTemplateCreateDTO dto) {
        AgentPromptTemplateDO entity = new AgentPromptTemplateDO();
        entity.setTemplateCode(dto.getTemplateCode());
        entity.setTemplateName(dto.getTemplateName());
        entity.setAgentType(dto.getAgentType());
        entity.setPromptRole(dto.getPromptRole());
        entity.setContent(dto.getContent());
        entity.setVersion(StringUtils.hasText(dto.getVersion()) ? dto.getVersion() : "1.0.0");
        entity.setIsActive(false);
        entity.setDescription(dto.getDescription());
        entity.setTenantId("1");
        mapper.insert(entity);
        log.info("[PromptTemplate] 创建模板: code={} version={}", dto.getTemplateCode(), entity.getVersion());
        return entity;
    }

    /**
     * 激活指定模板版本（同 code 的其他版本自动失效）
     *
     * <p>激活流程：
     * <ol>
     *   <li>排他 deactivate：同 templateCode 的其他版本置为 isActive=false</li>
     *   <li>激活当前版本 isActive=true</li>
     *   <li>刷新 PromptTemplateRegistry 缓存</li>
     * </ol>
     *
     * @param id 模板 ID
     * @return 激活后的模板实体
     * @throws BizException 模板不存在时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public AgentPromptTemplateDO activate(String id) {
        AgentPromptTemplateDO template = mapper.selectById(id);
        if (template == null) {
            throw new BizException(StandardResultCode.NOT_FOUND, "模板不存在: " + id);
        }
        // 排他：同 code 的其他版本置为非生效
        mapper.deactivateOthers(template.getTemplateCode(), id);
        // 激活当前版本
        template.setIsActive(true);
        mapper.updateById(template);
        // 刷新注册中心缓存
        registry.refresh();
        log.info("[PromptTemplate] 激活模板: code={} version={}", template.getTemplateCode(), template.getVersion());
        return template;
    }

    /**
     * 根据 ID 查询模板详情
     *
     * @param id 模板 ID
     * @return 模板实体；不存在返回 null
     */
    @Override
    public AgentPromptTemplateDO getById(String id) {
        return mapper.selectById(id);
    }

    /**
     * 分页查询模板列表
     *
     * <p>支持按 templateCode（LIKE）、agentType、promptRole、isActive 过滤，
     * 按创建时间倒序排列。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    @Override
    public PageResponse<AgentPromptTemplateDO> page(PromptTemplateQueryDTO query) {
        LambdaQueryWrapper<AgentPromptTemplateDO> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getTemplateCode())) {
            wrapper.like(AgentPromptTemplateDO::getTemplateCode, query.getTemplateCode());
        }
        if (StringUtils.hasText(query.getAgentType())) {
            wrapper.eq(AgentPromptTemplateDO::getAgentType, query.getAgentType());
        }
        if (StringUtils.hasText(query.getPromptRole())) {
            wrapper.eq(AgentPromptTemplateDO::getPromptRole, query.getPromptRole());
        }
        if (Objects.nonNull(query.getIsActive())) {
            wrapper.eq(AgentPromptTemplateDO::getIsActive, query.getIsActive());
        }
        wrapper.orderByDesc(AgentPromptTemplateDO::getCreatedAt);

        int pageNum = query.getPage() == null || query.getPage() < 1 ? 1 : query.getPage();
        int pageSize = query.getSize() == null || query.getSize() < 1 ? 20 : query.getSize();
        Page<AgentPromptTemplateDO> page = new Page<>(pageNum, pageSize);
        return PageResponse.ofPage(mapper.selectPage(page, wrapper));
    }

    /**
     * 删除模板（软删除）
     *
     * <p>若删除的是当前生效模板，删除后刷新注册中心缓存以降级为内置默认模板。
     *
     * @param id 模板 ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String id) {
        AgentPromptTemplateDO template = mapper.selectById(id);
        if (template == null) {
            return;
        }
        mapper.deleteById(id);
        // 若删除的是生效模板，刷新缓存以降级为内置默认
        if (Boolean.TRUE.equals(template.getIsActive())) {
            registry.refresh();
        }
        log.info("[PromptTemplate] 删除模板: id={} code={}", id, template.getTemplateCode());
    }
}
