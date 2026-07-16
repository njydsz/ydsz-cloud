package com.njydsz.userinfo.domain.vo;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import com.njydsz.userinfo.domain.entity.org.DepartmentDO;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 部门树节点 VO
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@Schema(description = "部门树节点")
public class DepartmentTreeVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "部门")
    private DepartmentDO department;

    @Schema(description = "子部门")
    private List<DepartmentTreeVO> children = new ArrayList<>();

    /**
     * 根据部门实体构建树节点（不含子节点）
     *
     * @param d 部门实体
     * @return 部门树节点
     */
    public static DepartmentTreeVO of(DepartmentDO d) {
        DepartmentTreeVO v = new DepartmentTreeVO();
        v.setDepartment(d);
        return v;
    }
}
