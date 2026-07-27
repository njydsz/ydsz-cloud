package com.njydsz.message.infra.mapper.batch;

import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.message.domain.entity.batch.MsgBatch;

/**
 * 消息批次 Mapper。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Mapper
public interface MsgBatchMapper extends BaseMapper<MsgBatch> {
}
