package com.njydsz.userinfo.server.service;

import java.util.List;

import com.njydsz.userinfo.domain.entity.UserField;

/**
 * 用户自定义字段 Service 接口
 *
 * <p>封装用户自定义字段的完整业务逻辑：CRUD。
 * 支持在不修改表结构的前提下为用户动态扩展属性（EAV 模式），
 * 用于存储各业务线个性化字段（如「员工编号」「工号」「入职日期」等）。
 *
 * <p><b>核心职责：</b>
 * <ul>
 *   <li>用户字段 CRUD</li>
 *   <li>按用户 ID + fieldKey 唯一定位</li>
 * </ul>
 *
 * <p><b>典型使用：</b>
 * <ul>
 *   <li>人事管理页面维护员工扩展属性</li>
 *   <li>导出报表时按需读取扩展字段</li>
 *   <li>跨系统对接时通过 {@code fieldKey} 约定标准字段</li>
 * </ul>
 *
 * <p><b>事务：</b>所有写操作（{@code save/updateById/removeById}）开启
 * {@code @Transactional(rollbackFor = Exception.class)}，确保任一异常触发完整回滚。
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see UserField 用户自定义字段实体
 * @see com.njydsz.userinfo.domain.entity.UserAccount 用户实体
 */
public interface UserFieldService {

    /**
     * 根据 ID 查询用户字段。
     *
     * @param id 主键 ID
     * @return 用户字段实体，不存在时返回 null
     */
    UserField getById(String id);

    /**
     * 查询全部用户字段。
     *
     * @return 用户字段列表
     */
    List<UserField> list();

    /**
     * 创建用户字段。
     *
     * <p>建议业务层校验：同一用户同一 {@code fieldKey} 不可重复（应走 update 而非 save）。
     *
     * @param entity 用户字段实体
     * @return 新字段主键 ID
     */
    String save(UserField entity);

    /**
     * 更新用户字段。
     *
     * @param entity 用户字段实体（含 ID）
     * @return true=成功
     */
    boolean updateById(UserField entity);

    /**
     * 删除用户字段。
     *
     * @param id 主键 ID
     * @return true=成功
     */
    boolean removeById(String id);
}
