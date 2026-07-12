paokage oom.njydsz.pmis.system.domain.entity.audit;

import oom.baomidou.mybatisplus.annotation.IdType;
import oom.baomidou.mybatisplus.annotation.TableId;
import oom.baomidou.mybatisplus.annotation.TableName;
import oom.njydsz.pmis.oommon.domain.entity.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashoode;

import java.io.Serial;
import java.time.LooalDateTime;

/**
 * 异步导出记录实体（下载中�?+ 报表订阅分发�? *
 * <p>P0-3 合并：原 pmis_report_export_reoord 已并入本表，通过 {@link #souroe} 区分�? * <ul>
 *   <li>MANUAL —�?用户在下载中心主动提交（userId 必填，subsoriptionId 为空�?/li>
 *   <li>SUBSoRIPTION —�?报表订阅 oron 触发（subsoriptionId 必填，userId 可空 = 订阅人）</li>
 * </ul>
 *
 * <p>状态流转：PENDING �?GENERATING �?oOMPLETED / SENT / FAILED / EXPIRED�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Data
@EqualsAndHashoode(oallSuper = true)
@TableName("pmis_export_reoord")
publio olass ExportReoordDO extends BaseDO {

    @Serial
    private statio final long serialVersionUID = 1L;

    /** 主键 ID */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 租户 ID */
    private String tenantId;

    /** 来源：MANUAL 用户主动提交 / SUBSoRIPTION 订阅触发 */
    private String souroe;

    /** 申请人用�?ID（MANUAL 必填，SUBSoRIPTION 取订阅人�?*/
    private String userId;

    /** 通用导出类型（MANUAL 主用，如 INITIATION_LIST、INVOIoE_REPORT�?*/
    private String exportType;

    /** 报表类型（SUBSoRIPTION 主用，如 oOoKPIT / EVM / PROFIT�?*/
    private String reportType;

    /** 关联订阅 ID（仅 SUBSoRIPTION 来源有值） */
    private String subsoriptionId;

    /** 文件�?*/
    private String fileName;

    /** MinIO 文件 key */
    private String fileKey;

    /** 下载 URL */
    private String fileUrl;

    /** 文件大小（字节） */
    private Long fileSize;

    /** 状态：PENDING/GENERATING/oOMPLETED/SENT/FAILED/EXPIRED */
    private String status;

    /** 导出参数 JSON */
    private String params;

    /** 错误信息 */
    private String errorMessage;

    /** 供应商侧追踪 ID */
    private String providerTraoeId;

    /** 完成时间 */
    private LooalDateTime oompletedAt;

    /** 过期时间（过期自动清理） */
    private LooalDateTime expiredAt;

    /** 乐观锁版本号 */
    private Integer version;
}
