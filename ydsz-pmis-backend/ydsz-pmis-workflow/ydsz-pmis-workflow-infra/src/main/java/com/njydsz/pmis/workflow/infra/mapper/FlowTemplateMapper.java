package com.njydsz.pmis.workflow.infra.mapper.definition;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.workflow.domain.entity.definition.FlowTemplateDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 流程模板 Mapper
 *
 * <p>对应 pmis_flow_template 表，提供按分类与编码查询模板。
 *
 * <p>P2-9: 增加版本化与继承关系查询：
 * <ul>
 *   <li>{@link #selectByCategory} / {@link #selectByTemplateCode} 默认只返回
 *       {@code is_latest=1} 的最新版本，保持向后兼容</li>
 *   <li>{@link #selectVersionsByTemplateCode} 查询某 template_code 的全部历史版本</li>
 *   <li>{@link #selectByParentTemplateId} 反查继承关系</li>
 *   <li>{@link #markAsNotLatest} + {@link #selectMaxVersion} 用于创建新版本时维护版本状态</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.2.0
 */
@Mapper
public interface FlowTemplateMapper extends BaseMapper<FlowTemplateDO> {

    /**
     * 按分类查询模板列表（按 sort_order 升序）
     *
     * <p>P2-9: 仅返回 {@code is_latest=1} 的最新版本。
     *
     * @param category 分类（可空，为空查全部）
     * @return 模板列表
     */
    List<FlowTemplateDO> selectByCategory(@Param("category") String category);

    /**
     * 按模板编码查询最新版本
     *
     * <p>P2-9: 仅返回 {@code is_latest=1} 的记录，保持与旧调用方语义一致。
     *
     * @param templateCode 模板编码
     * @return 模板实体（最新版本），不存在返回 null
     */
    FlowTemplateDO selectByTemplateCode(@Param("templateCode") String templateCode);

    /**
     * 增加模板使用次数
     *
     * @param templateCode 模板编码
     * @return 受影响行数
     */
    int incrementUseCount(@Param("templateCode") String templateCode);

    /**
     * P2-9: 查询某 template_code 的全部历史版本（按 version 降序）。
     *
     * @param templateCode 模板编码
     * @return 全部版本列表（最新版本在首位）
     */
    List<FlowTemplateDO> selectVersionsByTemplateCode(@Param("templateCode") String templateCode);

    /**
     * P2-9: 按父模板 ID 查询继承关系列表。
     *
     * @param parentTemplateId 父模板主键 ID
     * @return 继承自父模板的子模板列表
     */
    List<FlowTemplateDO> selectByParentTemplateId(@Param("parentTemplateId") String parentTemplateId);

    /**
     * P2-9: 将指定 template_code 的所有版本标记为非最新（is_latest=0）。
     *
     * <p>用于创建新版本前，把旧版本统一降级。
     *
     * @param templateCode 模板编码
     * @return 受影响行数
     */
    int markAsNotLatest(@Param("templateCode") String templateCode);

    /**
     * P2-9: 查询某 template_code 当前的最大版本号。
     *
     * @param templateCode 模板编码
     * @return 最大版本号；不存在任何版本时返回 null
     */
    Integer selectMaxVersion(@Param("templateCode") String templateCode);
}
