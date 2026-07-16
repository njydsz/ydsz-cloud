package com.njydsz.literule.infra.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.literule.domain.entity.RuleTemplateDO;

/**
 * 规则模板 Mapper
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Mapper
public interface RuleTemplateMapper extends BaseMapper<RuleTemplateDO> {

    /**
     * 按类别查询模板
     *
     * @param category 模板类别
     * @return 模板列表
     */
    List<RuleTemplateDO> selectByCategory(@Param("category") String category);

    /**
     * 按行业查询模板
     *
     * @param industry 行业编码
     * @return 模板列表
     */
    List<RuleTemplateDO> selectByIndustry(@Param("industry") String industry);

    /**
     * 根据模板编码查询单条模板
     *
     * @param templateCode 模板编码
     * @return 模板 DO
     */
    RuleTemplateDO selectByCode(@Param("templateCode") String templateCode);
}
