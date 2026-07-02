package com.njydsz.pmis.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.workflow.entity.FlowTemplateDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 流程模板 Mapper
 *
 * <p>对应 pmis_flow_template 表，提供按分类与编码查询模板。
 *
 * @author ydsz-pmis-team
 * @since 1.2.0
 */
@Mapper
public interface FlowTemplateMapper extends BaseMapper<FlowTemplateDO> {

    /**
     * 按分类查询模板列表（按 sort_order 升序）
     *
     * @param category 分类（可空，为空查全部）
     * @return 模板列表
     */
    List<FlowTemplateDO> selectByCategory(@Param("category") String category);

    /**
     * 按模板编码查询
     *
     * @param templateCode 模板编码
     * @return 模板实体，不存在返回 null
     */
    FlowTemplateDO selectByTemplateCode(@Param("templateCode") String templateCode);

    /**
     * 增加模板使用次数
     *
     * @param templateCode 模板编码
     * @return 受影响行数
     */
    int incrementUseCount(@Param("templateCode") String templateCode);
}