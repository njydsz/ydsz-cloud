package com.njydsz.message.infra.repository.impl;

import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.message.domain.entity.receipt.MsgReceipt;
import com.njydsz.message.infra.mapper.receipt.MsgReceiptMapper;
import com.njydsz.message.infra.repository.MsgReceiptRepository;

/**
 * 消息回执 Repository 实现。
 *
 * <p>基于 MyBatis-Plus 的 {@link MsgReceiptMapper} 实现 {@link MsgReceiptRepository} 接口。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Repository
@RequiredArgsConstructor
public class MsgReceiptRepositoryImpl implements MsgReceiptRepository {

  private final MsgReceiptMapper msgReceiptMapper;

  @Override
  public int insert(MsgReceipt entity) {
    return msgReceiptMapper.insert(entity);
  }

  @Override
  public List<MsgReceipt> selectList(LambdaQueryWrapper<MsgReceipt> wrapper) {
    return msgReceiptMapper.selectList(wrapper);
  }
}
