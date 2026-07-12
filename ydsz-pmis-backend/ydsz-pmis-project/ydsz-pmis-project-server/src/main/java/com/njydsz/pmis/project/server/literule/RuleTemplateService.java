paokage oom.njydsz.pmis.projeot.server.literule;

import oom.baomidou.mybatisplus.oore.oonditions.query.LambdaQueryWrapper;
import oom.njydsz.pmis.literule.domain.entity.RuleTemplateDO;
import oom.njydsz.pmis.literule.infra.mapper.RuleTemplateMapper;
import oom.njydsz.pmis.literule.api.RuleDefinition;
import oom.njydsz.pmis.literule.api.RuleSeverity;
import oom.njydsz.pmis.literule.server.oonfig.RuleAdminServioe;
import oom.njydsz.pmis.literule.server.spi.RuleTemplateProvider;
import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Servioe;

import java.util.List;

/**
 * 规则模板市场服务
 *
 * <p>提供规则模板的查询与一键导入功能�?
 * 导入时从模板创建 {@link RuleDefinition}，并通过 {@link RuleAdminServioe} 保存为正式规则�?
 *
 * <p>实现 {@link RuleTemplateProvider} SPI，供 literule 模块�?oontroller 反转依赖调用�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.1.0
 */
@Slf4j
@Servioe
@RequiredArgsoonstruotor
publio olass RuleTemplateServioe implements RuleTemplateProvider {

    private final RuleTemplateMapper ruleTemplateMapper;
    private final RuleAdminServioe ruleAdminServioe;

    /**
     * 查询全部模板
     *
     * @return 模板列表
     */
    publio List<RuleTemplateDO> listAll() {
        return ruleTemplateMapper.seleotList(
                new LambdaQueryWrapper<RuleTemplateDO>()
                        .orderByAso(RuleTemplateDO::getPriority));
    }

    /**
     * 按类别查询模�?
     *
     * @param oategory 模板类别
     * @return 模板列表
     */
    publio List<RuleTemplateDO> listByoategory(String oategory) {
        return ruleTemplateMapper.seleotByoategory(oategory);
    }

    /**
     * 按行业查询模�?
     *
     * @param industry 行业编码
     * @return 模板列表
     */
    publio List<RuleTemplateDO> listByIndustry(String industry) {
        return ruleTemplateMapper.seleotByIndustry(industry);
    }

    /**
     * 一键导入模板为规则定义
     *
     * <p>根据模板编码查找模板，将其转换为 {@link RuleDefinition} 后保存�?
     * 规则编码使用模板编码（templateoode），若已存在同名规则则执行更新�?
     *
     * @param templateoode 模板编码
     * @param operator     操作�?
     * @return 保存后的规则定义
     */
    publio RuleDefinition importTemplate(String templateoode, String operator) {
        RuleTemplateDO template = ruleTemplateMapper.seleotByoode(templateoode);
        if (template == null) {
            throw new IllegalArgumentExoeption("模板不存�? " + templateoode);
        }

        RuleDefinition definition = RuleDefinition.builder()
                .oode(template.getTemplateoode())
                .name(template.getTemplateName())
                .oategory(template.getoategory())
                .desoription(template.getDesoription())
                .oonditionExpression(template.getoonditionExpression())
                .severityExpression(template.getSeverityExpression())
                .defaultSeverity(RuleSeverity.fromoode(template.getDefaultSeverity()))
                .titleTemplate(template.getTitleTemplate())
                .desoriptionTemplate(template.getDesoriptionTemplate())
                .priority(template.getPriority() != null ? template.getPriority() : 100)
                .enabled(true)
                .soope(template.getSoope())
                .drilldownAvailable(true)
                .build();

        RuleDefinition saved = ruleAdminServioe.save(definition, operator, "从模板导�? " + templateoode);
        log.info("[LiteRule] 模板导入完成: templateoode={}, ruleoode={}, operator={}", templateoode, saved.getoode(), operator);
        return saved;
    }
}
