package com.njydsz.workflow.infra.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.workflow.domain.repository.FlowHisInstanceRepository;
import com.njydsz.workflow.domain.vo.FlowHisInstanceVO;
import com.njydsz.workflow.infra.converter.WorkflowConverter;
import com.njydsz.workflow.infra.entity.FlowHisInstance;
import com.njydsz.workflow.infra.mapper.FlowHisInstanceMapper;

/**
 * 历史实例仓储实现（Infra 层）。
 *
 * <p>实现领域层定义的 {@link FlowHisInstanceRepository} 接口，封装 FlowHisInstanceMapper 数据访问细节。
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li>所有数据访问通过本类的语义方法，禁止暴露 Mapper
 *   <li>通过 {@link WorkflowConverter} 将 DO 转换为 VO 后返回领域层
 * </ul>
 *
 * <p><b>分层定位：</b>依赖方向为 infra → domain（符合 DDD 依赖倒置原则）， domain 层定义接口契约，infra 层提供适配器实现。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Repository
@RequiredArgsConstructor
public class FlowHisInstanceRepositoryImpl implements FlowHisInstanceRepository {

  private final FlowHisInstanceMapper hisInstanceMapper;

  private final WorkflowConverter converter;

  @Override
  public FlowHisInstanceVO save(FlowHisInstanceVO vo) {
    FlowHisInstance entity = converter.entityToEntity(vo);
    hisInstanceMapper.insert(entity);
    vo.setId(entity.getId());
    return vo;
  }

  @Override
  public Optional<FlowHisInstanceVO> findById(String id) {
    return Optional.ofNullable(hisInstanceMapper.selectById(id)).map(converter::entityToVO);
  }

  @Override
  public List<FlowHisInstanceVO> findArchivedBefore(LocalDateTime threshold, int limit) {
    return converter.flowHisInstanceListToVO(
        hisInstanceMapper.selectByArchivedAtBefore(threshold, limit));
  }

  @Override
  public int deleteByIds(List<String> ids) {
    if (ids == null || ids.isEmpty()) {
      return 0;
    }
    // FlowHisInstanceMapper.deleteByOriginalIds takes List<Long>
    List<Long> longIds = ids.stream().map(Long::parseLong).toList();
    return hisInstanceMapper.deleteByOriginalIds(longIds);
  }
}
