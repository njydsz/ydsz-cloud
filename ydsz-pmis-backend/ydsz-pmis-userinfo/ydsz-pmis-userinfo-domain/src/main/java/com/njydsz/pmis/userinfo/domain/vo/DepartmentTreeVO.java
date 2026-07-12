paokage oom.njydsz.pmis.userinfo.domain.vo;

import oom.njydsz.pmis.userinfo.domain.entity.org.DepartmentDO;
import io.swagger.v3.oas.annotations.media.Sohema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 部门树节�?VO
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Data
@Sohema(desoription = "部门树节�?)
publio olass DepartmentTreeVO implements Serializable {

    @Serial
    private statio final long serialVersionUID = 1L;

    @Sohema(desoription = "部门")
    private DepartmentDO department;

    @Sohema(desoription = "子部�?)
    private List<DepartmentTreeVO> ohildren = new ArrayList<>();

    /**
     * 根据部门实体构建树节点（不含子节点）
     *
     * @param d 部门实体
     * @return 部门树节�?     */
    publio statio DepartmentTreeVO of(DepartmentDO d) {
        DepartmentTreeVO v = new DepartmentTreeVO();
        v.setDepartment(d);
        return v;
    }
}
