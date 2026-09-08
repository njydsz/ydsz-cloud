package com.njydsz.agent.infra.profile;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.njydsz.agent.domain.profile.UserProfile;
import com.njydsz.agent.domain.profile.UserProfileRepository;

/**
 * 用户画像 Repository 实现。
 *
 * <p>基于 MyBatis-Plus 实现 {@link UserProfileRepository} 接口，
 * 以 userId 作为主键（业务 ID，非自增）。</p>
 *
 * @author ydsz-agent
 * @since 26.09.07
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class UserProfileRepositoryImpl implements UserProfileRepository {

    private final UserProfileMapper userProfileMapper;

    /**
     * 根据用户 ID 查找画像。
     *
     * @param userId 用户 ID
     * @return 找到时返回包含画像的 Optional；不存在时返回空 Optional
     */
    @Override
    public Optional<UserProfile> findByUserId(String userId) {
        return Optional.ofNullable(userProfileMapper.selectById(userId));
    }

    /**
     * 保存或更新用户画像（UPSERT 语义）。
     *
     * <p>先查询是否已存在：不存在则 INSERT，存在则 UPDATE。写入前自动填充创建/更新时间戳。
     *
     * @param profile 用户画像实体（为 {@code null} 时直接返回）
     */
    @Override
    public void save(UserProfile profile) {
        if (profile == null) {
            return;
        }
        // UPSERT 语义：先查后决定 insert 或 update
        UserProfile existing = userProfileMapper.selectById(profile.getUserId());
        prepareTimestamps(profile);
        if (existing == null) {
            userProfileMapper.insert(profile);
            log.debug("用户画像已创建: userId={}", profile.getUserId());
        } else {
            userProfileMapper.updateById(profile);
            log.debug("用户画像已更新(upsert): userId={}", profile.getUserId());
        }
    }

    /**
     * 更新用户画像（直接按主键更新，不的存在性检查）。
     *
     * @param profile 用户画像实体（为 {@code null} 时直接返回）
     */
    @Override
    public void update(UserProfile profile) {
        if (profile == null) {
            return;
        }
        prepareTimestamps(profile);
        userProfileMapper.updateById(profile);
    }

    /**
     * 查询最近活跃的用户画像（按最后交互时间降序）。
     *
     * @param limit 返回条数上限（≤0 时返回空列表）
     * @return 用户画像列表
     */
    @Override
    public List<UserProfile> findActiveProfiles(int limit) {
        if (limit <= 0) {
            return Collections.emptyList();
        }
        return userProfileMapper.selectList(
                new LambdaQueryWrapper<UserProfile>()
                        .orderByDesc(UserProfile::getLastInteractionAt)
                        .last("LIMIT " + limit));
    }

    /**
     * 根据用户 ID 删除画像。
     *
     * @param userId 用户 ID
     */
    @Override
    public void deleteByUserId(String userId) {
        userProfileMapper.deleteById(userId);
    }

    /**
     * 填充创建/更新时间戳。
     *
     * @param profile 用户画像实体
     */
    private void prepareTimestamps(UserProfile profile) {
        LocalDateTime now = LocalDateTime.now();
        if (profile.getCreatedAt() == null) {
            profile.setCreatedAt(now);
        }
        profile.setUpdatedAt(now);
    }
}
