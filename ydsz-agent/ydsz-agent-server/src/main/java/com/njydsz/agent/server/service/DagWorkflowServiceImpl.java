package com.njydsz.agent.server.service;

import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.njydsz.agent.domain.converter.AgentConverter;
import com.njydsz.agent.domain.dto.DagWorkflowDTO;
import com.njydsz.agent.domain.entity.DagWorkflow;
import com.njydsz.agent.domain.enums.AgentExceptionCode;
import com.njydsz.agent.domain.repository.DagWorkflowRepository;
import com.njydsz.agent.domain.service.DagWorkflowService;
import com.njydsz.agent.domain.vo.DagWorkflowVO;
import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.util.id.IdGenerator;

/**
 * DAG 工作流业务服务实现。
 *
 * <p>封装工作流 CRUD 编排逻辑，注入 Repository 接口（DDD 依赖倒置），
 * Controller 层仅面向本接口编程。
 *
 * @author ydsz-team
 * @since 26.10.01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DagWorkflowServiceImpl implements DagWorkflowService {

  private final DagWorkflowRepository dagWorkflowRepository;
  private final AgentConverter converter;

  /**
   * {@inheritDoc}
   *
   * <p>新建时自动生成 workflowCode（dag-{雪花ID}），isPublished 设为 false。
   * 更新时按 workflowCode 查询现有记录，不存在则抛业务异常。
   */
  @Override
  @Transactional(rollbackFor = Exception.class)
  public String save(DagWorkflowDTO dto) {
    boolean isCreate = (dto.getWorkflowCode() == null || dto.getWorkflowCode().isBlank());
    DagWorkflow entity;
    if (isCreate) {
      entity = converter.dtoToEntity(dto);
      entity.setWorkflowCode(generateWorkflowCode());
      entity.setIsPublished(false);
    } else {
      DagWorkflowVO existing = dagWorkflowRepository.findByCode(dto.getWorkflowCode())
          .orElseThrow(() -> BusinessException.of(AgentExceptionCode.DAG_WORKFLOW_NOT_FOUND)
              .params(dto.getWorkflowCode()));
      entity = converter.voToEntityWithId(existing);
    }
    entity.setName(dto.getWorkflowName());
    entity.setDsl(dto.getDslContent());
    entity.setDescription(dto.getDescription());
    entity.setCategory(dto.getCategory());
    boolean ok = isCreate
        ? dagWorkflowRepository.insert(entity)
        : dagWorkflowRepository.updateById(entity);
    if (!ok) {
      throw BusinessException.of(AgentExceptionCode.AGENT_EXECUTION_FAILED);
    }
    log.info("[DagWorkflow] {} workflow: code={}, name={}",
        isCreate ? "created" : "updated",
        entity.getWorkflowCode(),
        entity.getName());
    return entity.getId();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public DagWorkflowVO getByCode(String code) {
    return dagWorkflowRepository.findByCode(code).orElse(null);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public List<DagWorkflowVO> listByCategory(String category) {
    if (category == null || category.isBlank()) {
      return dagWorkflowRepository.findAll();
    }
    return dagWorkflowRepository.findByCategory(category);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public DagWorkflowVO getById(String id) {
    return dagWorkflowRepository.findById(id).orElse(null);
  }

  /**
   * {@inheritDoc}
   *
   * <p>按编码查询目标工作流并执行逻辑删除（设置 isDeleted）。
   */
  @Override
  @Transactional(rollbackFor = Exception.class)
  public boolean deleteByCode(String code) {
    DagWorkflowVO vo = dagWorkflowRepository.findByCode(code)
        .orElseThrow(() -> BusinessException.of(AgentExceptionCode.DAG_WORKFLOW_NOT_FOUND).params(code));
    boolean deleted = dagWorkflowRepository.deleteById(vo.getId());
    if (deleted) {
      log.info("[DagWorkflow] deleted workflow: code={}", code);
    }
    return deleted;
  }

  /** 生成工作流编码：dag-{雪花ID字符串} */
  private String generateWorkflowCode() {
    return "dag-" + IdGenerator.nextIdStr();
  }
}
