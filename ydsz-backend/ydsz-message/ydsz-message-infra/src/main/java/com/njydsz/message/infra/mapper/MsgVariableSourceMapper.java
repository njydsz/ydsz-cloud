package com.njydsz.message.infra.mapper.config;

import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.message.domain.entity.config.MsgVariableSource;

/**
 * 消息变量数据源 Mapper。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Mapper
public interface MsgVariableSourceMapper extends BaseMapper<MsgVariableSource> {
}
