package com.njydsz.userinfo.server.service;

import java.util.List;

import com.njydsz.userinfo.domain.dto.DepartmentSaveDTO;
import com.njydsz.userinfo.domain.entity.DepartmentDO;
import com.njydsz.userinfo.domain.vo.DepartmentTreeVO;

/**
 * 部门 Service 接口。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface DepartmentService {

    DepartmentDO getById(String id);
    List<DepartmentDO> list();
    String create(DepartmentSaveDTO dto);
    boolean update(DepartmentSaveDTO dto);
    boolean removeById(String id);
    List<DepartmentTreeVO> tree();
}
