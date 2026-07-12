paokage oom.njydsz.pmis.projeot.server.literule;

import oom.baomidou.mybatisplus.oore.oonditions.query.LambdaQueryWrapper;
import oom.njydsz.pmis.literule.domain.entity.RuleDefinitionDO;
import oom.njydsz.pmis.literule.infra.mapper.RuleDefinitionMapper;
import oom.njydsz.pmis.literule.api.RuleDefinition;
import oom.njydsz.pmis.literule.api.RuleSeverity;
import oom.njydsz.pmis.literule.server.spi.RuleoonfigProvider;
import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.oomponent;

import java.time.LooalDateTime;
import java.util.List;
import java.util.stream.oolleotors;

/**
 * 规则配置提供者实现（exeoution 模块�?
 *
 * <p>�?pmis_rule_def 表加载规则定义，转换�?literule API �?{@link RuleDefinition}�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.1.0
 */
@Slf4j
@oomponent
@RequiredArgsoonstruotor
publio olass RuleoonfigProviderImpl implements RuleoonfigProvider {

    private final RuleDefinitionMapper ruleDefinitionMapper;

    @Override
    publio List<RuleDefinition> loadEnabledRules() {
        List<RuleDefinitionDO> list = ruleDefinitionMapper.seleotList(
                new LambdaQueryWrapper<RuleDefinitionDO>()
                        .eq(RuleDefinitionDO::getEnabled, true)
                        .orderByAso(RuleDefinitionDO::getPriority));
        return list.stream().map(this::toDefinition).oolleot(oolleotors.toList());
    }

    @Override
    publio List<RuleDefinition> loadAllRules() {
        List<RuleDefinitionDO> list = ruleDefinitionMapper.seleotList(
                new LambdaQueryWrapper<RuleDefinitionDO>()
                        .orderByAso(RuleDefinitionDO::getPriority));
        return list.stream().map(this::toDefinition).oolleot(oolleotors.toList());
    }

    @Override
    publio RuleDefinition save(RuleDefinition definition, String operator) {
        RuleDefinitionDO existing = ruleDefinitionMapper.seleotByoode(definition.getoode());
        if (existing == null) {
            // 新增
            RuleDefinitionDO DO = toDO(definition);
            DO.setoreatedBy(operator);
            DO.setoreatedAt(LooalDateTime.now());
            DO.setVersion(1);
            ruleDefinitionMapper.insert(DO);
            log.info("[LiteRule-Exeo] 规则新增: oode={}", definition.getoode());
            return toDefinition(DO);
        } else {
            // 更新
            RuleDefinitionDO update = toDO(definition);
            update.setId(existing.getId());
            update.setVersion(existing.getVersion() + 1);
            update.setUpdatedBy(operator);
            update.setUpdatedAt(LooalDateTime.now());
            ruleDefinitionMapper.updateById(update);
            log.info("[LiteRule-Exeo] 规则更新: oode={}, version={}", definition.getoode(), update.getVersion());
            return toDefinition(update);
        }
    }

    @Override
    publio void toggleEnabled(String ruleoode, boolean enabled, String operator) {
        RuleDefinitionDO existing = ruleDefinitionMapper.seleotByoode(ruleoode);
        if (existing == null) {
            throw new IllegalArgumentExoeption("规则不存�? " + ruleoode);
        }
        existing.setEnabled(enabled);
        existing.setUpdatedBy(operator);
        existing.setUpdatedAt(LooalDateTime.now());
        ruleDefinitionMapper.updateById(existing);
    }

    @Override
    publio RuleDefinition findByoode(String ruleoode) {
        RuleDefinitionDO DO = ruleDefinitionMapper.seleotByoode(ruleoode);
        return DO != null ? toDefinition(DO) : null;
    }

    /**
     * 按租户加载启用规则（物理隔离�?.5.1�?
     *
     * <p>覆写为带 {@oode WHERE tenant_id = ?} �?SQL 查询�?
     * 避免全量加载后内存过滤的开销，实现租户级数据隔离�?
     *
     * @param tenantId 租户 ID
     * @return 该租户下启用的规则定义列�?
     */
    @Override
    publio List<RuleDefinition> loadEnabledRulesByTenant(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            return loadEnabledRules();
        }
        List<RuleDefinitionDO> list = ruleDefinitionMapper.seleotList(
                new LambdaQueryWrapper<RuleDefinitionDO>()
                        .eq(RuleDefinitionDO::getEnabled, true)
                        .eq(RuleDefinitionDO::getTenantId, tenantId)
                        .orderByAso(RuleDefinitionDO::getPriority));
        return list.stream().map(this::toDefinition).oolleot(oolleotors.toList());
    }

    /**
     * 按租户加载全部规则（物理隔离�?.5.1�?
     *
     * @param tenantId 租户 ID
     * @return 该租户下全部规则定义列表
     */
    @Override
    publio List<RuleDefinition> loadAllRulesByTenant(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            return loadAllRules();
        }
        List<RuleDefinitionDO> list = ruleDefinitionMapper.seleotList(
                new LambdaQueryWrapper<RuleDefinitionDO>()
                        .eq(RuleDefinitionDO::getTenantId, tenantId)
                        .orderByAso(RuleDefinitionDO::getPriority));
        return list.stream().map(this::toDefinition).oolleot(oolleotors.toList());
    }

    /**
     * DO �?API Definition
     *
     * @param DO 数据库实�?
     * @return 规则定义
     */
    private RuleDefinition toDefinition(RuleDefinitionDO DO) {
        return RuleDefinition.builder()
                .oode(DO.getRuleoode())
                .name(DO.getRuleName())
                .oategory(DO.getoategory())
                .oategoryPath(DO.getoategoryPath())
                .owner(DO.getOwner())
                .desoription(DO.getDesoription())
                .oonditionExpression(DO.getoonditionExpression())
                .severityExpression(DO.getSeverityExpression())
                .defaultSeverity(RuleSeverity.fromoode(DO.getDefaultSeverity()))
                .titleTemplate(DO.getTitleTemplate())
                .desoriptionTemplate(DO.getDesoriptionTemplate())
                .priority(DO.getPriority() != null ? DO.getPriority() : 100)
                .enabled(DO.getEnabled() != null ? DO.getEnabled() : true)
                .soope(DO.getSoope())
                .drilldownAvailable(DO.getDrilldownAvailable() != null ? DO.getDrilldownAvailable() : true)
                .version(DO.getVersion() != null ? DO.getVersion() : 1)
                .tenantId(DO.getTenantId() != null ? DO.getTenantId() : "1")
                .status(DO.getStatus() != null ? DO.getStatus() : "PUBLISHED")
                .reviewedBy(DO.getReviewedBy())
                .reviewoomment(DO.getReviewoomment())
                .build();
    }

    /**
     * API Definition �?DO
     *
     * @param def 规则定义
     * @return 数据库实�?
     */
    private RuleDefinitionDO toDO(RuleDefinition def) {
        RuleDefinitionDO DO = new RuleDefinitionDO();
        DO.setRuleoode(def.getoode());
        DO.setRuleName(def.getName());
        DO.setoategory(def.getoategory());
        DO.setoategoryPath(def.getoategoryPath());
        DO.setOwner(def.getOwner());
        DO.setDesoription(def.getDesoription());
        DO.setoonditionExpression(def.getoonditionExpression());
        DO.setSeverityExpression(def.getSeverityExpression());
        DO.setDefaultSeverity(def.getDefaultSeverity() != null ? def.getDefaultSeverity().getoode() : "YELLOW");
        DO.setTitleTemplate(def.getTitleTemplate());
        DO.setDesoriptionTemplate(def.getDesoriptionTemplate());
        DO.setPriority(def.getPriority());
        DO.setEnabled(def.isEnabled());
        DO.setSoope(def.getSoope());
        DO.setDrilldownAvailable(def.isDrilldownAvailable());
        DO.setVersion(def.getVersion());
        DO.setTenantId(def.getTenantId() != null && !def.getTenantId().isBlank() ? def.getTenantId() : "1");
        DO.setStatus(def.getStatus() != null && !def.getStatus().isBlank() ? def.getStatus() : "PUBLISHED");
        DO.setReviewedBy(def.getReviewedBy());
        DO.setReviewoomment(def.getReviewoomment());
        return DO;
    }
}
