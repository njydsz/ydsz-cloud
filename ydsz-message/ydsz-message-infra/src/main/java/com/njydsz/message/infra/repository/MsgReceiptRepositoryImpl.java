package com.njydsz.message.infra.repository;

import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.message.domain.converter.MessageConverter;
import com.njydsz.message.domain.entity.MsgReceipt;
import com.njydsz.message.domain.query.MsgReceiptQuery;
import com.njydsz.message.domain.repository.MsgReceiptRepository;
import com.njydsz.message.domain.vo.MsgReceiptVO;
import com.njydsz.message.infra.mapper.receipt.MsgReceiptMapper;

/**
 * 消息回执仓储实现（Infra 层）。
 *
 * <p>实现 {@link MsgReceiptRepository} 接口，封装 MsgReceiptMapper 数据访问细节。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Repository
@RequiredArgsConstructor
public class MsgReceiptRepositoryImpl implements MsgReceiptRepository {

  private final MsgReceiptMapper msgReceiptMapper;

  private final MessageConverter converter;

  @Override
  public boolean save(MsgReceiptVO vo) {
    MsgReceipt entity = voToEntity(vo);
    return msgReceiptMapper.insert(entity) > 0;
  }

  @Override
  public List<MsgReceiptVO> findList(MsgReceiptQuery query) {
    QueryWrapper<MsgReceipt> wrapper = new QueryWrapper<>();
    if (query.getLogId() != null && !query.getLogId().isBlank()) {
      wrapper.eq("log_id", query.getLogId());
    }
    if (query.getReceiptType() != null && !query.getReceiptType().isBlank()) {
      wrapper.eq("receipt_type", query.getReceiptType());
    }
    if (query.getProviderCode() != null && !query.getProviderCode().isBlank()) {
      wrapper.eq("provider_code", query.getProviderCode());
    }
    wrapper.eq("deleted", 0);
    wrapper.orderByDesc("receipt_time");
    return converter.receiptListToVO(msgReceiptMapper.selectList(wrapper));
  }

  private MsgReceipt voToEntity(MsgReceiptVO vo) {
    if (vo == null) {
      return null;
    }
    MsgReceipt entity = new MsgReceipt();
    entity.setId(vo.getId());
    entity.setLogId(vo.getLogId());
    entity.setProviderTraceId(vo.getProviderTraceId());
    entity.setReceiptType(vo.getReceiptType());
    entity.setReceiptTime(vo.getReceiptTime());
    entity.setProviderCode(vo.getProviderCode());
    entity.setProviderMsg(vo.getProviderMsg());
    entity.setRawResponse(vo.getRawResponse());
    return entity;
  }
}
