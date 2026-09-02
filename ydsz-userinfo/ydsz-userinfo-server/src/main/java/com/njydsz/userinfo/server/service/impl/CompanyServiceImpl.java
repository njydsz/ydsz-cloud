package com.njydsz.userinfo.server.service.impl;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.njydsz.common.domain.tree.TreeBuilder;
import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.userinfo.domain.dto.CompanyDTO;
import com.njydsz.userinfo.domain.enums.UserInfoExceptionCode;
import com.njydsz.userinfo.domain.query.CompanyPageQuery;
import com.njydsz.userinfo.domain.repository.CompanyRepository;
import com.njydsz.userinfo.domain.vo.CompanyTreeVO;
import com.njydsz.userinfo.domain.vo.CompanyVO;
import com.njydsz.userinfo.server.service.CompanyService;

/**
 * 公司 Service 实现
 *
 * <p>实现 {@link CompanyService} 接口，封装公司的完整业务逻辑：CRUD、{@code companyCode} 唯一性校验、 跨服务名称富化。
 *
 * <p><b>核心职责：</b>
 *
 * <ul>
 *   <li>公司 CRUD（含 {@code companyCode} 唯一性校验）
 *   <li>公司全量列表查询（按创建时间降序）
 *   <li>公司树形结构查询（使用 {@link TreeBuilder#buildSimple} 构建，自动填充 level/path 元数据）
 *   <li>跨服务名称富化（{@code batchNamesByIds}，供 NameAssembler 调用）
 * </ul>
 *
 * <p><b>事务：</b>所有写操作开启 {@code @Transactional(rollbackFor = Exception.class)}。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see CompanyService Service 接口
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CompanyServiceImpl implements CompanyService {

  private final CompanyRepository companyRepository;

  @Override
  public CompanyVO getById(String id) {
    return companyRepository.findById(id)
        .orElseThrow(() -> new BusinessException(UserInfoExceptionCode.COMPANY_NOT_FOUND));
  }

  @Override
  public List<CompanyVO> list() {
    CompanyPageQuery query = new CompanyPageQuery();
    return companyRepository.list(query);
  }

  /**
   * {@inheritDoc}
   *
   * <p>一次性查询全表后在内存中构建树，使用 {@link TreeBuilder#buildSimple} O(n) 算法， 自动填充 {@code level}/{@code path} 元数据。
   * 公司数据量小（百级别），全量加载可接受。
   *
   * @return 公司树形结构根节点列表，无数据返回空列表
   */
  @Override
  public List<CompanyTreeVO> tree() {
    List<CompanyVO> all = companyRepository.list(new CompanyPageQuery());
    if (all.isEmpty()) {
      return List.of();
    }
    List<CompanyTreeVO> flatList = all.stream()
        .map(vo -> {
          CompanyTreeVO treeVO = new CompanyTreeVO();
          treeVO.setId(vo.getId());
          treeVO.setCompanyName(vo.getCompanyName());
          treeVO.setCompanyCode(vo.getCompanyCode());
          treeVO.setParentId(vo.getParentId());
          treeVO.setContactPerson(vo.getContactPerson());
          treeVO.setContactPhone(vo.getContactPhone());
          treeVO.setAddress(vo.getAddress());
          treeVO.setStatus(vo.getStatus());
          return treeVO;
        })
        .collect(Collectors.toList());
    return TreeBuilder.buildSimple(
        flatList,
        CompanyTreeVO::getId,
        CompanyTreeVO::getParentId,
        CompanyTreeVO::setChildren,
        null,
        CompanyTreeVO::setLevel,
        CompanyTreeVO::setPath);
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public String create(CompanyDTO dto) {
    CompanyPageQuery query = new CompanyPageQuery();
    query.setCompanyCode(dto.getCompanyCode());
    if (companyRepository.countByQuery(query) > 0) {
      throw new BusinessException(UserInfoExceptionCode.COMPANY_CODE_DUPLICATE);
    }
    CompanyVO vo = companyRepository.save(dto);
    log.info("Company created: code={}, id={}", dto.getCompanyCode(), vo.getId());
    return vo.getId();
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public boolean update(CompanyDTO dto) {
    companyRepository.findById(dto.getId())
        .orElseThrow(() -> new BusinessException(UserInfoExceptionCode.COMPANY_NOT_FOUND));
    CompanyVO vo = companyRepository.save(dto);
    return vo != null;
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public boolean removeById(String id) {
    companyRepository.findById(id)
        .orElseThrow(() -> new BusinessException(UserInfoExceptionCode.COMPANY_NOT_FOUND));
    return companyRepository.deleteById(id);
  }

  @Override
  public Map<String, String> batchNamesByIds(Collection<String> companyIds) {
    if (companyIds == null || companyIds.isEmpty()) {
      return Collections.emptyMap();
    }
    List<String> distinctIds =
        companyIds.stream()
            .filter(id -> id != null && !id.isBlank())
            .distinct()
            .collect(Collectors.toList());
    if (distinctIds.isEmpty()) {
      return Collections.emptyMap();
    }
    List<CompanyVO> companies = companyRepository.listByIds(distinctIds);
    Map<String, String> result = new LinkedHashMap<>(companies.size());
    for (CompanyVO company : companies) {
      if (company.getCompanyName() != null && !company.getCompanyName().isBlank()) {
        result.put(company.getId(), company.getCompanyName());
      }
    }
    return result;
  }
}
