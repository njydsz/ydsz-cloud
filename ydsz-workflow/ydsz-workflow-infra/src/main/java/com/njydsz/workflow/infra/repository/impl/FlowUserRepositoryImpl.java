package com.njydsz.workflow.infra.repository.impl;

import java.util.List;
import java.util.Optional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.workflow.domain.repository.FlowUserRepository;
import com.njydsz.workflow.domain.vo.FlowUserVO;
import com.njydsz.workflow.infra.converter.WorkflowConverter;
import com.njydsz.workflow.infra.entity.FlowUserDO;
import com.njydsz.workflow.infra.mapper.FlowUserMapper;

/**
 * 流程用户仓储实现（Infra 层）。
 *
 * <p>实现领域层定义的 {@link FlowUserRepository} 接口，封装 FlowUserMapper 数据访问细节。
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
 * @since 1.0.0
 */
@Repository
@RequiredArgsConstructor
public class FlowUserRepositoryImpl implements FlowUserRepository {

  private final FlowUserMapper userMapper;

  private final WorkflowConverter converter;

  @Override
  public FlowUserVO save(FlowUserVO vo) {
    FlowUserDO entity = converter.entityToDO(vo);
    userMapper.insert(entity);
    vo.setId(entity.getId());
    return vo;
  }

  @Override
  public List<FlowUserVO> saveBatch(List<FlowUserVO> users) {
    List<FlowUserDO> entities = users.stream().map(converter::entityToDO).toList();
    entities.forEach(userMapper::insert);
    return users;
  }

  @Override
  public Optional<FlowUserVO> findById(String id) {
    return Optional.ofNullable(userMapper.selectById(id)).map(converter::entityToVO);
  }

  @Override
  public List<FlowUserVO> findByInstanceId(String instanceId) {
    return converter.flowUserListToVO(
        userMapper.selectList(
            new LambdaQueryWrapper<FlowUserDO>()
                .eq(FlowUserDO::getInstanceId, instanceId)
                .eq(FlowUserDO::getDeleted, 0)));
  }

  @Override
  public List<FlowUserVO> findByInstanceAndType(String instanceId, String userType) {
    return converter.flowUserListToVO(
        userMapper.selectList(
            new LambdaQueryWrapper<FlowUserDO>()
                .eq(FlowUserDO::getInstanceId, instanceId)
                .eq(FlowUserDO::getUserType, userType)
                .eq(FlowUserDO::getDeleted, 0)));
  }

  @Override
  public void deleteById(String id) {
    userMapper.deleteById(id);
  }

  @Override
  public FlowUserVO update(FlowUserVO vo) {
    FlowUserDO entity = converter.entityToDO(vo);
    userMapper.updateById(entity);
    return vo;
  }
}
