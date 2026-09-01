package com.njydsz.agent.domain.skill;

import java.util.List;
import java.util.Optional;

/**
 * Skill 经验仓储接口。
 *
 * <p>定义 SkillLesson 的持久化操作。</p>
 *
 * @author ydsz-agent
 * @since 26.09.01
 */
public interface SkillLessonRepository {

    /**
     * 保存经验。
     *
     * @param lesson 经验对象
     * @return 保存后的经验实例
     */
    SkillLesson save(SkillLesson lesson);

    /**
     * 根据 ID 查询经验。
     *
     * @param lessonId 经验 ID
     * @return 经验实例（可能为空）
     */
    Optional<SkillLesson> findById(String lessonId);

    /**
     * 根据 Skill 代码查询经验列表。
     *
     * @param tenantId  租户 ID
     * @param skillCode Skill 代码
     * @return 经验列表
     */
    List<SkillLesson> findBySkillCode(String tenantId, String skillCode);

    /**
     * 根据经验类型查询。
     *
     * @param tenantId    租户 ID
     * @param skillCode   Skill 代码
     * @param lessonType  经验类型
     * @return 经验列表
     */
    List<SkillLesson> findBySkillAndType(String tenantId, String skillCode, LessonType lessonType);

    /**
     * 查询最常用的经验（按使用次数排序）。
     *
     * @param tenantId  租户 ID
     * @param skillCode Skill 代码
     * @param limit     返回数量限制
     * @return 经验列表
     */
    List<SkillLesson> findMostUsed(String tenantId, String skillCode, int limit);

    /**
     * 查询高置信度的经验。
     *
     * @param tenantId       租户 ID
     * @param skillCode      Skill 代码
     * @param minConfidence  最低置信度
     * @return 经验列表
     */
    List<SkillLesson> findByMinConfidence(String tenantId, String skillCode, int minConfidence);

    /**
     * 删除经验。
     *
     * @param lessonId 经验 ID
     */
    void delete(String lessonId);

    /**
     * 统计 Skill 的经验数量。
     *
     * @param tenantId  租户 ID
     * @param skillCode Skill 代码
     * @return 经验数量
     */
    long countBySkill(String tenantId, String skillCode);
}
