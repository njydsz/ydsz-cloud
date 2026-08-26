package com.njydsz.message.infra.repository;

import java.util.List;
import java.util.Optional;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.message.domain.query.MsgUserChannelQuery;
import com.njydsz.message.domain.repository.MsgUserChannelRepository;
import com.njydsz.message.domain.vo.MsgUserChannelVO;
import com.njydsz.message.infra.converter.MessageConverter;
import com.njydsz.message.infra.entity.MsgUserChannel;
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
    MsgUserChannel entity = voToEntity(vo);
    return msgUserChannelMapper.insert(entity) > 0;
  }

  @Override
  public boolean update(MsgUserChannelVO vo) {
    MsgUserChannel entity = voToEntity(vo);
    return msgUserChannelMapper.updateById(entity) > 0;
  }

  @Override
  public boolean deleteById(String id) {
    return msgUserChannelMapper.deleteById(id) > 0;
  }

  @Override
  public Optional<MsgUserChannelVO> findOne(MsgUserChannelQuery query) {
    QueryWrapper<MsgUserChannel> wrapper = buildWrapper(query);
    if (query.isPrimaryFirst()) {
      wrapper.orderByDesc("is_primary");
    }
    return Optional.ofNullable(msgUserChannelMapper.selectOne(wrapper)).map(converter::doToVO);
  }

  @Override
  public List<MsgUserChannelVO> findList(MsgUserChannelQuery query) {
    QueryWrapper<MsgUserChannel> wrapper = buildWrapper(query);
    wrapper.orderByDesc("is_primary");
    return converter.userChannelListToVO(msgUserChannelMapper.selectList(wrapper));
  }

  private QueryWrapper<MsgUserChannel> buildWrapper(MsgUserChannelQuery query) {
    QueryWrapper<MsgUserChannel> wrapper = new QueryWrapper<>();
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

  private MsgUserChannel voToEntity(MsgUserChannelVO vo) {
    if (vo == null) {
      return null;
    }
    MsgUserChannel entity = new MsgUserChannel();
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
