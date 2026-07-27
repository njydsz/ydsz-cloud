package com.njydsz.literule.server.spi;

import java.util.List;

import com.njydsz.literule.domain.entity.RuleDependency;

/**
 * 规则依赖关系提供者 SPI
 *
 * <p>由消费方（如 project 模块）提供实现，提供规则依赖的 CRUD、循环依赖检测、
 * 级联禁用影响范围计算等能力。将原有 {@code RuleDependencyService} 的能力抽象为 SPI，
 * 避免 literule 模块直接依赖 project 模块。
 *
 * @since 1.0.0
 */
public interface RuleDependencyProvider {

    /**
     * 新增依赖
     *
     * @param ruleCode           规则编码
     * @param dependsOnRuleCode  被依赖的规则编码
     * @param dependencyType     依赖类型：EXECUTE / DATA
     * @param cascadeOnDisable   被依赖规则禁用时是否级联禁用本规则
     * @param description        依赖关系描述
     * @param operator           操作人
     * @return 保存后的依赖记录
     */
    RuleDependency add(String ruleCode, String dependsOnRuleCode, String dependencyType,
                         boolean cascadeOnDisable, String description, String operator);

    /**
     * 删除一条依赖
     *
     * @param ruleCode          规则编码
     * @param dependsOnRuleCode 被依赖的规则编码
     */
    void remove(String ruleCode, String dependsOnRuleCode);

    /**
     * 查询规则的依赖（正向：依赖了哪些）
     *
     * @param ruleCode 规则编码
     * @return 依赖记录列表
     */
    List<RuleDependency> listDependencies(String ruleCode);

    /**
     * 查询被依赖（反向：被哪些规则依赖）
     *
     * @param ruleCode 规则编码
     * @return 被依赖记录列表
     */
    List<RuleDependency> listDependents(String ruleCode);

    /**
     * 计算禁用某条规则时，需要级联禁用的规则列表
     *
     * @param ruleCode 规则编码
     * @return 需要级联禁用的规则编码列表
     */
    List<String> cascadingDisable(String ruleCode);
}
