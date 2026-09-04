package com.njydsz.message.infra.repository;

import java.util.List;
import java.util.Optional;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.message.domain.converter.MessageConverter;
import com.njydsz.message.domain.entity.MsgBatch;
import com.njydsz.message.domain.query.MsgBatchQuery;
import com.njydsz.message.domain.repository.MsgBatchRepository;
import com.njydsz.message.domain.vo.MsgBatchVO;
import com.njydsz.message.infra.mapper.batch.MsgBatchMapper;

/**
 * 消息批次仓储实现（Infra 层）。
 *
 * <p>实现 {@link MsgBatchRepository} 接口，封装 MsgBatchMapper 数据访问细节。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Repository
@RequiredArgsConstructor
public class MsgBatchRepositoryImpl implements MsgBatchRepository {

  private final MsgBatchMapper msgBatchMapper;

  private final MessageConverter converter;

  @Override
  public boolean save(MsgBatchVO vo) {
    MsgBatch entity = voToEntity(vo);
    return msgBatchMapper.insert(entity) > 0;
  }

  @Override
  public Optional<MsgBatchVO> findById(String id) {
    return Optional.ofNullable(msgBatchMapper.selectById(id)).map(converter::entityToVO);
  }

  @Override
  public boolean update(MsgBatchVO vo) {
    MsgBatch entity = voToEntity(vo);
    return msgBatchMapper.updateById(entity) > 0;
  }

  @Override
  public Optional<MsgBatchVO> findOne(MsgBatchQuery query) {
    QueryWrapper<MsgBatch> wrapper = new QueryWrapper<>();
    if (query.getBatchId() != null && !query.getBatchId().isBlank()) {
      wrapper.eq("batch_id", query.getBatchId());
    }
    if (query.getChannel() != null && !query.getChannel().isBlank()) {
      wrapper.eq("channel", query.getChannel());
    }
    if (query.getBizType() != null && !query.getBizType().isBlank()) {
      wrapper.eq("biz_type", query.getBizType());
    }
    if (query.getStatus() != null && !query.getStatus().isBlank()) {
      wrapper.eq("status", query.getStatus());
    }
    if (query.getSenderId() != null && !query.getSenderId().isBlank()) {
      wrapper.eq("sender_id", query.getSenderId());
    }
    wrapper.eq("deleted", 0);
    wrapper.last("LIMIT 1");
    MsgBatch entity = msgBatchMapper.selectOne(wrapper);
    return Optional.ofNullable(entity).map(converter::entityToVO);
  }

  @Override
  public List<MsgBatchVO> findList(MsgBatchQuery query) {
    QueryWrapper<MsgBatch> wrapper = new QueryWrapper<>();
    if (query.getBatchId() != null && !query.getBatchId().isBlank()) {
      wrapper.eq("batch_id", query.getBatchId());
    }
    if (query.getChannel() != null && !query.getChannel().isBlank()) {
      wrapper.eq("channel", query.getChannel());
    }
    if (query.getBizType() != null && !query.getBizType().isBlank()) {
      wrapper.eq("biz_type", query.getBizType());
    }
    if (query.getStatus() != null && !query.getStatus().isBlank()) {
      wrapper.eq("status", query.getStatus());
    }
    if (query.getSenderId() != null && !query.getSenderId().isBlank()) {
      wrapper.eq("sender_id", query.getSenderId());
    }
    wrapper.eq("deleted", 0);
    wrapper.orderByDesc("created_at");
    return converter.batchListToVO(msgBatchMapper.selectList(wrapper));
  }

  private MsgBatch voToEntity(MsgBatchVO vo) {
    if (vo == null) {
      return null;
    }
    MsgBatch entity = new MsgBatch();
    entity.setId(vo.getId());
    entity.setBatchId(vo.getBatchId());
    entity.setBatchName(vo.getBatchName());
    entity.setChannel(vo.getChannel());
    entity.setTemplateCode(vo.getTemplateCode());
    entity.setBizType(vo.getBizType());
    entity.setTotal(vo.getTotal());
    entity.setSuccess(vo.getSuccess());
    entity.setFailed(vo.getFailed());
    entity.setSkipped(vo.getSkipped());
    entity.setStatus(vo.getStatus());
    entity.setAudienceSource(vo.getAudienceSource());
    entity.setErrorMessage(vo.getErrorMessage());
    entity.setStartedAt(vo.getStartedAt());
    entity.setCompletedAt(vo.getCompletedAt());
    entity.setSenderId(vo.getSenderId());
    return entity;
  }
}
