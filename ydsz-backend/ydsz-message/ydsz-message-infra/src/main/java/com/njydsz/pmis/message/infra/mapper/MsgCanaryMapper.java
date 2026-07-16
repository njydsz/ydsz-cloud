package com.njydsz.message.infra.mapper.canary;

import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.message.domain.entity.canary.MsgCanaryDO;

/**
 * 灰度桶 Mapper
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Mapper
public interface MsgCanaryMapper extends BaseMapper<MsgCanaryDO> {
}
