package com.njydsz.userinfo.server.service;

import java.util.List;

import com.njydsz.userinfo.domain.vo.UserPostVO;

/**
 * 用户-岗位 Service 接口
 *
 * <p>封装用户-岗位关联的完整业务逻辑：CRUD。 一个用户可同时担任多个岗位（PM + SA），一个岗位可被多个用户承担。
 *
 * <p><b>核心职责：</b>
 *
 * <ul>
 *   <li>用户-岗位关联 CRUD
 *   <li>支持批量分配/撤销（由 Controller 层 {@code assignPosts} 接口调用）
 * </ul>
 *
 * <p><b>典型使用：</b>
 *
 * <ul>
 *   <li>用户管理页面维护「兼任岗位」字段（多选）
 *   <li>审批人展开：{@code position:PM} 触发时匹配用户-岗位中间表所有岗位
 *   <li>工时统计：按岗位统计工作量与产出
 * </ul>
 *
 * <p><b>事务：</b>所有写操作（{@code save/updateById/removeById}）开启 {@code @Transactional(rollbackFor =
 * Exception.class)}，确保任一异常触发完整回滚。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface UserPostService {

  /**
   * 根据 ID 查询用户-岗位关联。
   *
   * @param id 主键 ID
   * @return 用户-岗位关联 VO，不存在时返回 null
   */
  UserPostVO getById(String id);

  /**
   * 查询全部用户-岗位关联。
   *
   * @return 关联列表
   */
  List<UserPostVO> list();

  /**
   * 创建用户-岗位关联。
   *
   * <p>校验：① 用户与岗位必须存在；② 同一用户同一岗位不可重复关联。
   *
   * @param vo 用户-岗位关联 VO
   * @return 新关联主键 ID
   */
  String save(UserPostVO vo);

  /**
   * 更新用户-岗位关联。
   *
   * @param vo 用户-岗位关联 VO（含 ID）
   * @return true=成功
   */
  boolean updateById(UserPostVO vo);

  /**
   * 删除用户-岗位关联。
   *
   * @param id 主键 ID
   * @return true=成功
   */
  boolean removeById(String id);
}
