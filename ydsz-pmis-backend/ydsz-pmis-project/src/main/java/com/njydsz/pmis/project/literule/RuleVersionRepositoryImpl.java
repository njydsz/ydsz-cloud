package com.njydsz.pmis.project.literule;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.njydsz.pmis.project.entity.ruleengine.RuleDefinitionDO;
import com.njydsz.pmis.project.entity.ruleengine.RuleVersionHistoryDO;
import com.njydsz.pmis.project.mapper.ruleengine.RuleDefinitionMapper;
import com.njydsz.pmis.project.mapper.ruleengine.RuleVersionHistoryMapper;
import com.njydsz.pmis.literule.api.RuleDefinition;
import com.njydsz.pmis.literule.api.RuleSeverity;
import com.njydsz.pmis.literule.spi.RuleVersion;
import com.njydsz.pmis.literule.spi.RuleVersionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 规则版本仓库实现（execution 模块）
 *
 * <p>使用 pmis_rule_version_history 表存储版本快照，支持变更追踪和回滚。
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RuleVersionRepositoryImpl implements RuleVersionRepository {

    private final RuleVersionHistoryMapper versionMapper;
    private final RuleDefinitionMapper ruleDefinitionMapper;

    @Override
    public void saveVersion(RuleDefinition definition, String operator, String changeDesc) {
        RuleVersionHistoryDO DO = new RuleVersionHistoryDO();
        DO.setRuleCode(definition.getCode());
        DO.setVersion(definition.getVersion());
        DO.setDefinitionJson(JSON.toJSONString(definition));
        DO.setChangeDesc(changeDesc);
        DO.setOperator(operator);
        DO.setCreatedAt(LocalDateTime.now());
        versionMapper.insert(DO);
        log.info("[LiteRule-Exec] 版本快照已保存: code={}, version={}", definition.getCode(), definition.getVersion());
    }

    @Override
    public List<RuleVersion> listVersions(String ruleCode) {
        List<RuleVersionHistoryDO> list = versionMapper.listByCode(ruleCode);
        return list.stream().map(this::toVersion).collect(Collectors.toList());
    }

    @Override
    public RuleDefinition rollback(String ruleCode, int version, String operator) {
        // 查找目标版本快照
        RuleVersionHistoryDO targetVersion = versionMapper.selectOne(
                new LambdaQueryWrapper<RuleVersionHistoryDO>()
                        .eq(RuleVersionHistoryDO::getRuleCode, ruleCode)
                        .eq(RuleVersionHistoryDO::getVersion, version));
        if (targetVersion == null) {
            throw new IllegalArgumentException("版本不存在: " + ruleCode + " v" + version);
        }

        // 从快照恢复规则定义
        RuleDefinition restored = JSON.parseObject(targetVersion.getDefinitionJson(), RuleDefinition.class);

        // 更新主表（版本号+1，因为回滚也是一次变更）
        RuleDefinitionDO existing = ruleDefinitionMapper.selectByCode(ruleCode);
        if (existing == null) {
            throw new IllegalStateException("规则主表记录不存在: " + ruleCode);
        }
        existing.setConditionExpression(restored.getConditionExpression());
        existing.setSeverityExpression(restored.getSeverityExpression());
        existing.setDefaultSeverity(restored.getDefaultSeverity() != null ? restored.getDefaultSeverity().getCode() : "YELLOW");
        existing.setTitleTemplate(restored.getTitleTemplate());
        existing.setDescriptionTemplate(restored.getDescriptionTemplate());
        existing.setPriority(restored.getPriority());
        existing.setScope(restored.getScope());
        existing.setMutexGroup(restored.getMutexGroup());
        existing.setVersion(existing.getVersion() + 1);
        existing.setUpdatedBy(operator);
        existing.setUpdatedAt(LocalDateTime.now());
        ruleDefinitionMapper.updateById(existing);

        // 保存回滚操作版本快照
        RuleDefinition rolledBack = RuleDefinition.builder()
                .code(existing.getRuleCode())
                .name(existing.getRuleName())
                .category(existing.getCategory())
                .conditionExpression(existing.getConditionExpression())
                .severityExpression(existing.getSeverityExpression())
                .defaultSeverity(RuleSeverity.fromCode(existing.getDefaultSeverity()))
                .titleTemplate(existing.getTitleTemplate())
                .descriptionTemplate(existing.getDescriptionTemplate())
                .priority(existing.getPriority())
                .enabled(Boolean.TRUE.equals(existing.getEnabled()))
                .scope(existing.getScope())
                .mutexGroup(existing.getMutexGroup())
                .drilldownAvailable(Boolean.TRUE.equals(existing.getDrilldownAvailable()))
                .version(existing.getVersion())
                .build();
        saveVersion(rolledBack, operator, "回滚至 v" + version);

        log.info("[LiteRule-Exec] 规则回滚: code={}, from v{} to v{}", ruleCode, version, existing.getVersion());
        return rolledBack;
    }

    /**
     * DO → RuleVersion
     *
     * @param DO 数据库实体
     * @return 版本快照
     */
    private RuleVersion toVersion(RuleVersionHistoryDO DO) {
        return RuleVersion.builder()
                .id(DO.getId())
                .ruleCode(DO.getRuleCode())
                .version(DO.getVersion())
                .definitionJson(DO.getDefinitionJson())
                .changeDesc(DO.getChangeDesc())
                .operator(DO.getOperator())
                .createdAt(DO.getCreatedAt())
                .build();
    }
}
