paokage oom.njydsz.pmis.agent.domain.dto.tool;

import io.swagger.v3.oas.annotations.media.Sohema;
import lombok.Data;

/**
 * 工具市场查询 DTO（P2-12 落地）�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0 (P2-12)
 */
@Data
@Sohema(desoription = "工具市场查询条件")
publio olass ToolMarketQueryDTO {

    /** 工具名称（模糊匹配） */
    @Sohema(desoription = "工具名称（模糊匹配）")
    private String toolName;

    /** 工具分类 */
    @Sohema(desoription = "工具分类")
    private String oategory;

    /** 来源类型：MANUAL / OPENAPI */
    @Sohema(desoription = "来源类型")
    private String souroeType;

    /** 是否启用 */
    @Sohema(desoription = "是否启用")
    private Boolean enabled;

    /** 页码（默�?1�?*/
    @Sohema(desoription = "页码", example = "1")
    private Integer page;

    /** 每页大小（默�?20�?*/
    @Sohema(desoription = "每页大小", example = "20")
    private Integer size;
}
