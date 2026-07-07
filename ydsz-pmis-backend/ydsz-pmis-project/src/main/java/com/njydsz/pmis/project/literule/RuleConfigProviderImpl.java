package com.njydsz.pmis.project.literule;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.njydsz.pmis.project.entity.RuleDefinitionDO;
import com.njydsz.pmis.project.mapper.RuleDefinitionMapper;
import com.njydsz.pmis.literule.api.RuleDefinition;
import com.njydsz.pmis.literule.api.RuleSeverity;
import com.njydsz.pmis.literule.spi.RuleConfigProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 规则配置提供者实现（execution 模块）
 *
 * <p>从 pmis_rule_def 表加载规则定义，转换为 literule API 的 {@link RuleDefinition}。
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RuleConfigProviderImpl implements RuleConfigProvider {

    private final RuleDefinitionMapper ruleDefinitionMapper;

    @Override
    public List<RuleDefinition> loadEnabledRules() {
        List<RuleDefinitionDO> list = ruleDefinitionMapper.selectList(
                new LambdaQueryWrapper<RuleDefinitionDO>()
                        .eq(RuleDefinitionDO::getEnabled, true)
                        .orderByAsc(RuleDefinitionDO::getPriority));
        return list.stream().map(this::toDefinition).collect(Collectors.toList());
    }

    @Override
    public List<RuleDefinition> loadAllRules() {
        List<RuleDefinitionDO> list = ruleDefinitionMapper.selectList(
                new LambdaQueryWrapper<RuleDefinitionDO>()
                        .orderByAsc(RuleDefinitionDO::getPriority));
        return list.stream().map(this::toDefinition).collect(Collectors.toList());
    }

    @Override
    public RuleDefinition save(RuleDefinition definition, String operator) {
        RuleDefinitionDO existing = ruleDefinitionMapper.selectByCode(definition.getCode());
        if (existing == null) {
            // 新增
            RuleDefinitionDO DO = toDO(definition);
            DO.setCreatedBy(operator);
            DO.setCreatedAt(LocalDateTime.now());
            DO.setVersion(1);
            ruleDefinitionMapper.insert(DO);
            log.info("[LiteRule-Exec] 规则新增: code={}", definition.getCode());
            return toDefinition(DO);
        } else {
            // 更新
            RuleDefinitionDO update = toDO(definition);
            update.setId(existing.getId());
            update.setVersion(existing.getVersion() + 1);
            update.setUpdatedBy(operator);
            update.setUpdatedAt(LocalDateTime.now());
            ruleDefinitionMapper.updateById(update);
            log.info("[LiteRule-Exec] 规则更新: code={}, version={}", definition.getCode(), update.getVersion());
            return toDefinition(update);
        }
    }

    @Override
    public void toggleEnabled(String ruleCode, boolean enabled, String operator) {
        RuleDefinitionDO existing = ruleDefinitionMapper.selectByCode(ruleCode);
        if (existing == null) {
            throw new IllegalArgumentException("规则不存在: " + ruleCode);
        }
        existing.setEnabled(enabled);
        existing.setUpdatedBy(operator);
        existing.setUpdatedAt(LocalDateTime.now());
        ruleDefinitionMapper.updateById(existing);
    }

    @Override
    public RuleDefinition findByCode(String ruleCode) {
        RuleDefinitionDO DO = ruleDefinitionMapper.selectByCode(ruleCode);
        return DO != null ? toDefinition(DO) : null;
    }

    /**
     * DO → API Definition
     *
     * @param DO 数据库实体
     * @return 规则定义
     */
    private RuleDefinition toDefinition(RuleDefinitionDO DO) {
        return RuleDefinition.builder()
                .code(DO.getRuleCode())
                .name(DO.getRuleName())
                .category(DO.getCategory())
                .categoryPath(DO.getCategoryPath())
                .owner(DO.getOwner())
                .description(DO.getDescription())
                .conditionExpression(DO.getConditionExpression())
                .severityExpression(DO.getSeverityExpression())
                .defaultSeverity(RuleSeverity.fromCode(DO.getDefaultSeverity()))
                .titleTemplate(DO.getTitleTemplate())
                .descriptionTemplate(DO.getDescriptionTemplate())
                .priority(DO.getPriority() != null ? DO.getPriority() : 100)
                .enabled(DO.getEnabled() != null ? DO.getEnabled() : true)
                .scope(DO.getScope())
                .drilldownAvailable(DO.getDrilldownAvailable() != null ? DO.getDrilldownAvailable() : true)
                .version(DO.getVersion() != null ? DO.getVersion() : 1)
                .tenantId(DO.getTenantId() != null ? Long.parseLong(DO.getTenantId()) : 1L)
                .status(DO.getStatus() != null ? DO.getStatus() : "PUBLISHED")
                .reviewedBy(DO.getReviewedBy())
                .reviewComment(DO.getReviewComment())
                .build();
    }

    /**
     * API Definition → DO
     *
     * @param def 规则定义
     * @return 数据库实体
     */
    private RuleDefinitionDO toDO(RuleDefinition def) {
        RuleDefinitionDO DO = new RuleDefinitionDO();
        DO.setRuleCode(def.getCode());
        DO.setRuleName(def.getName());
        DO.setCategory(def.getCategory());
        DO.setCategoryPath(def.getCategoryPath());
        DO.setOwner(def.getOwner());
        DO.setDescription(def.getDescription());
        DO.setConditionExpression(def.getConditionExpression());
        DO.setSeverityExpression(def.getSeverityExpression());
        DO.setDefaultSeverity(def.getDefaultSeverity() != null ? def.getDefaultSeverity().getCode() : "YELLOW");
        DO.setTitleTemplate(def.getTitleTemplate());
        DO.setDescriptionTemplate(def.getDescriptionTemplate());
        DO.setPriority(def.getPriority());
        DO.setEnabled(def.isEnabled());
        DO.setScope(def.getScope());
        DO.setDrilldownAvailable(def.isDrilldownAvailable());
        DO.setVersion(def.getVersion());
        DO.setTenantId(def.getTenantId() > 0 ? String.valueOf(def.getTenantId()) : "1");
        DO.setStatus(def.getStatus() != null && !def.getStatus().isBlank() ? def.getStatus() : "PUBLISHED");
        DO.setReviewedBy(def.getReviewedBy());
        DO.setReviewComment(def.getReviewComment());
        return DO;
    }
}
