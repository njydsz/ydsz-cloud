package com.njydsz.message.infra.repository;

import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.common.core.response.PageResponse;
import com.njydsz.message.domain.query.MsgOfflineQuery;
import com.njydsz.message.domain.repository.MsgOfflineRepository;
import com.njydsz.message.domain.vo.MsgOfflineVO;
import com.njydsz.message.infra.converter.MessageConverter;
import com.njydsz.message.infra.entity.MsgOffline;
import com.njydsz.message.infra.mapper.config.MsgOfflineMapper;

/**
 * 离线消息仓储实现（Infra 层）。
 *
 * <p>实现 {@link MsgOfflineRepository} 接口，封装 MsgOfflineMapper 数据访问细节。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Repository
@RequiredArgsConstructor
public class MsgOfflineRepositoryImpl implements MsgOfflineRepository {

  private final MsgOfflineMapper msgOfflineMapper;

  private final MessageConverter converter;

  @Override
  public boolean saveBatch(List<MsgOfflineVO> list) {
    if (list == null || list.isEmpty()) {
      return false;
    }
    List<MsgOffline> entities = list.stream().map(this::voToEntity).toList();
    return msgOfflineMapper.insertBatch(entities) > 0;
  }

  @Override
  public int markPushedByUser(String userId) {
    return msgOfflineMapper.markPushedByUser(userId);
  }

  @Override
  public int markExpired() {
    return msgOfflineMapper.markExpired();
  }

  @Override
  public List<MsgOfflineVO> findList(MsgOfflineQuery query) {
    QueryWrapper<MsgOffline> wrapper = buildWrapper(query);
    return converter.offlineListToVO(msgOfflineMapper.selectList(wrapper));
  }

  @Override
  public PageResponse<List<MsgOfflineVO>> findPage(MsgOfflineQuery query) {
    Page<MsgOffline> page = new Page<>(query.getPageNum(), query.getPageSize());
    QueryWrapper<MsgOffline> wrapper = buildWrapper(query);
    wrapper.orderByDesc("created_at");
    IPage<MsgOffline> entityPage = msgOfflineMapper.selectPage(page, wrapper);
    List<MsgOfflineVO> vos = converter.offlineListToVO(entityPage.getRecords());
    return PageResponse.success(entityPage.getTotal(), (long) query.getPageNum(), (long) query.getPageSize(), vos);
  }

  private QueryWrapper<MsgOffline> buildWrapper(MsgOfflineQuery query) {
    QueryWrapper<MsgOffline> wrapper = new QueryWrapper<>();
    if (query.getUserId() != null && !query.getUserId().isBlank()) {
      wrapper.eq("user_id", query.getUserId());
    }
    if (query.getMsgType() != null && !query.getMsgType().isBlank()) {
      wrapper.eq("msg_type", query.getMsgType());
    }
    if (query.getStatus() != null && !query.getStatus().isBlank()) {
      wrapper.eq("status", query.getStatus());
    }
    wrapper.eq("deleted", 0);
    return wrapper;
  }

  private MsgOffline voToEntity(MsgOfflineVO vo) {
    if (vo == null) {
      return null;
    }
    MsgOffline entity = new MsgOffline();
    entity.setId(vo.getId());
    entity.setUserId(vo.getUserId());
    entity.setMsgType(vo.getMsgType());
    entity.setPayload(vo.getPayload());
    entity.setMsgTimestamp(vo.getMsgTimestamp());
    entity.setStatus(vo.getStatus());
    entity.setPushedAt(vo.getPushedAt());
    entity.setExpiredAt(vo.getExpiredAt());
    return entity;
  }
}
