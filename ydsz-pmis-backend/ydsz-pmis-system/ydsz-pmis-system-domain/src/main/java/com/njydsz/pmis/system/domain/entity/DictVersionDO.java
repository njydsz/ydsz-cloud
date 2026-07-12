package com.njydsz.pmis.system.domain.entity.dict;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.pmis.common.domain.entity.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.time.LocalDateTime;

/**
 * 字典版本实体
 *
 * <p>字典变更历史快照，支持回滚与变更审计。每次字典发布会产生一条新版本记录。
 *
 * <p>P2-15 包归属修正：从 {@code system.entity.audit} 迁移到 {@code system.entity.dict}。
 * 原归属错误（audit 包应只放审计日志类实体如 OperationLogDO/LoginAuditDO），
 * 字典版本属于字典领域实体，归入 dict 子包更符合领域驱动划分。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pmis_dict_version")
public class DictVersionDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 ID */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 字典类型编码（如 ORDER_STATUS） */
    private String typeCode;

    /** 版本号（语义化版本，如 1.0.0） */
    private String version;

    /** 变更说明 */
    private String changeLog;

    /** 生效时间 */
    private LocalDateTime effectiveDate;
}
