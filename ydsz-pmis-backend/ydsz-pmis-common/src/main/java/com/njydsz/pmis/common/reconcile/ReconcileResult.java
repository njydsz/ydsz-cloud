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

    @Serial
    private static final long serialVersionUID = 1L;

    /** 对账项编码 */
    private String code;

    private String name;

    /** 检测到的不一致数 */
    private long diffCount;

    /** 已自动修复数 */
    private long autoFixedCount;

    /** 是否完成 */
    private boolean success = true;

    private String message;

    private LocalDateTime finishedAt = LocalDateTime.now();
}
