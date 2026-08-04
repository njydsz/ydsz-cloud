package com.remisoft.userinfo.server.service;

import java.util.List;

import com.remisoft.userinfo.domain.entity.UserPost;

/**
 * 用户-岗位 Service 接口
 *
 * <p>封装用户-岗位关联的完整业务逻辑：CRUD。
 * 一个用户可同时担任多个岗位（PM + SA），一个岗位可被多个用户承担。
 *
 * <p><b>核心职责：</b>
 * <ul>
 *   <li>用户-岗位关联 CRUD</li>
 *   <li>支持批量分配/撤销（由 Controller 层 {@code assignPosts} 接口调用）</li>
 * </ul>
 *
 * <p><b>典型使用：</b>
 * <ul>
 *   <li>用户管理页面维护「兼任岗位」字段（多选）</li>
 *   <li>审批人展开：{@code position:PM} 触发时匹配 {@code UserPost} 中间表所有岗位</li>
 *   <li>工时统计：按岗位统计工作量与产出</li>
 * </ul>
 *
 * <p><b>事务：</b>所有写操作（{@code save/updateById/removeById}）开启
 * {@code @Transactional(rollbackFor = Exception.class)}，确保任一异常触发完整回滚。
 *
 * @author remi-team
 * @since 1.0.0
 *
 * @see UserPost 用户-岗位关联实体
 * @see com.remisoft.userinfo.domain.entity.UserAccount 用户实体
 * @see com.remisoft.userinfo.domain.entity.Post 岗位实体
 */
public interface UserPostService {

    /**
     * 根据 ID 查询用户-岗位关联。
     *
     * @param id 主键 ID
     * @return 用户-岗位关联实体，不存在时返回 null
     */
    UserPost getById(String id);

    /**
     * 查询全部用户-岗位关联。
     *
     * @return 关联列表
     */
    List<UserPost> list();

    /**
     * 创建用户-岗位关联。
     *
     * <p>校验：① 用户与岗位必须存在；② 同一用户同一岗位不可重复关联。
     *
     * @param entity 用户-岗位关联实体
     * @return 新关联主键 ID
     */
    String save(UserPost entity);

    /**
     * 更新用户-岗位关联。
     *
     * @param entity 用户-岗位关联实体（含 ID）
     * @return true=成功
     */
    boolean updateById(UserPost entity);

    /**
     * 删除用户-岗位关联。
     *
     * @param id 主键 ID
     * @return true=成功
     */
    boolean removeById(String id);
}
