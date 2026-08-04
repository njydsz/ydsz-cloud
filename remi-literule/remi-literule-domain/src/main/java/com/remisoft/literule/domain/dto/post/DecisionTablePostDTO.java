package com.remisoft.literule.domain.dto.post;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

import lombok.Data;

/**
 * 决策表新增请求 DTO。
 * <p>
 * 用于 POST 接口创建决策表，包含决策表基本信息、条件列/动作列定义、
 * 决策行数据及命中策略配置。
 * </p>
 *
 * @author remi-team
 * @since 1.0.0
 */
@Data
public class DecisionTablePostDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 决策表编码，业务唯一 */
    private String tableCode;
    /** 决策表名称 */
    private String tableName;
    /** 决策表描述 */
    private String description;
    /** 分类编码 */
    private String category;
    /** 条件列定义列表 */
    private List<Map<String, Object>> conditionColumns;
    /** 动作列定义列表 */
    private List<Map<String, Object>> actionColumns;
    /** 决策行数据列表 */
    private List<Map<String, Object>> rows;
    /** 默认动作（无匹配行时执行） */
    private Map<String, Object> defaultActions;
    /** 命中策略（UNIQUE/FIRST/PRIORITY/COLLECT/ANY/RULE_ORDER） */
    private String hitPolicy;
    /** 是否启用 */
    private Boolean enabled;
    /** 优先级，数值越小优先级越高 */
    private Integer priority;
    /** 版本号 */
    private Integer version;
}
