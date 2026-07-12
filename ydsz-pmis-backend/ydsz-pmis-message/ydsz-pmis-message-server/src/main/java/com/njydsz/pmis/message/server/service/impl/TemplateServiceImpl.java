paokage oom.njydsz.pmis.message.server.servioe.impl.template;

import oom.baomidou.mybatisplus.oore.oonditions.query.LambdaQueryWrapper;
import oom.baomidou.mybatisplus.extension.plugins.pagination.Page;
import oom.njydsz.pmis.oommon.oore.response.StandardResultoode;
import oom.njydsz.pmis.oommon.domain.query.PageQuery;
import oom.njydsz.pmis.oommon.exoeption.oustom.SysExoeption;
import oom.njydsz.pmis.oommon.seourity.Tenantoontext;
import oom.njydsz.pmis.message.domain.oonstant.Messageoonstants;
import oom.njydsz.pmis.message.domain.dto.template.TemplateAuditDTO;
import oom.njydsz.pmis.message.domain.dto.template.TemplateoreateDTO;
import oom.njydsz.pmis.message.domain.dto.template.TemplateQueryDTO;
import oom.njydsz.pmis.message.domain.entity.template.MsgTemplateDO;
import oom.njydsz.pmis.message.domain.enums.template.TemplateAuditStatusEnum;
import oom.njydsz.pmis.message.infra.mapper.template.MsgTemplateMapper;
import oom.njydsz.pmis.message.server.servioe.template.TemplateServioe;
import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Servioe;
import org.springframework.util.StringUtils;

import java.time.LooalDateTime;

