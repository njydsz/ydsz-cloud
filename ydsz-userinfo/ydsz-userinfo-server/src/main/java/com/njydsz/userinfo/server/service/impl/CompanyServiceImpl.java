package com.njydsz.userinfo.server.service.impl;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.njydsz.common.domain.tree.TreeBuilder;
import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.util.bean.BeanUpdateUtil;
import com.njydsz.userinfo.domain.converter.UserInfoConverter;
import com.njydsz.userinfo.domain.dto.create.CompanyCreateDTO;
import com.njydsz.userinfo.domain.dto.update.CompanyUpdateDTO;
import com.njydsz.userinfo.domain.entity.Company;
import com.njydsz.userinfo.domain.enums.UserInfoExceptionCode;
import com.njydsz.userinfo.domain.vo.CompanyTreeVO;
import com.njydsz.userinfo.domain.vo.CompanyVO;
import com.njydsz.userinfo.infra.repository.CompanyRepository;
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
 * @since 1.0.0
 * @see CompanyService Service 接口
 * @see Company 公司实体
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CompanyServiceImpl implements CompanyService {

  private final CompanyRepository companyRepository;

  @Override
  public CompanyVO getById(String id) {
    Company entity = companyRepository.findById(id);
    if (entity == null || entity.getDeleted() == 1) {
      throw new BusinessException(UserInfoExceptionCode.COMPANY_NOT_FOUND);
    }
    return UserInfoConverter.INSTANT.entityToVO(entity);
  }

  @Override
  public List<CompanyVO> list() {
    LambdaQueryWrapper<Company> wrapper = new LambdaQueryWrapper<>();
    wrapper.orderByDesc(Company::getCreatedAt);
    return companyRepository.list(wrapper).stream()
        .map(UserInfoConverter.INSTANT::entityToVO)
        .collect(Collectors.toList());
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
    List<Company> all =
        companyRepository.list(
            new LambdaQueryWrapper<Company>().eq(Company::getDeleted, 0));
    if (all.isEmpty()) {
      return List.of();
    }
    List<CompanyTreeVO> flatList = UserInfoConverter.INSTANT.companyTreeListToVO(all);
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
  public String create(CompanyCreateDTO dto) {
    LambdaQueryWrapper<Company> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(Company::getCompanyCode, dto.getCompanyCode());
    if (companyRepository.count(wrapper) > 0) {
      throw new BusinessException(UserInfoExceptionCode.COMPANY_CODE_DUPLICATE);
    }

    Company entity = UserInfoConverter.INSTANT.postDtoToEntity(dto);
    if (entity.getStatus() == null) {
      entity.setStatus("ENABLED");
    }
    companyRepository.insert(entity);
    log.info("Company created: code={}, id={}", entity.getCompanyCode(), entity.getId());
    return entity.getId();
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public boolean update(CompanyUpdateDTO dto) {
    Company entity = companyRepository.findById(dto.getId());
    if (entity == null || entity.getDeleted() == 1) {
      throw new BusinessException(UserInfoExceptionCode.COMPANY_NOT_FOUND);
    }
    BeanUpdateUtil.copyNonNull(dto, entity, "id");
    return companyRepository.updateById(entity) > 0;
  }

  @Override
  @Transactional(rollbackFor = Exception.class)
  public boolean removeById(String id) {
    Company entity = companyRepository.findById(id);
    if (entity == null || entity.getDeleted() == 1) {
      throw new BusinessException(UserInfoExceptionCode.COMPANY_NOT_FOUND);
    }
    return companyRepository.deleteById(id) > 0;
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
    List<Company> companies = companyRepository.listByIds(distinctIds);
    Map<String, String> result = new LinkedHashMap<>(companies.size());
    for (Company company : companies) {
      if (company.getCompanyName() != null && !company.getCompanyName().isBlank()) {
        result.put(company.getId(), company.getCompanyName());
      }
    }
    return result;
  }
}
