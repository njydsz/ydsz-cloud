package com.njydsz.pmis.common.reconcile;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 对账结果
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
public class ReconcileResult implements Serializable {

    /** 序列化版本号 */
    @Serial
    private static final long serialVersionUID = 1L;

    /** 对账项编码 */
    private String code;

    /** 对账项名称 */
    private String name;

    /** 检测到的不一致数 */
    private long diffCount;

    /** 已自动修复数 */
    private long autoFixedCount;

    /** 是否完成 */
    private boolean success = true;

    /** 结果消息（失败时填充错误原因） */
    private String message;

    /** 对账完成时间 */
    private LocalDateTime finishedAt = LocalDateTime.now();
}
