package com.njydsz.literule.infra.converter;

import java.util.List;

import com.njydsz.literule.domain.dto.DecisionTableDTO;
import com.njydsz.literule.domain.dto.DecisionTableDefinitionDTO;
import com.njydsz.literule.domain.dto.RuleABPolicyDTO;
import com.njydsz.literule.domain.dto.RuleDefinitionDTO;
import com.njydsz.literule.domain.dto.RuleVersionDTO;
import com.njydsz.literule.domain.expression.ExpressionFunctionDef;
import com.njydsz.literule.domain.expression.ExpressionValidationResult;
import com.njydsz.literule.domain.vo.DecisionTableDefinitionVO;
import com.njydsz.literule.domain.vo.DecisionTableVO;
import com.njydsz.literule.domain.vo.ExpressionFunctionDefVO;
import com.njydsz.literule.domain.vo.ExpressionValidationResultVO;
import com.njydsz.literule.domain.vo.RuleABPolicyVO;
import com.njydsz.literule.domain.vo.RuleABRollbackVO;
import com.njydsz.literule.domain.vo.RuleChainGraphVO;
import com.njydsz.literule.domain.vo.RuleDefinitionVO;
import com.njydsz.literule.domain.vo.RuleDependencyVO;
import com.njydsz.literule.domain.vo.RuleEngineStatsVO;
import com.njydsz.literule.domain.vo.RuleExecutionTraceVO;
import com.njydsz.literule.domain.vo.RulePackVO;
import com.njydsz.literule.domain.vo.RuleResultVO;
import com.njydsz.literule.domain.vo.RuleTemplateVO;
import com.njydsz.literule.domain.vo.RuleVersionVO;
import com.njydsz.literule.infra.entity.DecisionTable;
import com.njydsz.literule.infra.entity.RuleABPolicy;
import com.njydsz.literule.infra.entity.RuleABRollback;
import com.njydsz.literule.infra.entity.RuleChainGraph;
import com.njydsz.literule.infra.entity.RuleDependency;
import com.njydsz.literule.infra.entity.RuleExecutionTrace;
import com.njydsz.literule.infra.entity.RulePack;
import com.njydsz.literule.infra.entity.RuleTemplate;
import com.njydsz.literule.infra.entity.RuleVersionHistory;

/**
 * literule 模块统一转换器门面。
 *
 * <p>委托给三个子转换器：
 *
 * <ul>
 *   <li>{@link RuleCoreConverter} - 规则定义、规则结果、引擎统计
 *   <li>{@link RuleComponentConverter} - 决策表、AB 策略、回滚、画布、模板
 *   <li>{@link RuleSupportConverter} - 依赖、执行轨迹、规则包、测试用例、版本
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 * @since 26.09.01 重构为门面模式，委托给子转换器
 */
public class LiteruleConverter {

  /** 单例实例（门面转换器） */
  public static final LiteruleConverter INSTANCE = new LiteruleConverter();

  private final RuleCoreConverter core = RuleCoreConverter.INSTANCE;
  private final RuleComponentConverter component = RuleComponentConverter.INSTANCE;
  private final RuleSupportConverter support = RuleSupportConverter.INSTANCE;

  private LiteruleConverter() {
    // 单例门面
  }

  // ===== DecisionTable =====

  /**
   * 将决策表持久化实体转换为对外返回的视图对象。
   *
   * <p>门面方法，实际映射委托给 {@link RuleComponentConverter#entityToVO(DecisionTable)}，字段按同名自动映射。
   *
   * @param entity 决策表持久化实体（含条件列与动作列配置），为 {@code null} 时返回 {@code null}
   * @return 决策表视图对象；入参为 {@code null} 时返回 {@code null}
   */
  public DecisionTableVO entityToVO(DecisionTable entity) {
    return component.entityToVO(entity);
  }

  /**
   * 批量将决策表实体转换为视图对象列表。
   *
   * <p>门面方法，委托给 {@link RuleComponentConverter#decisionTableListToVO(List)}，返回顺序与入参一致。
   *
   * @param entities 决策表实体列表；为 {@code null} 时返回 {@code null}，空列表时返回空列表
   * @return 决策表视图对象列表，顺序与入参一致
   */
  public List<DecisionTableVO> decisionTableListToVO(List<DecisionTable> entities) {
    return component.decisionTableListToVO(entities);
  }

  // ===== RuleABPolicy =====

