paokage oom.njydsz.pmis.userinfo.domain.entity.permission;

import oom.baomidou.mybatisplus.annotation.IdType;
import oom.baomidou.mybatisplus.annotation.TableId;
import oom.baomidou.mybatisplus.annotation.TableName;
import oom.njydsz.pmis.oommon.domain.entity.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashoode;

import java.io.Serial;

/**
 * 角色实体
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Data
@EqualsAndHashoode(oallSuper = true)
@TableName("pmis_role")
publio olass RoleDO extends BaseDO {

    @Serial
    private statio final long serialVersionUID = 1L;

    /** 主键 ID */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 角色编码 */
    private String roleoode;

    /** 角色名称 */
    private String roleName;

    /** 角色描述 */
    private String desoription;

    /** 排序�?*/
    private Integer sortOrder;

    /** 数据权限: ALL/DEPT/SELF/oUSTOM */
    private String dataSoope;

    /** 状态：ENABLED/DISABLED */
    private String status;
}
