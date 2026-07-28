#!/usr/bin/env python3
"""Fix compilation errors in ydsz-literule-web controllers.

Replaces LiteruleConverter calls that use API/server/SPI types (which the
converter doesn't handle) with YdszJson-based deep conversion or manual mapping.
"""
import re
import os

BASE = r"d:\Code\ydsz\ydsz-pmis\ydsz-backend\ydsz-literule\ydsz-literule-web\src\main\java\com\njydsz\literule\web"

def read_file(path):
    with open(path, "r", encoding="utf-8") as f:
        return f.read()

def write_file(path, content):
    with open(path, "w", encoding="utf-8") as f:
        f.write(content)

def ensure_import(content, import_line):
    """Add import_line if not present."""
    if import_line in content:
        return content
    # Find the last import line and add after it
    lines = content.split("\n")
    last_import_idx = -1
    for i, line in enumerate(lines):
        if line.startswith("import "):
            last_import_idx = i
    if last_import_idx >= 0:
        lines.insert(last_import_idx + 1, import_line)
    return "\n".join(lines)

def remove_import(content, import_line):
    """Remove import_line if present."""
    return content.replace(import_line + "\n", "")

def fix_file(filename, replacements, add_imports=None, remove_imports=None):
    path = os.path.join(BASE, filename)
    content = read_file(path)

    for old, new in replacements:
        if old not in content:
            print(f"WARNING: In {filename}, pattern not found: {old[:80]}...")
            continue
        content = content.replace(old, new)

    if add_imports:
        for imp in add_imports:
            content = ensure_import(content, imp)

    if remove_imports:
        for imp in remove_imports:
            content = remove_import(content, imp)

    write_file(path, content)
    print(f"Fixed: {filename}")

# ===== RuleAdminController.java =====
fix_file("RuleAdminController.java", [
    # Line 105: ruleDefinitionListToVO with API type
    (
        "LiteruleConverter.INSTANT.ruleDefinitionListToVO(ruleAdminService.listAll())",
        "ruleAdminService.listAll().stream().map(e -> YdszJson.toObject(YdszJson.toJson(e), RuleDefinitionVO.class)).toList()"
    ),
    # Line 116: entityToVO(RuleDefinition) - API type
    (
        "LiteruleConverter.INSTANT.entityToVO(ruleAdminService.getByCode(ruleCode))",
        "YdszJson.toObject(YdszJson.toJson(ruleAdminService.getByCode(ruleCode)), RuleDefinitionVO.class)"
    ),
    # Line 134: entityToVO(RuleDefinition) - API type
    (
        "LiteruleConverter.INSTANT.entityToVO(ruleAdminService.save(definition, operator, changeDesc))",
        "YdszJson.toObject(YdszJson.toJson(ruleAdminService.save(definition, operator, changeDesc)), RuleDefinitionVO.class)"
    ),
    # Line 164: ruleVersionListToVO - missing method
    (
        "LiteruleConverter.INSTANT.ruleVersionListToVO(ruleAdminService.listVersions(ruleCode))",
        "ruleAdminService.listVersions(ruleCode).stream().map(e -> YdszJson.toObject(YdszJson.toJson(e), RuleVersionVO.class)).toList()"
    ),
    # Line 195: diffService.diff returns RuleVersionDiff, not RuleVersionDiffVO
    (
        "return BaseResponse.success(diffService.diff(oldDef, newDef));",
        "return BaseResponse.success(YdszJson.toObject(YdszJson.toJson(diffService.diff(oldDef, newDef)), RuleVersionDiffVO.class));"
    ),
    # Line 216: entityToVO(RuleDefinition) - API type
    (
        "LiteruleConverter.INSTANT.entityToVO(ruleAdminService.rollback(ruleCode, version, operator))",
        "YdszJson.toObject(YdszJson.toJson(ruleAdminService.rollback(ruleCode, version, operator)), RuleDefinitionVO.class)"
    ),
    # Line 231: ruleResultListToVO - missing method
    (
        "LiteruleConverter.INSTANT.ruleResultListToVO(ruleAdminService.dryRun(ruleCode, facts))",
        "ruleAdminService.dryRun(ruleCode, facts).stream().map(e -> YdszJson.toObject(YdszJson.toJson(e), RuleResultVO.class)).toList()"
    ),
    # Line 304: BaseResponse.success(result) where result is ExpressionValidationResult
    (
        "        return BaseResponse.success(result);\n    }\n\nn    /**\n     * 批量校验表达式",
        "        return BaseResponse.success(result);\n    }\n\n    /**\n     * 批量校验表达式"
    ),
    # Actually, let me be more specific for line 304
    (
        "        return BaseResponse.success(result);",
        "        return BaseResponse.success(YdszJson.toObject(YdszJson.toJson(result), ExpressionValidationResultVO.class));"
    ),
    # Line 353: entityToVO(RuleEngineStats) - API type
    (
        "LiteruleConverter.INSTANT.entityToVO(ruleEngine.getStats())",
        "YdszJson.toObject(YdszJson.toJson(ruleEngine.getStats()), RuleEngineStatsVO.class)"
    ),
], remove_imports=[
    "import com.njydsz.literule.domain.converter.LiteruleConverter;",
])

