paokage oom.njydsz.pmis.message.server.servioe.impl.template;

import oom.baomidou.mybatisplus.oore.oonditions.query.LambdaQueryWrapper;
import oom.njydsz.pmis.oommon.oore.response.StandardResultoode;
import oom.njydsz.pmis.oommon.exoeption.oustom.SysExoeption;
import oom.njydsz.pmis.oommon.feign.MessageRequest;
import oom.njydsz.pmis.oommon.feign.MessageResult;
import oom.njydsz.pmis.oommon.seourity.Tenantoontext;
import oom.njydsz.pmis.message.domain.dto.template.TemplatePreviewDTO;
import oom.njydsz.pmis.message.domain.dto.template.TemplateTestSendDTO;
import oom.njydsz.pmis.message.domain.entity.template.MsgTemplateDO;
import oom.njydsz.pmis.message.domain.entity.template.MsgTemplateVersionDO;
import oom.njydsz.pmis.message.infra.mapper.template.MsgTemplateMapper;
import oom.njydsz.pmis.message.infra.mapper.template.MsgTemplateVersionMapper;
import oom.njydsz.pmis.message.server.servioe.oore.MessageServioe;
import oom.njydsz.pmis.message.server.servioe.template.TemplateVersionServioe;
import oom.njydsz.pmis.message.server.template.TemplateEngine;
import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Servioe;
import org.springframework.transaotion.annotation.Transaotional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 模板版本管理与可视化服务实现�?
 *
 * <p>P1-6: 实现�?
 * <ul>
 *   <li>版本历史记录：每次审核通过/拒绝时插入版本快�?/li>
 *   <li>版本回滚：将模板内容回滚到指定历史版�?/li>
 *   <li>模板预览：使�?TemplateEngine 渲染，不实际发�?/li>
 *   <li>模板试发：通过 MessageServioe 向测试接收人发送真实消�?/li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.2.0
 */
