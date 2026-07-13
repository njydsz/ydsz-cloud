package com.njydsz.pmis.message.infra.mapper.core;

import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.message.domain.entity.core.MsgLogDO;

/**
 * 消息发送日志 Mapper
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Mapper
public interface MsgLogMapper extends BaseMapper<MsgLogDO> {
}
