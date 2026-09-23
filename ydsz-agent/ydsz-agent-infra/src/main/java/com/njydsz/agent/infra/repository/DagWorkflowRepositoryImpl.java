package com.njydsz.agent.infra.repository;

import java.util.List;
import java.util.Optional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import com.njydsz.agent.domain.entity.DagWorkflow;
import com.njydsz.agent.domain.repository.DagWorkflowRepository;
import com.njydsz.agent.infra.converter.AgentPoConverter;
import com.njydsz.agent.infra.entity.DagWorkflowPO;

/**
 * DAG 工作流仓储实现。
 *
 * <p>写入时通过 {@link AgentPoConverter} 将 domain 实体转为 PO，
 * 读取时通过 {@link AgentPoConverter} 将 PO 转为 domain 实体。
 *
 * <p><b>DDD 分层：</b> domain 层 DagWorkflow 为纯净 POJO；PO 层承载 MP 注解。
 *
 * @author ydsz-team
 * @since 26.09.17
 */
@Slf4j
@Repository
public class DagWorkflowRepositoryImpl implements DagWorkflowRepository {

  private final BaseMapper<DagWorkflowPO> mapper;

  private final AgentPoConverter poConverter;

  public DagWorkflowRepositoryImpl(BaseMapper<DagWorkflowPO> mapper, AgentPoConverter poConverter) {
    this.mapper = mapper;
    this.poConverter = poConverter;
  }

  @Override
  public boolean insert(DagWorkflow workflow) {
    return mapper.insert(poConverter.domainToPo(workflow)) > 0;
  }

  @Override
  public boolean updateById(DagWorkflow workflow) {
    return mapper.updateById(poConverter.domainToPo(workflow)) > 0;
  }

  @Override
  public Optional<DagWorkflow> findById(String id) {
    return Optional.ofNullable(mapper.selectById(id))
        .map(poConverter::poToDomain);
  }

  @Override
  public Optional<DagWorkflow> findByCode(String workflowCode) {
    LambdaQueryWrapper<DagWorkflowPO> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(DagWorkflowPO::getWorkflowCode, workflowCode);
    return Optional.ofNullable(mapper.selectOne(wrapper))
        .map(poConverter::poToDomain);
  }

  @Override
  public List<DagWorkflow> findAll() {
    LambdaQueryWrapper<DagWorkflowPO> wrapper = new LambdaQueryWrapper<>();
    wrapper.orderByDesc(DagWorkflowPO::getUpdatedAt);
    return mapper.selectList(wrapper).stream()
        .map(poConverter::poToDomain)
        .toList();
  }

  @Override
  public List<DagWorkflow> findByCategory(String category) {
    LambdaQueryWrapper<DagWorkflowPO> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(DagWorkflowPO::getCategory, category);
    wrapper.orderByDesc(DagWorkflowPO::getUpdatedAt);
    return mapper.selectList(wrapper).stream()
        .map(poConverter::poToDomain)
        .toList();
  }

  @Override
  public boolean deleteById(String id) {
    return mapper.deleteById(id) > 0;
  }

  @Override
  public boolean existsByCode(String workflowCode) {
    LambdaQueryWrapper<DagWorkflowPO> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(DagWorkflowPO::getWorkflowCode, workflowCode);
    return mapper.exists(wrapper);
  }
}
