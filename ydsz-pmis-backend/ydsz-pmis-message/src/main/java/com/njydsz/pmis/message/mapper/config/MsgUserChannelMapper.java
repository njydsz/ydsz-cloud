package com.njydsz.pmis.message.mapper.config;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.message.entity.config.MsgUserChannelDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户通道绑定 Mapper。
 *
 * @author ydsz-pmis-team
 * @since 1.5.0
 */
@Mapper
public interface MsgUserChannelMapper extends BaseMapper<MsgUserChannelDO> {
}
