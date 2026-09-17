package com.njydsz.agent.infra.knowledge;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import lombok.extern.slf4j.Slf4j;

import com.njydsz.agent.domain.gateway.LlmClient;
import com.njydsz.agent.domain.knowledge.Entity;
import com.njydsz.agent.domain.knowledge.EntityExtractionService;
import com.njydsz.agent.domain.knowledge.EntityType;
import com.njydsz.agent.domain.knowledge.Relation;
import com.njydsz.agent.domain.knowledge.RelationType;
import com.njydsz.agent.domain.model.ChatMessage;
import com.njydsz.agent.domain.model.ChatRequest;
import com.njydsz.agent.domain.model.ChatResponse;
import com.njydsz.common.json.YdszJson;
import com.njydsz.common.json.tree.ArrayNode;
import com.njydsz.common.json.tree.JsonNode;
import com.njydsz.common.json.tree.ObjectNode;

/**
 * 基于 LLM 的实体抽取服务实现。
 *
 * <p>将长文本按 chunk 切分后逐段调用 LLM，请求以 JSON 格式返回实体和关系。
 * 抽取结果自动去重（相同 name + type 的实体合并），关系统一使用实体 ID 引用。
 *
 * <p><b>装配条件：</b>由 Spring 自动装配，要求容器中已存在 {@link LlmClient} 实现。
 *
 * @author ydsz-team
 * @since 26.09.17
 */
@Slf4j
public class LlmEntityExtractionService implements EntityExtractionService {

  /** 默认 chunk 切分大小（字符数） */
  private static final int DEFAULT_CHUNK_SIZE = 4000;

  /** chunk 之间的重叠字符数，避免跨 chunk 的实体被截断 */
  private static final int CHUNK_OVERLAP = 200;

  /** 默认使用的 LLM 模型 */
  private static final String DEFAULT_EXTRACTION_MODEL = "gpt-4o-mini";

  /** LLM 调用使用的模型 */
  private final String model;

  /** LLM 客户端 */
  private final LlmClient llmClient;

  /**
   * 构造 LLM 抽取服务。
   *
   * @param llmClient LLM 客户端
   */
  public LlmEntityExtractionService(LlmClient llmClient) {
    this(llmClient, DEFAULT_EXTRACTION_MODEL);
  }

  /**
   * 构造 LLM 抽取服务（指定模型）。
   *
   * @param llmClient LLM 客户端
   * @param model LLM 模型名称
   */
  public LlmEntityExtractionService(LlmClient llmClient, String model) {
    this.llmClient = Objects.requireNonNull(llmClient, "llmClient 不能为 null");
    this.model = model != null ? model : DEFAULT_EXTRACTION_MODEL;
  }

  @Override
  public ExtractionResult extract(String text, String sourceDocId) {
    if (text == null || text.isBlank()) {
      return new ExtractionResult(Collections.emptyList(), Collections.emptyList());
    }
    List<String> chunks = splitIntoChunks(text, DEFAULT_CHUNK_SIZE, CHUNK_OVERLAP);
    log.info("[KG-Extract] 开始抽取: sourceDocId={}, chunkCount={}", sourceDocId, chunks.size());

    // 去重集合
    Map<String, Entity> entityMap = new HashMap<>();
    Set<String> relationIdSet = new HashSet<>();
    List<Relation> relationList = new ArrayList<>();

    for (int i = 0; i < chunks.size(); i++) {
      String chunk = chunks.get(i);
      try {
        ExtractionResult chunkResult = extractFromChunk(chunk, sourceDocId);
        // 合并实体（按 entityId 去重）
        for (Entity entity : chunkResult.entities()) {
          entityMap.merge(entity.id(), entity, this::mergeEntity);
        }
        // 合并关系（按 relationId 去重）
        for (Relation relation : chunkResult.relations()) {
          if (relationIdSet.add(relation.id())) {
            relationList.add(relation);
          }
        }
      } catch (Exception e) {
        log.warn("[KG-Extract] 第 {}/{} 个 chunk 抽取失败: {}", i + 1, chunks.size(), e.getMessage());
      }
    }

    List<Entity> entities = new ArrayList<>(entityMap.values());
    log.info("[KG-Extract] 抽取完成: sourceDocId={}, entities={}, relations={}",
        sourceDocId, entities.size(), relationList.size());
    return new ExtractionResult(entities, relationList);
  }

