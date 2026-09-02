package com.njydsz.userinfo.server.service.impl;

import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.njydsz.userinfo.domain.dto.CompanyDeptDTO;
import com.njydsz.userinfo.domain.repository.CompanyDeptRepository;
import com.njydsz.userinfo.domain.vo.CompanyDeptVO;
import com.njydsz.userinfo.server.service.CompanyDeptService;

/**
 * 公司-部门关联服务实现。
 *
 * <p>维护公司-部门的多对多关联 ({@code ydsz_org_company_dept})：一个部门可隶属多个公司，
 *
 * <p>一个公司可包含多个部门。用于跨公司组织架构展示与权限合并。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CompanyDeptServiceImpl implements CompanyDeptService {

  private final CompanyDeptRepository companyDeptRepository;

  @Override
  public CompanyDeptVO getById(String id) {
    return companyDeptRepository.findById(id).orElse(null);
  }

  @Override
  public List<CompanyDeptVO> list() {
    return companyDeptRepository.list();
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public String save(CompanyDeptDTO dto) {
    CompanyDeptVO vo = companyDeptRepository.save(dto);
    return vo.getId();
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public boolean updateById(CompanyDeptDTO dto) {
    CompanyDeptVO vo = companyDeptRepository.save(dto);
    return vo != null;
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public boolean removeById(String id) {
    return companyDeptRepository.deleteById(id);
  }
}
