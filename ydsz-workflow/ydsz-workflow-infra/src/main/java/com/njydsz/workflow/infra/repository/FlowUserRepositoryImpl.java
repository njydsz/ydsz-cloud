package com.njydsz.workflow.infra.repository;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.workflow.domain.repository.FlowUserRepository;
import com.njydsz.workflow.domain.vo.FlowUserVO;
import com.njydsz.workflow.domain.converter.WorkflowConverter;
import com.njydsz.workflow.domain.entity.FlowUser;
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
 * @since 26.09.01
 */
@Repository
@RequiredArgsConstructor
public class FlowUserRepositoryImpl implements FlowUserRepository {

  private final FlowUserMapper userMapper;

  private final WorkflowConverter converter;

  @Override
  public FlowUserVO save(FlowUserVO vo) {
    FlowUser entity = converter.entityToEntity(vo);
    userMapper.insert(entity);
    vo.setId(entity.getId());
    return vo;
  }

  @Override
  public List<FlowUserVO> saveBatch(List<FlowUserVO> users) {
    List<FlowUser> entities = users.stream().map(converter::entityToEntity).toList();
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
            new LambdaQueryWrapper<FlowUser>()
                .eq(FlowUser::getInstanceId, instanceId)
                .eq(FlowUser::getDeleted, 0)));
  }

  @Override
  public List<FlowUserVO> findByInstanceAndType(String instanceId, String userType) {
    return converter.flowUserListToVO(
        userMapper.selectList(
            new LambdaQueryWrapper<FlowUser>()
                .eq(FlowUser::getInstanceId, instanceId)
                .eq(FlowUser::getUserType, userType)
                .eq(FlowUser::getDeleted, 0)));
  }

  @Override
  public void deleteById(String id) {
    userMapper.deleteById(id);
  }

  @Override
  public FlowUserVO update(FlowUserVO vo) {
    FlowUser entity = converter.entityToEntity(vo);
    userMapper.updateById(entity);
    return vo;
  }

  @Override
  public List<String> selectTaskIdsByUser(String userId, String tenantId) {
    List<Long> ids = userMapper.selectTaskIdsByUser(userId, tenantId);
    return ids == null ? Collections.emptyList().stream().map(String::valueOf).toList()
        : ids.stream().map(String::valueOf).toList();
  }

  @Override
  public int deleteByInstanceAndNodeAndUser(String instanceId, String nodeCode, String userId) {
    Map<String, Object> deleteMap = new HashMap<>(16);
    deleteMap.put("instance_id", instanceId);
    deleteMap.put("node_code", nodeCode);
    deleteMap.put("user_id", userId);
    return userMapper.deleteByMap(deleteMap);
  }
}
