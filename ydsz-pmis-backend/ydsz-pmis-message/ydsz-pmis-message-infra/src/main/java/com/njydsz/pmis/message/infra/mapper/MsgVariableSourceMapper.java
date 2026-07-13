package com.njydsz.pmis.message.infra.mapper.config;

import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.message.domain.entity.config.MsgVariableSourceDO;

/**
 * 消息变量数据源 Mapper。
 *
 * @author ydsz-pmis-team
 * @since 1.5.0
 */
@Mapper
public interface MsgVariableSourceMapper extends BaseMapper<MsgVariableSourceDO> {
}
