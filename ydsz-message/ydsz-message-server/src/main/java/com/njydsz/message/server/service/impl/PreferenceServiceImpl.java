package com.njydsz.message.server.service.impl.config;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.njydsz.common.core.code.BaseResultCode;
import com.njydsz.common.exception.custom.SysException;
import com.njydsz.common.security.TenantContext;
import com.njydsz.message.domain.constant.MessageConstants;
import com.njydsz.message.domain.dto.config.PreferenceUpsertDTO;
import com.njydsz.message.domain.entity.config.MsgPreference;
import com.njydsz.message.infra.mapper.config.MsgPreferenceMapper;
import com.njydsz.message.server.service.config.PreferenceService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 通知偏好服务实现。
 *
 * <p>管理用户/租户的通知偏好设置：免打扰时段、渠道白/黑名单、模板订阅/退订、频次上限。
 *
 * <p>偏好检查在消息发送前触发，未通过则跳过发送并打上 SKIPPED 状态。
 *
 * @author ydsz-team
 * @since 1.0.0
 */

@Slf4j
@Service
@RequiredArgsConstructor
public class PreferenceServiceImpl implements PreferenceService {

    /** 用户消息偏好 Mapper */
    private final MsgPreferenceMapper msgPreferenceMapper;

    /**
     * {@inheritDoc}
     * <p>按 (userId, channel, bizType) 查找已有偏好：存在则更新，不存在则新建。
     * bizType 为空时默认 {@link MessageConstants#DEFAULT_BIZ_TYPE}。
     *
     * @throws SysException 当 userId 或 channel 为空时抛出
     */
    @Override
    public MsgPreference upsert(PreferenceUpsertDTO dto) {
        if (dto == null || !StringUtils.hasText(dto.getUserId()) || !StringUtils.hasText(dto.getChannel())) {
            throw SysException.builder()
                .resultCode(BaseResultCode.BAD_REQUEST)
                .message("用户 ID 与通道不能为空")
                .build();
        }
        String bizType = StringUtils.hasText(dto.getBizType()) ? dto.getBizType() : MessageConstants.DEFAULT_BIZ_TYPE;
        MsgPreference existing = msgPreferenceMapper.selectOne(new LambdaQueryWrapper<MsgPreference>()
                .eq(MsgPreference::getUserId, dto.getUserId())
                .eq(MsgPreference::getChannel, dto.getChannel())
                .eq(MsgPreference::getBizType, bizType)
                .last("LIMIT 1"));
        if (existing == null) {
            MsgPreference entity = new MsgPreference();
            entity.setUserId(dto.getUserId());
            entity.setChannel(dto.getChannel());
            entity.setBizType(bizType);
            entity.setEnabled(dto.getEnabled() == null ? 1 : dto.getEnabled());
            entity.setDndEnabled(dto.getDndEnabled() == null ? 0 : dto.getDndEnabled());
            entity.setDndStart(dto.getDndStart());
            entity.setDndEnd(dto.getDndEnd());
            entity.setDailyLimit(dto.getDailyLimit());
            entity.setHourlyLimit(dto.getHourlyLimit());
            entity.setDigestEnabled(dto.getDigestEnabled() == null ? 0 : dto.getDigestEnabled());
            entity.setDigestFrequency(dto.getDigestFrequency());
            entity.setLocale(dto.getLocale());
            entity.setExtra(dto.getExtra());
            entity.setTenantId(TenantContext.getTenantId());
            msgPreferenceMapper.insert(entity);
            log.info("[Preference] 新建偏好: user={} channel={} bizType={}", dto.getUserId(), dto.getChannel(), bizType);
            return entity;
        }
        existing.setEnabled(dto.getEnabled() == null ? existing.getEnabled() : dto.getEnabled());
        existing.setDndEnabled(dto.getDndEnabled() == null ? existing.getDndEnabled() : dto.getDndEnabled());
        existing.setDndStart(dto.getDndStart());
        existing.setDndEnd(dto.getDndEnd());
        existing.setDailyLimit(dto.getDailyLimit());
        existing.setHourlyLimit(dto.getHourlyLimit());
        existing.setDigestEnabled(dto.getDigestEnabled() == null ? existing.getDigestEnabled() : dto.getDigestEnabled());
        existing.setDigestFrequency(dto.getDigestFrequency());
        existing.setLocale(dto.getLocale());
        existing.setExtra(dto.getExtra());
        msgPreferenceMapper.updateById(existing);
        return existing;
    }

    /**
     * {@inheritDoc}
     * <p>优先按精确 bizType 查询，未命中时回退 {@link MessageConstants#DEFAULT_BIZ_TYPE} 默认偏好。
     *
     * @param userId   用户 ID
     * @param channel  通道类型
     * @param bizType  业务类型（可选，为空时使用默认）
     * @return 偏好记录，不存在时返回 null
     */
    @Override
    public MsgPreference getByUser(String userId, String channel, String bizType) {
        if (!StringUtils.hasText(userId) || !StringUtils.hasText(channel)) {
            return null;
        }
        String bt = StringUtils.hasText(bizType) ? bizType : MessageConstants.DEFAULT_BIZ_TYPE;
        // 优先精确 bizType
        MsgPreference entity = msgPreferenceMapper.selectOne(new LambdaQueryWrapper<MsgPreference>()
                .eq(MsgPreference::getUserId, userId)
                .eq(MsgPreference::getChannel, channel)
                .eq(MsgPreference::getBizType, bt)
                .last("LIMIT 1"));
        if (entity != null) {
            return entity;
        }
        // 回退默认
        if (!MessageConstants.DEFAULT_BIZ_TYPE.equals(bt)) {
            entity = msgPreferenceMapper.selectOne(new LambdaQueryWrapper<MsgPreference>()
                    .eq(MsgPreference::getUserId, userId)
                    .eq(MsgPreference::getChannel, channel)
                    .eq(MsgPreference::getBizType, MessageConstants.DEFAULT_BIZ_TYPE)
                    .last("LIMIT 1"));
        }
        return entity;
    }

    /**
     * {@inheritDoc}
     *
     * @param userId 用户 ID
     * @return 偏好列表，按 channel 升序排列
     */
    @Override
    public List<MsgPreference> listByUser(String userId) {
        if (!StringUtils.hasText(userId)) {
            return List.of();
        }
        return msgPreferenceMapper.selectList(new LambdaQueryWrapper<MsgPreference>()
                .eq(MsgPreference::getUserId, userId)
                .orderByAsc(MsgPreference::getChannel));
    }

    /**
     * {@inheritDoc}
     *
     * @throws SysException 当 id 为空时抛出
     */
    @Override
    public void delete(String id) {
        if (!StringUtils.hasText(id)) {
            throw SysException.builder()
                .resultCode(BaseResultCode.BAD_REQUEST)
                .message("偏好 ID 不能为空")
                .build();
        }
        msgPreferenceMapper.deleteById(id);
    }
}
