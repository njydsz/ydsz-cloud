package com.njydsz.pmis.message.infra.mapper.batch;

import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.message.domain.entity.batch.MsgAggregateDO;

/**
 * 聚合批次 Mapper
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Mapper
public interface MsgAggregateMapper extends BaseMapper<MsgAggregateDO> {
}