# ===== RuleCategoryController.java =====
fix_file("RuleCategoryController.java", [
    (
        "LiteruleConverter.INSTANT.entityToVO(ruleCategoryProvider.buildTree())",
        "YdszJson.toObject(YdszJson.toJson(ruleCategoryProvider.buildTree()), CategoryNodeVO.class)"
    ),
    (
        "LiteruleConverter.INSTANT.ruleDefinitionListToVO(ruleCategoryProvider.listDefinitionsByCategoryPath(path))",
        "ruleCategoryProvider.listDefinitionsByCategoryPath(path).stream().map(e -> YdszJson.toObject(YdszJson.toJson(e), RuleDefinitionVO.class)).toList()"
    ),
    (
        "LiteruleConverter.INSTANT.ruleDefinitionListToVO(ruleCategoryProvider.listDefinitionsByOwner(owner))",
        "ruleCategoryProvider.listDefinitionsByOwner(owner).stream().map(e -> YdszJson.toObject(YdszJson.toJson(e), RuleDefinitionVO.class)).toList()"
    ),
], add_imports=[
    "import com.njydsz.common.json.YdszJson;",
], remove_imports=[
    "import com.njydsz.literule.domain.converter.LiteruleConverter;",
])

# ===== RuleConflictController.java =====
fix_file("RuleConflictController.java", [
    (
        "LiteruleConverter.INSTANT.ruleConflictInfoListToVO(ruleConflictDetectorProvider.detectConflicts())",
        "ruleConflictDetectorProvider.detectConflicts().stream().map(e -> YdszJson.toObject(YdszJson.toJson(e), RuleConflictInfoVO.class)).toList()"
    ),
], add_imports=[
    "import com.njydsz.common.json.YdszJson;",
], remove_imports=[
    "import com.njydsz.literule.domain.converter.LiteruleConverter;",
])

# ===== RuleDecisionTableController.java =====
fix_file("RuleDecisionTableController.java", [
    # Line 112: BaseResponse.success(decisionTable) - DecisionTable entity, needs converter
    (
        "        return BaseResponse.success(decisionTable);",
        "        return BaseResponse.success(LiteruleConverter.INSTANT.entityToVO(decisionTable));"
    ),
    # Line 198: BaseResponse.success(saved) - DecisionTableDefinition (API) → DecisionTableDefinitionVO
    (
        "            return BaseResponse.success(saved);",
        "            return BaseResponse.success(YdszJson.toObject(YdszJson.toJson(saved), DecisionTableDefinitionVO.class));"
    ),
], add_imports=[
    "import com.njydsz.common.json.YdszJson;",
])

# ===== RuleDependencyController.java =====
fix_file("RuleDependencyController.java", [
    # Line 113: stringListToVO - missing method
    (
        "LiteruleConverter.INSTANT.stringListToVO(ruleDependencyProvider.cascadingDisable(ruleCode))",
        "ruleDependencyProvider.cascadingDisable(ruleCode).stream().map(StringVO::new).toList()"
    ),
])

