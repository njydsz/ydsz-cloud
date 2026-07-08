package com.njydsz.pmis.message.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.message.entity.MsgBatchDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 消息批次 Mapper。
 *
 * @author ydsz-pmis-team
 * @since 1.2.0
 */
@Mapper
public interface MsgBatchMapper extends BaseMapper<MsgBatchDO> {
}
