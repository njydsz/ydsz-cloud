package com.njydsz.message.infra.repository;

import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.message.domain.query.MsgTraceQuery;
import com.njydsz.message.domain.repository.MsgTraceRepository;
import com.njydsz.message.domain.vo.MsgTraceVO;
import com.njydsz.message.infra.converter.MessageConverter;
import com.njydsz.message.infra.entity.MsgTraceDO;
import com.njydsz.message.infra.mapper.config.MsgTraceMapper;

/**
 * 消息轨迹仓储实现（Infra 层）。
 *
 * <p>实现 {@link MsgTraceRepository} 接口，封装 MsgTraceMapper 数据访问细节。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Repository
@RequiredArgsConstructor
public class MsgTraceRepositoryImpl implements MsgTraceRepository {

  private final MsgTraceMapper msgTraceMapper;

  private final MessageConverter converter;

  @Override
  public boolean save(MsgTraceVO vo) {
    MsgTraceDO entity = voToDO(vo);
    return msgTraceMapper.insert(entity) > 0;
  }

  @Override
  public List<MsgTraceVO> findList(MsgTraceQuery query) {
    QueryWrapper<MsgTraceDO> wrapper = new QueryWrapper<>();
    if (query.getMsgId() != null && !query.getMsgId().isBlank()) {
      wrapper.eq("msg_id", query.getMsgId());
    }
    if (query.getTraceId() != null && !query.getTraceId().isBlank()) {
      wrapper.eq("trace_id", query.getTraceId());
    }
    if (query.getNode() != null && !query.getNode().isBlank()) {
      wrapper.eq("node", query.getNode());
    }
    if (query.getStatus() != null && !query.getStatus().isBlank()) {
      wrapper.eq("status", query.getStatus());
    }
    wrapper.eq("deleted", 0);
    wrapper.orderByAsc("event_at");
    return converter.traceDoListToVO(msgTraceMapper.selectList(wrapper));
  }

  private MsgTraceDO voToDO(MsgTraceVO vo) {
    if (vo == null) {
      return null;
    }
    MsgTraceDO entity = new MsgTraceDO();
    entity.setId(vo.getId());
    entity.setMsgId(vo.getMsgId());
    entity.setTraceId(vo.getTraceId());
    entity.setNode(vo.getNode());
    entity.setStatus(vo.getStatus());
    entity.setChannel(vo.getChannel());
    entity.setReceiver(vo.getReceiver());
    entity.setBizType(vo.getBizType());
    entity.setBizId(vo.getBizId());
    entity.setTemplateCode(vo.getTemplateCode());
    entity.setCostMs(vo.getCostMs());
    entity.setMessage(vo.getMessage());
    entity.setExtra(vo.getExtra());
    entity.setEventAt(vo.getEventAt());
    return entity;
  }
}
