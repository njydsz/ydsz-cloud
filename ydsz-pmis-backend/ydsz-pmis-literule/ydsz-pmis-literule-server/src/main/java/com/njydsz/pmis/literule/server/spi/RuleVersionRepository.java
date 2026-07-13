package com.njydsz.pmis.literule.server.spi;

import java.util.List;

import com.njydsz.pmis.literule.api.RuleDefinition;

/**
 * 规则版本仓库接口（SPI）
 *
 * <p>由消费方提供实现，负责规则版本历史的管理。
 * 采用"主表+历史表"设计，支持变更追踪、一键回滚。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public interface RuleVersionRepository {

    /**
     * 保存规则版本快照（在规则变更时调用）
     *
     * @param definition 规则定义
     * @param operator   操作人
     * @param changeDesc 变更描述
     */
    void saveVersion(RuleDefinition definition, String operator, String changeDesc);

    /**
     * 查询规则版本历史
     *
     * @param ruleCode 规则编码
     * @return 版本历史列表（按版本号倒序）
     */
    List<RuleVersion> listVersions(String ruleCode);

    /**
     * 回滚到指定版本
     *
     * @param ruleCode 规则编码
     * @param version  目标版本号
     * @param operator 操作人
     * @return 回滚后的规则定义
     */
    RuleDefinition rollback(String ruleCode, int version, String operator);
}
