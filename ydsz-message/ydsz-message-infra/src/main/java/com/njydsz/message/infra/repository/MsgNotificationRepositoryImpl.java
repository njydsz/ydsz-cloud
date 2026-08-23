package com.njydsz.message.infra.repository;

import java.util.List;
import java.util.Optional;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.common.core.response.PageResponse;
import com.njydsz.message.domain.dto.MsgNotificationDTO;
import com.njydsz.message.domain.dto.NotificationQueryDTO;
import com.njydsz.message.domain.repository.MsgNotificationRepository;
import com.njydsz.message.domain.vo.MsgNotificationVO;
import com.njydsz.message.infra.converter.MessageConverter;
import com.njydsz.message.infra.entity.MsgNotificationDO;
import com.njydsz.message.infra.mapper.core.MsgNotificationMapper;

/**
 * 站内通知仓储实现（Infra 层）。
 *
 * <p>实现 {@link MsgNotificationRepository} 接口，封装 MsgNotificationMapper 数据访问细节。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Repository
@RequiredArgsConstructor
public class MsgNotificationRepositoryImpl implements MsgNotificationRepository {

  private final MsgNotificationMapper msgNotificationMapper;

  private final MessageConverter converter;

  @Override
  public boolean saveBatch(List<MsgNotificationDTO> list) {
    if (list == null || list.isEmpty()) {
      return false;
    }
    List<MsgNotificationDO> entities = converter.notificationDtoListToDO(list);
    return msgNotificationMapper.insertBatch(entities) > 0;
  }

  @Override
  public Optional<MsgNotificationVO> findById(String id) {
    return Optional.ofNullable(msgNotificationMapper.selectById(id)).map(converter::doToVO);
  }

  @Override
  public boolean update(MsgNotificationDTO dto) {
    MsgNotificationDO entity = converter.dtoToDO(dto);
    return msgNotificationMapper.updateById(entity) > 0;
  }

  @Override
  public PageResponse<List<MsgNotificationVO>> findPage(NotificationQueryDTO query) {
    Page<MsgNotificationDO> page = new Page<>(query.getPageNum(), query.getPageSize());
    QueryWrapper<MsgNotificationDO> wrapper = buildWrapper(query);
    wrapper.orderByDesc("created_at");
    IPage<MsgNotificationDO> entityPage = msgNotificationMapper.selectPage(page, wrapper);
    List<MsgNotificationVO> vos = converter.notificationDoListToVO(entityPage.getRecords());
    return PageResponse.success(entityPage.getTotal(), (long) query.getPageNum(), (long) query.getPageSize(), vos);
  }

  @Override
  public List<MsgNotificationVO> findList(NotificationQueryDTO query) {
    QueryWrapper<MsgNotificationDO> wrapper = buildWrapper(query);
    wrapper.orderByDesc("created_at");
    return converter.notificationDoListToVO(msgNotificationMapper.selectList(wrapper));
  }

  @Override
  public long count(NotificationQueryDTO query) {
    QueryWrapper<MsgNotificationDO> wrapper = buildWrapper(query);
    Long count = msgNotificationMapper.selectCount(wrapper);
    return count != null ? count : 0L;
  }

  @Override
  public boolean deleteById(String id) {
    return msgNotificationMapper.deleteById(id) > 0;
  }

  @Override
  public int markRead(String id, String userId) {
    return msgNotificationMapper.markRead(id, userId);
  }

  @Override
  public int markAllRead(String userId, int batchSize) {
    return msgNotificationMapper.markAllRead(userId, batchSize);
  }

  @Override
  public long countUnread(String userId) {
    Long count = msgNotificationMapper.countUnread(userId);
    return count != null ? count : 0L;
  }

  private QueryWrapper<MsgNotificationDO> buildWrapper(NotificationQueryDTO query) {
    QueryWrapper<MsgNotificationDO> wrapper = new QueryWrapper<>();
    if (query.getCategory() != null && !query.getCategory().isBlank()) {
      wrapper.eq("category", query.getCategory());
    }
    if (query.getLevel() != null && !query.getLevel().isBlank()) {
      wrapper.eq("level", query.getLevel());
    }
    if (query.getReadStatus() != null) {
      wrapper.eq("read_status", query.getReadStatus());
    }
    wrapper.eq("deleted", 0);
    return wrapper;
  }

}

