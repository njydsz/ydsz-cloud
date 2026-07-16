package com.njydsz.message.server.service.impl.template;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.njydsz.common.core.response.BaseResultCode;
import com.njydsz.common.exception.custom.SysException;
import com.njydsz.common.feign.MessageRequest;
import com.njydsz.common.feign.MessageResult;
import com.njydsz.common.security.TenantContext;
import com.njydsz.message.domain.dto.template.TemplatePreviewDTO;
import com.njydsz.message.domain.dto.template.TemplateTestSendDTO;
import com.njydsz.message.domain.entity.template.MsgTemplateDO;
import com.njydsz.message.domain.entity.template.MsgTemplateVersionDO;
import com.njydsz.message.infra.mapper.template.MsgTemplateMapper;
import com.njydsz.message.infra.mapper.template.MsgTemplateVersionMapper;
import com.njydsz.message.server.service.core.MessageService;
import com.njydsz.message.server.service.template.TemplateVersionService;
import com.njydsz.message.server.template.TemplateEngine;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

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
 * @author ydsz-team
 * @since 1.2.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TemplateVersionServiceImpl implements TemplateVersionService {

    /** 模板版本历史 Mapper */
    private final MsgTemplateVersionMapper versionMapper;
    /** 模板 Mapper（查询当前模板） */
    private final MsgTemplateMapper templateMapper;
    /** 模板引擎（预览渲染） */
    private final TemplateEngine templateEngine;
    /** 消息发送服务（试发） */
    private final MessageService messageService;

    /**
     * 查询指定模板的版本历史列表
     *
     * @param templateCode 模板编码
     * @return 版本列表（按版本号倒序）
     * @throws SysException templateCode 为空时抛出
     */
    @Override
    public List<MsgTemplateVersionDO> listVersions(String templateCode) {
        if (!StringUtils.hasText(templateCode)) {
            throw new SysException(BaseResultCode.BAD_REQUEST, "模板编码不能为空");
        }
        return versionMapper.selectList(new LambdaQueryWrapper<MsgTemplateVersionDO>()
                .eq(MsgTemplateVersionDO::getTemplateCode, templateCode)
                .orderByDesc(MsgTemplateVersionDO::getVersion));
    }

    /**
     * 记录模板版本快照
     *
     * <p>查询当前最大版本号并 +1，插入版本记录。每次审核通过/拒绝时调用。
     *
     * @param templateCode 模板编码
     * @param content      模板内容快照
     * @param variableDefs 变量定义 JSON
     * @param auditStatus  审核状态（APPROVED/REJECTED）
     * @param auditor      审核人
     * @param auditRemark  审核备注
     * @return 落库后的版本记录
     */
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

    /**
     * 将模板内容回滚到指定历史版本
     *
     * @param templateCode 模板编码
     * @param version      目标版本号
     * @return 回滚后的模板内容
     * @throws SysException 版本或模板不存在时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public String rollbackToVersion(String templateCode, int version) {
        MsgTemplateVersionDO versionDO = versionMapper.selectOne(new LambdaQueryWrapper<MsgTemplateVersionDO>()
                .eq(MsgTemplateVersionDO::getTemplateCode, templateCode)
                .eq(MsgTemplateVersionDO::getVersion, version)
                .last("LIMIT 1"));
        if (versionDO == null) {
            throw new SysException(BaseResultCode.NOT_FOUND, "版本不存在: " + version);
        }
        MsgTemplateDO template = templateMapper.selectOne(new LambdaQueryWrapper<MsgTemplateDO>()
                .eq(MsgTemplateDO::getTemplateCode, templateCode)
                .last("LIMIT 1"));
        if (template == null) {
            throw new SysException(BaseResultCode.NOT_FOUND, "模板不存在: " + templateCode);
        }
        template.setContent(versionDO.getContent());
        templateMapper.updateById(template);
        log.info("[TemplateVersion] 版本回滚: code={} targetVersion={}", templateCode, version);
        return versionDO.getContent();
    }

    /**
     * 预览模板渲染效果（不实际发送）
     *
     * <p>优先使用 DTO 中的 content，为空时从数据库加载指定模板的内容。
     *
     * @param dto 预览参数（templateCode 或 content + params）
     * @return 渲染后的内容
     * @throws SysException 参数为空或模板不存在时抛出
     */
    @Override
    public String preview(TemplatePreviewDTO dto) {
        if (dto == null) {
            throw new SysException(BaseResultCode.BAD_REQUEST, "预览参数不能为空");
        }
        String content = dto.getContent();
        if (!StringUtils.hasText(content)) {
            // 从模板加载
            if (!StringUtils.hasText(dto.getTemplateCode())) {
                throw new SysException(BaseResultCode.BAD_REQUEST, "templateCode 和 content 不能同时为空");
            }
            MsgTemplateDO template = templateMapper.selectOne(new LambdaQueryWrapper<MsgTemplateDO>()
                    .eq(MsgTemplateDO::getTemplateCode, dto.getTemplateCode())
                    .last("LIMIT 1"));
            if (template == null) {
                throw new SysException(BaseResultCode.NOT_FOUND, "模板不存在: " + dto.getTemplateCode());
            }
            content = template.getContent();
        }
        return templateEngine.render(content, dto.getParams());
    }

    /**
     * 试发模板消息（实际发送给测试接收人）
     *
     * @param dto 试发参数（templateCode、testReceiver、params、testChannel）
     * @return 消息发送结果
     * @throws SysException 模板编码或接收人为空时抛出
     */
    @Override
    public MessageResult testSend(TemplateTestSendDTO dto) {
        if (dto == null || !StringUtils.hasText(dto.getTemplateCode())) {
            throw new SysException(BaseResultCode.BAD_REQUEST, "模板编码不能为空");
        }
        if (!StringUtils.hasText(dto.getTestReceiver())) {
            throw new SysException(BaseResultCode.BAD_REQUEST, "测试接收人不能为空");
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
