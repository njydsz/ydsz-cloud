package com.njydsz.pmis.workflow.service.impl.notification;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.njydsz.pmis.workflow.entity.notification.FlowNotifyChannelDO;
import com.njydsz.pmis.workflow.mapper.notification.FlowNotifyChannelMapper;
import com.njydsz.pmis.workflow.service.notification.FlowNotifyChannelService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 通知通道配置服务实现
 *
 * <p>基于 MyBatis-Plus LambdaQueryWrapper 实现通道配置的增删改查。
 * 所有查询均按 tenantId 隔离，支持多租户。
 *
 * @author ydsz-pmis-team
 * @since 1.3.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlowNotifyChannelServiceImpl implements FlowNotifyChannelService {

    private final FlowNotifyChannelMapper notifyChannelMapper;

    @Override
    @Transactional(readOnly = true)
    public List<FlowNotifyChannelDO> listChannels(String tenantId) {
        LambdaQueryWrapper<FlowNotifyChannelDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FlowNotifyChannelDO::getTenantId, tenantId)
                .orderByDesc(FlowNotifyChannelDO::getCreatedAt);
        return notifyChannelMapper.selectList(wrapper);
    }

    @Override
    @Transactional(readOnly = true)
    public List<FlowNotifyChannelDO> listEnabledChannels(String tenantId) {
        LambdaQueryWrapper<FlowNotifyChannelDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FlowNotifyChannelDO::getTenantId, tenantId)
                .eq(FlowNotifyChannelDO::getEnabled, true)
                .orderByDesc(FlowNotifyChannelDO::getCreatedAt);
        return notifyChannelMapper.selectList(wrapper);
    }

    @Override
    public FlowNotifyChannelDO saveChannel(FlowNotifyChannelDO dto) {
        LocalDateTime now = LocalDateTime.now();
        if (dto.getId() == null) {
            // 新增
            dto.setCreatedAt(now);
            dto.setUpdatedAt(now);
            notifyChannelMapper.insert(dto);
            log.info("[FlowNotifyChannel] 新增通道配置: tenantId={} channelType={} channelName={} id={}",
                    dto.getTenantId(), dto.getChannelType(), dto.getChannelName(), dto.getId());
        } else {
            // 更新
            dto.setUpdatedAt(now);
            notifyChannelMapper.updateById(dto);
            log.info("[FlowNotifyChannel] 更新通道配置: id={} channelType={} channelName={}",
                    dto.getId(), dto.getChannelType(), dto.getChannelName());
        }
        return dto;
    }

    @Override
    public void toggleChannel(String id, Boolean enabled) {
        FlowNotifyChannelDO update = new FlowNotifyChannelDO();
        update.setId(id);
        update.setEnabled(enabled);
        update.setUpdatedAt(LocalDateTime.now());
        notifyChannelMapper.updateById(update);
        log.info("[FlowNotifyChannel] 切换通道状态: id={} enabled={}", id, enabled);
    }

    @Override
    public void deleteChannel(String id) {
        notifyChannelMapper.deleteById(id);
        log.info("[FlowNotifyChannel] 删除通道配置: id={}", id);
    }

    @Override
    @Transactional(readOnly = true)
    public String getConfig(String channelType, String tenantId) {
        LambdaQueryWrapper<FlowNotifyChannelDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FlowNotifyChannelDO::getTenantId, tenantId)
                .eq(FlowNotifyChannelDO::getChannelType, channelType)
                .eq(FlowNotifyChannelDO::getEnabled, true)
                .last("LIMIT 1");
        FlowNotifyChannelDO channel = notifyChannelMapper.selectOne(wrapper);
        return channel == null ? null : channel.getConfig();
    }
}
