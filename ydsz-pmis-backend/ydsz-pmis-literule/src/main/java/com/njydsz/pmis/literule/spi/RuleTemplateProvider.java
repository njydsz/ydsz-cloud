package com.njydsz.pmis.literule.spi;

import com.njydsz.pmis.literule.api.RuleDefinition;

import java.util.List;

/**
 * 规则模板提供者 SPI
 *
 * <p>由消费方（如 project 模块）提供实现，从规则模板市场（{@code pmis_rule_template} 表）
 * 加载预置模板，将原有 {@code RuleTemplateService} 的能力抽象为 SPI，避免 literule 模块
 * 直接依赖持久层实现。
 *
 * <p>literule 模块仅定义接口与模板元数据（{@link RuleTemplateMeta}），
 * 具体查询、过滤、导入逻辑由实现方提供。通过此接口反转依赖，支持：
 * <ul>
 *   <li>多数据源适配（MySQL / 配置中心 / 远程市场）</li>
 *   <li>测试环境注入 mock 实现</li>
 *   <li>未来规则集市场（RulePack）远程拉取</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.4.0
 */
public interface RuleTemplateProvider {

    /**
     * 列出全部模板
     *
     * @return 模板元数据列表（按优先级升序）
     */
    List<RuleTemplateMeta> listAll();

    /**
     * 按类别查询模板
     *
     * @param category 模板类别（如 FINANCE / EVM / BENCH）
     * @return 模板元数据列表
     */
    List<RuleTemplateMeta> listByCategory(String category);

    /**
     * 按行业查询模板
     *
     * @param industry 行业编码
     * @return 模板元数据列表
     */
    List<RuleTemplateMeta> listByIndustry(String industry);

    /**
     * 按编码查询单个模板
     *
     * @param templateCode 模板编码
     * @return 模板元数据；不存在返回 null
     */
    RuleTemplateMeta findByCode(String templateCode);

    /**
     * 导入模板为规则定义
     *
     * <p>根据模板编码查找模板，将其转换为 {@link RuleDefinition} 后保存为正式规则。
     * 规则编码使用模板编码（{@code templateCode}），若已存在同名规则则执行更新。
     *
     * @param templateCode 模板编码
     * @param operator     操作人
     * @return 保存后的规则定义（含版本号）
     */
    RuleDefinition importTemplate(String templateCode, String operator);
}
