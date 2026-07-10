package com.njydsz.pmis.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.agent.dto.tool.PromptTemplateCreateDTO;
import com.njydsz.pmis.agent.dto.tool.PromptTemplateQueryDTO;
import com.njydsz.pmis.agent.engine.prompt.PromptTemplateRegistry;
import com.njydsz.pmis.agent.entity.agent.AgentPromptTemplateDO;
import com.njydsz.pmis.agent.mapper.agent.AgentPromptTemplateMapper;
import com.njydsz.pmis.agent.service.tool.PromptTemplateService;
import com.njydsz.pmis.common.api.PageResult;
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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AgentPromptTemplateDO activate(String id) {
        AgentPromptTemplateDO template = mapper.selectById(id);
        if (template == null) {
            throw new BizException("模板不存在: " + id);
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

    @Override
    public AgentPromptTemplateDO getById(String id) {
        return mapper.selectById(id);
    }

    @Override
    public PageResult<AgentPromptTemplateDO> page(PromptTemplateQueryDTO query) {
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
        return PageResult.ofPage(mapper.selectPage(page, wrapper));
    }

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
