package com.njydsz.literule.server.converter;

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
 * <p>承担 server 包源类型到 domain VO 的转换（原先位于 literule-infra，后迁入 server 层，彻底消除 infra 对 server 的反向依赖）。
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li>使用 MapStruct 注解处理器，编译期生成实现类，性能优于反射</li>
 *   <li>通过 {@link #INSTANCE} 单例访问，零依赖注入</li>
 *   <li>同名字段自动映射；源类型额外字段自动忽略</li>
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
    @Mapping(source = "level", target = "depth")
    CategoryNodeVO entityToVO(CategoryTreeNode entity);

    // ===== RuleVersion → RuleVersionVO =====
    RuleVersionVO entityToVO(RuleVersion entity);

    // ===== RuleVersionDiff → RuleVersionDiffVO =====
    @Mapping(target = "type", ignore = true)
    @Mapping(target = "field", ignore = true)
    @Mapping(target = "fieldLabel", ignore = true)
    @Mapping(target = "oldValue", ignore = true)
    @Mapping(target = "newValue", ignore = true)
    RuleVersionDiffVO entityToVO(RuleVersionDiff entity);

    // ===== RuleChainGraph → RuleChainGraphVO =====
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

    // ===== DecisionTableDefinitionDTO (api) → DecisionTableDefinitionVO =====
    DecisionTableDefinitionVO entityToVO(DecisionTableDefinitionDTO entity);

    // ===== ExpressionFunctionDef (api.expr) → ExpressionFunctionDefVO =====
    ExpressionFunctionDefVO entityToVO(ExpressionFunctionDef entity);

    /** 表达式校验结果（api.expr）→ VO */
    ExpressionValidationResultVO entityToVO(ExpressionValidationResult entity);
}