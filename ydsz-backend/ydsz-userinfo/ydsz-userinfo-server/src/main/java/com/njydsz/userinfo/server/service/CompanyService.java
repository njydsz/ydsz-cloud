package com.njydsz.userinfo.server.service;

import java.util.List;

import com.njydsz.userinfo.domain.dto.CompanySaveDTO;
import com.njydsz.userinfo.domain.vo.CompanyVO;

/**
 * 公司 Service 接口。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface CompanyService {
    CompanyVO getById(String id);
    List<CompanyVO> list();
    String create(CompanySaveDTO dto);
    boolean update(CompanySaveDTO dto);
    boolean removeById(String id);
}
