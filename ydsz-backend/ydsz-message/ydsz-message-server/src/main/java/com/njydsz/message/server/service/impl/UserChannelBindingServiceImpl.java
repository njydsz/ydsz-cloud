package com.njydsz.message.server.service.impl.config;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.njydsz.common.core.response.BaseResultCode;
import com.njydsz.common.exception.custom.SysException;
import com.njydsz.common.security.TenantContext;
import com.njydsz.message.domain.dto.config.UserChannelBindingDTO;
import com.njydsz.message.domain.entity.config.MsgUserChannelDO;
import com.njydsz.message.infra.mapper.config.MsgUserChannelMapper;
import com.njydsz.message.server.service.config.UserChannelBindingService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 用户通道绑定服务实现。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserChannelBindingServiceImpl implements UserChannelBindingService {

    /** 用户-通道绑定 Mapper */
    private final MsgUserChannelMapper msgUserChannelMapper;

    /**
     * {@inheritDoc}
     * <p>按 userId + channelType 查找已有绑定：存在则更新 channelUserId/verified/isPrimary/extra，
     * 不存在则新建。channelType 统一转大写存储，tenantId 从 {@link TenantContext} 获取。
     *
     * @throws SysException 当 userId 或 channelType 为空时抛出
     */
    @Override
    public MsgUserChannelDO upsert(UserChannelBindingDTO dto) {
        if (dto == null || !StringUtils.hasText(dto.getUserId()) || !StringUtils.hasText(dto.getChannelType())) {
            throw new SysException(BaseResultCode.BAD_REQUEST, "用户ID和通道类型不能为空");
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

    /**
     * {@inheritDoc}
     * <p>按 ID 逻辑删除绑定记录，id 为空时直接返回。
     */
    @Override
    public void delete(String id) {
        if (!StringUtils.hasText(id)) {
            return;
        }
        msgUserChannelMapper.deleteById(id);
    }

    /**
     * {@inheritDoc}
     * <p>按 tenantId 隔离，结果按 isPrimary 降序、createdAt 降序排列。
     *
     * @param userId 用户 ID
     * @return 绑定列表，userId 为空时返回空列表
     */
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

    /**
     * {@inheritDoc}
     * <p>channelType 统一转大写查询，按 tenantId 隔离，优先返回 isPrimary=1 的记录。
     *
     * @param userId      用户 ID
     * @param channelType 通道类型（不区分大小写）
     * @return 绑定记录，不存在时返回 null
     */
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

    /**
     * {@inheritDoc}
     * <p>查询用户的通道绑定 channelUserId，无绑定时返回 null（降级使用原 receiver），
     * 绑定未验证时记 WARN 日志但仍返回。
     *
     * @param userId      用户 ID
     * @param channelType 通道类型
     * @return 通道用户 ID，无绑定时返回 null
     */
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
