package com.njydsz.message.infra.repository;

import java.util.List;
import java.util.Optional;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.message.domain.converter.MessageConverter;
import com.njydsz.message.domain.entity.MsgPreference;
import com.njydsz.message.domain.query.MsgPreferenceQuery;
import com.njydsz.message.domain.repository.MsgPreferenceRepository;
import com.njydsz.message.domain.vo.MsgPreferenceVO;
import com.njydsz.message.infra.mapper.config.MsgPreferenceMapper;

/**
 * 用户消息偏好仓储实现（Infra 层）。
 *
 * <p>实现 {@link MsgPreferenceRepository} 接口，封装 MsgPreferenceMapper 数据访问细节。
 *
 * @author ydsz-team
 * @since 26.10.01
 */
@Repository
@RequiredArgsConstructor
public class MsgPreferenceRepositoryImpl implements MsgPreferenceRepository {

  private final MsgPreferenceMapper msgPreferenceMapper;

  private final MessageConverter converter;

  @Override
  public boolean save(MsgPreferenceVO vo) {
    MsgPreference entity = voToEntity(vo);
    return msgPreferenceMapper.insert(entity) > 0;
  }

  @Override
  public boolean update(MsgPreferenceVO vo) {
    MsgPreference entity = voToEntity(vo);
    return msgPreferenceMapper.updateById(entity) > 0;
  }

  @Override
  public boolean deleteById(String id) {
    return msgPreferenceMapper.deleteById(id) > 0;
  }

  @Override
  public Optional<MsgPreferenceVO> findOne(MsgPreferenceQuery query) {
    QueryWrapper<MsgPreference> wrapper = buildWrapper(query);
    return Optional.ofNullable(msgPreferenceMapper.selectOne(wrapper)).map(converter::entityToVO);
  }

  @Override
  public List<MsgPreferenceVO> findList(MsgPreferenceQuery query) {
    QueryWrapper<MsgPreference> wrapper = buildWrapper(query);
    applyPagination(wrapper, query.getPageNum(), query.getPageSize());
    return converter.preferenceListToVO(msgPreferenceMapper.selectList(wrapper));
  }

  private QueryWrapper<MsgPreference> buildWrapper(MsgPreferenceQuery query) {
    QueryWrapper<MsgPreference> wrapper = new QueryWrapper<>();
    if (query.getUserId() != null && !query.getUserId().isBlank()) {
      wrapper.eq("user_id", query.getUserId());
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
    wrapper.eq("is_deleted", 0);
    return wrapper;
  }

  private MsgPreference voToEntity(MsgPreferenceVO vo) {
    if (vo == null) {
      return null;
    }
    MsgPreference entity = new MsgPreference();
    entity.setId(vo.getId());
    entity.setUserId(vo.getUserId());
    entity.setChannel(vo.getChannel());
    entity.setBizType(vo.getBizType());
    entity.setIsEnabled(vo.getIsEnabled());
    entity.setIsDndEnabled(vo.getIsDndEnabled());
    entity.setDndStart(vo.getDndStart());
    entity.setDndEnd(vo.getDndEnd());
    entity.setDailyLimit(vo.getDailyLimit());
    entity.setHourlyLimit(vo.getHourlyLimit());
    entity.setIsDigestEnabled(vo.getIsDigestEnabled());
    entity.setDigestFrequency(vo.getDigestFrequency());
    entity.setLocale(vo.getLocale());
    entity.setExtra(vo.getExtra());
    return entity;
  }

  /**
   * 当查询 DTO 携带分页参数时，为 wrapper 追加 LIMIT/OFFSET。
   *
   * <p>调用方（如健康探针、批量对账）可通过 {@code PageQuery.pageNum/pageSize}
   * 限制返回量；未设置分页时不改变既有行为（向后兼容）。
   *
   * @param wrapper MyBatis-Plus 查询条件构造器
   * @param pageNum 页码（从 1 开始），null 或 &lt;=0 表示不分页
   * @param pageSize 每页条数，null 或 &lt;=0 表示不分页
   */
  protected void applyPagination(
      com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<?> wrapper,
      Integer pageNum,
      Integer pageSize) {
    if (pageNum == null || pageSize == null || pageNum <= 0 || pageSize <= 0) {
      return;
    }
    wrapper.last("LIMIT " + pageSize + " OFFSET " + (long) (pageNum - 1) * pageSize);
  }

}
