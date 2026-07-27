package com.njydsz.message.infra.mapper.config;

import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.message.domain.entity.config.MsgSubscription;

/**
 * 订阅关系 Mapper
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Mapper
public interface MsgSubscriptionMapper extends BaseMapper<MsgSubscription> {
}
