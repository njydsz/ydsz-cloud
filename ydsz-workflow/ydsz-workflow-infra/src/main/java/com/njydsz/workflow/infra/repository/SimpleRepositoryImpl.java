package com.njydsz.workflow.infra.repository;

import java.util.List;
import java.util.Optional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/**
 * 通用简单仓储基类（Infra 层）。
 *
 * <p>提供 CRUD 操作的通用实现，适用于纯 CRUD 仓储场景。子类只需提供：
 *
 * <ul>
 *   <li>实体类型转换（VO ↔ DO）
 *   <li>复杂查询方法（如需要）
 * </ul>
 *
 * <p><b>适用场景：</b>单表 CRUD、无跨表关联、无复杂业务规则的仓储。
 *
 * <p><b>不适用场景：</b>复杂查询（如分页聚合、子查询）、跨表事务、领域事件发布等应保持独立实现。
 *
 * @param <VO> 领域值对象类型
 * @param <DO> 数据库实体类型
 * @param <MAPPER> MyBatis Mapper 类型
 * @author ydsz-team
 * @since 1.0.0
 */
public abstract class SimpleRepositoryImpl<VO, DO, MAPPER extends BaseMapper<DO>> {

  /** MyBatis Mapper 实例。 */
  protected final MAPPER mapper;

  /**
   * 构造通用仓储基类。
   *
   * @param mapper MyBatis Mapper 实例
   */
  protected SimpleRepositoryImpl(MAPPER mapper) {
    this.mapper = mapper;
  }

  /**
   * 将 VO 转换为 DO（子类实现）。
   *
   * @param vo 领域值对象
   * @return 数据库实体
   */
  protected abstract DO voToEntity(VO vo);

  /**
   * 将 DO 转换为 VO（子类实现）。
   *
   * @param entity 数据库实体
   * @return 领域值对象
   */
  protected abstract VO entityToVO(DO entity);

  /**
   * 将 DO 列表转换为 VO 列表（子类实现）。
   *
   * @param entities 数据库实体列表
   * @return 领域值对象列表
   */
  protected abstract List<VO> entityListToVO(List<DO> entities);

  /**
   * 保存实体（新增）。
   *
   * @param vo 领域值对象
   * @return 保存后的值对象（含生成的 ID）
   */
  public VO save(VO vo) {
    DO entity = voToEntity(vo);
    mapper.insert(entity);
    setId(vo, getId(entity));
    return vo;
  }

  /**
   * 根据 ID 查询。
   *
   * @param id 实体 ID
   * @return 领域值对象；不存在返回 {@code Optional.empty()}
   */
  public Optional<VO> findById(String id) {
    return Optional.ofNullable(mapper.selectById(id)).map(this::entityToVO);
  }

  /**
   * 根据 ID 删除。
   *
   * @param id 实体 ID
   */
  public void deleteById(String id) {
    mapper.deleteById(id);
  }

  /**
   * 更新实体。
   *
   * @param vo 领域值对象（含 ID）
   * @return 更新后的值对象
   */
  public VO update(VO vo) {
    DO entity = voToEntity(vo);
    mapper.updateById(entity);
    return vo;
  }

  /**
   * 根据条件查询列表。
   *
   * @param wrapper 查询条件
   * @return 领域值对象列表
   */
  protected List<VO> selectList(LambdaQueryWrapper<DO> wrapper) {
    return entityListToVO(mapper.selectList(wrapper));
  }

  /**
   * 从 DO 中提取 ID（子类实现）。
   *
   * @param entity 数据库实体
   * @return 实体 ID
   */
  protected abstract String getId(DO entity);

  /**
   * 设置 VO 的 ID（子类实现）。
   *
   * @param vo 领域值对象
   * @param id 实体 ID
   */
  protected abstract void setId(VO vo, String id);
}
