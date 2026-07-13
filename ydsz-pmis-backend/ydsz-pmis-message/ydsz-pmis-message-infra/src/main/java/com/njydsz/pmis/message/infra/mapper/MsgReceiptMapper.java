package com.njydsz.pmis.message.infra.mapper.receipt;

import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.message.domain.entity.receipt.MsgReceiptDO;

/**
 * 消息回执 Mapper
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Mapper
public interface MsgReceiptMapper extends BaseMapper<MsgReceiptDO> {
}