  /**
   * 将 A/B 实验策略实体转换为视图对象。
   *
   * <p>门面方法，实际映射委托给 {@link RuleComponentConverter#entityToVO(RuleABPolicy)}。
   *
   * @param entity A/B 策略实体（含流量比例、灰度条件与回滚阈值），为 {@code null} 时返回 {@code null}
   * @return A/B 策略视图对象；入参为 {@code null} 时返回 {@code null}
   */
  public RuleABPolicyVO entityToVO(RuleABPolicy entity) {
    return component.entityToVO(entity);
  }

  /**
   * 批量将 A/B 实验策略实体转换为视图对象列表。
   *
   * <p>门面方法，委托给 {@link RuleComponentConverter#ruleABPolicyListToVO(List)}，返回顺序与入参一致。
   *
   * @param entities A/B 策略实体列表；为 {@code null} 时返回 {@code null}，空列表时返回空列表
   * @return A/B 策略视图对象列表，顺序与入参一致
   */
  public List<RuleABPolicyVO> ruleABPolicyListToVO(List<RuleABPolicy> entities) {
    return component.ruleABPolicyListToVO(entities);
  }

  // ===== RuleABRollback =====

  /**
   * 将 A/B 实验回滚记录实体转换为视图对象。
   *
   * <p>门面方法，实际映射委托给 {@link RuleComponentConverter#entityToVO(RuleABRollback)}。
   *
   * @param entity 回滚记录实体（含触发原因、回滚前后版本），为 {@code null} 时返回 {@code null}
   * @return 回滚记录视图对象；入参为 {@code null} 时返回 {@code null}
   */
  public RuleABRollbackVO entityToVO(RuleABRollback entity) {
    return component.entityToVO(entity);
  }

  /**
   * 批量将 A/B 实验回滚记录转换为视图对象列表。
   *
   * <p>门面方法，委托给 {@link RuleComponentConverter#ruleABRollbackListToVO(List)}，返回顺序与入参一致。
   *
   * @param entities 回滚记录实体列表；为 {@code null} 时返回 {@code null}，空列表时返回空列表
   * @return 回滚记录视图对象列表，顺序与入参一致
   */
  public List<RuleABRollbackVO> ruleABRollbackListToVO(List<RuleABRollback> entities) {
    return component.ruleABRollbackListToVO(entities);
  }

  // ===== RuleChainGraph =====

  /**
   * 将规则链画布实体转换为视图对象。
   *
   * <p>门面方法，实际映射委托给 {@link RuleComponentConverter#entityToVO(RuleChainGraph)}。 画布的节点与连线定义以 {@code contentJson} 整体透传，本方法不做结构化解析。
   *
   * @param entity 规则链画布实体（含画布版本与状态），为 {@code null} 时返回 {@code null}
   * @return 规则链画布视图对象；入参为 {@code null} 时返回 {@code null}
   */
  public RuleChainGraphVO entityToVO(RuleChainGraph entity) {
    return component.entityToVO(entity);
  }

  /**
   * 批量将规则链画布实体转换为视图对象列表。
   *
   * <p>门面方法，委托给 {@link RuleComponentConverter#ruleChainGraphListToVO(List)}，返回顺序与入参一致。
   *
   * @param entities 规则链画布实体列表；为 {@code null} 时返回 {@code null}，空列表时返回空列表
   * @return 规则链画布视图对象列表，顺序与入参一致
   */
  public List<RuleChainGraphVO> ruleChainGraphListToVO(List<RuleChainGraph> entities) {
    return component.ruleChainGraphListToVO(entities);
  }

  // ===== RuleDefinition (infra entity) =====

  /**
   * 将规则定义持久化实体转换为视图对象。
   *
   * <p>门面方法，实际映射委托给 {@link RuleCoreConverter#entityToVO(com.njydsz.literule.infra.entity.RuleDefinition)}，
   * 条件表达式与动作定义原样带出。
   *
   * @param entity 规则定义持久化实体（含条件表达式、动作与生效时间窗），为 {@code null} 时返回 {@code null}
   * @return 规则定义视图对象；入参为 {@code null} 时返回 {@code null}
   */
  public RuleDefinitionVO entityToVO(com.njydsz.literule.infra.entity.RuleDefinition entity) {
    return core.entityToVO(entity);
  }

