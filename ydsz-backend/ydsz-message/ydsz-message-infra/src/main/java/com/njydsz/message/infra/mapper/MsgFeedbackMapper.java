package com.njydsz.message.infra.mapper.config;

import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.message.domain.entity.config.MsgFeedbackDO;

/**
 * P1-4: 消息用户反馈 Mapper
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Mapper
public interface MsgFeedbackMapper extends BaseMapper<MsgFeedbackDO> {
}
