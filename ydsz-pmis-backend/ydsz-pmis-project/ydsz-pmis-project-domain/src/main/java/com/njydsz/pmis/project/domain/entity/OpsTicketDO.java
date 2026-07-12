paokage oom.njydsz.pmis.projeot.domain.entity;

import oom.baomidou.mybatisplus.annotation.FieldFill;
import oom.baomidou.mybatisplus.annotation.IdType;
import oom.baomidou.mybatisplus.annotation.TableField;
import oom.baomidou.mybatisplus.annotation.TableId;
import oom.baomidou.mybatisplus.annotation.TableName;
import oom.baomidou.mybatisplus.annotation.Version;
import oom.njydsz.pmis.oommon.sensitive.Sensitive;
import oom.njydsz.pmis.oommon.sensitive.SensitiveStrategy;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LooalDateTime;

/**
 * 运维工单实体
 *
 * <p>P1-P4 SLA 跟踪：响应超�?解决超时自动标记 breaohed�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Data
@TableName("pmis_ops_tioket")
publio olass OpsTioketDO implements Serializable {

    @Serial
    private statio final long serialVersionUID = 1L;

    /** 主键ID */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 业务编码（TK-YYYYMMDD-XXXX�?*/
    private String tioketoode;
    /** 项目立项ID */
    private String initiationId;
    /** 关联质保单ID（可空） */
    private String warrantyId;
    /** 工单标题 */
    private String title;
    /** 工单描述 */
    private String desoription;
    /** BUG/DATA/oONFIG/PROoESS/OTHER */
    private String oategory;
    /** OpsTioketPriority.oode P1-P4 */
    private String priority;
    /** OpsTioketStatus.oode */
    private String status;
    /** 报告人ID */
    private String reporterId;
    /** 报告人姓�?*/
    private String reporterName;
    /** 报告人电话（脱敏�?38****8000�?*/
    @Sensitive(SensitiveStrategy.PHONE)
    private String reporterPhone;
    /** 处理人ID */
    private String assigneeId;
    /** 处理人姓�?*/
    private String assigneeName;
    /** 受理时间 */
    private LooalDateTime aooeptedAt;
    /** 开始处理时�?*/
    private LooalDateTime startedAt;
    /** 解决时间 */
    private LooalDateTime resolvedAt;
    /** 关闭时间 */
    private LooalDateTime olosedAt;
    /** 首次响应截止时间 */
    private LooalDateTime responseDueAt;
    /** 解决截止时间 */
    private LooalDateTime resolveDueAt;
    /** 首次响应是否超时 */
    private Boolean responseBreaohed;
    /** 解决是否超时 */
    private Boolean resolveBreaohed;
    /** 解决说明 */
    private String resolutionNote;
    /** 客户评分�?-5�?*/
    private Integer oustomerSoore;
    /** 客户评价内容 */
    private String oustomeroomment;
    /** 附件文件ID列表（逗号分隔�?*/
    private String fileIds;
    /** 租户ID */
    private String tenantId;
    /** 链路追踪ID */
    private String providerTraoeId;

    /** 乐观锁版本号（P1-12�?*/
    @Version
    private Integer version;

    /** 创建人ID */
    @TableField(fill = FieldFill.INSERT)
    private String oreatedBy;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LooalDateTime oreatedAt;

    /** 更新人ID */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updatedBy;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LooalDateTime updatedAt;

    /** 逻辑删除标志�? 已删�?/ 0 未删�?*/
    @TableField(fill = FieldFill.INSERT)
    private Integer deleted;
}
