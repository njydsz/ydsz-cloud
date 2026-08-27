package com.njydsz.agent.infra.skill;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import com.njydsz.agent.domain.skill.LessonType;
import com.njydsz.agent.domain.skill.SkillLesson;
import com.njydsz.agent.domain.skill.SkillLessonRepository;

import org.springframework.stereotype.Component;

/**
 * 基于内存的 Skill 经验仓储实现。
 *
 * <p>使用 ConcurrentHashMap 存储 Skill 经验，适用于开发和测试环境。
 * 生产环境建议替换为基于数据库的实现。</p>
 *
 * @author ydsz-agent
 * @since 1.0.0
 */
@Component
public class InMemorySkillLessonRepository implements SkillLessonRepository {

    private final Map<String, SkillLesson> lessonStore = new ConcurrentHashMap<>();

    @Override
    public SkillLesson save(SkillLesson lesson) {
        Objects.requireNonNull(lesson, "lesson 不能为 null");
        lessonStore.put(lesson.getLessonId(), lesson);
        return lesson;
    }

    @Override
    public Optional<SkillLesson> findById(String lessonId) {
        return Optional.ofNullable(lessonStore.get(lessonId));
    }

    @Override
    public List<SkillLesson> findBySkillCode(String tenantId, String skillCode) {
        return lessonStore.values().stream()
                .filter(l -> l.getTenantId().equals(tenantId))
                .filter(l -> l.getSkillCode().equals(skillCode))
                .sorted(Comparator.comparing(SkillLesson::getCreatedAt).reversed())
                .collect(Collectors.toList());
    }

    @Override
    public List<SkillLesson> findBySkillAndType(String tenantId, String skillCode, LessonType lessonType) {
        return lessonStore.values().stream()
                .filter(l -> l.getTenantId().equals(tenantId))
                .filter(l -> l.getSkillCode().equals(skillCode))
                .filter(l -> l.getLessonType() == lessonType)
                .sorted(Comparator.comparing(SkillLesson::getCreatedAt).reversed())
                .collect(Collectors.toList());
    }

    @Override
    public List<SkillLesson> findMostUsed(String tenantId, String skillCode, int limit) {
        return lessonStore.values().stream()
                .filter(l -> l.getTenantId().equals(tenantId))
                .filter(l -> l.getSkillCode().equals(skillCode))
                .sorted(Comparator.comparing(SkillLesson::getUsageCount).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    @Override
    public List<SkillLesson> findByMinConfidence(String tenantId, String skillCode, int minConfidence) {
        return lessonStore.values().stream()
                .filter(l -> l.getTenantId().equals(tenantId))
                .filter(l -> l.getSkillCode().equals(skillCode))
                .filter(l -> l.getConfidence() >= minConfidence)
                .sorted(Comparator.comparing(SkillLesson::getConfidence).reversed())
                .collect(Collectors.toList());
    }

    @Override
    public void delete(String lessonId) {
        lessonStore.remove(lessonId);
    }

    @Override
    public long countBySkill(String tenantId, String skillCode) {
        return lessonStore.values().stream()
                .filter(l -> l.getTenantId().equals(tenantId))
                .filter(l -> l.getSkillCode().equals(skillCode))
                .count();
    }
}
