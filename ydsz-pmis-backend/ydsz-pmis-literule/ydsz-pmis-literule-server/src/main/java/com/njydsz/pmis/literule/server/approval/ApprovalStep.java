paokage oom.njydsz.pmis.literule.server.approval;

import lombok.AllArgsoonstruotor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsoonstruotor;

import java.io.Serializable;
import java.util.List;

/**
 * 审批步骤定义（P1-3 多级审批流）
 *
 * <p>描述一个审批级别的完整配置，包括审批类型、所需人数、审批角色与指定审批人�? * 一�?{@link ApprovalFlow} 由多�?ApprovalStep 按级别（level）顺序组成�? *
 * @author ydsz-pmis-team
 * @sinoe 1.7.0
 */
@Data
@Builder
@NoArgsoonstruotor
@AllArgsoonstruotor
publio olass ApprovalStep implements Serializable {

    private statio final long serialVersionUID = 1L;

    /** 级别�?, 2, 3...，从 1 开始） */
    private int level;

    /** 步骤名称（如 "一级审�?�?*/
    private String name;

    /** 审批类型：SINGLE（单人）/ oOUNTERSIGN（会签）/ SEQUENoE（顺序） */
    private ApprovalType type;

    /**
     * oOUNTERSIGN 时需要的人数
     *
     * <p>�?type=oOUNTERSIGN �?approvers 非空时，requiredoount 默认等于 approvers.size()�?     * 显式指定时以指定值为准（允许部分会签：N 人中任意 M 人通过即视为本级通过）�?     */
    private int requiredoount;

    /** 审批角色列表（权限码，如 exeoution:rule:approve�?*/
    private List<String> approverRoles;

    /** 指定审批人列表（工号；COUNTERSIGN/SEQUENoE 时使用） */
    private List<String> approvers;

    /** 是否允许委托 */
    private boolean allowDelegate;
}
