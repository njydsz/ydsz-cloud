package com.njydsz.userinfo.server.service;

import java.util.List;

import com.njydsz.userinfo.domain.entity.CompanyDept;

/**
 * 公司-部门 Service 接口
 *
 * <p>封装公司-部门关联的完整业务逻辑：CRUD。 一个公司可包含多个部门，一个部门可被多个公司共享（如「研发中心」归属集团总部与子公司）。
 *
 * <p><b>核心职责：</b>
 *
 * <ul>
 *   <li>公司-部门关联 CRUD
 *   <li>支持批量绑定/解绑（由 Controller 层维护）
 * </ul>
 *
 * <p><b>典型使用：</b>
 *
 * <ul>
 *   <li>公司管理页面维护「包含部门」字段（多选）
 *   <li>部门管理页面维护「所属公司」字段（多选）
 *   <li>跨公司调岗场景：通过维护本表实现部门归属调整
 * </ul>
 *
 * <p><b>事务：</b>所有写操作（{@code save/updateById/removeById}）开启 {@code @Transactional(rollbackFor =
 * Exception.class)}，确保任一异常触发完整回滚。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see CompanyDept 公司-部门关联实体
 * @see com.njydsz.userinfo.domain.entity.Company 公司实体
 * @see com.njydsz.userinfo.domain.entity.Department 部门实体
 */
public interface CompanyDeptService {

  /**
   * 根据 ID 查询公司-部门关联。
   *
   * @param id 主键 ID
   * @return 公司-部门关联实体，不存在时返回 null
   */
  CompanyDept getById(String id);

  /**
   * 查询全部公司-部门关联。
   *
   * @return 关联列表
   */
  List<CompanyDept> list();

  /**
   * 创建公司-部门关联。
   *
   * <p>建议业务层校验：同一公司同一部门不可重复关联。
   *
   * @param entity 公司-部门关联实体
   * @return 新关联主键 ID
   */
  String save(CompanyDept entity);

  /**
   * 更新公司-部门关联。
   *
   * @param entity 公司-部门关联实体（含 ID）
   * @return true=成功
   */
  boolean updateById(CompanyDept entity);

  /**
   * 删除公司-部门关联。
   *
   * @param id 主键 ID
   * @return true=成功
   */
  boolean removeById(String id);
}
