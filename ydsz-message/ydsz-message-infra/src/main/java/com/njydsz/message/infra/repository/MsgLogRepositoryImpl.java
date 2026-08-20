package com.njydsz.message.infra.repository;

import java.util.List;
import java.util.Optional;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.common.core.response.PageResponse;
import com.njydsz.message.domain.dto.MessageLogQueryDTO;
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
 *   <li>通过 {@link MessageConverter} 实现 VO ↔ DO 的双向转换
 *   <li>查询入参使用领域 Query（{@link MessageLogQueryDTO}），返回领域 VO（{@link MsgLogVO}）
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

  // ===== 基本 CRUD =====

  @Override
  public boolean save(MsgLogVO vo) {
    MsgLogDO entity = converter.voToDO(vo);
    return msgLogMapper.insert(entity) > 0;
  }

  @Override
  public boolean update(MsgLogVO vo) {
    MsgLogDO entity = converter.voToDO(vo);
    return msgLogMapper.updateById(entity) > 0;
  }

  @Override
  public boolean deleteById(String id) {
    return msgLogMapper.deleteById(id) > 0;
  }

  // ===== 查询方法 =====

  @Override
  public Optional<MsgLogVO> findById(String id) {
    MsgLogDO entity = msgLogMapper.selectById(id);
    return Optional.ofNullable(entity).map(converter::doToVO);
  }

  @Override
  public Optional<MsgLogVO> findOne(MessageLogQueryDTO query) {
    QueryWrapper<MsgLogDO> wrapper = buildWrapper(query);
    if (query != null && query.getPageSize() != null && query.getPageSize() > 0) {
      // 如果调用方通过 pageSize 暗示 LIMIT，这里不做额外处理，由 buildWrapper 的调用方决定
    }
    MsgLogDO entity = msgLogMapper.selectOne(wrapper);
    return Optional.ofNullable(entity).map(converter::doToVO);
  }

  @Override
  public PageResponse<List<MsgLogVO>> findPage(MessageLogQueryDTO query) {
    Page<MsgLogDO> page = new Page<>(query.getPageNum(), query.getPageSize());
    QueryWrapper<MsgLogDO> wrapper = buildWrapper(query);
    wrapper.orderByDesc("created_at");
    IPage<MsgLogDO> entityPage = msgLogMapper.selectPage(page, wrapper);
    List<MsgLogVO> vos = converter.logDoListToVO(entityPage.getRecords());
    return PageResponse.success(entityPage.getTotal(), (long) query.getPageNum(), (long) query.getPageSize(), vos);
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
  public boolean saveBatch(List<MsgLogVO> list) {
    if (list == null || list.isEmpty()) {
      return false;
    }
    List<MsgLogDO> entities = converter.logVoListToDO(list);
    return msgLogMapper.insertBatch(entities) > 0;
  }

  // ===== 私有辅助方法 =====

  private QueryWrapper<MsgLogDO> buildWrapper(MessageLogQueryDTO query) {
    QueryWrapper<MsgLogDO> wrapper = new QueryWrapper<>();
    if (query == null) {
      return wrapper;
    }
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
    if (query.getMsgId() != null && !query.getMsgId().isBlank()) {
      wrapper.eq("msg_id", query.getMsgId());
    }
    if (query.getReceiptStatus() != null && !query.getReceiptStatus().isBlank()) {
      wrapper.eq("receipt_status", query.getReceiptStatus());
    }
    if (query.getTenantId() != null && !query.getTenantId().isBlank()) {
      wrapper.eq("tenant_id", query.getTenantId());
    }
    if (query.getStartTime() != null && !query.getStartTime().isBlank()) {
      try {
        wrapper.ge("created_at", java.time.LocalDateTime.parse(query.getStartTime()));
      } catch (java.time.format.DateTimeParseException e) {
        // ignore invalid date format
      }
    }
    if (query.getEndTime() != null && !query.getEndTime().isBlank()) {
      try {
        wrapper.le("created_at", java.time.LocalDateTime.parse(query.getEndTime()));
      } catch (java.time.format.DateTimeParseException e) {
        // ignore invalid date format
      }
    }
    wrapper.eq("deleted", 0);
    return wrapper;
  }
}
