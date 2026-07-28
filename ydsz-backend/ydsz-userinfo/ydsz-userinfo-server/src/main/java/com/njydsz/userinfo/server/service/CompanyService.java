package com.njydsz.userinfo.server.service;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import com.njydsz.common.domain.service.BaseCrudService;
import com.njydsz.userinfo.domain.dto.post.CompanyPostDTO;
import com.njydsz.userinfo.domain.dto.put.CompanyPutDTO;
import com.njydsz.userinfo.domain.entity.Company;
import com.njydsz.userinfo.domain.query.CompanyPageQuery;
import com.njydsz.userinfo.domain.vo.CompanyVO;

/**
 * 公司 Service 接口
 *
 * <p>封装公司的完整业务逻辑：CRUD、跨服务名称富化。
 * 继承 {@link BaseCrudService} 获取标准 CRUD 能力，新增全量列表与名称批量查询能力。
 *
 * <p><b>核心职责：</b>
 * <ul>
 *   <li>公司 CRUD（继承自 {@link BaseCrudService}）</li>
 *   <li>公司全量列表查询（{@code list}，按创建时间降序）</li>
 *   <li>跨服务名称富化（{@code batchNamesByIds}，供 NameAssembler 调用）</li>
 * </ul>
 *
 * <p><b>多公司架构：</b>支持集团-子公司多级架构（{@code parentId="0"} = 顶级公司），
 * 一个公司可包含多个部门（通过 {@link com.njydsz.userinfo.domain.entity.CompanyDept} 维护）。
 *
 * <p><b>事务：</b>所有写操作（{@code create/update/removeById}）开启
 * {@code @Transactional(rollbackFor = Exception.class)}，确保任一异常触发完整回滚。
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see Company 公司实体
 * @see com.njydsz.userinfo.web.controller.CompanyController 公司 Controller
 */
public interface CompanyService extends BaseCrudService<Company, CompanySaveDTO, CompanyVO, CompanyPageQuery, String> {

    /**
     * 查询全部未删除公司列表（按创建时间降序）。
     *
     * <p>适用于公司选择下拉框、租户注册时选择所属公司等场景。
     *
     * @return 公司视图对象列表（按创建时间降序）
     */
    List<CompanyVO> list();

    /**
     * 批量查询公司 ID → 公司名映射（供 NameAssembler 跨服务富化 companyName 字段）。
     *
     * <p>实现：单条 SQL {@code SELECT id, company_name FROM ydsz_company WHERE id IN (...)}，
     * 一次往返拿到全部结果。已逻辑删除的公司不会出现在结果中。
     *
     * @param companyIds 公司 ID 集合（允许 null / 空，返回空 Map）
     * @return companyId → companyName 映射；未命中的 companyId 不出现在 Map 中
     */
    Map<String, String> batchNamesByIds(Collection<String> companyIds);
}
