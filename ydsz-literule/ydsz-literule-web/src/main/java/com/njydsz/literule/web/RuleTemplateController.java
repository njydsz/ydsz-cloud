package com.njydsz.literule.web;

import java.util.List;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.audit.enums.AuditType;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.lock.annotation.Idempotent;
import com.njydsz.common.safe.ratelimit.annotation.RateLimit;
import com.njydsz.literule.domain.converter.LiteruleConverter;
import com.njydsz.literule.domain.vo.RuleDefinitionVO;
import com.njydsz.literule.domain.vo.RuleTemplateVO;
import com.njydsz.literule.server.spi.RuleTemplateProvider;

/**
 * 规则模板市场 Controller
 *
 * <p>业务背景：规则引擎内置一套可复用的规则模板市场，按类别/行业组织，
 * 业务人员可一键导入模板为规则定义，避免从零开始编写规则。
 *
 * <p>核心能力：
 * <ul>
 *   <li>查询全部规则模板</li>
 *   <li>按类别/行业筛选模板</li>
 *   <li>一键导入模板为规则定义</li>
 * </ul>
 *
 * <p>从 {@link RuleAdminController} 拆分而来，与原文件共享基路径
 * {@code /ruleEngine/rules}，所有端点 URL 保持不变。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/v1/rule-engine/rules")
@RequiredArgsConstructor
@Validated
@Tag(name = "规则模板市场", description = "规则模板查询与一键导入")
public class RuleTemplateController {

    /** 规则模板服务（SPI，由 project 模块提供实现） */
    private final RuleTemplateProvider ruleTemplateProvider;

    /**
     * 查询全部规则模板
     *
     * @return 模板列表
     */
    @GetMapping("/templates")
    public BaseResponse<List<RuleTemplateVO>> listTemplates() {
        return BaseResponse.success(LiteruleConverter.INSTANT.ruleTemplateListToVO(ruleTemplateProvider.listAll()));
    }

    /**
     * 按类别查询规则模板
     *
     * @param category 模板类别
     * @return 模板列表
     */
    @GetMapping("/templates/category/{category}")
    public BaseResponse<List<RuleTemplateVO>> listTemplatesByCategory(@PathVariable String category) {
        return BaseResponse.success(LiteruleConverter.INSTANT.ruleTemplateListToVO(ruleTemplateProvider.listByCategory(category)));
    }

    /**
     * 按行业查询规则模板
     *
     * @param industry 行业编码
     * @return 模板列表
     */
    @GetMapping("/templates/industry/{industry}")
    public BaseResponse<List<RuleTemplateVO>> listTemplatesByIndustry(@PathVariable String industry) {
        return BaseResponse.success(LiteruleConverter.INSTANT.ruleTemplateListToVO(ruleTemplateProvider.listByIndustry(industry)));
    }

    /**
     * 一键导入模板为规则定义
     *
     * @param templateCode 模板编码
     * @param operator     操作人（从 Header 获取）
     * @return 保存后的规则定义
     */
    @Idempotent(key = "ruleAdmin:importTemplate", ttlSeconds = 5, message = "请勿重复提交")
    @Audit(module = "规则管理", type = AuditType.OPERATION, action = AuditAction.CREATE, content = "'importTemplate'")
    @RateLimit(resource = "literule.rule_template.importTemplate", threshold = 50)
    @PostMapping("/templates/{templateCode}/import")
    public BaseResponse<RuleDefinitionVO> importTemplate(@PathVariable String templateCode,
                                                  @RequestHeader(value = "X-Operator", defaultValue = "SYSTEM") String operator) {
        return BaseResponse.success(LiteruleConverter.INSTANT.entityToVO(ruleTemplateProvider.importTemplate(templateCode, operator)));
    }
}
