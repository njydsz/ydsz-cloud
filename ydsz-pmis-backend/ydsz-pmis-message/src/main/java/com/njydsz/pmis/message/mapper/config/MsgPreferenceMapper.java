package com.njydsz.pmis.message.mapper.config;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.message.entity.config.MsgPreferenceDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户消息偏好 Mapper
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Mapper
public interface MsgPreferenceMapper extends BaseMapper<MsgPreferenceDO> {
}
