package com.njydsz.message.infra.repository;

import java.util.List;
import java.util.Optional;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.common.core.response.PageResponse;
import com.njydsz.message.domain.query.MsgAggregateQuery;
import com.njydsz.message.domain.repository.MsgAggregateRepository;
import com.njydsz.message.domain.vo.MsgAggregateVO;
import com.njydsz.message.infra.converter.MessageConverter;
import com.njydsz.message.infra.entity.MsgAggregate;
import com.njydsz.message.infra.mapper.batch.MsgAggregateMapper;

/**
 * 聚合批次仓储实现（Infra 层）。
 *
 * <p>实现 {@link MsgAggregateRepository} 接口，封装 MsgAggregateMapper 数据访问细节。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Repository
@RequiredArgsConstructor
public class MsgAggregateRepositoryImpl implements MsgAggregateRepository {

  private final MsgAggregateMapper msgAggregateMapper;

  private final MessageConverter converter;

  @Override
  public boolean save(MsgAggregateVO vo) {
    MsgAggregate entity = voToEntity(vo);
    return msgAggregateMapper.insert(entity) > 0;
  }

  @Override
  public Optional<MsgAggregateVO> findById(String id) {
    return Optional.ofNullable(msgAggregateMapper.selectById(id)).map(converter::doToVO);
  }

  @Override
  public boolean update(MsgAggregateVO vo) {
    MsgAggregate entity = voToEntity(vo);
    return msgAggregateMapper.updateById(entity) > 0;
  }

  @Override
  public Optional<MsgAggregateVO> findOne(MsgAggregateQuery query) {
    QueryWrapper<MsgAggregate> wrapper = buildWrapper(query);
    wrapper.last("LIMIT 1");
    MsgAggregate entity = msgAggregateMapper.selectOne(wrapper);
    return Optional.ofNullable(entity).map(converter::doToVO);
  }

  @Override
  public List<MsgAggregateVO> findList(MsgAggregateQuery query) {
    QueryWrapper<MsgAggregate> wrapper = buildWrapper(query);
    return converter.aggregateListToVO(msgAggregateMapper.selectList(wrapper));
  }

  @Override
  public PageResponse<List<MsgAggregateVO>> findPage(MsgAggregateQuery query) {
    Page<MsgAggregate> page = new Page<>(query.getPageNum(), query.getPageSize());
    QueryWrapper<MsgAggregate> wrapper = buildWrapper(query);
    wrapper.orderByDesc("created_at");
    IPage<MsgAggregate> entityPage = msgAggregateMapper.selectPage(page, wrapper);
    List<MsgAggregateVO> vos = converter.aggregateListToVO(entityPage.getRecords());
    return PageResponse.success(entityPage.getTotal(), (long) query.getPageNum(), (long) query.getPageSize(), vos);
  }

  @Override
  public int updateStatus(String id, String fromStatus, String toStatus) {
    QueryWrapper<MsgAggregate> wrapper = new QueryWrapper<>();
    wrapper.eq("id", id);
    wrapper.eq("batch_status", fromStatus);
    MsgAggregate entity = new MsgAggregate();
    entity.setBatchStatus(toStatus);
    return msgAggregateMapper.update(entity, wrapper);
  }

  @Override
  public int updateStatusByGroup(
      String group, String receiver, String fromStatus, String toStatus) {
    QueryWrapper<MsgAggregate> wrapper = new QueryWrapper<>();
    wrapper.eq("aggregate_group", group);
    wrapper.eq("receiver", receiver);
    wrapper.eq("batch_status", fromStatus);
    MsgAggregate entity = new MsgAggregate();
    entity.setBatchStatus(toStatus);
    return msgAggregateMapper.update(entity, wrapper);
  }

  private QueryWrapper<MsgAggregate> buildWrapper(MsgAggregateQuery query) {
    QueryWrapper<MsgAggregate> wrapper = new QueryWrapper<>();
    if (query.getAggregateGroup() != null && !query.getAggregateGroup().isBlank()) {
      wrapper.eq("aggregate_group", query.getAggregateGroup());
    }
    if (query.getReceiver() != null && !query.getReceiver().isBlank()) {
      wrapper.eq("receiver", query.getReceiver());
    }
    if (query.getChannel() != null && !query.getChannel().isBlank()) {
      wrapper.eq("channel", query.getChannel());
    }
    if (query.getBatchStatus() != null && !query.getBatchStatus().isBlank()) {
      wrapper.eq("batch_status", query.getBatchStatus());
    }
    if (query.getScheduledSendAtBefore() != null) {
      wrapper.le("scheduled_send_at", query.getScheduledSendAtBefore());
    }
    wrapper.eq("deleted", 0);
    return wrapper;
  }

  private MsgAggregate voToEntity(MsgAggregateVO vo) {
    if (vo == null) {
      return null;
    }
    MsgAggregate entity = new MsgAggregate();
    entity.setId(vo.getId());
    entity.setAggregateGroup(vo.getAggregateGroup());
    entity.setReceiver(vo.getReceiver());
    entity.setChannel(vo.getChannel());
    entity.setBatchStatus(vo.getBatchStatus());
    entity.setMessageCount(vo.getMessageCount());
    entity.setFirstMessageAt(vo.getFirstMessageAt());
    entity.setLastMessageAt(vo.getLastMessageAt());
    entity.setScheduledSendAt(vo.getScheduledSendAt());
    entity.setSentAt(vo.getSentAt());
    entity.setDigestContent(vo.getDigestContent());
    return entity;
  }
}
