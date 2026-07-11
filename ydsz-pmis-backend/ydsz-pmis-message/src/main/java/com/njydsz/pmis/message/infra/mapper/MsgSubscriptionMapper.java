package com.njydsz.pmis.message.infra.mapper.config;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.message.domain.entity.config.MsgSubscriptionDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 订阅关系 Mapper
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Mapper
public interface MsgSubscriptionMapper extends BaseMapper<MsgSubscriptionDO> {
}