  /**
   * 批量将规则定义持久化实体转换为视图对象列表。
   *
   * <p>门面方法，委托给 {@link RuleCoreConverter#ruleDefinitionListToVO(List)}，返回顺序与入参一致。
   *
   * @param entities 规则定义持久化实体列表；为 {@code null} 时返回 {@code null}，空列表时返回空列表
   * @return 规则定义视图对象列表，顺序与入参一致
   */
  public List<RuleDefinitionVO> ruleDefinitionListToVO(
      List<com.njydsz.literule.infra.entity.RuleDefinition> entities) {
    return core.ruleDefinitionListToVO(entities);
  }

  // ===== RuleDependency =====

  /**
   * 将规则依赖关系实体转换为视图对象。
   *
   * <p>门面方法，实际映射委托给 {@link RuleSupportConverter#entityToVO(RuleDependency)}。
   *
   * @param entity 规则依赖实体（描述上游规则与下游规则的引用关系），为 {@code null} 时返回 {@code null}
   * @return 规则依赖视图对象；入参为 {@code null} 时返回 {@code null}
   */
  public RuleDependencyVO entityToVO(RuleDependency entity) {
    return support.entityToVO(entity);
  }

  /**
   * 批量将规则依赖关系实体转换为视图对象列表。
   *
   * <p>门面方法，委托给 {@link RuleSupportConverter#ruleDependencyListToVO(List)}，返回顺序与入参一致。
   *
   * @param entities 规则依赖实体列表；为 {@code null} 时返回 {@code null}，空列表时返回空列表
   * @return 规则依赖视图对象列表，顺序与入参一致
   */
  public List<RuleDependencyVO> ruleDependencyListToVO(List<RuleDependency> entities) {
    return support.ruleDependencyListToVO(entities);
  }

  // ===== RuleExecutionTrace =====

  /**
   * 将规则执行轨迹实体转换为视图对象。
   *
   * <p>门面方法，实际映射委托给 {@link RuleSupportConverter#entityToVO(com.njydsz.literule.infra.entity.RuleExecutionTrace)}。
   * 事实快照与结果快照为 JSON 字段，转换时整体透传。
   *
   * @param entity 执行轨迹实体（含 traceId、条件求值结果与耗时），为 {@code null} 时返回 {@code null}
   * @return 执行轨迹视图对象；入参为 {@code null} 时返回 {@code null}
   */
  public RuleExecutionTraceVO entityToVO(RuleExecutionTrace entity) {
    return support.entityToVO(entity);
  }

  /**
   * 批量将规则执行轨迹实体转换为视图对象列表。
   *
   * <p>门面方法，委托给 {@link RuleSupportConverter#ruleExecutionTraceListToVO(List)}，返回顺序与入参一致。
   *
   * @param entities 执行轨迹实体列表；为 {@code null} 时返回 {@code null}，空列表时返回空列表
   * @return 执行轨迹视图对象列表，顺序与入参一致
   */
  public List<RuleExecutionTraceVO> ruleExecutionTraceListToVO(List<RuleExecutionTrace> entities) {
    return support.ruleExecutionTraceListToVO(entities);
  }

  // ===== RulePack =====

  /**
   * 将规则集持久化实体转换为视图对象。
   *
   * <p>门面方法，实际映射委托给 {@link RuleSupportConverter#entityToVO(com.njydsz.literule.infra.entity.RulePack)}。
   * 标签与规则编码列表在库中以 JSON 字符串存储，转换时不做拆分。
   *
   * @param entity 规则集实体（含集编码、版本号与规则快照），为 {@code null} 时返回 {@code null}
   * @return 规则集视图对象；入参为 {@code null} 时返回 {@code null}
   */
  public RulePackVO entityToVO(RulePack entity) {
    return support.entityToVO(entity);
  }

  /**
   * 批量将规则集实体转换为视图对象列表。
   *
   * <p>门面方法，委托给 {@link RuleSupportConverter#rulePackListToVO(List)}，返回顺序与入参一致。
   *
   * @param entities 规则集实体列表；为 {@code null} 时返回 {@code null}，空列表时返回空列表
   * @return 规则集视图对象列表，顺序与入参一致
   */
  public List<RulePackVO> rulePackListToVO(List<RulePack> entities) {
    return support.rulePackListToVO(entities);
  }

  // ===== RuleTemplate =====

  /**
   * 将规则模板实体转换为视图对象。
   *
   * <p>门面方法，实际映射委托给 {@link RuleComponentConverter#entityToVO(RuleTemplate)}。
   *
   * @param entity 规则模板实体（含模板化的条件/动作骨架），为 {@code null} 时返回 {@code null}
   * @return 规则模板视图对象；入参为 {@code null} 时返回 {@code null}
   */
  public RuleTemplateVO entityToVO(RuleTemplate entity) {
    return component.entityToVO(entity);
  }

