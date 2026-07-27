package com.njydsz.message.infra.mapper.core;

import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.message.domain.entity.core.MsgLog;

/**
 * 消息发送日志 Mapper
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Mapper
public interface MsgLogMapper extends BaseMapper<MsgLog> {
}
