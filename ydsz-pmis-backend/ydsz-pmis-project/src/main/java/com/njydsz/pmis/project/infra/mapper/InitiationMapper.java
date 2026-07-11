package com.njydsz.pmis.project.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.project.domain.entity.InitiationDO;
import com.njydsz.pmis.project.domain.query.ProjectSearchVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 立项数据访问层
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Mapper
public interface InitiationMapper extends BaseMapper<InitiationDO> {

    /**
     * 根据项目编号查询立项。
     *
     * @param code 项目编号
     * @return 立项实体；不存在返回 null
     */
    InitiationDO selectByCode(@Param("code") String code);

    /**
     * 更新立项阶段与当前门径评审点。
     *
     * @param id    立项 ID
     * @param stage 阶段码（InitiationStage.code）
     * @param gate  门径评审点（GateCode），可空
     * @return 受影响行数
     */
    int updateStage(@Param("id") String id,
                    @Param("stage") String stage,
                    @Param("gate") String gate);

    /**
     * 按阶段聚合计数（用于看板）。
     *
     * @param tenantId 租户 ID
     * @return 每种阶段对应的数量列表
     */
    List<Map<String, Object>> aggregateByStage(@Param("tenantId") String tenantId);

    /**
     * 统计指定阶段的立项数量。
     *
     * @param stage    阶段码
     * @param tenantId 租户 ID
     * @return 数量
     */
    Long countByStage(@Param("stage") String stage, @Param("tenantId") String tenantId);

    /**
     * 基于 PG tsvector 的项目全文检索（P2-19，替代 ES）。
     *
     * <p>检索范围：立项主表的 project_name / customer_name / pm_name + 关联合同表（pmis_project_contract）
     * 的 contract_name，使用 {@code to_tsvector('simple', ...)} 构建文本向量，
     * {@code plainto_tsquery} 解析关键词（防 SQL 注入），按 {@code ts_rank} 倒序返回。
     *
     * <p>仅返回逻辑未删除的记录，且最多返回 {@code limit} 条。合同名称通过 LEFT JOIN 拼入，无合同时为 NULL。
     *
     * @param keyword 关键词（用户输入）
     * @param tenantId 租户 ID（数据隔离）
     * @param offset 分页偏移
     * @param limit 分页大小
     * @return 匹配的项目搜索结果列表
     */
    List<ProjectSearchVO> searchByFullText(
            @Param("keyword") String keyword,
            @Param("tenantId") String tenantId,
            @Param("offset") int offset,
            @Param("limit") int limit);

    /**
     * 基于 PG tsvector 的全文检索结果总数。
     *
     * @param keyword  关键词
     * @param tenantId 租户 ID
     * @return 命中总数
     */
    Long countByFullText(@Param("keyword") String keyword, @Param("tenantId") String tenantId);
}
