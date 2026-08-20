package com.njydsz.literule.domain.repository;

import java.util.List;
import java.util.Optional;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.njydsz.common.domain.query.PageQuery;
import com.njydsz.literule.domain.vo.RuleDefinitionVO;

/**
 * 规则定义仓库接口（DDD domain 层）
 *
 * <p>定义规则定义持久化的标准操作，包括分页查询、全文搜索等。
 * 消费方可提供自定义实现（如数据库 + Redis 缓存）以满足不同性能与一致性需求。
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li>返回值必须为 VO，禁止返回 infra 层 Entity（DO）
 *   <li>入参必须为 DTO 或 Query，禁止接收 infra 层 Entity（DO）
 *   <li>禁止暴露 Mapper、IPage 等 infra 技术细节给 server 层</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface RuleDefinitionRepository {

  /**
   * 根据规则编码查询规则定义
   *
   * @param ruleCode 规则编码
   * @return 规则定义 VO；不存在时返回 {@link Optional#empty()}
   */
  Optional<RuleDefinitionVO> findByCode(String ruleCode);

  /**
   * 根据 ID 查询规则定义
   *
   * @param id 规则定义主键 ID
   * @return 规则定义 VO；不存在时返回 {@link Optional#empty()}
   */
  Optional<RuleDefinitionVO> findById(String id);

  /**
   * 分页查询规则定义
   *
   * @param pageQuery 分页查询参数
   * @return 分页结果（包含规则定义 VO 列表）
   */
  IPage<RuleDefinitionVO> pageRuleDefinitions(PageQuery pageQuery);

  /**
   * 全文搜索规则（数据库级 LIKE 查询）
   *
   * <p>使用 MyBatis-Plus 的 LIKE 查询在数据库层完成过滤，避免全量加载内存。
   *
   * @param query 搜索关键词（空格分隔为 AND 条件，null/空返回全部）
   * @param status 状态过滤（null=不过滤）
   * @param category 分类过滤（null=不过滤）
   * @param enabled 启停过滤（null=不过滤）
   * @param offset 分页偏移
   * @param limit 分页大小
   * @return 搜索结果列表
   */
  List<RuleDefinitionVO> search(
      String query, String status, String category, Boolean enabled, int offset, int limit);

  /**
   * 统计搜索结果总数（不分页）
   *
   * @param query 搜索关键词
   * @param status 状态过滤
   * @param category 分类过滤
   * @param enabled 启停过滤
   * @return 匹配的规则总数
   */
  int searchCount(String query, String status, String category, Boolean enabled);

  /**
   * 分页搜索规则
   *
   * @param query 搜索关键词
   * @param status 状态过滤
   * @param category 分类过滤
   * @param enabled 启停过滤
   * @param pageQuery 分页查询参数
   * @return 分页结果
   */
  IPage<RuleDefinitionVO> searchPage(
      String query, String status, String category, Boolean enabled, PageQuery pageQuery);
}
