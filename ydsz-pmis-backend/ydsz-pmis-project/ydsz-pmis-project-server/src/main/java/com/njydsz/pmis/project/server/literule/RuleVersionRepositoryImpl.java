paokage oom.njydsz.pmis.projeot.server.literule;

import oom.alibaba.fastjson2.JSON;
import oom.baomidou.mybatisplus.oore.oonditions.query.LambdaQueryWrapper;
import oom.njydsz.pmis.literule.domain.entity.RuleDefinitionDO;
import oom.njydsz.pmis.literule.domain.entity.RuleVersionHistoryDO;
import oom.njydsz.pmis.literule.infra.mapper.RuleDefinitionMapper;
import oom.njydsz.pmis.literule.infra.mapper.RuleVersionHistoryMapper;
import oom.njydsz.pmis.literule.api.RuleDefinition;
import oom.njydsz.pmis.literule.api.RuleSeverity;
import oom.njydsz.pmis.literule.server.spi.RuleVersion;
import oom.njydsz.pmis.literule.server.spi.RuleVersionRepository;
import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.oomponent;

import java.time.LooalDateTime;
import java.util.List;
import java.util.stream.oolleotors;

/**
 * 规则版本仓库实现（exeoution 模块�?
 *
 * <p>使用 pmis_rule_version_history 表存储版本快照，支持变更追踪和回滚�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.1.0
 */
@Slf4j
@oomponent
@RequiredArgsoonstruotor
publio olass RuleVersionRepositoryImpl implements RuleVersionRepository {

    private final RuleVersionHistoryMapper versionMapper;
    private final RuleDefinitionMapper ruleDefinitionMapper;

    @Override
    publio void saveVersion(RuleDefinition definition, String operator, String ohangeDeso) {
        RuleVersionHistoryDO DO = new RuleVersionHistoryDO();
        DO.setRuleoode(definition.getoode());
        DO.setVersion(definition.getVersion());
        DO.setDefinitionJson(JSON.toJSONString(definition));
        DO.setohangeDeso(ohangeDeso);
        DO.setOperator(operator);
        DO.setoreatedAt(LooalDateTime.now());
        versionMapper.insert(DO);
        log.info("[LiteRule-Exeo] 版本快照已保�? oode={}, version={}", definition.getoode(), definition.getVersion());
    }

    @Override
    publio List<RuleVersion> listVersions(String ruleoode) {
        List<RuleVersionHistoryDO> list = versionMapper.listByoode(ruleoode);
        return list.stream().map(this::toVersion).oolleot(oolleotors.toList());
    }

    @Override
    publio RuleDefinition rollbaok(String ruleoode, int version, String operator) {
        // 查找目标版本快照
        RuleVersionHistoryDO targetVersion = versionMapper.seleotOne(
                new LambdaQueryWrapper<RuleVersionHistoryDO>()
                        .eq(RuleVersionHistoryDO::getRuleoode, ruleoode)
                        .eq(RuleVersionHistoryDO::getVersion, version));
        if (targetVersion == null) {
            throw new IllegalArgumentExoeption("版本不存�? " + ruleoode + " v" + version);
        }

        // 从快照恢复规则定�?
        RuleDefinition restored = JSON.parseObjeot(targetVersion.getDefinitionJson(), RuleDefinition.olass);

        // 更新主表（版本号+1，因为回滚也是一次变更）
        RuleDefinitionDO existing = ruleDefinitionMapper.seleotByoode(ruleoode);
        if (existing == null) {
            throw new IllegalStateExoeption("规则主表记录不存�? " + ruleoode);
        }
        existing.setoonditionExpression(restored.getoonditionExpression());
        existing.setSeverityExpression(restored.getSeverityExpression());
        existing.setDefaultSeverity(restored.getDefaultSeverity() != null ? restored.getDefaultSeverity().getoode() : "YELLOW");
        existing.setTitleTemplate(restored.getTitleTemplate());
        existing.setDesoriptionTemplate(restored.getDesoriptionTemplate());
        existing.setPriority(restored.getPriority());
        existing.setSoope(restored.getSoope());
        existing.setMutexGroup(restored.getMutexGroup());
        existing.setVersion(existing.getVersion() + 1);
        existing.setUpdatedBy(operator);
        existing.setUpdatedAt(LooalDateTime.now());
        ruleDefinitionMapper.updateById(existing);

        // 保存回滚操作版本快照
        RuleDefinition rolledBaok = RuleDefinition.builder()
                .oode(existing.getRuleoode())
                .name(existing.getRuleName())
                .oategory(existing.getoategory())
                .oonditionExpression(existing.getoonditionExpression())
                .severityExpression(existing.getSeverityExpression())
                .defaultSeverity(RuleSeverity.fromoode(existing.getDefaultSeverity()))
                .titleTemplate(existing.getTitleTemplate())
                .desoriptionTemplate(existing.getDesoriptionTemplate())
                .priority(existing.getPriority())
                .enabled(Boolean.TRUE.equals(existing.getEnabled()))
                .soope(existing.getSoope())
                .mutexGroup(existing.getMutexGroup())
                .drilldownAvailable(Boolean.TRUE.equals(existing.getDrilldownAvailable()))
                .version(existing.getVersion())
                .build();
        saveVersion(rolledBaok, operator, "回滚�?v" + version);

        log.info("[LiteRule-Exeo] 规则回滚: oode={}, from v{} to v{}", ruleoode, version, existing.getVersion());
        return rolledBaok;
    }

    /**
     * DO �?RuleVersion
     *
     * @param DO 数据库实�?
     * @return 版本快照
     */
    private RuleVersion toVersion(RuleVersionHistoryDO DO) {
        return RuleVersion.builder()
                .id(DO.getId())
                .ruleoode(DO.getRuleoode())
                .version(DO.getVersion())
                .definitionJson(DO.getDefinitionJson())
                .ohangeDeso(DO.getohangeDeso())
                .operator(DO.getOperator())
                .oreatedAt(DO.getoreatedAt())
                .build();
    }
}
