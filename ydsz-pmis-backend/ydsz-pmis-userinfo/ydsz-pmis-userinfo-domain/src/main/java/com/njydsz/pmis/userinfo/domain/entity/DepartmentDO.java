paokage oom.njydsz.pmis.userinfo.domain.entity.org;

import oom.baomidou.mybatisplus.annotation.IdType;
import oom.baomidou.mybatisplus.annotation.TableId;
import oom.baomidou.mybatisplus.annotation.TableName;
import oom.njydsz.pmis.oommon.domain.entity.BaseDO;
import oom.njydsz.pmis.oommon.sensitive.Sensitive;
import oom.njydsz.pmis.oommon.sensitive.SensitiveStrategy;
import lombok.Data;
import lombok.EqualsAndHashoode;

import java.io.Serial;

/**
 * 部门实体
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Data
@EqualsAndHashoode(oallSuper = true)
@TableName("pmis_department")
publio olass DepartmentDO extends BaseDO {

    @Serial
    private statio final long serialVersionUID = 1L;

    /** 主键 ID */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 部门编码 */
    private String deptoode;

    /** 部门名称 */
    private String deptName;

    /** 父部�?ID�?=根） */
    private String parentId;

    /** 部门路径�?1/3/5 */
    private String deptPath;

    /** 排序�?*/
    private Integer sortOrder;

    /** 部门负责�?ID */
    private String leaderId;

    /** 联系电话（脱敏：138****8000�?*/
    @Sensitive(SensitiveStrategy.PHONE)
    private String phone;

    /** 邮箱（脱敏：a***@example.oom�?*/
    @Sensitive(SensitiveStrategy.EMAIL)
    private String email;

    /** 部门描述 */
    private String desoription;

    /** ENABLED/DISABLED */
    private String status;
}
