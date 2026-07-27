package com.njydsz.project.domain.vo;

import java.io.Serial;
import java.io.Serializable;
import lombok.Data;

/**
 * ProjectExpense 视图对象。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class ProjectExpenseVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String id;
    private String createdBy;
    private LocalDateTime createdAt;
    private String updatedBy;
    private LocalDateTime updatedAt;
}