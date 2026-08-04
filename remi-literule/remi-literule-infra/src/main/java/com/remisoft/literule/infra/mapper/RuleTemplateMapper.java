package com.remisoft.literule.infra.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.remisoft.literule.domain.entity.RuleTemplate;

/**
 * 规则模板 Mapper
 *
 * <p>对应数据表 <code>remi_rule_template</code>。
 * <p>规则模板是规则的「母版」（预置规则集），按业务场景（OA/财务/HR）提供开箱即用的规则。
 *
 * <p><b>主要索引：</b>
 * <ul>
 *   <li>uk_template_code — 模板编码唯一索引</li>
 *   <li>idx_category — 业务分类过滤索引</li>
 * </ul>
 *
 * <p><b>多租户：</b>由 MyBatis 拦截器自动注入 {@code tenant_id} 过滤条件，本接口不感知。
 *
 * <p><b>逻辑删除：</b>{@code deleted} 字段标识，所有查询自动过滤已删除记录。
 *
 * @author remi-team
 * @since 1.0.0
 *
 * @see com.remisoft.literule.domain.entity.RuleTemplate 规则模板实体
 * @see com.remisoft.literule.server.service.RuleTemplateService 规则模板 Service
 * @see com.baomidou.mybatisplus.core.mapper.BaseMapper MyBatis-Plus 通用 Mapper
 */
@Mapper
public interface RuleTemplateMapper extends BaseMapper<RuleTemplate> {

    /**
     * 按类别查询模板
     *
     * @param category 模板类别
     * @return 模板列表
     */
    List<RuleTemplate> selectByCategory(@Param("category") String category);

    /**
     * 按行业查询模板
     *
     * @param industry 行业编码
     * @return 模板列表
     */
    List<RuleTemplate> selectByIndustry(@Param("industry") String industry);

    /**
     * 根据模板编码查询单条模板
     *
     * @param templateCode 模板编码
     * @return 模板 DO
     */
    RuleTemplate selectByCode(@Param("templateCode") String templateCode);
}
