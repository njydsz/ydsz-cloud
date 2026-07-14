ackage com.njydsz.pmis.common.domain.entity;

/**
 * 乐观锁版本号接口
 *
 * <p>具备此接口的实体在执行 UPDATE 时会自动带上 {@code WHERE revision = oldRevision}，
 * 防止并发覆盖更新。配合 MyBatis-Plus {@code @Version} 注解使用。
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 */
public interface Versionable {

    /**
     * 获取乐观锁版本号
     *
     * @return 版本号
     */
    Integer getRevision();

    /**
     * 设置乐观锁版本号
     *
     * @param revision 版本号
     */
    void setRevision(Integer revision);
}
