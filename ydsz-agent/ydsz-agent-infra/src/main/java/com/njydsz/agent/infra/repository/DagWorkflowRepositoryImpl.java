package com.njydsz.agent.infra.repository;

import java.util.List;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.njydsz.agent.domain.entity.DagWorkflow;
import com.njydsz.agent.domain.repository.DagWorkflowRepository;
import com.njydsz.agent.infra.mapper.DagWorkflowMapper;

/**
 * DAG 工作流仓储实现。
 *
 * @author ydsz-team
 * @since 26.09.17
 */
@Slf4j
@Repository
public class DagWorkflowRepositoryImpl implements DagWorkflowRepository {

  private final DagWorkflowMapper mapper;

  public DagWorkflowRepositoryImpl(DagWorkflowMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public boolean insert(DagWorkflow workflow) {
    return mapper.insert(workflow) > 0;
  }

  @Override
  public boolean updateById(DagWorkflow workflow) {
    return mapper.updateById(workflow) > 0;
  }

  @Override
  public Optional<DagWorkflow> findById(String id) {
    return Optional.ofNullable(mapper.selectById(id));
  }

  @Override
  public Optional<DagWorkflow> findByCode(String workflowCode) {
    LambdaQueryWrapper<DagWorkflow> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(DagWorkflow::getWorkflowCode, workflowCode);
    return Optional.ofNullable(mapper.selectOne(wrapper));
  }

  @Override
  public List<DagWorkflow> findAll() {
    LambdaQueryWrapper<DagWorkflow> wrapper = new LambdaQueryWrapper<>();
    wrapper.orderByDesc(DagWorkflow::getUpdatedAt);
    return mapper.selectList(wrapper);
  }

  @Override
  public List<DagWorkflow> findByCategory(String category) {
    LambdaQueryWrapper<DagWorkflow> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(DagWorkflow::getCategory, category);
    wrapper.orderByDesc(DagWorkflow::getUpdatedAt);
    return mapper.selectList(wrapper);
  }

  @Override
  public boolean deleteById(String id) {
    return mapper.deleteById(id) > 0;
  }

  @Override
  public boolean existsByCode(String workflowCode) {
    LambdaQueryWrapper<DagWorkflow> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(DagWorkflow::getWorkflowCode, workflowCode);
    return mapper.exists(wrapper);
  }
}
