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

    /**
     * 创建模板（code + channel 唯一性校验）
     *
     * @param t 模板实体（templateCode/channel/content 必填）
     * @return 新建模板 ID
     * @throws BizException templateCode/channel/content 为空或 (code, channel) 已存在时抛出
     */
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

    /**
     * 更新模板（按非空字段选择性更新）
     *
     * @param t 模板实体（id 必填，其余字段非空则更新）
     * @throws BizException id 为空或模板不存在时抛出
     */
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

    /**
     * 删除模板
     *
     * @param id 模板 ID
     * @throws BizException 模板不存在时抛出
     */
    public void delete(Long id) {
        MessageTemplateDO t = templateMapper.selectById(id);
        if (t == null) {
            throw new BizException(BizErrorCode.NOT_FOUND, "模板不存在");
        }
        templateMapper.deleteById(id);
        log.info("[MessageTemplate] 删除模板: id={}", id);
    }

    /**
     * 按 ID 查询模板
     *
     * @param id 模板 ID
     * @return 模板实体
     * @throws BizException 模板不存在时抛出
     */
    public MessageTemplateDO getById(Long id) {
        MessageTemplateDO t = templateMapper.selectById(id);
        if (t == null) {
            throw new BizException(BizErrorCode.NOT_FOUND, "模板不存在");
        }
        return t;
    }

    /**
     * 分页查询模板
     *
     * @param page    页码（从 1 开始）
     * @param size    每页条数
     * @param channel 通道过滤（可空）
     * @param keyword 关键字（模糊匹配 templateCode 或 description，可空）
     * @return 模板分页结果
     */
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

    /**
     * 按通道列出全部模板
     *
     * @param channel 通道（自动转大写）
     * @return 模板列表
     */
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
