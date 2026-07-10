package com.njydsz.pmis.message.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.message.entity.batch.MsgAggregateDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 聚合批次 Mapper
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Mapper
public interface MsgAggregateMapper extends BaseMapper<MsgAggregateDO> {
}
