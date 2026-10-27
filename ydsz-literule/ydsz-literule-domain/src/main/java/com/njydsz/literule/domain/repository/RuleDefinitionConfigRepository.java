package com.njydsz.literule.domain.repository;

import java.util.List;

import com.njydsz.literule.domain.entity.RuleDefinition;

/**
 * 规则定义「引擎配置源」持久化端口（DDD domain 层）。
 *
 * <p>面向 {@code RuleConfigProvider} 的规则加载/回写路径，只提供本路径实际需要的四类操作：
 * 全量读取、按编码读取、插入、按主键更新。实现由 infra 层持有 Mapper 完成（依赖倒置：infra → domain）。
 *
 * <p><b>为什么与 {@link RuleDefinitionRepository} 分开定义：</b>
 *
 * <ul>
 *   <li>{@link RuleDefinitionRepository} 面向管理端查询，契约规定"出入参一律使用 VO"，
 *       且其 Javadoc 明确"消费方可提供自定义实现"——向它追加写操作会破坏外部已有实现的兼容性；
 *   <li>引擎配置源路径需要 Entity 原样往返（保存时补齐 version、启停时改写 isEnabled 后整体 updateById），
 *       经 VO 中转会丢失 VO 未承载的列，故本端口以 domain 自身的 {@link RuleDefinition} 为契约类型。
 * </ul>
 *
 * <p><b>契约约束：</b>出入参只允许 domain 类型，禁止出现 Mapper / IPage / PO 等 infra 技术细节。
 *
 * @author ydsz-team
 * @since 26.10.27
 */
public interface RuleDefinitionConfigRepository {

  /**
   * 读取当前租户全部规则定义（不做状态过滤，由调用方按业务条件筛选）。
   *
   * <p>多租户与逻辑删除过滤由 MyBatis 拦截器自动注入，本接口不感知。
   *
   * @return 规则定义实体列表，无数据时为空列表，不为 null
   */
  List<RuleDefinition> listAll();

  /**
   * 按规则编码查询规则定义。
   *
   * @param ruleCode 规则编码
   * @return 规则定义实体；不存在时返回 null（调用方据此区分"新增"与"更新"）
   */
  RuleDefinition findByCode(String ruleCode);

  /**
   * 新增规则定义。
   *
   * @param definition 规则定义实体（主键由数据库/MyBatis-Plus 生成）
   * @return 影响行数
   */
  int insert(RuleDefinition definition);

  /**
   * 按主键更新规则定义。
   *
   * @param definition 规则定义实体（需携带 id）
   * @return 影响行数
   */
  int updateById(RuleDefinition definition);
}
