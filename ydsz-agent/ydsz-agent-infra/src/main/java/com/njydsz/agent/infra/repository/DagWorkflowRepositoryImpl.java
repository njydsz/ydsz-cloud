package com.njydsz.agent.infra.repository;

import java.util.List;
import java.util.Optional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import com.njydsz.agent.domain.converter.AgentConverter;
import com.njydsz.agent.domain.entity.DagWorkflow;
import com.njydsz.agent.domain.repository.DagWorkflowRepository;
import com.njydsz.agent.domain.vo.DagWorkflowVO;

/**
 * DAG 工作流仓储实现。
 *
 * <p>读取时通过 {@link AgentConverter} 将 domain 实体转为 VO，对调用方屏蔽持久化细节。
 *
 * <p><b>DDD 分层：</b> domain 层 DagWorkflow 直接承载 MP 注解用于持久化。
 *
 * @author ydsz-team
 * @since 26.09.17
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class DagWorkflowRepositoryImpl implements DagWorkflowRepository {

  private final BaseMapper<DagWorkflow> mapper;

  private final AgentConverter converter;

  @Override
  public boolean insert(DagWorkflow workflow) {
    return mapper.insert(workflow) > 0;
  }

  @Override
  public boolean updateById(DagWorkflow workflow) {
    return mapper.updateById(workflow) > 0;
  }

  @Override
  public Optional<DagWorkflowVO> findById(String id) {
    DagWorkflow entity = mapper.selectById(id);
    return Optional.ofNullable(entity)
        .map(converter::entityToVO);
  }

  @Override
  public Optional<DagWorkflowVO> findByCode(String workflowCode) {
    LambdaQueryWrapper<DagWorkflow> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(DagWorkflow::getWorkflowCode, workflowCode);
    DagWorkflow entity = mapper.selectOne(wrapper);
    return Optional.ofNullable(entity)
        .map(converter::entityToVO);
  }

  @Override
  public List<DagWorkflowVO> findAll() {
    LambdaQueryWrapper<DagWorkflow> wrapper = new LambdaQueryWrapper<>();
    wrapper.orderByDesc(DagWorkflow::getUpdatedAt);
    List<DagWorkflow> entities = mapper.selectList(wrapper);
    return converter.dagWorkflowListToVO(entities);
  }

  @Override
  public List<DagWorkflowVO> findByCategory(String category) {
    LambdaQueryWrapper<DagWorkflow> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(DagWorkflow::getCategory, category);
    wrapper.orderByDesc(DagWorkflow::getUpdatedAt);
    List<DagWorkflow> entities = mapper.selectList(wrapper);
    return converter.dagWorkflowListToVO(entities);
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
