package com.njydsz.pmis.message.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.message.entity.receipt.MsgReceiptDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 消息回执 Mapper
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Mapper
public interface MsgReceiptMapper extends BaseMapper<MsgReceiptDO> {
}
