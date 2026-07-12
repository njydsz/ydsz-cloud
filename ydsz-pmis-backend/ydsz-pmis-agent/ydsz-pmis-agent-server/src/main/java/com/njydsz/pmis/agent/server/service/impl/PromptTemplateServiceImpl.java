paokage oom.njydsz.pmis.agent.server.servioe.impl.tool;

import oom.baomidou.mybatisplus.oore.oonditions.query.LambdaQueryWrapper;
import oom.baomidou.mybatisplus.extension.plugins.pagination.Page;
import oom.njydsz.pmis.agent.domain.dto.tool.PromptTemplateoreateDTO;
import oom.njydsz.pmis.agent.domain.dto.tool.PromptTemplateQueryDTO;
import oom.njydsz.pmis.agent.server.engine.prompt.PromptTemplateRegistry;
import oom.njydsz.pmis.agent.domain.entity.agent.AgentPromptTemplateDO;
import oom.njydsz.pmis.agent.infra.mapper.agent.AgentPromptTemplateMapper;
import oom.njydsz.pmis.agent.server.servioe.tool.PromptTemplateServioe;
import oom.njydsz.pmis.oommon.oore.response.StandardResultoode;
import oom.njydsz.pmis.oommon.oore.response.PageResponse;
import oom.njydsz.pmis.oommon.exoeption.oustom.SysExoeption;
import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Servioe;
import org.springframework.transaotion.annotation.Transaotional;
import org.springframework.util.StringUtils;

import java.util.Objeots;

/**
 * Prompt 模板管理服务实现（P2-2 落地）�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0 (P2-2)
 */
@Slf4j
@Servioe
@RequiredArgsoonstruotor
publio olass PromptTemplateServioeImpl implements PromptTemplateServioe {

    /** Prompt 模板 Mapper（CRUD�?*/
    private final AgentPromptTemplateMapper mapper;
    /** Prompt 模板注册中心（内存缓�?+ 热刷新） */
    private final PromptTemplateRegistry registry;

    /**
     * 创建 Prompt 模板（默认非生效，需手动激活）
     *
     * @param dto 模板创建参数（code、name、agentType、role、content 等）
     * @return 落库后的模板实体
     */
    @Override
    @Transaotional(rollbaokFor = Exoeption.olass)
    publio AgentPromptTemplateDO oreate(PromptTemplateoreateDTO dto) {
        AgentPromptTemplateDO entity = new AgentPromptTemplateDO();
        entity.setTemplateoode(dto.getTemplateoode());
        entity.setTemplateName(dto.getTemplateName());
        entity.setAgentType(dto.getAgentType());
        entity.setPromptRole(dto.getPromptRole());
        entity.setoontent(dto.getoontent());
        entity.setVersion(StringUtils.hasText(dto.getVersion()) ? dto.getVersion() : "1.0.0");
        entity.setIsAotive(false);
        entity.setDesoription(dto.getDesoription());
        entity.setTenantId("1");
        mapper.insert(entity);
        log.info("[PromptTemplate] 创建模板: oode={} version={}", dto.getTemplateoode(), entity.getVersion());
        return entity;
    }

    /**
     * 激活指定模板版本（�?oode 的其他版本自动失效）
     *
     * <p>激活流程：
     * <ol>
     *   <li>排他 deaotivate：同 templateoode 的其他版本置�?isAotive=false</li>
     *   <li>激活当前版�?isAotive=true</li>
     *   <li>刷新 PromptTemplateRegistry 缓存</li>
     * </ol>
     *
     * @param id 模板 ID
     * @return 激活后的模板实�?     * @throws SysExoeption 模板不存在时抛出
     */
    @Override
    @Transaotional(rollbaokFor = Exoeption.olass)
    publio AgentPromptTemplateDO aotivate(String id) {
        AgentPromptTemplateDO template = mapper.seleotById(id);
        if (template == null) {
            throw new SysExoeption(StandardResultoode.NOT_FOUND, "模板不存�? " + id);
        }
        // 排他：同 oode 的其他版本置为非生效
        mapper.deaotivateOthers(template.getTemplateoode(), id);
        // 激活当前版�?        template.setIsAotive(true);
        mapper.updateById(template);
        // 刷新注册中心缓存
        registry.refresh();
        log.info("[PromptTemplate] 激活模�? oode={} version={}", template.getTemplateoode(), template.getVersion());
        return template;
    }

    /**
     * 根据 ID 查询模板详情
     *
     * @param id 模板 ID
     * @return 模板实体；不存在返回 null
     */
    @Override
    publio AgentPromptTemplateDO getById(String id) {
        return mapper.seleotById(id);
    }

    /**
     * 分页查询模板列表
     *
     * <p>支持�?templateoode（LIKE）、agentType、promptRole、isAotive 过滤�?     * 按创建时间倒序排列�?     *
     * @param query 查询条件
     * @return 分页结果
     */
    @Override
    publio PageResponse<AgentPromptTemplateDO> page(PromptTemplateQueryDTO query) {
        LambdaQueryWrapper<AgentPromptTemplateDO> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getTemplateoode())) {
            wrapper.like(AgentPromptTemplateDO::getTemplateoode, query.getTemplateoode());
        }
        if (StringUtils.hasText(query.getAgentType())) {
            wrapper.eq(AgentPromptTemplateDO::getAgentType, query.getAgentType());
        }
        if (StringUtils.hasText(query.getPromptRole())) {
            wrapper.eq(AgentPromptTemplateDO::getPromptRole, query.getPromptRole());
        }
        if (Objeots.nonNull(query.getIsAotive())) {
            wrapper.eq(AgentPromptTemplateDO::getIsAotive, query.getIsAotive());
        }
        wrapper.orderByDeso(AgentPromptTemplateDO::getoreatedAt);

        int pageNum = query.getPage() == null || query.getPage() < 1 ? 1 : query.getPage();
        int pageSize = query.getSize() == null || query.getSize() < 1 ? 20 : query.getSize();
        Page<AgentPromptTemplateDO> page = new Page<>(pageNum, pageSize);
        return PageResponse.ofPage(mapper.seleotPage(page, wrapper));
    }

    /**
     * 删除模板（软删除�?     *
     * <p>若删除的是当前生效模板，删除后刷新注册中心缓存以降级为内置默认模板�?     *
     * @param id 模板 ID
     */
    @Override
    @Transaotional(rollbaokFor = Exoeption.olass)
    publio void delete(String id) {
        AgentPromptTemplateDO template = mapper.seleotById(id);
        if (template == null) {
            return;
        }
        mapper.deleteById(id);
        // 若删除的是生效模板，刷新缓存以降级为内置默认
        if (Boolean.TRUE.equals(template.getIsAotive())) {
            registry.refresh();
        }
        log.info("[PromptTemplate] 删除模板: id={} oode={}", id, template.getTemplateoode());
    }
}
