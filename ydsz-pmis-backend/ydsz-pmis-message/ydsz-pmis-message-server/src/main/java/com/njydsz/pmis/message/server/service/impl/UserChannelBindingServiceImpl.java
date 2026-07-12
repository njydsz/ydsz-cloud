package com.njydsz.pmis.message.server.service.impl.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.njydsz.pmis.common.core.response.StandardResultCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.common.security.TenantContext;
import com.njydsz.pmis.message.domain.dto.config.UserChannelBindingDTO;
import com.njydsz.pmis.message.domain.entity.config.MsgUserChannelDO;
import com.njydsz.pmis.message.infra.mapper.config.MsgUserChannelMapper;
import com.njydsz.pmis.message.server.service.config.UserChannelBindingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 用户通道绑定服务实现。
 *
 * @author ydsz-pmis-team
 * @since 1.5.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserChannelBindingServiceImpl implements UserChannelBindingService {

    private final MsgUserChannelMapper msgUserChannelMapper;

    @Override
    public MsgUserChannelDO upsert(UserChannelBindingDTO dto) {
        if (dto == null || !StringUtils.hasText(dto.getUserId()) || !StringUtils.hasText(dto.getChannelType())) {
            throw new BizException(StandardResultCode.BAD_REQUEST, "用户ID和通道类型不能为空");
        }
        String tenantId = TenantContext.getTenantId();
        String channelType = dto.getChannelType().trim().toUpperCase();

        // 查找已有绑定
        MsgUserChannelDO existing = getByUserAndChannel(dto.getUserId(), channelType);
        if (existing != null) {
            existing.setChannelUserId(dto.getChannelUserId());
            if (dto.getVerified() != null) {
                existing.setVerified(dto.getVerified());
            }
            if (dto.getIsPrimary() != null) {
                existing.setIsPrimary(dto.getIsPrimary());
            }
            if (dto.getExtra() != null) {
                existing.setExtra(dto.getExtra());
            }
            msgUserChannelMapper.updateById(existing);
            log.info("[UserChannelBinding] 更新绑定: userId={} channel={} channelUserId={}",
                    dto.getUserId(), channelType, dto.getChannelUserId());
            return existing;
        }

        MsgUserChannelDO entity = new MsgUserChannelDO();
        entity.setUserId(dto.getUserId());
        entity.setChannelType(channelType);
        entity.setChannelUserId(dto.getChannelUserId());
        entity.setVerified(dto.getVerified() != null ? dto.getVerified() : 0);
        entity.setIsPrimary(dto.getIsPrimary() != null ? dto.getIsPrimary() : 0);
        entity.setExtra(dto.getExtra());
        entity.setTenantId(tenantId);
        msgUserChannelMapper.insert(entity);
        log.info("[UserChannelBinding] 新增绑定: userId={} channel={} channelUserId={}",
                dto.getUserId(), channelType, dto.getChannelUserId());
        return entity;
    }

    @Override
    public void delete(String id) {
        if (!StringUtils.hasText(id)) {
            return;
        }
        msgUserChannelMapper.deleteById(id);
    }

    @Override
    public List<MsgUserChannelDO> listByUser(String userId) {
        if (!StringUtils.hasText(userId)) {
            return List.of();
        }
        return msgUserChannelMapper.selectList(new LambdaQueryWrapper<MsgUserChannelDO>()
                .eq(MsgUserChannelDO::getUserId, userId)
                .eq(MsgUserChannelDO::getTenantId, TenantContext.getTenantId())
                .orderByDesc(MsgUserChannelDO::getIsPrimary)
                .orderByDesc(MsgUserChannelDO::getCreatedAt));
    }

    @Override
    public MsgUserChannelDO getByUserAndChannel(String userId, String channelType) {
        if (!StringUtils.hasText(userId) || !StringUtils.hasText(channelType)) {
            return null;
        }
        return msgUserChannelMapper.selectOne(new LambdaQueryWrapper<MsgUserChannelDO>()
                .eq(MsgUserChannelDO::getUserId, userId)
                .eq(MsgUserChannelDO::getChannelType, channelType.trim().toUpperCase())
                .eq(MsgUserChannelDO::getTenantId, TenantContext.getTenantId())
                .orderByDesc(MsgUserChannelDO::getIsPrimary)
                .last("LIMIT 1"));
    }

    @Override
    public String resolveChannelUserId(String userId, String channelType) {
        if (!StringUtils.hasText(userId) || !StringUtils.hasText(channelType)) {
            return null;
        }
        MsgUserChannelDO binding = getByUserAndChannel(userId, channelType);
        if (binding == null) {
            log.debug("[UserChannelBinding] 无通道绑定,降级使用原 receiver: userId={} channel={}",
                    userId, channelType);
            return null;
        }
        if (binding.getVerified() != null && binding.getVerified() == 0) {
            log.warn("[UserChannelBinding] 通道绑定未验证: userId={} channel={} channelUserId={}",
                    userId, channelType, binding.getChannelUserId());
        }
        return binding.getChannelUserId();
    }
}