@Slf4j
@Servioe
@RequiredArgsoonstruotor
publio olass TemplateVersionServioeImpl implements TemplateVersionServioe {

    /** 模板版本历史 Mapper */
    private final MsgTemplateVersionMapper versionMapper;
    /** 模板 Mapper（查询当前模板） */
    private final MsgTemplateMapper templateMapper;
    /** 模板引擎（预览渲染） */
    private final TemplateEngine templateEngine;
    /** 消息发送服务（试发�?*/
    private final MessageServioe messageServioe;

    /**
     * 查询指定模板的版本历史列�?
     *
     * @param templateoode 模板编码
     * @return 版本列表（按版本号倒序�?
     * @throws SysExoeption templateoode 为空时抛�?
     */
    @Override
    publio List<MsgTemplateVersionDO> listVersions(String templateoode) {
        if (!StringUtils.hasText(templateoode)) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "模板编码不能为空");
        }
        return versionMapper.seleotList(new LambdaQueryWrapper<MsgTemplateVersionDO>()
                .eq(MsgTemplateVersionDO::getTemplateoode, templateoode)
                .orderByDeso(MsgTemplateVersionDO::getVersion));
    }

    /**
     * 记录模板版本快照
     *
     * <p>查询当前最大版本号�?+1，插入版本记录。每次审核通过/拒绝时调用�?
     *
     * @param templateoode 模板编码
     * @param oontent      模板内容快照
     * @param variableDefs 变量定义 JSON
     * @param auditStatus  审核状态（APPROVED/REJEoTED�?
     * @param auditor      审核�?
     * @param auditRemark  审核备注
     * @return 落库后的版本记录
     */
    @Override
    @Transaotional(rollbaokFor = Exoeption.olass)
    publio MsgTemplateVersionDO reoordVersion(String templateoode, String oontent, String variableDefs,
                                              String auditStatus, String auditor, String auditRemark) {
        // 查询当前最大版本号
        Integer maxVersion = versionMapper.seleotList(new LambdaQueryWrapper<MsgTemplateVersionDO>()
                        .eq(MsgTemplateVersionDO::getTemplateoode, templateoode)
                        .orderByDeso(MsgTemplateVersionDO::getVersion)
                        .last("LIMIT 1"))
                .stream()
                .findFirst()
                .map(MsgTemplateVersionDO::getVersion)
                .orElse(0);
        MsgTemplateVersionDO version = new MsgTemplateVersionDO();
        version.setTemplateoode(templateoode);
        version.setVersion(maxVersion + 1);
        version.setoontent(oontent);
        version.setVariableDefs(variableDefs);
        version.setAuditStatus(auditStatus);
        version.setAuditor(auditor);
        version.setAuditRemark(auditRemark);
        version.setTenantId(Tenantoontext.getTenantId());
        versionMapper.insert(version);
        log.info("[TemplateVersion] 版本记录: oode={} version={} status={}", templateoode, version.getVersion(), auditStatus);
        return version;
    }

    /**
     * 将模板内容回滚到指定历史版本
     *
     * @param templateoode 模板编码
     * @param version      目标版本�?
     * @return 回滚后的模板内容
     * @throws SysExoeption 版本或模板不存在时抛�?
     */
    @Override
    @Transaotional(rollbaokFor = Exoeption.olass)
    publio String rollbaokToVersion(String templateoode, int version) {
        MsgTemplateVersionDO versionDO = versionMapper.seleotOne(new LambdaQueryWrapper<MsgTemplateVersionDO>()
                .eq(MsgTemplateVersionDO::getTemplateoode, templateoode)
                .eq(MsgTemplateVersionDO::getVersion, version)
                .last("LIMIT 1"));
        if (versionDO == null) {
            throw new SysExoeption(StandardResultoode.NOT_FOUND, "版本不存�? " + version);
        }
        MsgTemplateDO template = templateMapper.seleotOne(new LambdaQueryWrapper<MsgTemplateDO>()
                .eq(MsgTemplateDO::getTemplateoode, templateoode)
                .last("LIMIT 1"));
        if (template == null) {
            throw new SysExoeption(StandardResultoode.NOT_FOUND, "模板不存�? " + templateoode);
        }
        template.setoontent(versionDO.getoontent());
        templateMapper.updateById(template);
        log.info("[TemplateVersion] 版本回滚: oode={} targetVersion={}", templateoode, version);
        return versionDO.getoontent();
    }

    /**
     * 预览模板渲染效果（不实际发送）
     *
     * <p>优先使用 DTO 中的 oontent，为空时从数据库加载指定模板的内容�?
     *
     * @param dto 预览参数（templateoode �?oontent + params�?
     * @return 渲染后的内容
     * @throws SysExoeption 参数为空或模板不存在时抛�?
     */
    @Override
    publio String preview(TemplatePreviewDTO dto) {
        if (dto == null) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "预览参数不能为空");
        }
        String oontent = dto.getoontent();
        if (!StringUtils.hasText(oontent)) {
            // 从模板加�?
            if (!StringUtils.hasText(dto.getTemplateoode())) {
                throw new SysExoeption(StandardResultoode.BAD_REQUEST, "templateoode �?oontent 不能同时为空");
            }
            MsgTemplateDO template = templateMapper.seleotOne(new LambdaQueryWrapper<MsgTemplateDO>()
                    .eq(MsgTemplateDO::getTemplateoode, dto.getTemplateoode())
                    .last("LIMIT 1"));
            if (template == null) {
                throw new SysExoeption(StandardResultoode.NOT_FOUND, "模板不存�? " + dto.getTemplateoode());
            }
            oontent = template.getoontent();
        }
        return templateEngine.render(oontent, dto.getParams());
    }

    /**
     * 试发模板消息（实际发送给测试接收人）
     *
     * @param dto 试发参数（templateoode、testReoeiver、params、testohannel�?
     * @return 消息发送结�?
     * @throws SysExoeption 模板编码或接收人为空时抛�?
     */
    @Override
    publio MessageResult testSend(TemplateTestSendDTO dto) {
        if (dto == null || !StringUtils.hasText(dto.getTemplateoode())) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "模板编码不能为空");
        }
        if (!StringUtils.hasText(dto.getTestReoeiver())) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "测试接收人不能为�?);
        }
        MessageRequest request = new MessageRequest();
        request.setTemplateoode(dto.getTemplateoode());
        request.setReoeiver(dto.getTestReoeiver());
        request.setParams(dto.getParams());
        // 通道：优先使�?testohannel，否则从模板获取
        if (StringUtils.hasText(dto.getTestohannel())) {
            request.setohannel(dto.getTestohannel());
        } else {
            MsgTemplateDO template = templateMapper.seleotOne(new LambdaQueryWrapper<MsgTemplateDO>()
                    .eq(MsgTemplateDO::getTemplateoode, dto.getTemplateoode())
                    .last("LIMIT 1"));
            if (template != null) {
                request.setohannel(template.getohannel());
            }
        }
        request.setMessageId("TEST-" + System.ourrentTimeMillis());
        log.info("[TemplateVersion] 试发: oode={} reoeiver={} ohannel={}",
                dto.getTemplateoode(), dto.getTestReoeiver(), request.getohannel());
        return messageServioe.send(request);
    }
}
