package com.njydsz.pmis.message.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.message.entity.MessageTemplateDO;
import com.njydsz.pmis.message.mapper.MessageTemplateMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 消息模板管理服务
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessageTemplateServiceImpl {

    private final MessageTemplateMapper templateMapper;

    public Long create(MessageTemplateDO t) {
        validate(t);
        if (templateMapper.selectByCodeAndChannel(t.getTemplateCode(), t.getChannel().toUpperCase(), 1L) != null) {
            throw new BizException(BizErrorCode.DUPLICATE_KEY,
                    "模板已存在: code=" + t.getTemplateCode() + " channel=" + t.getChannel());
        }
        t.setChannel(t.getChannel().toUpperCase());
        if (t.getStatus() == null) t.setStatus("ENABLED");
        if (t.getTenantId() == null) t.setTenantId(1L);
        templateMapper.insert(t);
        log.info("[MessageTemplate] 创建模板: code={} channel={}", t.getTemplateCode(), t.getChannel());
        return t.getId();
    }

    public void update(MessageTemplateDO t) {
        if (t.getId() == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "模板 ID 不能为空");
        }
        MessageTemplateDO exists = templateMapper.selectById(t.getId());
        if (exists == null) {
            throw new BizException(BizErrorCode.NOT_FOUND, "模板不存在");
        }
        if (StringUtils.hasText(t.getContent())) exists.setContent(t.getContent());
        if (StringUtils.hasText(t.getSubject())) exists.setSubject(t.getSubject());
        if (StringUtils.hasText(t.getProvider())) exists.setProvider(t.getProvider());
        if (StringUtils.hasText(t.getProviderKey())) exists.setProviderKey(t.getProviderKey());
        if (StringUtils.hasText(t.getSignName())) exists.setSignName(t.getSignName());
        if (StringUtils.hasText(t.getStatus())) exists.setStatus(t.getStatus());
        if (t.getDescription() != null) exists.setDescription(t.getDescription());
        templateMapper.updateById(exists);
        log.info("[MessageTemplate] 更新模板: id={}", exists.getId());
    }

    public void delete(Long id) {
        MessageTemplateDO t = templateMapper.selectById(id);
        if (t == null) {
            throw new BizException(BizErrorCode.NOT_FOUND, "模板不存在");
        }
        templateMapper.deleteById(id);
        log.info("[MessageTemplate] 删除模板: id={}", id);
    }

    public MessageTemplateDO getById(Long id) {
        MessageTemplateDO t = templateMapper.selectById(id);
        if (t == null) {
            throw new BizException(BizErrorCode.NOT_FOUND, "模板不存在");
        }
        return t;
    }

    public Page<MessageTemplateDO> page(int page, int size, String channel, String keyword) {
        Page<MessageTemplateDO> p = new Page<>(page, size);
        LambdaQueryWrapper<MessageTemplateDO> w = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(channel)) w.eq(MessageTemplateDO::getChannel, channel.toUpperCase());
        if (StringUtils.hasText(keyword)) {
            w.and(qw -> qw.like(MessageTemplateDO::getTemplateCode, keyword)
                    .or().like(MessageTemplateDO::getDescription, keyword));
        }
        w.orderByDesc(MessageTemplateDO::getCreatedAt);
        return templateMapper.selectPage(p, w);
    }

    public List<MessageTemplateDO> listByChannel(String channel) {
        return templateMapper.selectByChannel(channel.toUpperCase(), 1L);
    }

    private void validate(MessageTemplateDO t) {
        if (!StringUtils.hasText(t.getTemplateCode())) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "templateCode 不能为空");
        }
        if (!StringUtils.hasText(t.getChannel())) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "channel 不能为空");
        }
        if (!StringUtils.hasText(t.getContent())) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "content 不能为空");
        }
    }
}
