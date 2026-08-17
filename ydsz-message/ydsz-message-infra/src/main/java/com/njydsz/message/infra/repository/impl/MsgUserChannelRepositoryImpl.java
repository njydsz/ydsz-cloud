package com.njydsz.message.infra.repository.impl;

import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.message.domain.entity.config.MsgUserChannel;
import com.njydsz.message.infra.mapper.config.MsgUserChannelMapper;
import com.njydsz.message.infra.repository.MsgUserChannelRepository;

/**
 * 用户通道绑定 Repository 实现。
 *
 * <p>基于 MyBatis-Plus 的 {@link MsgUserChannelMapper} 实现 {@link MsgUserChannelRepository} 接口。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Repository
@RequiredArgsConstructor
public class MsgUserChannelRepositoryImpl implements MsgUserChannelRepository {

  private final MsgUserChannelMapper msgUserChannelMapper;

  @Override
  public int insert(MsgUserChannel entity) {
    return msgUserChannelMapper.insert(entity);
  }

  @Override
  public int updateById(MsgUserChannel entity) {
    return msgUserChannelMapper.updateById(entity);
  }

  @Override
  public int deleteById(String id) {
    return msgUserChannelMapper.deleteById(id);
  }

  @Override
  public MsgUserChannel selectOne(LambdaQueryWrapper<MsgUserChannel> wrapper) {
    return msgUserChannelMapper.selectOne(wrapper);
  }

  @Override
  public List<MsgUserChannel> selectList(LambdaQueryWrapper<MsgUserChannel> wrapper) {
    return msgUserChannelMapper.selectList(wrapper);
  }
}
