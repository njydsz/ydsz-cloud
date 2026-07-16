package com.njydsz.pmis.project.domain.query;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 统一搜索结果项（跨实体通用结构）。
 *
 * <p>对应前端 {@code UniversalSearchDoc}，一次请求返回多种实体类型的搜索结果。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "统一搜索结果项")
public class UniversalSearchVO {

    /** 实体类型 */
    @Schema(description = "实体类型", example = "project")
    private String type;

    /** 实体 ID */
    @Schema(description = "实体 ID", example = "123")
    private Object id;

    /** 主标题（项目名 / 合同名 / 流程标题 / 工单标题 / 员工姓名 / 文档标题） */
    @Schema(description = "主标题", example = "智慧城市项目")
    private String title;

    /** 副标题（客户名 / 合同编号 / 流程编号 / 工单编号 / 部门 / 标签） */
    @Schema(description = "副标题", example = "南京市信息中心")
    private String subtitle;

    /** 状态 */
    @Schema(description = "状态", example = "进行中")
    private String status;

    /** 跳转路径（前端路由，已组装好查询参数） */
    @Schema(description = "前端跳转路径", example = "/project/initiation?highlight=123")
    private String path;
}
