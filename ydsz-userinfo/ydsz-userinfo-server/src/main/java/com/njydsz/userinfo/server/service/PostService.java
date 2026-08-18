package com.njydsz.userinfo.server.service;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import com.njydsz.userinfo.domain.dto.PostDTO;
import com.njydsz.userinfo.domain.vo.PostVO;

/**
 * 岗位 Service 接口
 *
 * <p>封装岗位的完整业务逻辑：CRUD、跨服务名称富化。 岗位是「职责维度」，描述用户做什么事（如 PM、DEV、QA），区别于角色（权限维度）。
 *
 * <p><b>核心职责：</b>
 *
 * <ul>
 *   <li>岗位 CRUD
 *   <li>岗位全量列表查询（按 {@code sortOrder} 升序）
 *   <li>跨服务名称富化（{@code batchNamesByIds}，供 NameAssembler 调用）
 * </ul>
 *
 * <p><b>典型使用：</b>
 *
 * <ul>
 *   <li>前端岗位管理页面通过 {@code /api/v1/PostDO/list} 加载岗位列表
 *   <li>用户列表通过 {@code NameAssembler} 自动富化 {@code postName} 字段
 *   <li>审批人展开：{@code position:PM} 触发时按 {@code postCode} 匹配
 * </ul>
 *
 * <p><b>事务：</b>所有写操作（{@code create/update/removeById}）开启 {@code @Transactional(rollbackFor =
 * Exception.class)}，确保任一异常触发完整回滚。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see com.njydsz.userinfo.infra.entity.PostDO 岗位实体
 * @see com.njydsz.userinfo.web.controller.PostController 岗位 Controller
 */
public interface PostService {

  /**
   * 根据 ID 查询岗位详情。
   *
   * @param id 岗位 ID
   * @return 岗位 VO，不存在时返回 null
   */
  PostVO getById(String id);

  /**
   * 查询全部岗位列表（按 {@code sortOrder} 升序）。
   *
   * @return 岗位 VO 列表
   */
  List<PostVO> list();

  /**
   * 创建岗位。
   *
   * <p>校验：{@code postCode} 唯一性。
   *
   * @param dto 岗位 DTO
   * @return 新岗位 ID
   */
  String create(PostDTO dto);

  /**
   * 更新岗位。
   *
   * @param dto 岗位 DTO（含 ID）
   * @return true=成功
   */
  boolean update(PostDTO dto);

  /**
   * 删除岗位（逻辑删除）。
   *
   * <p>校验：仍有用户关联时禁止删除。
   *
   * @param id 岗位 ID
   * @return true=成功
   */
  boolean removeById(String id);

  /**
   * 批量查询岗位 ID → 岗位名映射（供 NameAssembler 跨服务富化 postName 字段）。
   *
   * <p>实现：单条 SQL {@code SELECT id, post_name FROM ydsz_post WHERE id IN (...)}，
   * 一次往返拿到全部结果。已逻辑删除的岗位不会出现在结果中。
   *
   * @param postIds 岗位 ID 集合（允许 null / 空，返回空 Map）
   * @return postId → postName 映射；未命中的 postId 不出现在 Map 中
   */
  Map<String, String> batchNamesByIds(Collection<String> postIds);
}
