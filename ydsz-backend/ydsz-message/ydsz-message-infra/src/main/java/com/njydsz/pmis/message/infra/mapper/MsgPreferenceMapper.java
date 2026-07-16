package com.njydsz.message.infra.mapper.config;

import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.message.domain.entity.config.MsgPreferenceDO;

/**
 * 用户消息偏好 Mapper
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Mapper
public interface MsgPreferenceMapper extends BaseMapper<MsgPreferenceDO> {
}
