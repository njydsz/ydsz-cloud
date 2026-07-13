package com.njydsz.pmis.message.server.service.impl.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.njydsz.pmis.common.core.response.StandardResultCode;
import com.njydsz.pmis.common.exception.SysException;
import com.njydsz.pmis.common.security.TenantContext;
import com.njydsz.pmis.message.domain.constant.MessageConstants;
import com.njydsz.pmis.message.domain.dto.config.PreferenceUpsertDTO;
import com.njydsz.pmis.message.domain.entity.config.MsgPreferenceDO;
import com.njydsz.pmis.message.infra.mapper.config.MsgPreferenceMapper;
import com.njydsz.pmis.message.server.service.config.PreferenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 用户消息偏好服务实现。
 *
 * <p>按 (userId, channel, bizType) upsert；查询优先精确 bizType，回退 {@code __DEFAULT__}。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PreferenceServiceImpl implements PreferenceService {

    /** 用户消息偏好 Mapper */
    private final MsgPreferenceMapper msgPreferenceMapper;

    @Override
    public MsgPreferenceDO upsert(PreferenceUpsertDTO dto) {
        if (dto == null || !StringUtils.hasText(dto.getUserId()) || !StringUtils.hasText(dto.getChannel())) {
            throw new SysException(StandardResultCode.BAD_REQUEST, "用户 ID 与通道不能为空");
        }
        String bizType = StringUtils.hasText(dto.getBizType()) ? dto.getBizType() : MessageConstants.DEFAULT_BIZ_TYPE;
        MsgPreferenceDO existing = msgPreferenceMapper.selectOne(new LambdaQueryWrapper<MsgPreferenceDO>()
                .eq(MsgPreferenceDO::getUserId, dto.getUserId())
                .eq(MsgPreferenceDO::getChannel, dto.getChannel())
                .eq(MsgPreferenceDO::getBizType, bizType)
                .last("LIMIT 1"));
        if (existing == null) {
            MsgPreferenceDO entity = new MsgPreferenceDO();
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

    @Override
    public MsgPreferenceDO getByUser(String userId, String channel, String bizType) {
        if (!StringUtils.hasText(userId) || !StringUtils.hasText(channel)) {
            return null;
        }
        String bt = StringUtils.hasText(bizType) ? bizType : MessageConstants.DEFAULT_BIZ_TYPE;
        // 优先精确 bizType
        MsgPreferenceDO entity = msgPreferenceMapper.selectOne(new LambdaQueryWrapper<MsgPreferenceDO>()
                .eq(MsgPreferenceDO::getUserId, userId)
                .eq(MsgPreferenceDO::getChannel, channel)
                .eq(MsgPreferenceDO::getBizType, bt)
                .last("LIMIT 1"));
        if (entity != null) {
            return entity;
        }
        // 回退默认
        if (!MessageConstants.DEFAULT_BIZ_TYPE.equals(bt)) {
            entity = msgPreferenceMapper.selectOne(new LambdaQueryWrapper<MsgPreferenceDO>()
                    .eq(MsgPreferenceDO::getUserId, userId)
                    .eq(MsgPreferenceDO::getChannel, channel)
                    .eq(MsgPreferenceDO::getBizType, MessageConstants.DEFAULT_BIZ_TYPE)
                    .last("LIMIT 1"));
        }
        return entity;
    }

    @Override
    public List<MsgPreferenceDO> listByUser(String userId) {
        if (!StringUtils.hasText(userId)) {
            return List.of();
        }
        return msgPreferenceMapper.selectList(new LambdaQueryWrapper<MsgPreferenceDO>()
                .eq(MsgPreferenceDO::getUserId, userId)
                .orderByAsc(MsgPreferenceDO::getChannel));
    }

    @Override
    public void delete(String id) {
        if (!StringUtils.hasText(id)) {
            throw new SysException(StandardResultCode.BAD_REQUEST, "偏好 ID 不能为空");
        }
        msgPreferenceMapper.deleteById(id);
    }
}
