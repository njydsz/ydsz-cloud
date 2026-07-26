package com.njydsz.userinfo.server.service;

import java.util.List;

import com.njydsz.userinfo.domain.dto.DepartmentSaveDTO;
import com.njydsz.userinfo.domain.vo.DepartmentTreeVO;
import com.njydsz.userinfo.domain.vo.DepartmentVO;

/**
 * 部门 Service 接口。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface DepartmentService {

    DepartmentVO getById(String id);
    List<DepartmentVO> list();
    String create(DepartmentSaveDTO dto);
    boolean update(DepartmentSaveDTO dto);
    boolean removeById(String id);
    List<DepartmentTreeVO> tree();
}
