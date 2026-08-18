package com.njydsz.message.infra.repository.impl;

import java.util.List;
import java.util.Optional;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.common.core.response.PageResponse;
import com.njydsz.message.domain.dto.core.MessageLogQueryDTO;
import com.njydsz.message.domain.repository.MsgLogRepository;
import com.njydsz.message.domain.vo.MsgLogVO;
import com.njydsz.message.infra.converter.MessageConverter;
import com.njydsz.message.infra.entity.MsgLogDO;
import com.njydsz.message.infra.mapper.core.MsgLogMapper;

/**
 * 消息发送日志仓储实现（Infra 层）。
 *
 * <p>实现 {@link MsgLogRepository} 接口，封装 MsgLogMapper 数据访问细节。
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li>所有数据访问通过本类的语义方法，禁止暴露 Mapper
 *   <li>通过 {@link MessageConverter} 将 DO 转换为 VO 后返回
 *   <li>CUD 入参 VO 通过 {@link MessageConverter} 转换为 DO 后执行数据库操作
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Repository
@RequiredArgsConstructor
public class MsgLogRepositoryImpl implements MsgLogRepository {

  private final MsgLogMapper msgLogMapper;

  private final MessageConverter converter;

  @Override
  public boolean save(MsgLogVO vo) {
    MsgLogDO entity = voToDO(vo);
    return msgLogMapper.insert(entity) > 0;
  }

  @Override
  public Optional<MsgLogVO> findById(String id) {
    return Optional.ofNullable(msgLogMapper.selectById(id)).map(converter::doToVO);
  }

  @Override
  public PageResponse<List<MsgLogVO>> findPage(MessageLogQueryDTO query) {
    Page<MsgLogDO> page = new Page<>(query.getPageNum(), query.getPageSize());
    QueryWrapper<MsgLogDO> wrapper = buildWrapper(query);
    wrapper.orderByDesc("created_at");
    IPage<MsgLogDO> entityPage = msgLogMapper.selectPage(page, wrapper);
    List<MsgLogVO> vos = converter.logDoListToVO(entityPage.getRecords());
    return PageResponse.success(entityPage.getTotal(), (long)query.getPageNum(), (long)query.getPageSize(), vos);
  }

  @Override
  public List<MsgLogVO> findList(MessageLogQueryDTO query) {
    QueryWrapper<MsgLogDO> wrapper = buildWrapper(query);
    wrapper.orderByDesc("created_at");
    return converter.logDoListToVO(msgLogMapper.selectList(wrapper));
  }

  @Override
  public long count(MessageLogQueryDTO query) {
    QueryWrapper<MsgLogDO> wrapper = buildWrapper(query);
    Long count = msgLogMapper.selectCount(wrapper);
    return count != null ? count : 0L;
  }

  @Override
  public boolean deleteById(String id) {
    return msgLogMapper.deleteById(id) > 0;
  }

  @Override
  public boolean saveBatch(List<MsgLogVO> list) {
    if (list == null || list.isEmpty()) {
      return false;
    }
    List<MsgLogDO> entities = list.stream().map(this::voToDO).toList();
    return msgLogMapper.insertBatch(entities) > 0;
  }

  private QueryWrapper<MsgLogDO> buildWrapper(MessageLogQueryDTO query) {
    QueryWrapper<MsgLogDO> wrapper = new QueryWrapper<>();
    if (query.getChannel() != null && !query.getChannel().isBlank()) {
      wrapper.eq("channel", query.getChannel());
    }
    if (query.getBizType() != null && !query.getBizType().isBlank()) {
      wrapper.eq("biz_type", query.getBizType());
    }
    if (query.getBizId() != null && !query.getBizId().isBlank()) {
      wrapper.eq("biz_id", query.getBizId());
    }
    if (query.getStatus() != null && !query.getStatus().isBlank()) {
      wrapper.eq("status", query.getStatus());
    }
    if (query.getReceiver() != null && !query.getReceiver().isBlank()) {
      wrapper.eq("receiver", query.getReceiver());
    }
    if (query.getPriority() != null && !query.getPriority().isBlank()) {
      wrapper.eq("priority", query.getPriority());
    }
    if (query.getRecallStatus() != null && !query.getRecallStatus().isBlank()) {
      wrapper.eq("recall_status", query.getRecallStatus());
    }
    if (query.getKeyword() != null && !query.getKeyword().isBlank()) {
      wrapper.and(w -> w.like("content", query.getKeyword())
          .or().like("receiver", query.getKeyword())
          .or().like("template_code", query.getKeyword()));
    }
    if (query.getMessageGroup() != null && !query.getMessageGroup().isBlank()) {
      wrapper.eq("message_group", query.getMessageGroup());
    }
    wrapper.eq("deleted", 0);
    return wrapper;
  }

  private MsgLogDO voToDO(MsgLogVO vo) {
    if (vo == null) {
      return null;
    }
    MsgLogDO entity = new MsgLogDO();
    entity.setId(vo.getId());
    entity.setChannel(vo.getChannel());
    entity.setBizType(vo.getBizType());
    entity.setBizId(vo.getBizId());
    entity.setReceiver(vo.getReceiver());
    entity.setTemplateCode(vo.getTemplateCode());
    entity.setTemplateParams(vo.getTemplateParams());
    entity.setContent(vo.getContent());
    entity.setStatus(vo.getStatus());
    entity.setErrorMessage(vo.getErrorMessage());
    entity.setPriority(vo.getPriority());
    entity.setSenderId(vo.getSenderId());
    entity.setMessageGroup(vo.getMessageGroup());
    entity.setBatchId(vo.getBatchId());
    entity.setRouteRuleId(vo.getRouteRuleId());
    entity.setCanary(vo.getCanary());
    entity.setCanaryKey(vo.getCanaryKey());
    entity.setDedupKey(vo.getDedupKey());
    entity.setRecallStatus(vo.getRecallStatus());
    entity.setRecallAt(vo.getRecallAt());
    entity.setReceiptStatus(vo.getReceiptStatus());
    entity.setReceiptAt(vo.getReceiptAt());
    entity.setRetryCount(vo.getRetryCount());
    entity.setNextRetryAt(vo.getNextRetryAt());
    entity.setProviderTraceId(vo.getProviderTraceId());
    entity.setCostMs(vo.getCostMs());
    entity.setCost(vo.getCost());
    entity.setTraceId(vo.getTraceId());
    entity.setMsgId(vo.getMsgId());
    entity.setTopic(vo.getTopic());
    entity.setReconsumeTimes(vo.getReconsumeTimes());
    entity.setParentMsgId(vo.getParentMsgId());
    entity.setScheduledAt(vo.getScheduledAt());
    return entity;
  }
}
