package com.njydsz.agent.server.skill;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import com.njydsz.agent.domain.skill.LessonType;
import com.njydsz.agent.domain.skill.SkillLesson;
import com.njydsz.agent.domain.skill.SkillLessonRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * Skill 经验记录器。
 *
 * <p>核心职责：
 * <ul>
 *   <li>记录 Skill 执行过程中产生的经验教训</li>
 *   <li>查询和检索相关经验</li>
 *   <li>管理经验的置信度和使用统计</li>
 *   <li>将经验注入到 Skill 执行上下文中</li>
 * </ul>
 *
 * <p>借鉴 MateClaw 的 LESSONS 设计，实现知识的持续积累和复用。</p>
 *
 * @author ydsz-agent
 * @since 1.0.0
 */
@Slf4j
public class SkillLessonRecorder {

    private final SkillLessonRepository lessonRepository;

    /** 单 Skill 最大经验数量 */
    private static final int MAX_LESSONS_PER_SKILL = 100;

    /** 默认最低置信度阈值 */
    private static final int DEFAULT_MIN_CONFIDENCE = 60;

    /** 注入上下文的最大经验数 */
    private static final int MAX_INJECTED_LESSONS = 5;

    public SkillLessonRecorder(SkillLessonRepository lessonRepository) {
        this.lessonRepository = Objects.requireNonNull(lessonRepository, "lessonRepository 不能为 null");
    }

    /**
     * 记录一条经验。
     *
     * @param tenantId         租户 ID
     * @param skillCode        Skill 代码
     * @param skillName        Skill 名称
     * @param lessonType       经验类型
     * @param title            标题
     * @param content          内容
     * @param sourceExecutionId 来源执行 ID
     * @return 记录的经验
     */
    public SkillLesson recordLesson(String tenantId,
                                     String skillCode,
                                     String skillName,
                                     LessonType lessonType,
                                     String title,
                                     String content,
                                     String sourceExecutionId) {
        return recordLesson(tenantId, skillCode, skillName, lessonType,
                title, content, null, null, null, 50, sourceExecutionId, null);
    }

    /**
     * 记录一条完整经验。
     *
     * @param tenantId         租户 ID
     * @param skillCode        Skill 代码
     * @param skillName        Skill 名称
     * @param lessonType       经验类型
     * @param title            标题
     * @param content          内容
     * @param scenario         场景描述
     * @param action           执行动作
     * @param result           执行结果
     * @param confidence       置信度（0-100）
     * @param sourceExecutionId 来源执行 ID
     * @param tags             标签
     * @return 记录的经验
     */
    public SkillLesson recordLesson(String tenantId,
                                     String skillCode,
                                     String skillName,
                                     LessonType lessonType,
                                     String title,
                                     String content,
                                     String scenario,
                                     String action,
                                     String result,
                                     int confidence,
                                     String sourceExecutionId,
                                     Map<String, Object> tags) {
        Objects.requireNonNull(tenantId, "tenantId 不能为 null");
        Objects.requireNonNull(skillCode, "skillCode 不能为 null");
        Objects.requireNonNull(lessonType, "lessonType 不能为 null");
        Objects.requireNonNull(title, "title 不能为 null");

        // 校验经验数量限制
        long currentCount = lessonRepository.countBySkill(tenantId, skillCode);
        if (currentCount >= MAX_LESSONS_PER_SKILL) {
            throw new SkillLessonException("Skill 经验数量已达上限: " + MAX_LESSONS_PER_SKILL);
        }

        LocalDateTime now = LocalDateTime.now();
        SkillLesson lesson = SkillLesson.builder()
                .lessonId(generateLessonId())
                .tenantId(tenantId)
                .skillCode(skillCode)
                .skillName(skillName)
                .lessonType(lessonType)
                .title(title)
                .content(content)
                .scenario(scenario)
                .action(action)
                .result(result)
                .confidence(confidence)
                .usageCount(0)
                .sourceExecutionId(sourceExecutionId)
                .createdAt(now)
                .lastUsedAt(now)
                .tags(tags)
                .build();

        SkillLesson saved = lessonRepository.save(lesson);
        log.info("[SkillLesson] 经验记录成功: lessonId={}, skillCode={}, type={}",
                saved.getLessonId(), skillCode, lessonType);

        return saved;
    }

