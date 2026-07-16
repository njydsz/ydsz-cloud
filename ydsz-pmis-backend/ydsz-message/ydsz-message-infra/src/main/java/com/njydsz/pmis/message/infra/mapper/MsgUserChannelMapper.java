package com.njydsz.message.infra.mapper.config;

import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.message.domain.entity.config.MsgUserChannelDO;

/**
 * 用户通道绑定 Mapper。
 *
 * @author ydsz-team
 * @since 1.5.0
 */
@Mapper
public interface MsgUserChannelMapper extends BaseMapper<MsgUserChannelDO> {
}
