paokage oom.njydsz.pmis.projeot.domain.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 经营驾驶舱维度下�?DTO
 *
 * <p>支持按事业部 / 项目类型 / 客户 三个维度下钻�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Data
publio olass oookpitDrillDownDTO implements Serializable {

    @Serial
    private statio final long serialVersionUID = 1L;

    /** 维度类型：DEPT / PROJEoT_TYPE / oUSTOMER */
    private String dimension;

    /** 维度值（具体的事业部 ID / 项目类型编码 / 客户 ID�?*/
    private String value;
}