    /**
     * 查询 Skill 的相关经验。
     *
     * @param tenantId  租户 ID
     * @param skillCode Skill 代码
     * @return 经验列表
     */
    public List<SkillLesson> getLessonsForSkill(String tenantId, String skillCode) {
        return lessonRepository.findBySkillCode(tenantId, skillCode);
    }

    /**
     * 查询高置信度经验。
     *
     * @param tenantId       租户 ID
     * @param skillCode      Skill 代码
     * @param minConfidence  最低置信度
     * @return 经验列表
     */
    public List<SkillLesson> getHighConfidenceLessons(String tenantId, String skillCode, int minConfidence) {
        return lessonRepository.findByMinConfidence(tenantId, skillCode, minConfidence);
    }

    /**
     * 获取最常用的经验。
     *
     * @param tenantId  租户 ID
     * @param skillCode Skill 代码
     * @param limit     返回数量限制
     * @return 经验列表
     */
    public List<SkillLesson> getMostUsedLessons(String tenantId, String skillCode, int limit) {
        return lessonRepository.findMostUsed(tenantId, skillCode, limit);
    }

    /**
     * 将经验注入到执行上下文中。
     *
     * @param tenantId  租户 ID
     * @param skillCode Skill 代码
     * @param context   执行上下文（将被修改）
     */
    public void injectLessonsIntoContext(String tenantId, String skillCode, Map<String, Object> context) {
        List<SkillLesson> lessons = lessonRepository.findMostUsed(tenantId, skillCode, MAX_INJECTED_LESSONS);

        if (lessons.isEmpty()) {
            return;
        }

        // 过滤低置信度经验
        List<SkillLesson> qualifiedLessons = lessons.stream()
                .filter(l -> l.getConfidence() >= DEFAULT_MIN_CONFIDENCE)
                .toList();

        if (!qualifiedLessons.isEmpty()) {
            context.put("skillLessons", qualifiedLessons);
            log.debug("[SkillLesson] 注入 {} 条经验到执行上下文: skillCode={}",
                    qualifiedLessons.size(), skillCode);
        }
    }

    /**
     * 标记经验被使用（增加使用次数）。
     *
     * @param lessonId 经验 ID
     * @return 更新后的经验
     */
    public SkillLesson markLessonUsed(String lessonId) {
        SkillLesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> new SkillLessonException("经验不存在: " + lessonId));

        SkillLesson updated = lesson.withUsed();
        lessonRepository.save(updated);
        log.debug("[SkillLesson] 经验使用次数+1: lessonId={}, count={}",
                lessonId, updated.getUsageCount());

        return updated;
    }

    /**
     * 更新经验置信度。
     *
     * @param lessonId    经验 ID
     * @param newConfidence 新的置信度
     * @return 更新后的经验
     */
    public SkillLesson updateConfidence(String lessonId, int newConfidence) {
        SkillLesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> new SkillLessonException("经验不存在: " + lessonId));

        SkillLesson updated = lesson.withConfidence(newConfidence);
        lessonRepository.save(updated);
        log.info("[SkillLesson] 经验置信度更新: lessonId={}, confidence={}",
                lessonId, newConfidence);

        return updated;
    }

    /**
     * 删除经验。
     *
     * @param lessonId 经验 ID
     * @param tenantId 租户 ID
     */
    public void deleteLesson(String lessonId, String tenantId) {
        SkillLesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> new SkillLessonException("经验不存在: " + lessonId));

        if (!lesson.getTenantId().equals(tenantId)) {
            throw new SkillLessonException("无权删除此经验");
        }

        lessonRepository.delete(lessonId);
        log.info("[SkillLesson] 经验删除成功: lessonId={}", lessonId);
    }

    /**
     * 生成经验 ID。
     *
     * @return 唯一经验 ID
     */
    private String generateLessonId() {
        return "lsn-" + UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * Skill 经验异常。
     */
    public static class SkillLessonException extends RuntimeException {
        public SkillLessonException(String message) {
            super(message);
        }
    }
}