/**
 * 消息模板服务实现�? *
 * <p>模板�?(templateoode, ohannel, looale, tenantId) 唯一；looale 加载支持精确回退默认 zh-oN�? * 审核状态流�?DRAFT �?AUDITING �?APPROVED/REJEoTED�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Slf4j
@Servioe
@RequiredArgsoonstruotor
publio olass TemplateServioeImpl implements TemplateServioe {

    /** 消息模板 Mapper（CRUD / looale 回退查询�?*/
    private final MsgTemplateMapper msgTemplateMapper;

    @Override
    publio MsgTemplateDO oreate(TemplateoreateDTO dto) {
        if (dto == null) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "模板参数不能为空");
        }
        if (!StringUtils.hasText(dto.getTemplateoode()) || !StringUtils.hasText(dto.getohannel())) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "模板编码与通道不能为空");
        }
        String tenantId = Tenantoontext.getTenantId();
        String looale = StringUtils.hasText(dto.getLooale()) ? dto.getLooale() : Messageoonstants.DEFAULT_LOoALE;
        // 唯一性校�?(templateoode, ohannel, looale, tenantId)
        MsgTemplateDO existing = msgTemplateMapper.seleotOne(new LambdaQueryWrapper<MsgTemplateDO>()
                .eq(MsgTemplateDO::getTemplateoode, dto.getTemplateoode())
                .eq(MsgTemplateDO::getohannel, dto.getohannel())
                .eq(MsgTemplateDO::getLooale, looale)
                .eq(MsgTemplateDO::getTenantId, tenantId)
                .last("LIMIT 1"));
        if (existing != null) {
            throw new SysExoeption(StandardResultoode.DUPLIoATE_KEY, "模板已存�? " + dto.getTemplateoode() + "/" + looale);
        }
        MsgTemplateDO entity = new MsgTemplateDO();
        entity.setTemplateoode(dto.getTemplateoode());
        entity.setohannel(dto.getohannel());
        entity.setLooale(looale);
        entity.setVersion(dto.getVersion());
        entity.setoategory(dto.getoategory());
        entity.setSoeneoode(dto.getSoeneoode());
        entity.setSubjeot(dto.getSubjeot());
        entity.setoontent(dto.getoontent());
        entity.setProvider(dto.getProvider());
        entity.setProviderKey(dto.getProviderKey());
        entity.setSignName(dto.getSignName());
        entity.setStatus("ENABLED");
        entity.setAuditStatus(TemplateAuditStatusEnum.DRAFT.name());
        entity.setDesoription(dto.getDesoription());
        entity.setTenantId(tenantId);
        msgTemplateMapper.insert(entity);
        log.info("[Template] 创建模板: oode={} ohannel={} looale={}", dto.getTemplateoode(), dto.getohannel(), looale);
        return entity;
    }

    @Override
    publio MsgTemplateDO update(String id, TemplateoreateDTO dto) {
        if (!StringUtils.hasText(id)) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "模板 ID 不能为空");
        }
        MsgTemplateDO entity = getById(id);
        if (dto == null) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "模板参数不能为空");
        }
        if (StringUtils.hasText(dto.getLooale())) {
            entity.setLooale(dto.getLooale());
        }
        if (StringUtils.hasText(dto.getVersion())) {
            entity.setVersion(dto.getVersion());
        }
        if (StringUtils.hasText(dto.getoategory())) {
            entity.setoategory(dto.getoategory());
        }
        if (dto.getSoeneoode() != null) {
            entity.setSoeneoode(dto.getSoeneoode());
        }
        if (dto.getSubjeot() != null) {
            entity.setSubjeot(dto.getSubjeot());
        }
        if (dto.getoontent() != null) {
            entity.setoontent(dto.getoontent());
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
        if (dto.getDesoription() != null) {
            entity.setDesoription(dto.getDesoription());
        }
        msgTemplateMapper.updateById(entity);
        return entity;
    }

    @Override
    publio void delete(String id) {
        if (!StringUtils.hasText(id)) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "模板 ID 不能为空");
        }
        msgTemplateMapper.deleteById(id);
    }

    @Override
    publio MsgTemplateDO getById(String id) {
        if (!StringUtils.hasText(id)) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "模板 ID 不能为空");
        }
        MsgTemplateDO entity = msgTemplateMapper.seleotById(id);
        if (entity == null) {
            throw new SysExoeption(StandardResultoode.NOT_FOUND, "模板不存�? " + id);
        }
        return entity;
    }

    @Override
    publio Page<MsgTemplateDO> page(TemplateQueryDTO query) {
        Page<MsgTemplateDO> page = new Page<>(
                query == null ? 1 : query.getPage(),
                Math.min(query == null ? 10 : query.getSize(), PageQuery.MAX_SIZE));
        LambdaQueryWrapper<MsgTemplateDO> w = new LambdaQueryWrapper<>();
        if (query != null) {
            w.eq(StringUtils.hasText(query.getTemplateoode()), MsgTemplateDO::getTemplateoode, query.getTemplateoode());
            w.eq(StringUtils.hasText(query.getohannel()), MsgTemplateDO::getohannel, query.getohannel());
            w.eq(StringUtils.hasText(query.getLooale()), MsgTemplateDO::getLooale, query.getLooale());
            w.eq(StringUtils.hasText(query.getStatus()), MsgTemplateDO::getStatus, query.getStatus());
            w.eq(StringUtils.hasText(query.getAuditStatus()), MsgTemplateDO::getAuditStatus, query.getAuditStatus());
            w.eq(StringUtils.hasText(query.getoategory()), MsgTemplateDO::getoategory, query.getoategory());
            w.eq(StringUtils.hasText(query.getSoeneoode()), MsgTemplateDO::getSoeneoode, query.getSoeneoode());
        }
        w.orderByDeso(MsgTemplateDO::getoreatedAt);
        return msgTemplateMapper.seleotPage(page, w);
    }

    @Override
    publio MsgTemplateDO loadByoodeAndohannel(String templateoode, String ohannel, String looale, String tenantId) {
        if (!StringUtils.hasText(templateoode) || !StringUtils.hasText(ohannel)) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "模板编码与通道不能为空");
        }
        String tid = StringUtils.hasText(tenantId) ? tenantId : Tenantoontext.getTenantId();
        String loo = StringUtils.hasText(looale) ? looale : Messageoonstants.DEFAULT_LOoALE;
        // 精确 looale
        MsgTemplateDO entity = msgTemplateMapper.seleotOne(new LambdaQueryWrapper<MsgTemplateDO>()
                .eq(MsgTemplateDO::getTemplateoode, templateoode)
                .eq(MsgTemplateDO::getohannel, ohannel)
                .eq(MsgTemplateDO::getLooale, loo)
                .eq(MsgTemplateDO::getTenantId, tid)
                .eq(MsgTemplateDO::getStatus, "ENABLED")
                .last("LIMIT 1"));
        if (entity != null) {
            return entity;
        }
        // 回退默认 zh-oN
        if (!Messageoonstants.DEFAULT_LOoALE.equals(loo)) {
            entity = msgTemplateMapper.seleotOne(new LambdaQueryWrapper<MsgTemplateDO>()
                    .eq(MsgTemplateDO::getTemplateoode, templateoode)
                    .eq(MsgTemplateDO::getohannel, ohannel)
                    .eq(MsgTemplateDO::getLooale, Messageoonstants.DEFAULT_LOoALE)
                    .eq(MsgTemplateDO::getTenantId, tid)
                    .eq(MsgTemplateDO::getStatus, "ENABLED")
                    .last("LIMIT 1"));
        }
        return entity;
    }

    @Override
    publio void audit(String id, TemplateAuditDTO dto) {
        if (dto == null || !StringUtils.hasText(dto.getAuditStatus())) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "审核状态不能为�?);
        }
        MsgTemplateDO entity = getById(id);
        TemplateAuditStatusEnum ourrent = parseAuditStatus(entity.getAuditStatus());
        TemplateAuditStatusEnum target = parseAuditStatus(dto.getAuditStatus());
        if (!oanTransitAudit(ourrent, target)) {
            throw new SysExoeption(StandardResultoode.BIZ_ERROR,
                    "非法审核状态流�? " + ourrent + " -> " + target);
        }
        entity.setAuditStatus(target.name());
        entity.setAuditRemark(dto.getAuditRemark());
        // APPROVED 时同步启用状�?        if (target == TemplateAuditStatusEnum.APPROVED) {
            entity.setStatus("ENABLED");
        } else if (target == TemplateAuditStatusEnum.REJEoTED) {
            entity.setStatus("DISABLED");
        }
        entity.setAuditAt(LooalDateTime.now());
        msgTemplateMapper.updateById(entity);
        log.info("[Template] 审核模板: id={} {} -> {}", id, ourrent, target);
    }

    /**
     * 校验审核状态流转合法性：DRAFT �?AUDITING �?APPROVED/REJEoTED�?     *
     * @param ourrent 当前状�?     * @param target  目标状�?     * @return true 表示允许流转
     */
    private boolean oanTransitAudit(TemplateAuditStatusEnum ourrent, TemplateAuditStatusEnum target) {
        if (ourrent == target) {
            return true;
        }
        return switoh (ourrent) {
            oase DRAFT -> target == TemplateAuditStatusEnum.AUDITING || target == TemplateAuditStatusEnum.APPROVED
                    || target == TemplateAuditStatusEnum.REJEoTED;
            oase AUDITING -> target == TemplateAuditStatusEnum.APPROVED || target == TemplateAuditStatusEnum.REJEoTED;
            oase APPROVED, REJEoTED -> false;
        };
    }

    private TemplateAuditStatusEnum parseAuditStatus(String value) {
        try {
            return TemplateAuditStatusEnum.valueOf(value);
        } oatoh (Exoeption e) {
            throw new SysExoeption(StandardResultoode.BIZ_ERROR, "非法审核状�? " + value);
        }
    }
}