  /**
   * 批量将规则模板实体转换为视图对象列表。
   *
   * <p>门面方法，委托给 {@link RuleComponentConverter#ruleTemplateListToVO(List)}，返回顺序与入参一致。
   *
   * @param entities 规则模板实体列表；为 {@code null} 时返回 {@code null}，空列表时返回空列表
   * @return 规则模板视图对象列表，顺序与入参一致
   */
  public List<RuleTemplateVO> ruleTemplateListToVO(List<RuleTemplate> entities) {
    return component.ruleTemplateListToVO(entities);
  }

  // ===== RuleVersionHistory → RuleVersionVO =====

  /**
   * 将规则版本历史实体转换为版本视图对象。
   *
   * <p>门面方法，实际映射委托给 {@link RuleSupportConverter#ruleVersionHistoryToVO(RuleVersionHistory)}，
   * 创建时间 {@code createdAt} 不参与映射，由调用方按需回填。
   *
   * @param entity 版本历史实体（含版本号的规则定义 JSON 快照），为 {@code null} 时返回 {@code null}
   * @return 规则版本视图对象；入参为 {@code null} 时返回 {@code null}
   */
  public RuleVersionVO ruleVersionHistoryToVO(RuleVersionHistory entity) {
    return support.ruleVersionHistoryToVO(entity);
  }

  /**
   * 批量将规则版本历史实体转换为版本视图对象列表。
   *
   * <p>门面方法，委托给 {@link RuleSupportConverter#ruleVersionListToVO(List)}，返回顺序与入参一致。
   *
   * @param entities 版本历史实体列表；为 {@code null} 时返回 {@code null}，空列表时返回空列表
   * @return 规则版本视图对象列表，顺序与入参一致
   */
  public List<RuleVersionVO> ruleVersionListToVO(List<RuleVersionHistory> entities) {
    return support.ruleVersionListToVO(entities);
  }

  // ===== RuleVersionDTO → RuleVersionHistory =====

  /**
   * 将新建版本请求 DTO 转换为待落库的版本历史实体。
   *
   * <p>门面方法，委托给 {@link RuleSupportConverter#postDtoToEntity(RuleVersionDTO)}，主键 {@code id} 由数据库生成故忽略映射。
   *
   * @param dto 新建版本入参（含规则编码、版本号、定义 JSON 与变更说明），为 {@code null} 时返回 {@code null}
   * @return 版本历史实体；入参为 {@code null} 时返回 {@code null}
   */
  public RuleVersionHistory postDtoToEntity(RuleVersionDTO dto) {
    return support.postDtoToEntity(dto);
  }

  // ===== RuleDefinitionDTO (api) → RuleDefinitionVO =====

  /**
   * 将 API 层规则定义 DTO 转换为视图对象。
   *
   * <p>门面方法，委托给 {@link RuleCoreConverter#entityToVO(com.njydsz.literule.domain.dto.RuleDefinitionDTO)}，
   * 其中 {@code code} 映射为 {@code ruleCode}、{@code name} 映射为 {@code ruleName}， 主键与灰度条件、生效时间窗等字段不参与映射。
   *
   * @param entity API 层规则定义 DTO，为 {@code null} 时返回 {@code null}
   * @return 规则定义视图对象；入参为 {@code null} 时返回 {@code null}
   */
  public RuleDefinitionVO entityToVO(RuleDefinitionDTO entity) {
    return core.entityToVO(entity);
  }

  // ===== RuleResultVO (api) → RuleResultVO =====

  /**
   * 将 API 层规则执行结果转换为视图对象。
   *
   * <p>门面方法，实际映射委托给 {@link RuleCoreConverter#entityToVO(RuleResultVO)}，字段同名直接映射。
   *
   * @param entity API 层规则执行结果（含是否命中、动作输出与耗时），为 {@code null} 时返回 {@code null}
   * @return 规则执行结果视图对象；入参为 {@code null} 时返回 {@code null}
   */
  public RuleResultVO entityToVO(RuleResultVO entity) {
    return core.entityToVO(entity);
  }

  // ===== RuleEngineStatsVO (api) → RuleEngineStatsVO =====

