package com.njydsz.literule.web;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import com.njydsz.literule.domain.vo.ApprovalFlowVO;
import com.njydsz.literule.domain.vo.ApprovalRecordVO;
import com.njydsz.literule.domain.vo.CategoryNodeVO;
import com.njydsz.literule.domain.vo.ExpressionPreviewResultVO;
import com.njydsz.literule.domain.vo.InstallResultVO;
import com.njydsz.literule.domain.vo.PackDiffVO;
import com.njydsz.literule.domain.vo.PackUpdateInfoVO;
import com.njydsz.literule.domain.vo.RuleChainGraphVO;
import com.njydsz.literule.domain.vo.RuleConflictInfoVO;
import com.njydsz.literule.domain.vo.RuleVersionDiffVO;
import com.njydsz.literule.domain.vo.RuleVersionVO;
import com.njydsz.literule.server.approval.ApprovalFlow;
import com.njydsz.literule.server.approval.ApprovalRecord;
import com.njydsz.literule.server.expr.ExpressionPreviewResult;
import com.njydsz.literule.server.orchestrator.RuleChainGraph;
import com.njydsz.literule.server.spi.RuleCategoryProvider.CategoryNode;
import com.njydsz.literule.server.spi.RuleConflictDetectorProvider.RuleConflictInfo;
import com.njydsz.literule.server.spi.RulePackProvider.InstallResult;
import com.njydsz.literule.server.spi.RulePackProvider.PackDiff;
import com.njydsz.literule.server.spi.RulePackProvider.PackUpdateInfo;
import com.njydsz.literule.server.spi.RuleVersion;
import com.njydsz.literule.server.version.RuleVersionDiff;

/**
 * literule-web 模块的 MapStruct 转换器。
 *
 * <p>承担 server 包源类型到 domain VO 的转换。这些 server 类型无法在 domain 模块的
 * {@code LiteruleConverter} 中声明，因为 domain 模块不依赖 server 模块（避免循环依赖）。
 * web 模块同时依赖 domain 与 server，因此在此声明对应映射方法。
 *
 * <p><b>设计要点：</b>
 * <ul>
 *   <li>使用 MapStruct 注解处理器，编译期生成实现类，性能优于反射</li>
 *   <li>通过 {@link #INSTANT} 单例访问，零依赖注入</li>
 *   <li>同名字段自动映射；源类型额外字段自动忽略</li>
 *   <li>列表转换通过 {@code .stream().map(INSTANT::entityToVO).toList()} 完成，
 *       避免泛型类型擦除导致的方法签名冲突</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Mapper
public interface LiteruleWebConverter {

    LiteruleWebConverter INSTANT = Mappers.getMapper(LiteruleWebConverter.class);

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

    // ===== RuleCategoryProvider.CategoryNode → CategoryNodeVO =====
    CategoryNodeVO entityToVO(CategoryNode entity);

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

}
