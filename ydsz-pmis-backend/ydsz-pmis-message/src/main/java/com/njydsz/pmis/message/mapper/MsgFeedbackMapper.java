package com.njydsz.pmis.message.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.message.entity.config.MsgFeedbackDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * P1-4: 消息用户反馈 Mapper
 *
 * @author ydsz-pmis-team
 * @since 1.3.0
 */
@Mapper
public interface MsgFeedbackMapper extends BaseMapper<MsgFeedbackDO> {
}
