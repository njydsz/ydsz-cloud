package com.njydsz.system.domain.vo;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 字典版本 VO。
 *
 * @author ydsz-team
 */
@Data
@Schema(description = "字典版本视图对象")
public class DictVersionVO {

    @Schema(description = "主键 ID")
    private String id;

    @Schema(description = "字典类型编码")
    private String typeCode;

    @Schema(description = "版本号")
    private String version;

    @Schema(description = "变更说明")
    private String changeLog;

    @Schema(description = "生效时间")
    private LocalDateTime effectiveDate;

    @Schema(description = "快照数据（JSON）")
    private String snapshotJson;

    @Schema(description = "发布时间")
    private LocalDateTime createdAt;
}
