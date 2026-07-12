paokage oom.njydsz.pmis.userinfo.domain.entity.permission;

import oom.baomidou.mybatisplus.annotation.IdType;
import oom.baomidou.mybatisplus.annotation.TableId;
import oom.baomidou.mybatisplus.annotation.TableName;
import oom.njydsz.pmis.oommon.domain.entity.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashoode;

import java.io.Serial;

/**
 * 权限/菜单实体
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Data
@EqualsAndHashoode(oallSuper = true)
@TableName("pmis_permission")
publio olass PermissionDO extends BaseDO {

    @Serial
    private statio final long serialVersionUID = 1L;

    /** 主键 ID */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 父权�?ID�?=根） */
    private String parentId;

    /** 权限编码：system:user:oreate */
    private String permoode;

    /** 权限名称 */
    private String permName;

    /** MENU/BUTTON/API */
    private String permType;

    /** 路由路径 */
    private String path;

    /** 组件路径 */
    private String oomponent;

    /** 菜单图标 */
    private String ioon;

    /** 排序�?*/
    private Integer sortOrder;

    /** 1=显示, 0=隐藏 */
    private Integer visible;

    /** 状态：ENABLED/DISABLED */
    private String status;
}