  /**
   * 将 API 层引擎统计数据转换为视图对象。
   *
   * <p>门面方法，实际映射委托给 {@link RuleCoreConverter#entityToVO(RuleEngineStatsVO)}，字段同名直接映射。
   *
   * @param entity API 层引擎统计数据（含评估次数、触发次数与平均耗时），为 {@code null} 时返回 {@code null}
   * @return 引擎统计视图对象；入参为 {@code null} 时返回 {@code null}
   */
  public RuleEngineStatsVO entityToVO(RuleEngineStatsVO entity) {
    return core.entityToVO(entity);
  }

  // ===== RulePackVO (api) → RulePackVO =====

  /**
   * 将 API 层规则集对象转换为视图对象。
   *
   * <p>门面方法，委托给 {@link RuleCoreConverter#entityToVO(com.njydsz.literule.domain.vo.RulePackVO)}。
   * API 侧 {@code tags} / {@code ruleCodes} / {@code ruleSnapshots} 为集合类型、{@code rating} 为 {@code double}，
   * 与 VO 侧的字符串与 {@code BigDecimal} 表示不兼容，这些字段均忽略映射。
   *
   * @param entity API 层规则集对象，为 {@code null} 时返回 {@code null}
   * @return 规则集视图对象，{@code tags} / {@code ruleCodes} / {@code ruleSnapshots} / {@code rating} 为 {@code null}；
   *     入参为 {@code null} 时返回 {@code null}
   */
  public RulePackVO entityToVO(RulePackVO entity) {
    return core.entityToVO(entity);
  }

  // ===== DecisionTableDefinitionDTO (api) → DecisionTableDefinitionVO =====

  /**
   * 将 API 层决策表定义 DTO 转换为视图对象。
   *
   * <p>门面方法，实际映射委托给 {@link RuleCoreConverter#entityToVO(DecisionTableDefinitionDTO)}，字段同名直接映射。
   *
   * @param entity API 层决策表定义（含条件列、动作列与命中策略），为 {@code null} 时返回 {@code null}
   * @return 决策表定义视图对象；入参为 {@code null} 时返回 {@code null}
   */
  public DecisionTableDefinitionVO entityToVO(DecisionTableDefinitionDTO entity) {
    return core.entityToVO(entity);
  }

  // ===== ExpressionValidationResult (api.expr) → ExpressionValidationResultVO =====

  /**
   * 将 API 层表达式校验结果转换为视图对象。
   *
   * <p>门面方法，实际映射委托给 {@link RuleCoreConverter#entityToVO(ExpressionValidationResult)}，字段同名直接映射。
   *
   * @param entity API 层校验结果（含是否合法、错误码与错误位置），为 {@code null} 时返回 {@code null}
   * @return 表达式校验结果视图对象；入参为 {@code null} 时返回 {@code null}
   */
  public ExpressionValidationResultVO entityToVO(ExpressionValidationResult entity) {
    return core.entityToVO(entity);
  }

  // ===== ExpressionFunctionDef (api.expr) → ExpressionFunctionDefVO =====

  /**
   * 将 API 层表达式函数定义转换为视图对象。
   *
   * <p>门面方法，实际映射委托给 {@link RuleCoreConverter#entityToVO(ExpressionFunctionDef)}，字段同名直接映射。
   *
   * @param entity API 层函数定义（含函数名、参数签名与返回值说明），为 {@code null} 时返回 {@code null}
   * @return 表达式函数定义视图对象；入参为 {@code null} 时返回 {@code null}
   */
  public ExpressionFunctionDefVO entityToVO(ExpressionFunctionDef entity) {
    return core.entityToVO(entity);
  }

  // ===== DecisionTable PostDTO → Entity =====

  /**
   * 将新建决策表请求 DTO 转换为待落库的持久化实体。
   *
   * <p>门面方法，委托给 {@link RuleSupportConverter#postDtoToEntity(DecisionTableDTO)}。
   * 主键、逻辑删除标记、乐观锁版本号、租户与审计字段均由持久层与拦截器填写，故忽略映射。
   *
   * @param dto 新建决策表入参，为 {@code null} 时返回 {@code null}
   * @return 决策表持久化实体，{@code id} / {@code deleted} / {@code revision} 等字段为 {@code null}；
   *     入参为 {@code null} 时返回 {@code null}
   */
  public DecisionTable postDtoToEntity(DecisionTableDTO dto) {
    return support.postDtoToEntity(dto);
  }

  // ===== RuleABPolicy PutDTO → Entity =====
  public RuleABPolicy putDtoToEntity(RuleABPolicyDTO dto) {
    return support.putDtoToEntity(dto);
  }
}


