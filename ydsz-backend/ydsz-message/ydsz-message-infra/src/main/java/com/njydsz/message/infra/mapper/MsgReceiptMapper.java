package com.njydsz.message.infra.mapper.receipt;

import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.message.domain.entity.receipt.MsgReceipt;

/**
 * 消息回执 Mapper
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Mapper
public interface MsgReceiptMapper extends BaseMapper<MsgReceipt> {
}
