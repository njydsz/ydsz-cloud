package com.njydsz.pmis.message.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.message.entity.config.MsgTraceDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 消息轨迹 Mapper
 *
 * @author ydsz-pmis-team
 * @since 1.3.0
 */
@Mapper
public interface MsgTraceMapper extends BaseMapper<MsgTraceDO> {
}
