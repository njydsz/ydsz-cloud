package com.njydsz.userinfo.server.service;

import java.util.List;
import com.njydsz.userinfo.domain.entity.UserDept;

/**
 * 用户-部门 Service 接口
 *
 * <p>封装用户-部门关联的完整业务逻辑：CRUD、主部门唯一性管理。
 * 一个用户可属于多个部门（兼岗），但只能有 1 个主部门。
 *
 * <p><b>核心职责：</b>
 * <ul>
 *   <li>用户-部门关联 CRUD</li>
 *   <li>主部门唯一性管理（同一用户仅 1 个主部门，事务保证）</li>
 * </ul>
 *
 * <p><b>典型使用：</b>
 * <ul>
 *   <li>用户管理页面维护「所属部门」字段（支持多选 + 标记主部门）</li>
 *   <li>审批人展开：{@code dept:xxx} 触发时匹配 {@code UserDept} 中间表所有部门</li>
 *   <li>数据权限：{@code Role.dataScope} 配合部门树实现隔离</li>
 * </ul>
 *
 * <p><b>事务：</b>所有写操作（{@code save/updateById/removeById}）开启
 * {@code @Transactional(rollbackFor = Exception.class)}，确保任一异常触发完整回滚。
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see UserDept 用户-部门关联实体
 * @see com.njydsz.userinfo.domain.entity.UserAccount 用户实体
 * @see com.njydsz.userinfo.domain.entity.Department 部门实体
 */
public interface UserDeptService {

    /**
     * 根据 ID 查询用户-部门关联。
     *
     * @param id 主键 ID
     * @return 用户-部门关联实体，不存在时返回 null
     */
    UserDept getById(String id);

    /**
     * 查询全部用户-部门关联。
     *
     * @return 关联列表
     */
    List<UserDept> list();

    /**
     * 创建用户-部门关联。
     *
     * <p>校验：① 用户与部门必须存在；② 设为主部门时取消该用户其它主部门（事务内）。
     *
     * @param entity 用户-部门关联实体
     * @return 新关联主键 ID
     */
    String save(UserDept entity);

    /**
     * 更新用户-部门关联。
     *
     * <p>设为主部门时取消该用户其它主部门（事务内）。
     *
     * @param entity 用户-部门关联实体（含 ID）
     * @return true=成功
     */
    boolean updateById(UserDept entity);

    /**
     * 删除用户-部门关联。
     *
     * @param id 主键 ID
     * @return true=成功
     */
    boolean removeById(String id);
}
