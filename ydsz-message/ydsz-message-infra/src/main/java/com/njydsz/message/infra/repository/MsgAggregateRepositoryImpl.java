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
import com.njydsz.message.infra.entity.MsgAggregateDO;
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
    MsgAggregateDO entity = voToDO(vo);
    return msgAggregateMapper.insert(entity) > 0;
  }

  @Override
  public Optional<MsgAggregateVO> findById(String id) {
    return Optional.ofNullable(msgAggregateMapper.selectById(id)).map(converter::doToVO);
  }

  @Override
  public boolean update(MsgAggregateVO vo) {
    MsgAggregateDO entity = voToDO(vo);
    return msgAggregateMapper.updateById(entity) > 0;
  }

  @Override
  public List<MsgAggregateVO> findList(MsgAggregateQuery query) {
    QueryWrapper<MsgAggregateDO> wrapper = buildWrapper(query);
    return converter.aggregateDoListToVO(msgAggregateMapper.selectList(wrapper));
  }

  @Override
  public PageResponse<List<MsgAggregateVO>> findPage(MsgAggregateQuery query) {
    Page<MsgAggregateDO> page = new Page<>(query.getPageNum(), query.getPageSize());
    QueryWrapper<MsgAggregateDO> wrapper = buildWrapper(query);
    wrapper.orderByDesc("created_at");
    IPage<MsgAggregateDO> entityPage = msgAggregateMapper.selectPage(page, wrapper);
    List<MsgAggregateVO> vos = converter.aggregateDoListToVO(entityPage.getRecords());
    return PageResponse.success(entityPage.getTotal(), (long) query.getPageNum(), (long) query.getPageSize(), vos);
  }

  private QueryWrapper<MsgAggregateDO> buildWrapper(MsgAggregateQuery query) {
    QueryWrapper<MsgAggregateDO> wrapper = new QueryWrapper<>();
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
    wrapper.eq("deleted", 0);
    return wrapper;
  }

  private MsgAggregateDO voToDO(MsgAggregateVO vo) {
    if (vo == null) {
      return null;
    }
    MsgAggregateDO entity = new MsgAggregateDO();
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
