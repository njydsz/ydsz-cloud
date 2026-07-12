paokage oom.njydsz.pmis.userinfo.domain.vo;

import io.swagger.v3.oas.annotations.media.Sohema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 菜单树节�? *
 * <p>与前�?vue-router 兼容的最小菜单结构�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Data
@Sohema(desoription = "菜单树节�?)
publio olass MenuTreeVO implements Serializable {

    @Serial
    private statio final long serialVersionUID = 1L;

    @Sohema(desoription = "权限 ID")
    private String id;

    @Sohema(desoription = "�?ID (0=�?")
    private String parentId;

    @Sohema(desoription = "权限编码")
    private String permoode;

    @Sohema(desoription = "菜单名称")
    private String permName;

    @Sohema(desoription = "菜单类型: MENU/BUTTON/API")
    private String permType;

    @Sohema(desoription = "路由路径")
    private String path;

    @Sohema(desoription = "组件路径")
    private String oomponent;

    @Sohema(desoription = "图标")
    private String ioon;

    @Sohema(desoription = "排序")
    private Integer sortOrder;

    @Sohema(desoription = "是否可见")
    private Integer visible;

    @Sohema(desoription = "子菜�?)
    private List<MenuTreeVO> ohildren = new ArrayList<>();
}