# ===== RuleGraphController.java =====
fix_file("RuleGraphController.java", [
    # Line 91: entityToVO(RuleChainGraph) - server type
    (
        "LiteruleConverter.INSTANT.entityToVO(ruleChainGraphProvider.getByRuleCode(ruleCode))",
        "YdszJson.toObject(YdszJson.toJson(ruleChainGraphProvider.getByRuleCode(ruleCode)), RuleChainGraphVO.class)"
    ),
    # Line 165: entityToVO(ExpressionPreviewResult) - server type
    (
        "LiteruleConverter.INSTANT.entityToVO(expressionValidationService.previewEvaluate(expression, facts))",
        "YdszJson.toObject(YdszJson.toJson(expressionValidationService.previewEvaluate(expression, facts)), ExpressionPreviewResultVO.class)"
    ),
    # Line 185: ruleResultListToVO - missing method
    (
        "LiteruleConverter.INSTANT.ruleResultListToVO(results)",
        "results.stream().map(e -> YdszJson.toObject(YdszJson.toJson(e), RuleResultVO.class)).toList()"
    ),
    # Line 203: stringListToVO - missing method
    (
        "LiteruleConverter.INSTANT.stringListToVO(graphExecutionProvider.collectInvalidReferences(ruleCode))",
        "graphExecutionProvider.collectInvalidReferences(ruleCode).stream().map(StringVO::new).toList()"
    ),
    # Line 223: expressionFunctionDefListToVO - missing method
    (
        "LiteruleConverter.INSTANT.expressionFunctionDefListToVO(filtered)",
        "filtered.stream().map(e -> YdszJson.toObject(YdszJson.toJson(e), ExpressionFunctionDefVO.class)).toList()"
    ),
], add_imports=[
    "import com.njydsz.common.json.YdszJson;",
], remove_imports=[
    "import com.njydsz.literule.domain.converter.LiteruleConverter;",
])

