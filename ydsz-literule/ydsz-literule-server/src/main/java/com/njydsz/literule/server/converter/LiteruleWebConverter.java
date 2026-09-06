package com.njydsz.literule.server.converter;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.njydsz.literule.domain.converter.RuleCoreConverter;
import com.njydsz.literule.domain.dto.DecisionTableDefinitionDTO;
import com.njydsz.literule.domain.dto.RuleABPolicyDTO;
import com.njydsz.literule.domain.dto.RuleDefinitionDTO;
import com.njydsz.literule.domain.expression.ExpressionFunctionDef;
import com.njydsz.literule.domain.expression.ExpressionValidationResult;
import com.njydsz.literule.domain.vo.ApprovalFlowVO;
import com.njydsz.literule.domain.vo.ApprovalRecordVO;
import com.njydsz.literule.domain.vo.CategoryNodeVO;
import com.njydsz.literule.domain.vo.DecisionTableDefinitionVO;
import com.njydsz.literule.domain.vo.ExpressionFunctionDefVO;
import com.njydsz.literule.domain.vo.ExpressionPreviewResultVO;
import com.njydsz.literule.domain.vo.ExpressionValidationResultVO;
import com.njydsz.literule.domain.vo.InstallResultVO;
import com.njydsz.literule.domain.vo.PackDiffVO;
import com.njydsz.literule.domain.vo.PackUpdateInfoVO;
import com.njydsz.literule.domain.vo.RuleABPolicyVO;
import com.njydsz.literule.domain.vo.RuleChainGraphVO;
import com.njydsz.literule.domain.vo.RuleConflictInfoVO;
import com.njydsz.literule.domain.vo.RuleDefinitionVO;
import com.njydsz.literule.domain.vo.RuleEngineStatsVO;
import com.njydsz.literule.domain.vo.RulePackVO;
import com.njydsz.literule.domain.vo.RuleResultVO;
import com.njydsz.literule.domain.vo.RuleVersionDiffVO;
import com.njydsz.literule.domain.vo.RuleVersionVO;
import com.njydsz.literule.server.approval.ApprovalFlow;
import com.njydsz.literule.server.approval.ApprovalRecord;
import com.njydsz.literule.server.expression.ExpressionPreviewResult;
import com.njydsz.literule.server.orchestrator.RuleChainGraph;
import com.njydsz.literule.server.spi.CategoryTreeNode;
import com.njydsz.literule.server.spi.RuleConflictDetectorProvider.RuleConflictInfo;
import com.njydsz.literule.server.spi.RulePackProvider.InstallResult;
import com.njydsz.literule.server.spi.RulePackProvider.PackDiff;
import com.njydsz.literule.server.spi.RulePackProvider.PackUpdateInfo;
import com.njydsz.literule.server.spi.RuleVersion;
import com.njydsz.literule.server.version.RuleVersionDiff;

