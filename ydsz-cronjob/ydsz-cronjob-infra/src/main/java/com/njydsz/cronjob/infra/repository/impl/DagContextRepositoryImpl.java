package com.njydsz.cronjob.infra.repository.impl;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.cronjob.domain.repository.DagContextRepository;
import com.njydsz.cronjob.domain.vo.JobDagContextVO;
import com.njydsz.cronjob.infra.converter.CronjobConverter;
import com.njydsz.cronjob.infra.entity.dag.JobDagContext;
import com.njydsz.cronjob.infra.mapper.dag.JobDagContextMapper;

/**
 * DAG 实例节点上下文 Repository 实现（Infra 层，P0-13 优化）。
 *
 * <p>实现 {@link DagContextRepository} 接口，封装 JobDagContextMapper 数据访问细节。
 *
 * <p>节点结果独立存储，避免 CAS 更新整行 context_json，解决行锁竞争与 JSON 写入放大问题。
 *
 * @author ydsz-team
 * @since 1.0.2
 */
@Repository
@RequiredArgsConstructor
public class DagContextRepositoryImpl implements DagContextRepository {

  private final JobDagContextMapper dagContextMapper;
  private final CronjobConverter converter;

  @Override
  public void save(JobDagContextVO vo) {
    JobDagContext entity = converter.jobDagContextVOToEntity(vo);
    // UPSERT 语义：先查询，存在则更新，不存在则插入
    JobDagContext existing =
        dagContextMapper.selectByDagInstanceAndNodeKey(vo.getDagInstanceId(), vo.getNodeKey());
    if (existing != null) {
      existing.setResultJson(vo.getResultJson());
      dagContextMapper.updateById(existing);
    } else {
      dagContextMapper.insert(entity);
    }
  }

  @Override
  public Optional<JobDagContextVO> findByDagInstanceAndNodeKey(String dagInstanceId, String nodeKey) {
    return Optional.ofNullable(
            dagContextMapper.selectByDagInstanceAndNodeKey(dagInstanceId, nodeKey))
        .map(converter::jobDagContextEntityToVO);
  }

  @Override
  public List<JobDagContextVO> findByDagInstanceId(String dagInstanceId) {
    List<JobDagContext> entities = dagContextMapper.selectByDagInstanceId(dagInstanceId);
    if (entities == null || entities.isEmpty()) {
      return Collections.emptyList();
    }
    return converter.jobDagContextListToVO(entities);
  }

  @Override
  public void saveBatch(List<JobDagContextVO> vos) {
    if (vos == null || vos.isEmpty()) {
      return;
    }
    List<JobDagContext> entities = converter.jobDagContextListVOToEntity(vos);
    dagContextMapper.insertBatch(entities);
  }

  @Override
  public void deleteByDagInstanceId(String dagInstanceId) {
    dagContextMapper.deleteByDagInstanceId(dagInstanceId);
  }
}