# ===== RuleLifecycleController.java =====
fix_file("RuleLifecycleController.java", [
    # Line 107: BaseResponse.success(ruleAdminService.save(...)) - returns RuleDefinition
    (
        'return BaseResponse.success(ruleAdminService.save(def, operator, "状态变更: " + current.getDesc() + " -> " + target.getDesc()));',
        'return BaseResponse.success(YdszJson.toObject(YdszJson.toJson(ruleAdminService.save(def, operator, "状态变更: " + current.getDesc() + " -> " + target.getDesc())), RuleDefinitionVO.class));'
    ),
    # Line 150: approve - BaseResponse.success(ruleAdminService.save(...))
    (
        'return BaseResponse.success(ruleAdminService.save(def, operator, changeDesc));\n    }\n\n    /**\n     * 审批驳回',
        'return BaseResponse.success(YdszJson.toObject(YdszJson.toJson(ruleAdminService.save(def, operator, changeDesc)), RuleDefinitionVO.class));\n    }\n\n    /**\n     * 审批驳回'
    ),
    # Line 193: reject - BaseResponse.success(ruleAdminService.save(...))
    (
        'return BaseResponse.success(ruleAdminService.save(def, operator, changeDesc));\n    }\n\n    /**\n     * 安全解析规则状态',
        'return BaseResponse.success(YdszJson.toObject(YdszJson.toJson(ruleAdminService.save(def, operator, changeDesc)), RuleDefinitionVO.class));\n    }\n\n    /**\n     * 安全解析规则状态'
    ),
    # Line 229: submitReview - BaseResponse.success(svc.submitForReview(...))
    (
        "return BaseResponse.success(svc.submitForReview(ruleCode, flowCode, operator));",
        "return BaseResponse.success(YdszJson.toObject(YdszJson.toJson(svc.submitForReview(ruleCode, flowCode, operator)), ApprovalRecordVO.class));"
    ),
    # Line 255: approveLevel - BaseResponse.success(svc.approve(...))
    (
        "return BaseResponse.success(svc.approve(ruleCode, operator, comment));",
        "return BaseResponse.success(YdszJson.toObject(YdszJson.toJson(svc.approve(ruleCode, operator, comment)), ApprovalRecordVO.class));"
    ),
    # Line 279: rejectLevel - BaseResponse.success(svc.reject(...))
    (
        "return BaseResponse.success(svc.reject(ruleCode, operator, dto.getReason()));",
        "return BaseResponse.success(YdszJson.toObject(YdszJson.toJson(svc.reject(ruleCode, operator, dto.getReason())), ApprovalRecordVO.class));"
    ),
    # Line 304: delegate - BaseResponse.success(svc.delegate(...))
    (
        "return BaseResponse.success(svc.delegate(ruleCode, operator, dto.getDelegatedTo(), comment));",
        "return BaseResponse.success(YdszJson.toObject(YdszJson.toJson(svc.delegate(ruleCode, operator, dto.getDelegatedTo(), comment)), ApprovalRecordVO.class));"
    ),
    # Line 317: LiteruleConverter.INSTANT.entityToVO(null) - ambiguous + type mismatch
    (
        "return BaseResponse.success(LiteruleConverter.INSTANT.entityToVO(null));",
        "return BaseResponse.success((ApprovalRecordVO) null);"
    ),
    # Line 319: BaseResponse.success(svc.getApprovalStatus(ruleCode))
    (
        "return BaseResponse.success(svc.getApprovalStatus(ruleCode));",
        "return BaseResponse.success(YdszJson.toObject(YdszJson.toJson(svc.getApprovalStatus(ruleCode)), ApprovalRecordVO.class));"
    ),
    # Line 332: LiteruleConverter.INSTANT.approvalRecordListToVO(List.of())
    (
        "return BaseResponse.success(LiteruleConverter.INSTANT.approvalRecordListToVO(List.of()));",
        "return BaseResponse.success(List.of());"
    ),
    # Line 334: BaseResponse.success(svc.listPendingApprovals(approver))
    (
        "return BaseResponse.success(svc.listPendingApprovals(approver));",
        "return BaseResponse.success(svc.listPendingApprovals(approver).stream().map(e -> YdszJson.toObject(YdszJson.toJson(e), ApprovalRecordVO.class)).toList());"
    ),
    # Line 356: BaseResponse.success(svc.cancelReview(ruleCode, operator))
    (
        "return BaseResponse.success(svc.cancelReview(ruleCode, operator));",
        "return BaseResponse.success(YdszJson.toObject(YdszJson.toJson(svc.cancelReview(ruleCode, operator)), ApprovalRecordVO.class));"
    ),
    # Line 368: LiteruleConverter.INSTANT.approvalFlowListToVO(List.of())
    (
        "return BaseResponse.success(LiteruleConverter.INSTANT.approvalFlowListToVO(List.of()));",
        "return BaseResponse.success(List.of());"
    ),
    # Line 370: BaseResponse.success(svc.listFlows())
    (
        "return BaseResponse.success(svc.listFlows());",
        "return BaseResponse.success(svc.listFlows().stream().map(e -> YdszJson.toObject(YdszJson.toJson(e), ApprovalFlowVO.class)).toList());"
    ),
], add_imports=[
    "import com.njydsz.common.json.YdszJson;",
], remove_imports=[
    "import com.njydsz.literule.domain.converter.LiteruleConverter;",
])

