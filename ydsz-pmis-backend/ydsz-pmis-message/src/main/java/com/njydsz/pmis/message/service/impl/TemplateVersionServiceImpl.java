package com.njydsz.pmis.message.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.common.feign.MessageRequest;
import com.njydsz.pmis.common.feign.MessageResult;
import com.njydsz.pmis.common.security.TenantContext;
import com.njydsz.pmis.message.dto.TemplatePreviewDTO;
import com.njydsz.pmis.message.dto.TemplateTestSendDTO;
import com.njydsz.pmis.message.entity.MsgTemplateDO;
import com.njydsz.pmis.message.entity.MsgTemplateVersionDO;
import com.njydsz.pmis.message.mapper.MsgTemplateMapper;
import com.njydsz.pmis.message.mapper.MsgTemplateVersionMapper;
import com.njydsz.pmis.message.service.MessageService;
import com.njydsz.pmis.message.service.TemplateVersionService;
import com.njydsz.pmis.message.template.TemplateEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 模板版本管理与可视化服务实现。
 *
 * <p>P1-6: 实现：
 * <ul>
 *   <li>版本历史记录：每次审核通过/拒绝时插入版本快照</li>
 *   <li>版本回滚：将模板内容回滚到指定历史版本</li>
 *   <li>模板预览：使用 TemplateEngine 渲染，不实际发送</li>
 *   <li>模板试发：通过 MessageService 向测试接收人发送真实消息</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.2.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TemplateVersionServiceImpl implements TemplateVersionService {

    private final MsgTemplateVersionMapper versionMapper;
    private final MsgTemplateMapper templateMapper;
    private final TemplateEngine templateEngine;
    private final MessageService messageService;

    @Override
    public List<MsgTemplateVersionDO> listVersions(String templateCode) {
        if (!StringUtils.hasText(templateCode)) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "模板编码不能为空");
        }
        return versionMapper.selectList(new LambdaQueryWrapper<MsgTemplateVersionDO>()
                .eq(MsgTemplateVersionDO::getTemplateCode, templateCode)
                .orderByDesc(MsgTemplateVersionDO::getVersion));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MsgTemplateVersionDO recordVersion(String templateCode, String content, String variableDefs,
                                              String auditStatus, String auditor, String auditRemark) {
        // 查询当前最大版本号
        Integer maxVersion = versionMapper.selectList(new LambdaQueryWrapper<MsgTemplateVersionDO>()
                        .eq(MsgTemplateVersionDO::getTemplateCode, templateCode)
                        .orderByDesc(MsgTemplateVersionDO::getVersion)
                        .last("LIMIT 1"))
                .stream()
                .findFirst()
                .map(MsgTemplateVersionDO::getVersion)
                .orElse(0);
        MsgTemplateVersionDO version = new MsgTemplateVersionDO();
        version.setTemplateCode(templateCode);
        version.setVersion(maxVersion + 1);
        version.setContent(content);
        version.setVariableDefs(variableDefs);
        version.setAuditStatus(auditStatus);
        version.setAuditor(auditor);
        version.setAuditRemark(auditRemark);
        version.setTenantId(TenantContext.getTenantId());
        versionMapper.insert(version);
        log.info("[TemplateVersion] 版本记录: code={} version={} status={}", templateCode, version.getVersion(), auditStatus);
        return version;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String rollbackToVersion(String templateCode, int version) {
        MsgTemplateVersionDO versionDO = versionMapper.selectOne(new LambdaQueryWrapper<MsgTemplateVersionDO>()
                .eq(MsgTemplateVersionDO::getTemplateCode, templateCode)
                .eq(MsgTemplateVersionDO::getVersion, version)
                .last("LIMIT 1"));
        if (versionDO == null) {
            throw new BizException(BizErrorCode.NOT_FOUND, "版本不存在: " + version);
        }
        MsgTemplateDO template = templateMapper.selectOne(new LambdaQueryWrapper<MsgTemplateDO>()
                .eq(MsgTemplateDO::getTemplateCode, templateCode)
                .last("LIMIT 1"));
        if (template == null) {
            throw new BizException(BizErrorCode.NOT_FOUND, "模板不存在: " + templateCode);
        }
        template.setContent(versionDO.getContent());
        templateMapper.updateById(template);
        log.info("[TemplateVersion] 版本回滚: code={} targetVersion={}", templateCode, version);
        return versionDO.getContent();
    }

    @Override
    public String preview(TemplatePreviewDTO dto) {
        if (dto == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "预览参数不能为空");
        }
        String content = dto.getContent();
        if (!StringUtils.hasText(content)) {
            // 从模板加载
            if (!StringUtils.hasText(dto.getTemplateCode())) {
                throw new BizException(BizErrorCode.BAD_REQUEST, "templateCode 和 content 不能同时为空");
            }
            MsgTemplateDO template = templateMapper.selectOne(new LambdaQueryWrapper<MsgTemplateDO>()
                    .eq(MsgTemplateDO::getTemplateCode, dto.getTemplateCode())
                    .last("LIMIT 1"));
            if (template == null) {
                throw new BizException(BizErrorCode.NOT_FOUND, "模板不存在: " + dto.getTemplateCode());
            }
            content = template.getContent();
        }
        return templateEngine.render(content, dto.getParams());
    }

    @Override
    public MessageResult testSend(TemplateTestSendDTO dto) {
        if (dto == null || !StringUtils.hasText(dto.getTemplateCode())) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "模板编码不能为空");
        }
        if (!StringUtils.hasText(dto.getTestReceiver())) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "测试接收人不能为空");
        }
        MessageRequest request = new MessageRequest();
        request.setTemplateCode(dto.getTemplateCode());
        request.setReceiver(dto.getTestReceiver());
        request.setParams(dto.getParams());
        // 通道：优先使用 testChannel，否则从模板获取
        if (StringUtils.hasText(dto.getTestChannel())) {
            request.setChannel(dto.getTestChannel());
        } else {
            MsgTemplateDO template = templateMapper.selectOne(new LambdaQueryWrapper<MsgTemplateDO>()
                    .eq(MsgTemplateDO::getTemplateCode, dto.getTemplateCode())
                    .last("LIMIT 1"));
            if (template != null) {
                request.setChannel(template.getChannel());
            }
        }
        request.setMessageId("TEST-" + System.currentTimeMillis());
        log.info("[TemplateVersion] 试发: code={} receiver={} channel={}",
                dto.getTemplateCode(), dto.getTestReceiver(), request.getChannel());
        return messageService.send(request);
    }
}
