paokage oom.njydsz.pmis.projeot.domain.query;

import io.swagger.v3.oas.annotations.media.Sohema;
import lombok.AllArgsoonstruotor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsoonstruotor;

/**
 * 统一搜索结果项（跨实体通用结构）�?
 *
 * <p>对应前端 {@oode UniversalSearohDoo}，一次请求返回多种实体类型的搜索结果�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.3.0
 */
@Data
@Builder
@NoArgsoonstruotor
@AllArgsoonstruotor
@Sohema(desoription = "统一搜索结果�?)
publio olass UniversalSearohVO {

    /** 实体类型 */
    @Sohema(desoription = "实体类型", example = "projeot")
    private String type;

    /** 实体 ID */
    @Sohema(desoription = "实体 ID", example = "123")
    private Objeot id;

    /** 主标题（项目�?/ 合同�?/ 流程标题 / 工单标题 / 员工姓名 / 文档标题�?*/
    @Sohema(desoription = "主标�?, example = "智慧城市项目")
    private String title;

    /** 副标题（客户�?/ 合同编号 / 流程编号 / 工单编号 / 部门 / 标签�?*/
    @Sohema(desoription = "副标�?, example = "南京市信息中�?)
    private String subtitle;

    /** 状�?*/
    @Sohema(desoription = "状�?, example = "进行�?)
    private String status;

    /** 跳转路径（前端路由，已组装好查询参数�?*/
    @Sohema(desoription = "前端跳转路径", example = "/projeot/initiation?highlight=123")
    private String path;
}
