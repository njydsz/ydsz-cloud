package com.njydsz.common.domain.entity;

/**
 * 乐观锁版本接口
 *
 * <p>具备此接口的实体在执行 UPDATE 时会自动带上 {@code WHERE revision = oldRevision}，
 * 防止并发覆盖更新。配合 MyBatis-Plus {@code @Version} 注解使用。
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 */
public interface Versionable {

    /**
     * 获取乐观锁版本
     *
     * @return 版本
     */
    Integer getRevision();

    /**
     * 设置乐观锁版本
     *
     * @param revision 版本
     */
    void setRevision(Integer revision);
}
