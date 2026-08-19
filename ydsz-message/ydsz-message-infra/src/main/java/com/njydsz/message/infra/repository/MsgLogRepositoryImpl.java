package com.njydsz.message.infra.repository;

import java.util.List;
import java.util.Optional;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.common.core.response.PageResponse;
import com.njydsz.message.domain.dto.MessageLogQueryDTO;
import com.njydsz.message.infra.entity.MsgLog;
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
 *   <li>通过 {@link MessageConverter} 实现领域实体 ↔ DO 的双向转换
 *   <li>领域实体入参方法：内部转换为 DO 后委托 Mapper 执行
 *   <li>查询返回领域实体方法：Mapper 返回 DO 后转换为领域实体
 *   <li>查询返回 VO 方法：Mapper 返回 DO 后转换为 VO
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

  // ===== 基本 CRUD（领域实体入参） =====

  @Override
  public int insert(MsgLog log) {
    return msgLogMapper.insert(converter.entityToDO(log));
  }

  @Override
  public int updateById(MsgLog log) {
    return msgLogMapper.updateById(converter.entityToDO(log));
  }

  @Override
  public int update(MsgLog entity, Wrapper<MsgLog> updateWrapper) {
    // 注意：MyBatis-Plus 的 update(entity, wrapper) 需要实体类型与 Wrapper 类型一致。
    // 由于 Wrapper<MsgLog> 是针对领域实体的，而 Mapper 操作的是 MsgLogDO，
    // 这里将领域实体转换为 DO 后，使用 DO 的 Wrapper 进行更新。
    if (entity != null && entity.getId() != null) {
      return msgLogMapper.updateById(converter.entityToDO(entity));
    }
    return 0;
  }

  @Override
  public boolean save(MsgLogVO vo) {
    MsgLogDO entity = converter.voToDO(vo);
    return msgLogMapper.insert(entity) > 0;
  }

  @Override
  public boolean deleteById(String id) {
    return msgLogMapper.deleteById(id) > 0;
  }

  // ===== 查询方法（返回领域实体） =====

  @Override
  public MsgLog selectById(String id) {
    MsgLogDO entity = msgLogMapper.selectById(id);
    return converter.doToEntity(entity);
  }

  @Override
  public Optional<MsgLogVO> findById(String id) {
    MsgLogDO entity = msgLogMapper.selectById(id);
    return Optional.ofNullable(entity).map(converter::doToVO);
  }

  @Override
  public MsgLog selectOne(Wrapper<MsgLog> queryWrapper) {
    // 将领域实体的 Wrapper 转换为 DO 的 Wrapper
    QueryWrapper<MsgLogDO> doWrapper = convertToDOWrapper(queryWrapper);
    MsgLogDO entity = msgLogMapper.selectOne(doWrapper);
    return converter.doToEntity(entity);
  }

  @Override
  public List<MsgLog> selectList(Wrapper<MsgLog> queryWrapper) {
    QueryWrapper<MsgLogDO> doWrapper = convertToDOWrapper(queryWrapper);
    List<MsgLogDO> entities = msgLogMapper.selectList(doWrapper);
    return converter.logDoListToEntity(entities);
  }

  @Override
  public Long selectCount(Wrapper<MsgLog> queryWrapper) {
    QueryWrapper<MsgLogDO> doWrapper = convertToDOWrapper(queryWrapper);
    return msgLogMapper.selectCount(doWrapper);
  }

  @Override
  public IPage<MsgLog> selectPage(IPage<MsgLog> page, Wrapper<MsgLog> queryWrapper) {
    // 创建 DO 的分页对象
    Page<MsgLogDO> doPage = new Page<>(page.getCurrent(), page.getSize());
    QueryWrapper<MsgLogDO> doWrapper = convertToDOWrapper(queryWrapper);
    IPage<MsgLogDO> doResult = msgLogMapper.selectPage(doPage, doWrapper);

    // 将 DO 分页结果转换为领域实体分页结果
    Page<MsgLog> entityPage = new Page<>(doResult.getCurrent(), doResult.getSize(), doResult.getTotal());
    entityPage.setRecords(converter.logDoListToEntity(doResult.getRecords()));
    return entityPage;
  }

  // ===== 查询方法（返回 VO） =====

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

  /**
   * 将领域实体的 Wrapper 转换为 DO 的 Wrapper。
   *
   * <p>由于 Mapper 操作的是 MsgLogDO，需要将查询条件转换为 DO 的字段名。
   * 使用 MyBatis-Plus 的 {@link com.baomidou.mybatisplus.core.conditions.interfaces.Compare#getSqlSegment()}
   * 获取原始 SQL 片段，然后应用到 DO 的 Wrapper 中。
   *
   * @param wrapper 领域实体的 Wrapper
   * @return DO 的 Wrapper
   */
  private QueryWrapper<MsgLogDO> convertToDOWrapper(Wrapper<MsgLog> wrapper) {
    QueryWrapper<MsgLogDO> doWrapper = new QueryWrapper<>();
    if (wrapper != null) {
      // 获取原始 Wrapper 的 SQL 片段和参数
      String sqlSegment = wrapper.getSqlSegment();
      if (sqlSegment != null && !sqlSegment.isEmpty()) {
        // 将 SQL 片段中的领域实体字段名替换为 DO 的列名
        // 由于领域实体和 DO 字段名相同（驼峰），列名也相同（下划线），直接应用
        doWrapper.apply(sqlSegment, wrapper.getParamNameValuePairs().toArray());
      }
    }
    return doWrapper;
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
}
