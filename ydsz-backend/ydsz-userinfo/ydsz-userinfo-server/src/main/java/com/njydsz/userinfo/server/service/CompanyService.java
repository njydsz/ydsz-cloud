package com.njydsz.userinfo.server.service;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import com.njydsz.common.domain.service.BaseCrudService;
import com.njydsz.userinfo.domain.dto.CompanySaveDTO;
import com.njydsz.userinfo.domain.entity.CompanyDO;
import com.njydsz.userinfo.domain.query.CompanyPageQuery;
import com.njydsz.userinfo.domain.vo.CompanyVO;

/**
 * 公司 Service 接口。
 *
 * <p>继承通用 CRUD 能力，并提供全量列表查询、批量名称查询等扩展能力。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface CompanyService extends BaseCrudService<CompanyDO, CompanySaveDTO, CompanyVO, CompanyPageQuery, String> {

    /**
     * 查询全部未删除公司列表（按创建时间降序）。
     *
     * @return 公司视图对象列表
     */
    List<CompanyVO> list();

    /**
     * 批量查询公司 ID → 公司名映射（供 NameAssembler 跨服务富化 companyName 字段）。
     *
     * @param companyIds 公司 ID 集合（允许 null / 空，返回空 Map）
     * @return companyId → companyName 映射；未命中的 companyId 不出现在 Map 中
     */
    Map<String, String> batchNamesByIds(Collection<String> companyIds);
}