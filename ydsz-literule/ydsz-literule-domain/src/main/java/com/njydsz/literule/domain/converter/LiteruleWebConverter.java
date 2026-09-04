package com.njydsz.literule.domain.converter;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

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
 * literule-server 模块的 MapStruct 转换器。
 *
 * <p>承担 server 包源类型到 domain VO 的转换（原位置 literule-infra，2026-09-03 迁层）。
 *
 * <p>此前放在 infra 会导致低层反向依赖高层（infra → server）。迁入 server 后，converter 可直接使用
 * server 自身类型与 domain VO，消除反向依赖，符合 DDD 分层（web → server → domain ← infra）。
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li>使用 MapStruct 注解处理器，编译期生成实现类，性能优于反射
 *   <li>通过 {@link #INSTANCE} 单例访问，零依赖注入
 *   <li>同名字段自动映射；源类型额外字段自动忽略
 *   <li>列表转换通过 {@code .stream().map(INSTANCE::entityToVO).toList()} 完成， 避免泛型类型擦除导致的方法签名冲突
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Mapper
public interface LiteruleWebConverter {

  /** MapStruct 单例实例 */
  LiteruleWebConverter INSTANCE = Mappers.getMapper(LiteruleWebConverter.class);

  // ===== RulePackProvider.InstallResult → InstallResultVO =====
  InstallResultVO entityToVO(InstallResult entity);

  // ===== RulePackProvider.PackDiff → PackDiffVO =====
  PackDiffVO entityToVO(PackDiff entity);

  // ===== RulePackProvider.PackUpdateInfo → PackUpdateInfoVO =====
  PackUpdateInfoVO entityToVO(PackUpdateInfo entity);

  // ===== ApprovalRecord → ApprovalRecordVO =====
  ApprovalRecordVO entityToVO(ApprovalRecord entity);

  // ===== ApprovalFlow → ApprovalFlowVO =====
  ApprovalFlowVO entityToVO(ApprovalFlow entity);

  // ===== RuleConflictDetectorProvider.RuleConflictInfo → RuleConflictInfoVO =====
  RuleConflictInfoVO entityToVO(RuleConflictInfo entity);

  // ===== CategoryTreeNode → CategoryNodeVO =====
  // 注意：TreeNode.level → VO.depth 字段名不同；TreeNode 的 id/parentId/sort/leaf/children 在 VO 中无对应字段，忽略。
  @Mapping(source = "level", target = "depth")
  CategoryNodeVO entityToVO(CategoryTreeNode entity);

  // ===== RuleVersion → RuleVersionVO =====
  RuleVersionVO entityToVO(RuleVersion entity);

  // ===== RuleVersionDiff → RuleVersionDiffVO =====
  // 注意：VO 中的 type/field/fieldLabel/oldValue/newValue 属于 DiffEntry 级别字段，
  // 在 RuleVersionDiff 层面无对应源字段，忽略。
  @Mapping(target = "type", ignore = true)
  @Mapping(target = "field", ignore = true)
  @Mapping(target = "fieldLabel", ignore = true)
  @Mapping(target = "oldValue", ignore = true)
  @Mapping(target = "newValue", ignore = true)
  RuleVersionDiffVO entityToVO(RuleVersionDiff entity);

  // ===== RuleChainGraph → RuleChainGraphVO =====
  // 注意：graphId → id 字段名不同；version(String) → graphVersion(Integer) 类型不兼容，忽略；
  // contentJson 在源对象中无对应字段，忽略。
  @Mapping(source = "graphId", target = "id")
  @Mapping(target = "graphVersion", ignore = true)
  @Mapping(target = "contentJson", ignore = true)
  RuleChainGraphVO entityToVO(RuleChainGraph entity);

  // ===== ExpressionPreviewResult → ExpressionPreviewResultVO =====
  ExpressionPreviewResultVO entityToVO(ExpressionPreviewResult entity);

  // ===== RuleABPolicyDTO → RuleABPolicyVO =====
  RuleABPolicyVO putDtoToVO(RuleABPolicyDTO dto);

  // ===== RuleDefinitionDTO (api) → RuleDefinitionVO =====
  @Mapping(source = "code", target = "ruleCode")
  @Mapping(source = "name", target = "ruleName")
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "canaryConditions", ignore = true)
  @Mapping(target = "effectiveFrom", ignore = true)
  @Mapping(target = "effectiveTo", ignore = true)
  @Mapping(target = "reviewedAt", ignore = true)
  RuleDefinitionVO entityToVO(RuleDefinitionDTO entity);

  // ===== RuleResultVO (api) → RuleResultVO =====
  RuleResultVO entityToVO(RuleResultVO entity);

  // ===== RuleEngineStatsVO (api) → RuleEngineStatsVO =====
  RuleEngineStatsVO entityToVO(RuleEngineStatsVO entity);

  // ===== RulePackVO (api) → RulePackVO =====
  // api.RulePackVO 的 tags/ruleCodes 为 List<String>、ruleSnapshots 为 List<RuleDefinitionDTO>、
  // rating 为 double，与 VO 的 String/BigDecimal 不兼容，忽略。
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "tags", ignore = true)
  @Mapping(target = "ruleCodes", ignore = true)
  @Mapping(target = "ruleSnapshots", ignore = true)
  @Mapping(target = "rating", ignore = true)
  RulePackVO entityToVO(RulePackVO entity);

  // ===== DecisionTableDefinitionDTO (api) → DecisionTableDefinitionVO =====
  DecisionTableDefinitionVO entityToVO(DecisionTableDefinitionDTO entity);

  // ===== ExpressionFunctionDef (api.expr) → ExpressionFunctionDefVO =====
  ExpressionFunctionDefVO entityToVO(ExpressionFunctionDef entity);

  /**
   * 表达式校验结果（api.expr）→ VO
   *
   * @param entity 校验结果
   * @return 校验结果 VO
   */
  ExpressionValidationResultVO entityToVO(ExpressionValidationResult entity);
}