  // ===== 私有方法 =====

  /**
   * 从单个文本块中抽取实体和关系。
   */
  private ExtractionResult extractFromChunk(String chunk, String sourceDocId) {
    String prompt = buildExtractionPrompt(chunk);
    ChatMessage systemMsg = ChatMessage.system(
        "你是一个知识图谱实体抽取助手。请从给定文本中精准抽取实体和关系，严格按照 JSON 格式返回。");
    ChatMessage userMsg = ChatMessage.user(prompt, "entity-extraction");

    ChatRequest request = ChatRequest.builder()
        .model(model)
        .messages(List.of(systemMsg, userMsg))
        .temperature(0.1)
        .maxTokens(2048)
        .build();

    ChatResponse response = llmClient.chat(request);
    String content = response.getContent();
    if (content == null || content.isBlank()) {
      log.warn("[KG-Extract] LLM 返回内容为空");
      return new ExtractionResult(Collections.emptyList(), Collections.emptyList());
    }

    return parseExtractionResponse(content, sourceDocId);
  }

  /**
   * 构建 LLM 抽取 prompt。
   */
  private String buildExtractionPrompt(String text) {
    return String.format(
        "从以下文本中抽取实体和关系，以 JSON 格式返回。%n"
        + "实体类型限定为：PERSON / LOCATION / ORGANIZATION / CONCEPT / EVENT / PRODUCT / TECHNOLOGY%n"
        + "关系类型限定为：WORKS_AT / LOCATED_IN / PART_OF / RELATED_TO / CREATED_BY / DEPENDS_ON / BELONGS_TO%n%n"
        + "返回格式示例：%n"
        + "{%n"
        + "  \"entities\": [{\"name\": \"张三\", \"type\": \"PERSON\", \"description\": \"软件工程师\"}],%n"
        + "  \"relations\": [{\"from\": \"张三\", \"to\": \"某公司\", \"type\": \"WORKS_AT\", \"confidence\": 0.9}]%n"
        + "}%n%n"
        + "注意：%n"
        + "1. confidence 为 0-1 之间的数值%n"
        + "2. relations 中的 from 和 to 必须与 entities 中的 name 一致%n"
        + "3. 仅抽取明确的实体和关系，不要猜测%n%n"
        + "文本：%n%s",
        text);
  }

  /**
   * 解析 LLM 返回的 JSON 抽取结果。
   */
  private ExtractionResult parseExtractionResponse(String content, String sourceDocId) {
    // 提取 JSON 部分（LLM 可能额外输出解释性文字）
    String json = extractJsonFromContent(content);
    if (json == null) {
      log.warn("[KG-Extract] 无法从响应中提取 JSON");
      return new ExtractionResult(Collections.emptyList(), Collections.emptyList());
    }

    try {
      JsonNode root = YdszJson.readTree(json);
      if (root == null || !root.isObject()) {
        return new ExtractionResult(Collections.emptyList(), Collections.emptyList());
      }

      List<Entity> entities = new ArrayList<>();
      Map<String, String> nameToIdMap = new HashMap<>();

      // 解析 entities 数组
      JsonNode entitiesNode = root.get("entities");
      if (entitiesNode != null && entitiesNode.isArray()) {
        ArrayNode entitiesArray = (ArrayNode) entitiesNode;
        Iterator<JsonNode> entityIter = entitiesArray.elements();
        while (entityIter.hasNext()) {
          JsonNode entityNode = entityIter.next();
          String name = getTextOrNull(entityNode, "name");
          String typeStr = getTextOrNull(entityNode, "type");
          String description = getTextOrNull(entityNode, "description");
          if (name == null || name.isBlank()) {
            continue;
          }
          EntityType type = EntityType.fromString(typeStr);
          String entityId = Entity.generateId(name, type);
          Entity entity = new Entity(entityId, name, type,
              description != null ? description : "", sourceDocId);
          entities.add(entity);
          nameToIdMap.put(name, entityId);
        }
      }

      List<Relation> relations = new ArrayList<>();
      // 解析 relations 数组
      JsonNode relationsNode = root.get("relations");
      if (relationsNode != null && relationsNode.isArray()) {
        ArrayNode relationsArray = (ArrayNode) relationsNode;
        Iterator<JsonNode> relationIter = relationsArray.elements();
        while (relationIter.hasNext()) {
          JsonNode relationNode = relationIter.next();
          String fromName = getTextOrNull(relationNode, "from");
          String toName = getTextOrNull(relationNode, "to");
          String typeStr = getTextOrNull(relationNode, "type");
          JsonNode confidenceNode = relationNode.get("confidence");
          if (fromName == null || toName == null || fromName.isBlank() || toName.isBlank()) {
            continue;
          }
          String fromId = nameToIdMap.get(fromName);
          String toId = nameToIdMap.get(toName);
          if (fromId == null || toId == null) {
            continue;
          }
          RelationType type = RelationType.fromString(typeStr);
          BigDecimal confidence = extractConfidence(confidenceNode);
          String relationId = Relation.generateId(fromId, toId, type);
          Relation relation = new Relation(relationId, fromId, toId, type, confidence, Collections.emptyMap());
          relations.add(relation);
        }
      }

      return new ExtractionResult(entities, relations);
    } catch (Exception e) {
      log.warn("[KG-Extract] 解析 JSON 响应失败: {}", e.getMessage());
      return new ExtractionResult(Collections.emptyList(), Collections.emptyList());
    }
  }

