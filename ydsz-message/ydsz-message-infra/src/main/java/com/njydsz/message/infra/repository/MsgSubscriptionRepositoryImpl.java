package com.njydsz.message.infra.repository;

import java.util.List;
import java.util.Optional;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.common.core.response.PageResponse;
import com.njydsz.message.domain.query.MsgSubscriptionQuery;
import com.njydsz.message.domain.repository.MsgSubscriptionRepository;
import com.njydsz.message.domain.vo.MsgSubscriptionVO;
import com.njydsz.message.infra.converter.MessageConverter;
import com.njydsz.message.infra.entity.MsgSubscriptionDO;
import com.njydsz.message.infra.mapper.config.MsgSubscriptionMapper;

/**
 * 消息订阅关系仓储实现（Infra 层）。
 *
 * <p>实现 {@link MsgSubscriptionRepository} 接口，封装 MsgSubscriptionMapper 数据访问细节。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Repository
@RequiredArgsConstructor
public class MsgSubscriptionRepositoryImpl implements MsgSubscriptionRepository {

  private final MsgSubscriptionMapper msgSubscriptionMapper;

  private final MessageConverter converter;

  @Override
  public boolean save(MsgSubscriptionVO vo) {
    MsgSubscriptionDO entity = voToDO(vo);
    return msgSubscriptionMapper.insert(entity) > 0;
  }

  @Override
  public boolean update(MsgSubscriptionVO vo) {
    MsgSubscriptionDO entity = voToDO(vo);
    return msgSubscriptionMapper.updateById(entity) > 0;
  }

  @Override
  public Optional<MsgSubscriptionVO> findOne(MsgSubscriptionQuery query) {
    QueryWrapper<MsgSubscriptionDO> wrapper = buildWrapper(query);
    return Optional.ofNullable(msgSubscriptionMapper.selectOne(wrapper)).map(converter::doToVO);
  }

  @Override
  public List<MsgSubscriptionVO> findList(MsgSubscriptionQuery query) {
    QueryWrapper<MsgSubscriptionDO> wrapper = buildWrapper(query);
    return converter.subscriptionDoListToVO(msgSubscriptionMapper.selectList(wrapper));
  }

  @Override
  public long count(MsgSubscriptionQuery query) {
    QueryWrapper<MsgSubscriptionDO> wrapper = buildWrapper(query);
    Long count = msgSubscriptionMapper.selectCount(wrapper);
    return count != null ? count : 0L;
  }

  @Override
  public PageResponse<List<MsgSubscriptionVO>> findPage(MsgSubscriptionQuery query) {
    Page<MsgSubscriptionDO> page = new Page<>(query.getPageNum(), query.getPageSize());
    QueryWrapper<MsgSubscriptionDO> wrapper = buildWrapper(query);
    wrapper.orderByDesc("created_at");
    IPage<MsgSubscriptionDO> entityPage = msgSubscriptionMapper.selectPage(page, wrapper);
    List<MsgSubscriptionVO> vos = converter.subscriptionDoListToVO(entityPage.getRecords());
    return PageResponse.success(entityPage.getTotal(), (long)query.getPageNum(), (long)query.getPageSize(), vos);
  }

  private QueryWrapper<MsgSubscriptionDO> buildWrapper(MsgSubscriptionQuery query) {
    QueryWrapper<MsgSubscriptionDO> wrapper = new QueryWrapper<>();
    if (query.getUserId() != null && !query.getUserId().isBlank()) {
      wrapper.eq("user_id", query.getUserId());
    }
    if (query.getTopicCode() != null && !query.getTopicCode().isBlank()) {
      wrapper.eq("topic_code", query.getTopicCode());
    }
    if (query.getChannel() != null && !query.getChannel().isBlank()) {
      wrapper.eq("channel", query.getChannel());
    }
    if (query.getStatus() != null && !query.getStatus().isBlank()) {
      wrapper.eq("status", query.getStatus());
    }
    wrapper.eq("deleted", 0);
    return wrapper;
  }

  private MsgSubscriptionDO voToDO(MsgSubscriptionVO vo) {
    if (vo == null) {
      return null;
    }
    MsgSubscriptionDO entity = new MsgSubscriptionDO();
    entity.setId(vo.getId());
    entity.setUserId(vo.getUserId());
    entity.setTopicCode(vo.getTopicCode());
    entity.setChannel(vo.getChannel());
    entity.setStatus(vo.getStatus());
    entity.setRoleScope(vo.getRoleScope());
    entity.setExtra(vo.getExtra());
    entity.setUnsubscribedAt(vo.getUnsubscribedAt());
    return entity;
  }
}
