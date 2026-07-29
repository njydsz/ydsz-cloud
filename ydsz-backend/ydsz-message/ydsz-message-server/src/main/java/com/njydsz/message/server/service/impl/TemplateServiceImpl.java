package com.njydsz.message.server.service.impl.template;

import java.time.LocalDateTime;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.common.core.constant.PageConstants;
import com.njydsz.common.core.code.BaseResultCode;
import com.njydsz.common.exception.custom.SysException;
import com.njydsz.common.security.TenantContext;
import com.njydsz.message.domain.constant.MessageConstants;
import com.njydsz.message.domain.dto.template.TemplateAuditDTO;
import com.njydsz.message.domain.dto.template.TemplateCreateDTO;
import com.njydsz.message.domain.dto.template.TemplateQueryDTO;
import com.njydsz.message.domain.entity.template.MsgTemplate;
import com.njydsz.message.domain.enums.template.TemplateAuditStatusEnum;
import com.njydsz.message.infra.mapper.template.MsgTemplateMapper;
import com.njydsz.message.server.service.template.TemplateService;
import com.njydsz.message.server.template.TemplateEngine;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 消息模板服务实现。
 *
 * <p>维护消息模板 ({@code ydsz_msg_template} / {@code ydsz_msg_template_version})：
 *
 * <p>按 (templateCode, channel, locale, tenantId) 唯一；
 *
 * <p>支持审核流转 DRAFT → AUDITING → APPROVED/REJECTED，发布后版本化；
 *
 * <p>变量支持 ${var} 嵌套替换与缺省值回退；locale 加载支持精确 + 默认 zh-CN 回退。
 *
 * @author ydsz-team
 * @since 1.0.0
 */

@Slf4j
@Service
@RequiredArgsConstructor
public class TemplateServiceImpl implements TemplateService {

    /** 消息模板 Mapper（CRUD / locale 回退查询） */
    private final MsgTemplateMapper msgTemplateMapper;

    /** 模板引擎（变量渲染） */
    private final TemplateEngine templateEngine;

    @Override
    public MsgTemplate create(TemplateCreateDTO dto) {
        if (dto == null) {
            throw new SysException(BaseResultCode.BAD_REQUEST, "模板参数不能为空");
        }
        if (!StringUtils.hasText(dto.getTemplateCode()) || !StringUtils.hasText(dto.getChannel())) {
            throw new SysException(BaseResultCode.BAD_REQUEST, "模板编码与通道不能为空");
        }
        String tenantId = TenantContext.getTenantId();
        String locale = StringUtils.hasText(dto.getLocale()) ? dto.getLocale() : MessageConstants.DEFAULT_LOCALE;
        // 唯一性校验 (templateCode, channel, locale, tenantId)
        MsgTemplate existing = msgTemplateMapper.selectOne(new LambdaQueryWrapper<MsgTemplate>()
                .eq(MsgTemplate::getTemplateCode, dto.getTemplateCode())
                .eq(MsgTemplate::getChannel, dto.getChannel())
                .eq(MsgTemplate::getLocale, locale)
                .eq(MsgTemplate::getTenantId, tenantId)
                .last("LIMIT 1"));
        if (existing != null) {
            throw new SysException(BaseResultCode.DUPLICATE_KEY, "模板已存在: " + dto.getTemplateCode() + "/" + locale);
        }
        MsgTemplate entity = new MsgTemplate();
        entity.setTemplateCode(dto.getTemplateCode());
        entity.setChannel(dto.getChannel());
        entity.setLocale(locale);
        entity.setVersion(dto.getVersion());
        entity.setCategory(dto.getCategory());
        entity.setSceneCode(dto.getSceneCode());
        entity.setSubject(dto.getSubject());
        entity.setContent(dto.getContent());
        entity.setProvider(dto.getProvider());
        entity.setProviderKey(dto.getProviderKey());
        entity.setSignName(dto.getSignName());
        entity.setStatus("ENABLED");
        entity.setAuditStatus(TemplateAuditStatusEnum.DRAFT.name());
        entity.setDescription(dto.getDescription());
        entity.setTenantId(tenantId);
        msgTemplateMapper.insert(entity);
        log.info("[Template] 创建模板: code={} channel={} locale={}", dto.getTemplateCode(), dto.getChannel(), locale);
        return entity;
    }