/**
 * literule-server 模块的 Web 层转换器（P0-F1：YDIZ-DDD-004 修复）。
 *
 * <p>承担 server 包源类型到 domain VO 的转换，替代原先基于 MapStruct 的接口实现（去除 server 层对 MapStruct 的
 * 依赖，符合 YDIZ-DDD-004 规范：server 层禁止声明 MapStruct 依赖）。
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li>使用手动映射，编译期可见、易于调试</li>
 *   <li>作为 Spring {@code @Component} 单例，由容器管理与注入</li>
 *   <li>复用 {@link RuleCoreConverter} 处理 domain DTO → domain VO 的标准映射</li>
 *   <li>纯 server 源类型（ApprovalRecord / RuleVersion 等）采用手写映射</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Component
public class LiteruleWebConverter {

  /** 复用 domain 层核心转换器（处理 DTO → VO 映射） */
  private final RuleCoreConverter coreConverter = RuleCoreConverter.INSTANCE;

  // ===== RulePackProvider.InstallResult → InstallResultVO =====

  /**
   * 将规则集安装结果转换为视图对象。
   *
   * @param entity 安装结果源对象；为 {@code null} 时返回 {@code null}
   * @return 安装结果视图对象
   */
  public InstallResultVO entityToVO(InstallResult entity) {
    if (entity == null) {
      return null;
    }
    InstallResultVO vo = new InstallResultVO();
    vo.setPackCode(entity.getPackCode());
    vo.setVersion(entity.getVersion());
    vo.setTotal(entity.getTotal());
    vo.setSuccess(entity.getSuccess());
    vo.setFailed(entity.getFailed());
    vo.setFailedCodes(entity.getFailedCodes());
    return vo;
  }

  // ===== RulePackProvider.PackDiff → PackDiffVO =====

  /**
   * 将规则集版本差异转换为视图对象。
   *
   * @param entity 版本差异源对象；为 {@code null} 时返回 {@code null}
   * @return 版本差异视图对象
   */
  public PackDiffVO entityToVO(PackDiff entity) {
    if (entity == null) {
      return null;
    }
    PackDiffVO vo = new PackDiffVO();
    vo.setPackCode(entity.getPackCode());
    vo.setFromVersion(entity.getFromVersion());
    vo.setToVersion(entity.getToVersion());
    vo.setAdded(entity.getAdded());
    vo.setRemoved(entity.getRemoved());
    vo.setChanged(entity.getChanged());
    return vo;
  }

  // ===== RulePackProvider.PackUpdateInfo → PackUpdateInfoVO =====

  /**
   * 将规则集更新信息转换为视图对象。
   *
   * @param entity 更新信息源对象；为 {@code null} 时返回 {@code null}
   * @return 更新信息视图对象
   */
  public PackUpdateInfoVO entityToVO(PackUpdateInfo entity) {
    if (entity == null) {
      return null;
    }
    PackUpdateInfoVO vo = new PackUpdateInfoVO();
    vo.setPackCode(entity.getPackCode());
    vo.setPackName(entity.getPackName());
    vo.setInstalledVersion(entity.getInstalledVersion());
    vo.setLatestVersion(entity.getLatestVersion());
    vo.setHasUpdate(entity.isHasUpdate());
    vo.setInstalledAt(entity.getInstalledAt());
    vo.setIndustry(entity.getIndustry());
    vo.setDescription(entity.getDescription());
    return vo;
  }

  // ===== ApprovalRecord → ApprovalRecordVO =====

  /**
   * 将审批记录转换为视图对象。
   *
   * @param entity 审批记录源对象；为 {@code null} 时返回 {@code null}
   * @return 审批记录视图对象
   */
  public ApprovalRecordVO entityToVO(ApprovalRecord entity) {
    if (entity == null) {
      return null;
    }
    ApprovalRecordVO vo = new ApprovalRecordVO();
    vo.setRecordId(entity.getRecordId());
    vo.setRuleCode(entity.getRuleCode());
    vo.setFlowCode(entity.getFlowCode());
    vo.setCurrentLevel(entity.getCurrentLevel());
    vo.setCurrentStatus(entity.getCurrentStatus());
    vo.setCreatedAt(entity.getCreatedAt());
    vo.setUpdatedAt(entity.getUpdatedAt());
    return vo;
  }

  // ===== ApprovalFlow → ApprovalFlowVO =====

  /**
   * 将审批流配置转换为视图对象。
   *
   * @param entity 审批流源对象；为 {@code null} 时返回 {@code null}
   * @return 审批流视图对象
   */
  public ApprovalFlowVO entityToVO(ApprovalFlow entity) {
    if (entity == null) {
      return null;
    }
    ApprovalFlowVO vo = new ApprovalFlowVO();
    vo.setFlowCode(entity.getFlowCode());
    vo.setName(entity.getName());
    vo.setSteps(entity.getSteps() != null ? Collections.unmodifiableList(entity.getSteps()) : null);
    vo.setEnabled(entity.isEnabled());
    return vo;
  }

  // ===== RuleConflictDetectorProvider.RuleConflictInfo → RuleConflictInfoVO =====

  /**
   * 将规则冲突信息转换为视图对象。
   *
   * @param entity 冲突信息源对象；为 {@code null} 时返回 {@code null}
   * @return 冲突信息视图对象
   */
  public RuleConflictInfoVO entityToVO(RuleConflictInfo entity) {
    if (entity == null) {
      return null;
    }
    RuleConflictInfoVO vo = new RuleConflictInfoVO();
    vo.setRuleA(entity.getRuleA());
    vo.setRuleAName(entity.getRuleAName());
    vo.setRuleB(entity.getRuleB());
    vo.setRuleBName(entity.getRuleBName());
    vo.setOverlapFields(entity.getOverlapFields());
    vo.setSeverity(entity.getSeverity());
    return vo;
  }

  // ===== CategoryTreeNode → CategoryNodeVO =====

  /**
   * 将规则分类目录树节点转换为视图对象。
   *
   * <p>源类型层级（level）映射为目标类型深度（depth）。
   *
   * @param entity 分类目录树节点；为 {@code null} 时返回 {@code null}
   * @return 分类节点视图对象
   */
  public CategoryNodeVO entityToVO(CategoryTreeNode entity) {
    if (entity == null) {
      return null;
    }
    CategoryNodeVO vo = new CategoryNodeVO();
    vo.setName(entity.getName());
    vo.setPath(entity.getPath());
    vo.setDepth(entity.getLevel());
    vo.setRoot(entity.isRoot());
    vo.setRuleCount(entity.getRuleCount());
    return vo;
  }

  // ===== RuleVersion → RuleVersionVO =====

  /**
   * 将规则版本快照转换为视图对象。
   *
   * @param entity 规则版本源对象；为 {@code null} 时返回 {@code null}
   * @return 规则版本视图对象
   */
  public RuleVersionVO entityToVO(RuleVersion entity) {
    if (entity == null) {
      return null;
    }
    RuleVersionVO vo = new RuleVersionVO();
    vo.setId(entity.getId());
    vo.setRuleCode(entity.getRuleCode());
    vo.setVersion(entity.getVersion());
    vo.setDefinitionJson(entity.getDefinitionJson());
    vo.setChangeDesc(entity.getChangeDesc());
    vo.setOperator(entity.getOperator());
    vo.setCreatedAt(entity.getCreatedAt());
    return vo;
  }

  // ===== RuleVersionDiff → RuleVersionDiffVO =====

  /**
   * 将规则版本结构化差异转换为视图对象。
   *
   * <p>差异条目列表以 {@link Object} 类型透传，字段级详情（type/field/fieldLabel/oldValue/newValue）不参与映射。
   *
   * @param entity 版本差异源对象；为 {@code null} 时返回 {@code null}
   * @return 版本差异视图对象
   */
  public RuleVersionDiffVO entityToVO(RuleVersionDiff entity) {
    if (entity == null) {
      return null;
    }
    RuleVersionDiffVO vo = new RuleVersionDiffVO();
    vo.setOldVersion(entity.getOldVersion());
    vo.setNewVersion(entity.getNewVersion());
    vo.setRuleCode(entity.getRuleCode());
    vo.setEntries(
        entity.getEntries() != null
            ? entity.getEntries().stream()
                .map(e -> (Object) e)
                .collect(Collectors.toList())
            : null);
    vo.setSummary(entity.getSummary());
    return vo;
  }

  // ===== RuleChainGraph → RuleChainGraphVO =====

  /**
   * 将规则链画布图转换为视图对象。
   *
   * <p>画布的 graphId 映射为目标 id。graphVersion 与 contentJson 不参与映射。
   *
   * @param entity 规则链画布图源对象；为 {@code null} 时返回 {@code null}
   * @return 规则链画布视图对象
   */
  public RuleChainGraphVO entityToVO(RuleChainGraph entity) {
    if (entity == null) {
      return null;
    }
    RuleChainGraphVO vo = new RuleChainGraphVO();
    vo.setId(entity.getGraphId());
    vo.setRuleCode(entity.getRuleCode());
    vo.setName(entity.getName());
    vo.setDescription(entity.getDescription());
    vo.setScenario(entity.getScenario());
    vo.setStatus(entity.getStatus());
    vo.setCreatedAt(entity.getCreatedAt());
    vo.setUpdatedAt(entity.getUpdatedAt());
    vo.setCreatedBy(entity.getCreatedBy());
    vo.setUpdatedBy(entity.getUpdatedBy());
    return vo;
  }

  // ===== ExpressionPreviewResult → ExpressionPreviewResultVO =====

  /**
   * 将表达式求值预览结果转换为视图对象。
   *
   * @param entity 表达式预览结果源对象；为 {@code null} 时返回 {@code null}
   * @return 表达式预览视图对象
   */
  public ExpressionPreviewResultVO entityToVO(ExpressionPreviewResult entity) {
    if (entity == null) {
      return null;
    }
    ExpressionPreviewResultVO vo = new ExpressionPreviewResultVO();
    vo.setExpression(entity.getExpression());
    vo.setValue(entity.getValue());
    vo.setJavaType(entity.getJavaType());
    vo.setBooleanValue(entity.getBooleanValue());
    vo.setElapsedMs(entity.getElapsedMs());
    vo.setError(entity.getError());
    return vo;
  }

  // ===== RuleABPolicyDTO → RuleABPolicyVO =====

  /**
   * 将 A/B 测试策略 DTO 转换为视图对象。
   *
   * <p>审计字段（lastEvaluatedAt / lastRollbackAt / createdBy / createdAt / updatedBy / updatedAt）不参与映射。
   *
   * @param dto A/B 测试策略 DTO；为 {@code null} 时返回 {@code null}
   * @return A/B 策略视图对象
   */
  public RuleABPolicyVO putDtoToVO(RuleABPolicyDTO dto) {
    if (dto == null) {
      return null;
    }
    RuleABPolicyVO vo = new RuleABPolicyVO();
    vo.setId(dto.getId());
    vo.setRuleCode(dto.getRuleCode());
    vo.setAutoRollbackEnabled(dto.getAutoRollbackEnabled());
    vo.setRollbackAction(dto.getRollbackAction());
    vo.setErrorRateThreshold(dto.getErrorRateThreshold());
    vo.setMinSampleSize(dto.getMinSampleSize());
    vo.setEvaluationWindowMinutes(dto.getEvaluationWindowMinutes());
    vo.setTrafficRatio(dto.getTrafficRatio());
    vo.setCooldownMinutes(dto.getCooldownMinutes());
    vo.setNotifyChannels(dto.getNotifyChannels());
    vo.setStatus(dto.getStatus());
    return vo;
  }

  // ===== RuleDefinitionDTO (api) → RuleDefinitionVO =====

  /**
   * 将 API 层规则定义 DTO 转换为视图对象。
   *
   * <p>委托给 {@link RuleCoreConverter#entityToVO(RuleDefinitionDTO)} 完成映射
   * （code → ruleCode、name → ruleName，其余字段由 domain 层映射处理）。
   *
   * @param dto API 层规则定义 DTO；为 {@code null} 时返回 {@code null}
   * @return 规则定义视图对象
   */
  public RuleDefinitionVO entityToVO(RuleDefinitionDTO dto) {
    return coreConverter.entityToVO(dto);
  }

  // ===== DecisionTableDefinitionDTO (api) → DecisionTableDefinitionVO =====

  /**
   * 将 API 层决策表定义 DTO 转换为视图对象。
   *
   * <p>委托给 {@link RuleCoreConverter#entityToVO(DecisionTableDefinitionDTO)} 完成映射。
   *
   * @param dto API 层决策表定义 DTO；为 {@code null} 时返回 {@code null}
   * @return 决策表定义视图对象
   */
  public DecisionTableDefinitionVO entityToVO(DecisionTableDefinitionDTO dto) {
    return coreConverter.entityToVO(dto);
  }

  // ===== ExpressionFunctionDef (api.expr) → ExpressionFunctionDefVO =====

  /**
   * 将 API 层表达式函数定义转换为视图对象。
   *
   * @param entity 表达式函数定义；为 {@code null} 时返回 {@code null}
   * @return 表达式函数定义视图对象
   */
  public ExpressionFunctionDefVO entityToVO(ExpressionFunctionDef entity) {
    return coreConverter.entityToVO(entity);
  }

  /**
   * 将 API 层表达式校验结果转换为视图对象。
   *
   * @param entity 表达式校验结果；为 {@code null} 时返回 {@code null}
   * @return 表达式校验结果视图对象
   */
  public ExpressionValidationResultVO entityToVO(ExpressionValidationResult entity) {
    return coreConverter.entityToVO(entity);
  }

  // ===== 同类型透传映射（Web 层引用需要） =====

  /**
   * RulePackVO 透传。
   *
   * @param vo 视图对象
   * @return 同一视图对象
   */
  public RulePackVO entityToVO(RulePackVO vo) {
    return vo;
  }

  /**
   * RuleEngineStatsVO 透传。
   *
   * @param vo 视图对象
   * @return 同一视图对象
   */
  public RuleEngineStatsVO entityToVO(RuleEngineStatsVO vo) {
    return vo;
  }

  /**
   * RuleResultVO 透传。
   *
   * @param vo 视图对象
   * @return 同一视图对象
   */
  public RuleResultVO entityToVO(RuleResultVO vo) {
    return vo;
  }
}
