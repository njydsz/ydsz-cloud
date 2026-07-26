package com.njydsz.userinfo.server.service;

import java.util.Collection;
import java.util.List;
import java.util.Map;

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

    /**
     * 批量查询公司 ID → 公司名映射（供 NameAssembler 跨服务富化 companyName 字段）。
     *
     * @param companyIds 公司 ID 集合（允许 null / 空，返回空 Map）
     * @return companyId → companyName 映射；未命中的 companyId 不出现在 Map 中
     */
    Map<String, String> batchNamesByIds(Collection<String> companyIds);
}
