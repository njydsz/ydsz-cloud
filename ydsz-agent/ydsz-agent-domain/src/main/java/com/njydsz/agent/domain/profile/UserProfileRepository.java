package com.njydsz.agent.domain.profile;

import java.util.List;
import java.util.Optional;

/**
 * 用户画像 Repository 接口。
 *
 * <p>封装 {@code ydsz_agt_user_profile} 表的数据库访问，提供用户画像的持久化操作。
 * 由基础设施层实现。</p>
 *
 * @author ydsz-agent
 * @since 26.09.07
 */
public interface UserProfileRepository {

    /**
     * 根据用户 ID 查询画像。
     *
     * @param userId 用户 ID
     * @return 用户画像；不存在返回 {@code Optional.empty()}
     */
    Optional<UserProfile> findByUserId(String userId);

    /**
     * 保存用户画像（INSERT 或 UPSERT）。
     *
     * <p>若该用户画像已存在则更新，否则插入新记录。</p>
     *
     * @param profile 用户画像实体
     */
    void save(UserProfile profile);

    /**
     * 更新用户画像（按 userId 主键更新）。
     *
     * @param profile 用户画像实体
     */
    void update(UserProfile profile);

    /**
     * 查询活跃用户画像（按最近交互时间降序）。
     *
     * <p>用于后台分析和画像批量刷新。</p>
     *
     * @param limit 返回数量上限
     * @return 活跃用户画像列表
     */
    List<UserProfile> findActiveProfiles(int limit);

    /**
     * 根据用户 ID 删除画像。
     *
     * @param userId 用户 ID
     */
    void deleteByUserId(String userId);
}