# ===== RulePackController.java =====
fix_file("RulePackController.java", [
    # Line 82: rulePackListToVO with API type RulePack
    (
        "LiteruleConverter.INSTANT.rulePackListToVO(rulePackProvider.listAll())",
        "rulePackProvider.listAll().stream().map(e -> YdszJson.toObject(YdszJson.toJson(e), RulePackVO.class)).toList()"
    ),
    # Line 90: same with search
    (
        "LiteruleConverter.INSTANT.rulePackListToVO(rulePackProvider.search(keyword))",
        "rulePackProvider.search(keyword).stream().map(e -> YdszJson.toObject(YdszJson.toJson(e), RulePackVO.class)).toList()"
    ),
    # Line 98: entityToVO(RulePack) - API type
    (
        "LiteruleConverter.INSTANT.entityToVO(rulePackProvider.getLatest(packCode))",
        "YdszJson.toObject(YdszJson.toJson(rulePackProvider.getLatest(packCode)), RulePackVO.class)"
    ),
    # Line 106: rulePackListToVO with listVersions
    (
        "LiteruleConverter.INSTANT.rulePackListToVO(rulePackProvider.listVersions(packCode))",
        "rulePackProvider.listVersions(packCode).stream().map(e -> YdszJson.toObject(YdszJson.toJson(e), RulePackVO.class)).toList()"
    ),
    # Line 116: entityToVO(RulePack) - API type
    (
        "LiteruleConverter.INSTANT.entityToVO(rulePackProvider.getVersion(packCode, version))",
        "YdszJson.toObject(YdszJson.toJson(rulePackProvider.getVersion(packCode, version)), RulePackVO.class)"
    ),
    # Line 128: entityToVO(InstallResult) - server SPI type
    (
        "LiteruleConverter.INSTANT.entityToVO(rulePackProvider.rollback(packCode, version, operator))",
        "YdszJson.toObject(YdszJson.toJson(rulePackProvider.rollback(packCode, version, operator)), InstallResultVO.class)"
    ),
    # Line 139: entityToVO(PackDiff) - server SPI type
    (
        "LiteruleConverter.INSTANT.entityToVO(rulePackProvider.diff(packCode, fromVersion, toVersion))",
        "YdszJson.toObject(YdszJson.toJson(rulePackProvider.diff(packCode, fromVersion, toVersion)), PackDiffVO.class)"
    ),
    # Line 151: entityToVO(RulePack) - API type
    (
        "LiteruleConverter.INSTANT.entityToVO(rulePackProvider.publish(pack, operator))",
        "YdszJson.toObject(YdszJson.toJson(rulePackProvider.publish(pack, operator)), RulePackVO.class)"
    ),
    # Line 163: entityToVO(InstallResult) - server SPI type
    (
        "LiteruleConverter.INSTANT.entityToVO(rulePackProvider.install(packCode, version, operator))",
        "YdszJson.toObject(YdszJson.toJson(rulePackProvider.install(packCode, version, operator)), InstallResultVO.class)"
    ),
    # Line 279: packUpdateInfoListToVO - missing method
    (
        "LiteruleConverter.INSTANT.packUpdateInfoListToVO(rulePackProvider.checkPackUpdates())",
        "rulePackProvider.checkPackUpdates().stream().map(e -> YdszJson.toObject(YdszJson.toJson(e), PackUpdateInfoVO.class)).toList()"
    ),
    # Line 295: installResultListToVO(List.of()) - missing method, empty list
    (
        "return BaseResponse.success(LiteruleConverter.INSTANT.installResultListToVO(List.of()));",
        "return BaseResponse.success(List.of());"
    ),
    # Line 305: BaseResponse.success(results) where results is List<InstallResult>
    (
        "        return BaseResponse.success(results);",
        "        return BaseResponse.success(results.stream().map(e -> YdszJson.toObject(YdszJson.toJson(e), InstallResultVO.class)).toList());"
    ),
], add_imports=[
    "import com.njydsz.common.json.YdszJson;",
], remove_imports=[
    "import com.njydsz.literule.domain.converter.LiteruleConverter;",
])

# ===== RuleTemplateController.java =====
fix_file("RuleTemplateController.java", [
    # Line 101: entityToVO(RuleDefinition) - API type, no matching method
    (
        "LiteruleConverter.INSTANT.entityToVO(ruleTemplateProvider.importTemplate(templateCode, operator))",
        "YdszJson.toObject(YdszJson.toJson(ruleTemplateProvider.importTemplate(templateCode, operator)), RuleDefinitionVO.class)"
    ),
], add_imports=[
    "import com.njydsz.common.json.YdszJson;",
])

print("\nAll files fixed!")