    @Override
    public MsgTemplate update(String id, TemplateCreateDTO dto) {
        if (!StringUtils.hasText(id)) {
            throw new SysException(BaseResultCode.BAD_REQUEST, "模板 ID 不能为空");
        }
        MsgTemplate entity = getById(id);
        if (dto == null) {
            throw new SysException(BaseResultCode.BAD_REQUEST, "模板参数不能为空");
        }
        if (StringUtils.hasText(dto.getLocale())) {
            entity.setLocale(dto.getLocale());
        }
        if (StringUtils.hasText(dto.getVersion())) {
            entity.setVersion(dto.getVersion());
        }
        if (StringUtils.hasText(dto.getCategory())) {
            entity.setCategory(dto.getCategory());
        }
        if (dto.getSceneCode() != null) {
            entity.setSceneCode(dto.getSceneCode());
        }
        if (dto.getSubject() != null) {
            entity.setSubject(dto.getSubject());
        }
        if (dto.getContent() != null) {
            entity.setContent(dto.getContent());
        }
        if (dto.getProvider() != null) {
            entity.setProvider(dto.getProvider());
        }
        if (dto.getProviderKey() != null) {
            entity.setProviderKey(dto.getProviderKey());
        }
        if (dto.getSignName() != null) {
            entity.setSignName(dto.getSignName());
        }
        if (dto.getDescription() != null) {
            entity.setDescription(dto.getDescription());
        }
        msgTemplateMapper.updateById(entity);
        return entity;
    }

    @Override
    public void delete(String id) {
        if (!StringUtils.hasText(id)) {
            throw new SysException(BaseResultCode.BAD_REQUEST, "模板 ID 不能为空");
        }
        msgTemplateMapper.deleteById(id);
    }

    @Override
    public MsgTemplate getById(String id) {
        if (!StringUtils.hasText(id)) {
            throw new SysException(BaseResultCode.BAD_REQUEST, "模板 ID 不能为空");
        }
        MsgTemplate entity = msgTemplateMapper.selectById(id);
        if (entity == null) {
            throw new SysException(BaseResultCode.NOT_FOUND, "模板不存在: " + id);
        }
        return entity;
    }

    @Override
    public Page<MsgTemplate> page(TemplateQueryDTO query) {
        Page<MsgTemplate> page = new Page<>(
                query == null ? 1 : query.getPageNum(),
                Math.min(query == null ? 10 : query.getPageSize(), PageConstants.getMaxPageSize()));
        LambdaQueryWrapper<MsgTemplate> w = new LambdaQueryWrapper<>();
        if (query != null) {
            w.eq(StringUtils.hasText(query.getTemplateCode()), MsgTemplate::getTemplateCode, query.getTemplateCode());
            w.eq(StringUtils.hasText(query.getChannel()), MsgTemplate::getChannel, query.getChannel());
            w.eq(StringUtils.hasText(query.getLocale()), MsgTemplate::getLocale, query.getLocale());
            w.eq(StringUtils.hasText(query.getStatus()), MsgTemplate::getStatus, query.getStatus());
            w.eq(StringUtils.hasText(query.getAuditStatus()), MsgTemplate::getAuditStatus, query.getAuditStatus());
            w.eq(StringUtils.hasText(query.getCategory()), MsgTemplate::getCategory, query.getCategory());
            w.eq(StringUtils.hasText(query.getSceneCode()), MsgTemplate::getSceneCode, query.getSceneCode());
        }
        w.orderByDesc(MsgTemplate::getCreatedAt);
        return msgTemplateMapper.selectPage(page, w);
    }

