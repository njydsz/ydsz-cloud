package com.njydsz.pmis.workflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * P2-3 流程变量归档 DO
 *
 * <p>对应 pmis_flow_his_variable 表，将 instance.variable JSON 拆分为独立行。
 * 解决 instance 归档后大 JSON 查询效率低的问题。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@TableName("pmis_flow_his_variable")
public class FlowHisVariableDO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 归档实例 ID（对应 pmis_flow_his_instance.id） */
    private Long instanceId;

    /** 变量键 */
    private String varKey;

    /** 变量值（字符串形式） */
    private String varValue;

    /** 归档时间 */
    private LocalDateTime archivedAt;
}
