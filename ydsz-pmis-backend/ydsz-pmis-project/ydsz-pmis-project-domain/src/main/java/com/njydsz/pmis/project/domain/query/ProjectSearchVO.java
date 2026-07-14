package com.njydsz.pmis.project.domain.query;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

import com.njydsz.pmis.common.json.annotation.YdszJsonFormat;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 项目全文检索结果 VO。
 *
 * <p>P2-19：使用 PostgreSQL {@code tsvector} 替代 Elasticsearch，
 * 检索范围覆盖立项主表的项目名称、客户名称、合同名称（关联查询）、项目经理姓名四个核心字段。
 * 返回的字段与前端 GlobalSearch 组件契约保持一致，前端可无感切换。
 *
 * <p>检索 SQL：{@code InitiationMapper.searchByFullText}，使用 {@code plainto_tsquery}
 * 避免 SQL 注入，匹配模式为 {@code simple}（不依赖外部分词扩展）。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@Schema(description = "项目全文检索结果（PG tsvector 替代 ES）")
public class ProjectSearchVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 立项 ID（与文档 ID 等价） */
    @Schema(description = "立项 ID")
    private String id;

    /** 项目编号 */
    @Schema(description = "项目编号")
    private String projectCode;

    /** 项目名称 */
    @Schema(description = "项目名称")
    private String projectName;

    /** 客户名称 */
    @Schema(description = "客户名称")
    private String customerName;

    /** 合同名称（来自关联合同表，无合同时为空） */
    @Schema(description = "合同名称")
    private String contractName;

    /** 项目类型（FIXED_PRICE/T&M/OUTSOURCING/PRODUCT） */
    @Schema(description = "项目类型")
    private String projectType;

    /** 立项阶段 */
    @Schema(description = "立项阶段")
    private String stage;

    /** 项目经理姓名 */
    @Schema(description = "项目经理姓名")
    private String pmName;

    /** 创建时间 */
    @Schema(description = "创建时间")
    @YdszJsonFormat(value = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createdAt;

    /** 更新时间 */
    @Schema(description = "更新时间")
    @YdszJsonFormat(value = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updatedAt;
}
