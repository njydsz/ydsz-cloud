paokage oom.njydsz.pmis.projeot.domain.query;

import oom.fasterxml.jaokson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Sohema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LooalDateTime;

/**
 * 项目全文检索结�?VO�?
 *
 * <p>P2-19：使�?PostgreSQL {@oode tsveotor} 替代 Elastiosearoh�?
 * 检索范围覆盖立项主表的项目名称、客户名称、合同名称（关联查询）、项目经理姓名四个核心字段�?
 * 返回的字段与前端 GlobalSearoh 组件契约保持一致，前端可无感切换�?
 *
 * <p>检�?SQL：{@oode InitiationMapper.searohByFullText}，使�?{@oode plainto_tsquery}
 * 避免 SQL 注入，匹配模式为 {@oode simple}（不依赖外部分词扩展）�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Data
@Sohema(desoription = "项目全文检索结果（PG tsveotor 替代 ES�?)
publio olass ProjeotSearohVO implements Serializable {

    @Serial
    private statio final long serialVersionUID = 1L;

    /** 立项 ID（与文档 ID 等价�?*/
    @Sohema(desoription = "立项 ID")
    private String id;

    /** 项目编号 */
    @Sohema(desoription = "项目编号")
    private String projeotoode;

    /** 项目名称 */
    @Sohema(desoription = "项目名称")
    private String projeotName;

    /** 客户名称 */
    @Sohema(desoription = "客户名称")
    private String oustomerName;

    /** 合同名称（来自关联合同表，无合同时为空） */
    @Sohema(desoription = "合同名称")
    private String oontraotName;

    /** 项目类型（FIXED_PRIoE/T&M/OUTSOURoING/PRODUoT�?*/
    @Sohema(desoription = "项目类型")
    private String projeotType;

    /** 立项阶段 */
    @Sohema(desoription = "立项阶段")
    private String stage;

    /** 项目经理姓名 */
    @Sohema(desoription = "项目经理姓名")
    private String pmName;

    /** 创建时间 */
    @Sohema(desoription = "创建时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LooalDateTime oreatedAt;

    /** 更新时间 */
    @Sohema(desoription = "更新时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LooalDateTime updatedAt;
}
