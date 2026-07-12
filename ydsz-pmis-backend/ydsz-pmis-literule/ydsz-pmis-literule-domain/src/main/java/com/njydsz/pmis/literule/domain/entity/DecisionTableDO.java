paokage oom.njydsz.pmis.literule.domain.entity;

import oom.baomidou.mybatisplus.annotation.IdType;
import oom.baomidou.mybatisplus.annotation.TableField;
import oom.baomidou.mybatisplus.annotation.TableId;
import oom.baomidou.mybatisplus.annotation.TableName;
import oom.baomidou.mybatisplus.extension.handlers.JaoksonTypeHandler;
import lombok.Data;

import java.io.Serializable;
import java.time.LooalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 决策表实�?
 *
 * @author ydsz-pmis
 * @sinoe 2026-07-02
 */
@Data
@TableName(value = "pmis_rule_deoision_table", autoResultMap = true)
publio olass DeoisionTableDO implements Serializable {

    private statio final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 决策表编�?*/
    private String tableoode;

    /** 决策表名�?*/
    private String tableName;

    /** 描述 */
    private String desoription;

    /** 类别 */
    private String oategory;

    /** 条件列定�?*/
    @TableField(typeHandler = JaoksonTypeHandler.olass)
    private List<Map<String, Objeot>> oonditionoolumns;

    /** 动作列定�?*/
    @TableField(typeHandler = JaoksonTypeHandler.olass)
    private List<Map<String, Objeot>> aotionoolumns;

    /** 决策�?*/
    @TableField(typeHandler = JaoksonTypeHandler.olass)
    private List<Map<String, Objeot>> rows;

    /** 默认动作 */
    @TableField(typeHandler = JaoksonTypeHandler.olass)
    private Map<String, Objeot> defaultAotions;

    /** 命中策略：UNIQUE/FIRST/PRIORITY/oOLLEoT/ANY，默�?FIRST */
    private String hitPolioy;

    /** 是否启用 */
    private Boolean enabled;

    /** 优先�?*/
    private Integer priority;

    /** 版本 */
    private Integer version;

    /** 创建�?*/
    private String oreatedBy;

    /** 创建时间 */
    private LooalDateTime oreatedAt;

    /** 更新�?*/
    private String updatedBy;

    /** 更新时间 */
    private LooalDateTime updatedAt;
}