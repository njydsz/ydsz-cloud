package com.njydsz.pmis.message.server.service.impl.template;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.core.constant.PageConstants;
import com.njydsz.pmis.common.core.response.BaseResultCode;
import com.njydsz.pmis.common.exception.custom.SysException;
import com.njydsz.pmis.common.security.TenantContext;
import com.njydsz.pmis.message.domain.constant.MessageConstants;
import com.njydsz.pmis.message.domain.dto.template.TemplateAuditDTO;
import com.njydsz.pmis.message.domain.dto.template.TemplateCreateDTO;
import com.njydsz.pmis.message.domain.dto.template.TemplateQueryDTO;
import com.njydsz.pmis.message.domain.entity.template.MsgTemplateDO;
import com.njydsz.pmis.message.domain.enums.template.TemplateAuditStatusEnum;
import com.njydsz.pmis.message.infra.mapper.template.MsgTemplateMapper;
import com.njydsz.pmis.message.server.service.template.TemplateService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 消息模板服务实现。
 *
 * <p>模板按 (templateCode, channel, locale, tenantId) 唯一；locale 加载支持精确回退默认 zh-CN；
 * 审核状态流转 DRAFT → AUDITING → APPROVED/REJECTED。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TemplateServiceImpl implements TemplateService {

    /** 消息模板 Mapper（CRUD / locale 回退查询） */
    private final MsgTemplateMapper msgTemplateMapper;

    @Override
    public MsgTemplateDO create(TemplateCreateDTO dto) {
        if (dto == null) {
            throw new SysException(BaseResultCode.BAD_REQUEST, "模板参数不能为空");
        }
        if (!StringUtils.hasText(dto.getTemplateCode()) || !StringUtils.hasText(dto.getChannel())) {
            throw new SysException(BaseResultCode.BAD_REQUEST, "模板编码与通道不能为空");
        }
        String tenantId = TenantContext.getTenantId();
        String locale = StringUtils.hasText(dto.getLocale()) ? dto.getLocale() : MessageConstants.DEFAULT_LOCALE;
        // 唯一性校验 (templateCode, channel, locale, tenantId)
        MsgTemplateDO existing = msgTemplateMapper.selectOne(new LambdaQueryWrapper<MsgTemplateDO>()
                .eq(MsgTemplateDO::getTemplateCode, dto.getTemplateCode())
                .eq(MsgTemplateDO::getChannel, dto.getChannel())
                .eq(MsgTemplateDO::getLocale, locale)
                .eq(MsgTemplateDO::getTenantId, tenantId)
                .last("LIMIT 1"));
        if (existing != null) {
            throw new SysException(BaseResultCode.DUPLICATE_KEY, "模板已存在: " + dto.getTemplateCode() + "/" + locale);
        }
        MsgTemplateDO entity = new MsgTemplateDO();
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
    public MsgTemplateDO update(String id, TemplateCreateDTO dto) {
        if (!StringUtils.hasText(id)) {
            throw new SysException(BaseResultCode.BAD_REQUEST, "模板 ID 不能为空");
        }
        MsgTemplateDO entity = getById(id);
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
    public MsgTemplateDO getById(String id) {
        if (!StringUtils.hasText(id)) {
            throw new SysException(BaseResultCode.BAD_REQUEST, "模板 ID 不能为空");
        }
        MsgTemplateDO entity = msgTemplateMapper.selectById(id);
        if (entity == null) {
            throw new SysException(BaseResultCode.NOT_FOUND, "模板不存在: " + id);
        }
        return entity;
    }

    @Override
    public Page<MsgTemplateDO> page(TemplateQueryDTO query) {
        Page<MsgTemplateDO> page = new Page<>(
                query == null ? 1 : query.getPageNum(),
                Math.min(query == null ? 10 : query.getPageSize(), PageConstants.MAX_PAGE_SIZE));
        LambdaQueryWrapper<MsgTemplateDO> w = new LambdaQueryWrapper<>();
        if (query != null) {
            w.eq(StringUtils.hasText(query.getTemplateCode()), MsgTemplateDO::getTemplateCode, query.getTemplateCode());
            w.eq(StringUtils.hasText(query.getChannel()), MsgTemplateDO::getChannel, query.getChannel());
            w.eq(StringUtils.hasText(query.getLocale()), MsgTemplateDO::getLocale, query.getLocale());
            w.eq(StringUtils.hasText(query.getStatus()), MsgTemplateDO::getStatus, query.getStatus());
            w.eq(StringUtils.hasText(query.getAuditStatus()), MsgTemplateDO::getAuditStatus, query.getAuditStatus());
            w.eq(StringUtils.hasText(query.getCategory()), MsgTemplateDO::getCategory, query.getCategory());
            w.eq(StringUtils.hasText(query.getSceneCode()), MsgTemplateDO::getSceneCode, query.getSceneCode());
        }
        w.orderByDesc(MsgTemplateDO::getCreatedAt);
        return msgTemplateMapper.selectPage(page, w);
    }

    @Override
    public MsgTemplateDO loadByCodeAndChannel(String templateCode, String channel, String locale, String tenantId) {
        if (!StringUtils.hasText(templateCode) || !StringUtils.hasText(channel)) {
            throw new SysException(BaseResultCode.BAD_REQUEST, "模板编码与通道不能为空");
        }
        String tid = StringUtils.hasText(tenantId) ? tenantId : TenantContext.getTenantId();
        String loc = StringUtils.hasText(locale) ? locale : MessageConstants.DEFAULT_LOCALE;
        // 精确 locale
        MsgTemplateDO entity = msgTemplateMapper.selectOne(new LambdaQueryWrapper<MsgTemplateDO>()
                .eq(MsgTemplateDO::getTemplateCode, templateCode)
                .eq(MsgTemplateDO::getChannel, channel)
                .eq(MsgTemplateDO::getLocale, loc)
                .eq(MsgTemplateDO::getTenantId, tid)
                .eq(MsgTemplateDO::getStatus, "ENABLED")
                .last("LIMIT 1"));
        if (entity != null) {
            return entity;
        }
        // 回退默认 zh-CN
        if (!MessageConstants.DEFAULT_LOCALE.equals(loc)) {
            entity = msgTemplateMapper.selectOne(new LambdaQueryWrapper<MsgTemplateDO>()
                    .eq(MsgTemplateDO::getTemplateCode, templateCode)
                    .eq(MsgTemplateDO::getChannel, channel)
                    .eq(MsgTemplateDO::getLocale, MessageConstants.DEFAULT_LOCALE)
                    .eq(MsgTemplateDO::getTenantId, tid)
                    .eq(MsgTemplateDO::getStatus, "ENABLED")
                    .last("LIMIT 1"));
        }
        return entity;
    }

    @Override
    public void audit(String id, TemplateAuditDTO dto) {
        if (dto == null || !StringUtils.hasText(dto.getAuditStatus())) {
            throw new SysException(BaseResultCode.BAD_REQUEST, "审核状态不能为空");
        }
        MsgTemplateDO entity = getById(id);
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
