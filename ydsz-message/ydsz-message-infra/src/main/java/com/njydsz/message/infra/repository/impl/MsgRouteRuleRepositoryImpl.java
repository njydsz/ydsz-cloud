package com.njydsz.message.infra.repository.impl;

import java.util.List;
import java.util.Optional;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.common.core.response.PageResponse;
import com.njydsz.message.domain.query.MsgRouteRuleQuery;
import com.njydsz.message.domain.repository.MsgRouteRuleRepository;
import com.njydsz.message.domain.vo.MsgRouteRuleVO;
import com.njydsz.message.infra.converter.MessageConverter;
import com.njydsz.message.infra.entity.MsgRouteRuleDO;
import com.njydsz.message.infra.mapper.config.MsgRouteRuleMapper;

/**
 * 消息路由规则仓储实现（Infra 层）。
 *
 * <p>实现 {@link MsgRouteRuleRepository} 接口，封装 MsgRouteRuleMapper 数据访问细节。
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
public class MsgRouteRuleRepositoryImpl implements MsgRouteRuleRepository {

  private final MsgRouteRuleMapper msgRouteRuleMapper;

  private final MessageConverter converter;

  @Override
  public boolean save(MsgRouteRuleVO vo) {
    MsgRouteRuleDO entity = voToDO(vo);
    return msgRouteRuleMapper.insert(entity) > 0;
  }

  @Override
  public Optional<MsgRouteRuleVO> findById(String id) {
    return Optional.ofNullable(msgRouteRuleMapper.selectById(id)).map(converter::doToVO);
  }

  @Override
  public boolean update(MsgRouteRuleVO vo) {
    MsgRouteRuleDO entity = voToDO(vo);
    return msgRouteRuleMapper.updateById(entity) > 0;
  }

  @Override
  public boolean deleteById(String id) {
    return msgRouteRuleMapper.deleteById(id) > 0;
  }

  @Override
  public List<MsgRouteRuleVO> findList(MsgRouteRuleQuery query) {
    QueryWrapper<MsgRouteRuleDO> wrapper = new QueryWrapper<>();
    if (query.getRuleCode() != null && !query.getRuleCode().isBlank()) {
      wrapper.eq("rule_code", query.getRuleCode());
    }
    if (query.getRuleName() != null && !query.getRuleName().isBlank()) {
      wrapper.like("rule_name", query.getRuleName());
    }
    if (query.getBizType() != null && !query.getBizType().isBlank()) {
      wrapper.eq("biz_type", query.getBizType());
    }
    if (query.getChannel() != null && !query.getChannel().isBlank()) {
      wrapper.eq("channel", query.getChannel());
    }
    if (query.getStatus() != null && !query.getStatus().isBlank()) {
      wrapper.eq("status", query.getStatus());
    }
    wrapper.eq("deleted", 0);
    return converter.routeRuleDoListToVO(msgRouteRuleMapper.selectList(wrapper));
  }

  @Override
  public PageResponse<List<MsgRouteRuleVO>> findPage(MsgRouteRuleQuery query) {
    Page<MsgRouteRuleDO> page = new Page<>(query.getPageNum(), query.getPageSize());
    QueryWrapper<MsgRouteRuleDO> wrapper = new QueryWrapper<>();
    if (query.getRuleCode() != null && !query.getRuleCode().isBlank()) {
      wrapper.eq("rule_code", query.getRuleCode());
    }
    if (query.getRuleName() != null && !query.getRuleName().isBlank()) {
      wrapper.like("rule_name", query.getRuleName());
    }
    if (query.getBizType() != null && !query.getBizType().isBlank()) {
      wrapper.eq("biz_type", query.getBizType());
    }
    if (query.getChannel() != null && !query.getChannel().isBlank()) {
      wrapper.eq("channel", query.getChannel());
    }
    if (query.getStatus() != null && !query.getStatus().isBlank()) {
      wrapper.eq("status", query.getStatus());
    }
    wrapper.eq("deleted", 0);
    wrapper.orderByAsc("sort_order");
    IPage<MsgRouteRuleDO> entityPage = msgRouteRuleMapper.selectPage(page, wrapper);
    List<MsgRouteRuleVO> vos = converter.routeRuleDoListToVO(entityPage.getRecords());
    return PageResponse.success(entityPage.getTotal(), (long)query.getPageNum(), (long)query.getPageSize(), vos);
  }

  private MsgRouteRuleDO voToDO(MsgRouteRuleVO vo) {
    if (vo == null) {
      return null;
    }
    MsgRouteRuleDO entity = new MsgRouteRuleDO();
    entity.setId(vo.getId());
    entity.setRuleCode(vo.getRuleCode());
    entity.setRuleName(vo.getRuleName());
    entity.setBizType(vo.getBizType());
    entity.setChannel(vo.getChannel());
    entity.setPriority(vo.getPriority());
    entity.setConditionExpr(vo.getConditionExpr());
    entity.setTargetChannel(vo.getTargetChannel());
    entity.setFallbackChannel(vo.getFallbackChannel());
    entity.setDescription(vo.getDescription());
    entity.setSortOrder(vo.getSortOrder());
    entity.setStatus(vo.getStatus());
    return entity;
  }
}
