package com.njydsz.pmis.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.workflow.entity.FlowNotifyChannelDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 通知通道配置 Mapper
 *
 * <p>对应 pmis_flow_notify_channel 表，管理通知通道配置的增删改查。
 *
 * @author ydsz-pmis-team
 * @since 1.3.0
 */
@Mapper
public interface FlowNotifyChannelMapper extends BaseMapper<FlowNotifyChannelDO> {
}