  /**
   * 从文本中提取 JSON 块（支持 markdown 代码块包裹和裸 JSON）。
   */
  private String extractJsonFromContent(String content) {
    if (content == null) {
      return null;
    }
    // 尝试提取 markdown 代码块中的 JSON
    int codeBlockStart = content.indexOf("```json");
    if (codeBlockStart >= 0) {
      int jsonStart = content.indexOf("\n", codeBlockStart) + 1;
      int codeBlockEnd = content.indexOf("```", jsonStart);
      if (codeBlockEnd > jsonStart) {
        return content.substring(jsonStart, codeBlockEnd).trim();
      }
    }
    // 尝试提取裸 JSON（从 { 开始到 }）
    int jsonStart = content.indexOf('{');
    int jsonEnd = content.lastIndexOf('}');
    if (jsonStart >= 0 && jsonEnd > jsonStart) {
      return content.substring(jsonStart, jsonEnd + 1).trim();
    }
    return null;
  }

  /**
   * 提取置信度值。
   *
   * @param node JSON 节点
   * @return confidence（0-1），解析失败返回 0.5
   */
  private BigDecimal extractConfidence(JsonNode node) {
    if (node == null || node.isNull()) {
      return new BigDecimal("0.5");
    }
    try {
      double value = node.asDouble();
      if (value < 0) {
        value = 0;
      }
      if (value > 1) {
        value = 1;
      }
      return BigDecimal.valueOf(value);
    } catch (Exception e) {
      return new BigDecimal("0.5");
    }
  }

  /**
   * 从 JSON 节点获取文本字段值。
   */
  private String getTextOrNull(JsonNode node, String fieldName) {
    if (node == null || !node.isObject()) {
      return null;
    }
    JsonNode fieldNode = node.get(fieldName);
    if (fieldNode == null || fieldNode.isNull() || fieldNode.isMissing()) {
      return null;
    }
    String value = fieldNode.asText();
    return value.isBlank() ? null : value.trim();
  }

  /**
   * 将文本按指定大小切分为若干 chunk，chunk 之间带重叠。
   *
   * @param text 原文本
   * @param chunkSize 每个 chunk 的最大字符数
   * @param overlap 相邻 chunk 的重叠字符数
   * @return chunk 列表
   */
  private List<String> splitIntoChunks(String text, int chunkSize, int overlap) {
    List<String> chunks = new ArrayList<>();
    int length = text.length();
    int start = 0;
    while (start < length) {
      int end = Math.min(start + chunkSize, length);
      chunks.add(text.substring(start, end));
      if (end >= length) {
        break;
      }
      // 下一 chunk 起点回退 overlap 字符，保证边界实体不被截断
      start = end - overlap;
      if (start >= length) {
        break;
      }
    }
    return chunks;
  }

  /**
   * 合并两个同名实体，保留较长描述。
   */
  private Entity mergeEntity(Entity existing, Entity incoming) {
    String description = existing.description();
    if (incoming.description() != null
        && (description == null || incoming.description().length() > description.length())) {
      description = incoming.description();
    }
    return new Entity(existing.id(), existing.name(), existing.type(), description,
        incoming.sourceDocId() != null ? incoming.sourceDocId() : existing.sourceDocId(),
        new HashMap<>(existing.properties()));
  }
}
