package com.njydsz.pmis.project.literule;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.njydsz.pmis.project.entity.RuleTemplateDO;
import com.njydsz.pmis.project.mapper.RuleTemplateMapper;
import com.njydsz.pmis.literule.api.RuleDefinition;
import com.njydsz.pmis.literule.api.RuleSeverity;
import com.njydsz.pmis.literule.config.RuleAdminService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 规则模板市场服务
 *
 * <p>提供规则模板的查询与一键导入功能。
 * 导入时从模板创建 {@link RuleDefinition}，并通过 {@link RuleAdminService} 保存为正式规则。
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RuleTemplateService {

    private final RuleTemplateMapper ruleTemplateMapper;
    private final RuleAdminService ruleAdminService;

    /**
     * 查询全部模板
     *
     * @return 模板列表
     */
    public List<RuleTemplateDO> listAll() {
        return ruleTemplateMapper.selectList(
                new LambdaQueryWrapper<RuleTemplateDO>()
                        .orderByAsc(RuleTemplateDO::getPriority));
    }

    /**
     * 按类别查询模板
     *
     * @param category 模板类别
     * @return 模板列表
     */
    public List<RuleTemplateDO> listByCategory(String category) {
        return ruleTemplateMapper.selectByCategory(category);
    }

    /**
     * 按行业查询模板
     *
     * @param industry 行业编码
     * @return 模板列表
     */
    public List<RuleTemplateDO> listByIndustry(String industry) {
        return ruleTemplateMapper.selectByIndustry(industry);
    }

    /**
     * 一键导入模板为规则定义
     *
     * <p>根据模板编码查找模板，将其转换为 {@link RuleDefinition} 后保存。
     * 规则编码使用模板编码（templateCode），若已存在同名规则则执行更新。
     *
     * @param templateCode 模板编码
     * @param operator     操作人
     * @return 保存后的规则定义
     */
    public RuleDefinition importTemplate(String templateCode, String operator) {
        RuleTemplateDO template = ruleTemplateMapper.selectByCode(templateCode);
        if (template == null) {
            throw new IllegalArgumentException("模板不存在: " + templateCode);
        }

        RuleDefinition definition = RuleDefinition.builder()
                .code(template.getTemplateCode())
                .name(template.getTemplateName())
                .category(template.getCategory())
                .description(template.getDescription())
                .conditionExpression(template.getConditionExpression())
                .severityExpression(template.getSeverityExpression())
                .defaultSeverity(RuleSeverity.fromCode(template.getDefaultSeverity()))
                .titleTemplate(template.getTitleTemplate())
                .descriptionTemplate(template.getDescriptionTemplate())
                .priority(template.getPriority() != null ? template.getPriority() : 100)
                .enabled(true)
                .scope(template.getScope())
                .drilldownAvailable(true)
                .build();

        RuleDefinition saved = ruleAdminService.save(definition, operator, "从模板导入: " + templateCode);
        log.info("[LiteRule] 模板导入完成: templateCode={}, ruleCode={}, operator={}", templateCode, saved.getCode(), operator);
        return saved;
    }
}