    @Override
    public MsgTemplate loadByCodeAndChannel(String templateCode, String channel, String locale, String tenantId) {
        if (!StringUtils.hasText(templateCode) || !StringUtils.hasText(channel)) {
            throw new SysException(BaseResultCode.BAD_REQUEST, "模板编码与通道不能为空");
        }
        String tid = StringUtils.hasText(tenantId) ? tenantId : TenantContext.getTenantId();
        String loc = StringUtils.hasText(locale) ? locale : MessageConstants.DEFAULT_LOCALE;
        // 精确 locale
        MsgTemplate entity = msgTemplateMapper.selectOne(new LambdaQueryWrapper<MsgTemplate>()
                .eq(MsgTemplate::getTemplateCode, templateCode)
                .eq(MsgTemplate::getChannel, channel)
                .eq(MsgTemplate::getLocale, loc)
                .eq(MsgTemplate::getTenantId, tid)
                .eq(MsgTemplate::getStatus, "ENABLED")
                .last("LIMIT 1"));
        if (entity != null) {
            return entity;
        }
        // 回退默认 zh-CN
        if (!MessageConstants.DEFAULT_LOCALE.equals(loc)) {
            entity = msgTemplateMapper.selectOne(new LambdaQueryWrapper<MsgTemplate>()
                    .eq(MsgTemplate::getTemplateCode, templateCode)
                    .eq(MsgTemplate::getChannel, channel)
                    .eq(MsgTemplate::getLocale, MessageConstants.DEFAULT_LOCALE)
                    .eq(MsgTemplate::getTenantId, tid)
                    .eq(MsgTemplate::getStatus, "ENABLED")
                    .last("LIMIT 1"));
        }
        return entity;
    }

    @Override
    public void audit(String id, TemplateAuditDTO dto) {
        if (dto == null || !StringUtils.hasText(dto.getAuditStatus())) {
            throw new SysException(BaseResultCode.BAD_REQUEST, "审核状态不能为空");
        }
        MsgTemplate entity = getById(id);
        TemplateAuditStatusEnum current = parseAuditStatus(entity.getAuditStatus());
        TemplateAuditStatusEnum target = parseAuditStatus(dto.getAuditStatus());
        if (!canTransitAudit(current, target)) {
            throw new SysException(BaseResultCode.BIZ_ERROR,
                    "非法审核状态流转: " + current + " -> " + target);
        }
        entity.setAuditStatus(target.name());
        entity.setAuditRemark(dto.getAuditRemark());
        // APPROVED 时同步启用状态
        if (target == TemplateAuditStatusEnum.APPROVED) {
            entity.setStatus("ENABLED");
        } else if (target == TemplateAuditStatusEnum.REJECTED) {
            entity.setStatus("DISABLED");
        }
        entity.setAuditAt(LocalDateTime.now());
        msgTemplateMapper.updateById(entity);
        log.info("[Template] 审核模板: id={} {} -> {}", id, current, target);
    }

    @Override
    public String preview(String templateCode, String channel, Map<String, Object> params, String locale, String tenantId) {
        if (!StringUtils.hasText(templateCode) || !StringUtils.hasText(channel)) {
            throw new SysException(BaseResultCode.BAD_REQUEST, "模板编码与通道不能为空");
        }
        MsgTemplate template = loadByCodeAndChannel(templateCode, channel, locale, tenantId);
        if (template == null) {
            throw new SysException(BaseResultCode.NOT_FOUND, "模板不存在: " + templateCode + "/" + channel);
        }
        return templateEngine.render(template.getContent(), params);
    }

    /**
     * 校验审核状态流转合法性：DRAFT → AUDITING → APPROVED/REJECTED。
     *
     * @param current 当前状态
     * @param target  目标状态
     * @return true 表示允许流转
     */
    private boolean canTransitAudit(TemplateAuditStatusEnum current, TemplateAuditStatusEnum target) {
        if (current == target) {
            return true;
        }
        return switch (current) {
            case DRAFT -> target == TemplateAuditStatusEnum.AUDITING || target == TemplateAuditStatusEnum.APPROVED
                    || target == TemplateAuditStatusEnum.REJECTED;
            case AUDITING -> target == TemplateAuditStatusEnum.APPROVED || target == TemplateAuditStatusEnum.REJECTED;
            case APPROVED, REJECTED -> false;
        };
    }

    private TemplateAuditStatusEnum parseAuditStatus(String value) {
        try {
            return TemplateAuditStatusEnum.valueOf(value);
        } catch (Exception e) {
            throw new SysException(BaseResultCode.BIZ_ERROR, "非法审核状态: " + value);
        }
    }
}
