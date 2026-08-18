package com.njydsz.message.infra.repository.impl;

import java.util.List;
import java.util.Optional;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.message.domain.query.MsgUserChannelQuery;
import com.njydsz.message.domain.repository.MsgUserChannelRepository;
import com.njydsz.message.domain.vo.MsgUserChannelVO;
import com.njydsz.message.infra.converter.MessageConverter;
import com.njydsz.message.infra.entity.MsgUserChannelDO;
import com.njydsz.message.infra.mapper.config.MsgUserChannelMapper;

/**
 * 用户通道绑定仓储实现（Infra 层）。
 *
 * <p>实现 {@link MsgUserChannelRepository} 接口，封装 MsgUserChannelMapper 数据访问细节。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Repository
@RequiredArgsConstructor
public class MsgUserChannelRepositoryImpl implements MsgUserChannelRepository {

  private final MsgUserChannelMapper msgUserChannelMapper;

  private final MessageConverter converter;

  @Override
  public boolean save(MsgUserChannelVO vo) {
    MsgUserChannelDO entity = voToDO(vo);
    return msgUserChannelMapper.insert(entity) > 0;
  }

  @Override
  public boolean update(MsgUserChannelVO vo) {
    MsgUserChannelDO entity = voToDO(vo);
    return msgUserChannelMapper.updateById(entity) > 0;
  }

  @Override
  public boolean deleteById(String id) {
    return msgUserChannelMapper.deleteById(id) > 0;
  }

  @Override
  public Optional<MsgUserChannelVO> findOne(MsgUserChannelQuery query) {
    QueryWrapper<MsgUserChannelDO> wrapper = buildWrapper(query);
    return Optional.ofNullable(msgUserChannelMapper.selectOne(wrapper)).map(converter::doToVO);
  }

  @Override
  public List<MsgUserChannelVO> findList(MsgUserChannelQuery query) {
    QueryWrapper<MsgUserChannelDO> wrapper = buildWrapper(query);
    return converter.userChannelDoListToVO(msgUserChannelMapper.selectList(wrapper));
  }

  private QueryWrapper<MsgUserChannelDO> buildWrapper(MsgUserChannelQuery query) {
    QueryWrapper<MsgUserChannelDO> wrapper = new QueryWrapper<>();
    if (query.getUserId() != null && !query.getUserId().isBlank()) {
      wrapper.eq("user_id", query.getUserId());
    }
    if (query.getChannelType() != null && !query.getChannelType().isBlank()) {
      wrapper.eq("channel_type", query.getChannelType());
    }
    if (query.getStatus() != null && !query.getStatus().isBlank()) {
      wrapper.eq("status", query.getStatus());
    }
    wrapper.eq("deleted", 0);
    return wrapper;
  }

  private MsgUserChannelDO voToDO(MsgUserChannelVO vo) {
    if (vo == null) {
      return null;
    }
    MsgUserChannelDO entity = new MsgUserChannelDO();
    entity.setId(vo.getId());
    entity.setUserId(vo.getUserId());
    entity.setChannelType(vo.getChannelType());
    entity.setChannelUserId(vo.getChannelUserId());
    entity.setVerified(vo.getVerified());
    entity.setIsPrimary(vo.getIsPrimary());
    entity.setExtra(vo.getExtra());
    return entity;
  }
}
