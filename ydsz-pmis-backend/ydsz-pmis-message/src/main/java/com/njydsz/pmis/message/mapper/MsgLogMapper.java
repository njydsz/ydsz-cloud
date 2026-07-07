package com.njydsz.pmis.message.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.message.entity.MsgLogDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 消息发送日志 Mapper
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Mapper
public interface MsgLogMapper extends BaseMapper<MsgLogDO> {
}
