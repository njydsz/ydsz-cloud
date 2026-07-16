#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
修复 debrand 重构后遗留的编译错误。

遵循 prefer-python-over-powershell 规则，使用 UTF-8 编码读写文件。
"""

import pathlib
import sys

ROOT = pathlib.Path(r"d:\Code\ydsz\ydsz-pmis")


def fix_index_rebuild_service():
    """修复 IndexRebuildService.java 第92行多余的 }，导致 class 提前关闭。"""
    f = ROOT / "ydsz-backend/ydsz-common/ydsz-common-search/src/main/java/com/njydsz/common/search/service/IndexRebuildService.java"
    content = f.read_text(encoding="utf-8")
    # 第92行多了一个 }，把 rebuildAllAsync 方法之后的 } 删除
    # 找到 rebuildAllAsync 方法结尾的额外 } 并删除
    old = """        }, "index-rebuild");
        t.setDaemon(true);
        t.start();
    }
    }

    /**
     * P1-9: 蓝绿重建索引"""
    new = """        }, "index-rebuild");
        t.setDaemon(true);
        t.start();
    }

    /**
     * P1-9: 蓝绿重建索引"""
    if old in content:
        content = content.replace(old, new, 1)
        f.write_text(content, encoding="utf-8")
        print(f"[OK] {f.name}: 删除多余的 }}")
    else:
        print(f"[SKIP] {f.name}: 未找到目标模式")


def fix_pg_search_engine():
    """修复 PgSearchEngine.java 中的字符串引号错误。

    这些错误是预存在的（debrand 之前就有），但现在需要修复以通过编译。
    """
    f = ROOT / "ydsz-backend/ydsz-common/ydsz-common-search/src/main/java/com/njydsz/common/search/engine/pg/PgSearchEngine.java"
    content = f.read_text(encoding="utf-8")

    fixes = []

    # Fix 1: Line 162 - 修复 ts_headline 参数字符串内的双引号
    # 原: '...StartSel=' || ? || "', StopSel=' || ?) AS highlight"
    # 应为: '...StartSel=' || ? || ', StopSel=' || ?) AS highlight'
    old1 = """selectSql.append("'MaxWords=60, MinWords=20, ShortWord=3, HighlightAll=FALSE, StartSel=' || ? || "', StopSel=' || ?) AS highlight");"""
    new1 = """selectSql.append("'MaxWords=60, MinWords=20, ShortWord=3, HighlightAll=FALSE, StartSel=' || ? || ', StopSel=' || ?) AS highlight");"""
    if old1 in content:
        content = content.replace(old1, new1, 1)
        fixes.append("Line 162 ts_headline 字符串引号")
    else:
        print(f"[WARN] {f.name}: 未找到 Line 162 模式")

    # Fix 2: Line 373 - 修复 ESCAPE 子句的引号
    # 原: " WHERE title ILIKE ? ESCAPE \"\"" ORDER BY title LIMIT ?"
    # 应为: " WHERE title ILIKE ? ESCAPE '\\' ORDER BY title LIMIT ?"
    old2 = '" WHERE title ILIKE ? ESCAPE \\"\\" ORDER BY title LIMIT ?"'
    new2 = '" WHERE title ILIKE ? ESCAPE \\\\\\\\ ORDER BY title LIMIT ?"'
    # 直接用字符串比较
    old2_literal = 'WHERE title ILIKE ? ESCAPE ""'
    new2_literal = 'WHERE title ILIKE ? ESCAPE \\\\\\\\'
    if old2_literal in content:
        content = content.replace(old2_literal, new2_literal, 1)
        fixes.append("Line 373 ESCAPE 子句引号")
    else:
        print(f"[WARN] {f.name}: 未找到 Line 373 模式")

    # Fix 3: Line 375 - 修复非法转义符 \%, \_
    # 原: prefix.replace("\\", "\\\\").replace("%", "\%").replace("_", "\_")
    # 应为: prefix.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
    old3 = '.replace("%", "\\%").replace("_", "\\_")'
    new3 = '.replace("%", "\\\\%").replace("_", "\\\\_")'
    if old3 in content:
        content = content.replace(old3, new3, 1)
        fixes.append("Line 375 非法转义符")
    else:
        print(f"[WARN] {f.name}: 未找到 Line 375 模式")

    if fixes:
        f.write_text(content, encoding="utf-8")
        print(f"[OK] {f.name}: 修复 {len(fixes)} 处: {', '.join(fixes)}")


def fix_minio_storage():
    """修复 MinioStorage.java - 改用 Json.toJson 方式生成 policy，参考 S3Storage 的写法。"""
    f = ROOT / "ydsz-backend/ydsz-common/ydsz-common-file/src/main/java/com/njydsz/common/file/storage/platform/MinioStorage.java"
    content = f.read_text(encoding="utf-8")

    # 旧代码（line 478-482）使用 escapeJsonString 手动构造 JSON
    old = """            String safePrefix = escapeJsonString(resolvedPrefix);
            String safeBucket = escapeJsonString(resolvedBucket);
            String policyJson = String.format(
                    "{\\"expiration\\":%d,\\"conditions\\":[[\\"starts-with\\",\\"$key\\",\\"%s\\"],[\\"eq\\",\\"$bucket\\",\\"%s\\"]]}",
                    expirationTime, safePrefix, safeBucket);"""

    # 新代码：参考 S3Storage 使用 Json.toJson
    new = """            Map<String, Object> policyMap = Map.of(
                    "expiration", expirationTime,
                    "conditions", List.of(
                            List.of("starts-with", "$key", resolvedPrefix),
                            List.of("eq", "$bucket", resolvedBucket)));
            String policyJson = Json.toJson(policyMap);"""

    if old in content:
        content = content.replace(old, new, 1)
        # 添加必要的 import (List 和 Map 可能已存在)
        if "import java.util.List;" not in content:
            content = content.replace("import java.util.ArrayList;",
                                      "import java.util.ArrayList;\nimport java.util.List;", 1)
        if "import java.util.Map;" not in content:
            content = content.replace("import java.util.ArrayList;",
                                      "import java.util.ArrayList;\nimport java.util.Map;", 1)
        f.write_text(content, encoding="utf-8")
        print(f"[OK] {f.name}: 改用 Json.toJson 生成 policy")
    else:
        print(f"[SKIP] {f.name}: 未找到 escapeJsonString 调用模式")


def fix_openai_compatible_client():
    """修复 OpenAICompatibleClient.java - 把 Map<String, Object> 改为 JsonObject。

    原代码可能用 fastjson 的 JSONObject，debrand 错误地替换为 Map<String, Object>。
    应改用 ydsz-common-json 的 JsonObject 和 JsonArray。
    """
    f = ROOT / "ydsz-backend/ydsz-agent/ydsz-agent-infra/src/main/java/com/njydsz/agent/infra/llm/OpenAICompatibleClient.java"
    content = f.read_text(encoding="utf-8")

    # 添加 import
    if "import com.njydsz.common.json.object.JsonArray;" not in content:
        content = content.replace(
            "import com.njydsz.common.json.Json;",
            "import com.njydsz.common.json.Json;\nimport com.njydsz.common.json.object.JsonArray;\nimport com.njydsz.common.json.object.JsonObject;",
            1,
        )

    # parseResponse 方法：Map<String, Object> obj = Json.parseMap(json) → JsonObject obj = Json.parseObjectToJsonObject(json)
    # 但 JsonObject.getJSONArray 返回 JsonArray，不是 List<Object>
    # 让我们重新写整个 parseResponse 方法

    old_parse = """    private ChatResponse parseResponse(String json) {
        Map<String, Object> obj = Json.parseMap(json);
        String id = obj.getString("id");
        String model = obj.getString("model");
        List<Object> choices = obj.getJSONArray("choices");
        if (choices == null || choices.isEmpty()) {
            throw new LlmException("LLM 响应无 choices", LlmException.ErrorType.INVALID_RESPONSE);
        }
        Map<String, Object> choice = choices.getJSONObject(0);
        Map<String, Object> message = choice.getJSONObject("message");
        String finishReason = choice.getString("finish_reason");
        String content = message != null ? message.getString("content") : null;

        List<ToolCall> toolCalls = new ArrayList<>();
        if (message != null && message.containsKey("tool_calls")) {
            List<Object> calls = message.getJSONArray("tool_calls");
            for (int i = 0; i < calls.size(); i++) {
                Map<String, Object> call = calls.getJSONObject(i);
                String callId = call.getString("id");
                Map<String, Object> function = call.getJSONObject("function");
                String name = function.getString("name");
                String argsStr = function.getString("arguments");
                Map<String, Object> args = Json.toObject(argsStr, Map.class);
                toolCalls.add(new ToolCall(callId, name, args));
            }
        }

        TokenUsage usage = TokenUsage.zero();
        if (obj.containsKey("usage")) {
            Map<String, Object> usageObj = obj.getJSONObject("usage");
            usage = new TokenUsage(
                    usageObj.getIntValue("prompt_tokens", 0),
                    usageObj.getIntValue("completion_tokens", 0));
        }

        ChatMessage chatMessage = toolCalls.isEmpty()
                ? ChatMessage.assistant(content, null, usage)
                : ChatMessage.assistantWithTools(content, null, toolCalls, usage);

        return new ChatResponse(id, model, chatMessage, usage, finishReason, toolCalls);
    }"""

    new_parse = """    private ChatResponse parseResponse(String json) {
        JsonObject obj = Json.parseObjectToJsonObject(json);
        String id = obj.getString("id");
        String model = obj.getString("model");
        JsonArray choices = obj.getJSONArray("choices");
        if (choices == null || choices.isEmpty()) {
            throw new LlmException("LLM 响应无 choices", LlmException.ErrorType.INVALID_RESPONSE);
        }
        JsonObject choice = choices.getJSONObject(0);
        JsonObject message = choice.getJSONObject("message");
        String finishReason = choice.getString("finish_reason");
        String content = message != null ? message.getString("content") : null;

        List<ToolCall> toolCalls = new ArrayList<>();
        if (message != null && message.containsKey("tool_calls")) {
            JsonArray calls = message.getJSONArray("tool_calls");
            for (int i = 0; i < calls.size(); i++) {
                JsonObject call = calls.getJSONObject(i);
                String callId = call.getString("id");
                JsonObject function = call.getJSONObject("function");
                String name = function.getString("name");
                String argsStr = function.getString("arguments");
                Map<String, Object> args = Json.toObject(argsStr, Map.class);
                toolCalls.add(new ToolCall(callId, name, args));
            }
        }

        TokenUsage usage = TokenUsage.zero();
        if (obj.containsKey("usage")) {
            JsonObject usageObj = obj.getJSONObject("usage");
            usage = new TokenUsage(
                    usageObj.getIntValue("prompt_tokens", 0),
                    usageObj.getIntValue("completion_tokens", 0));
        }

        ChatMessage chatMessage = toolCalls.isEmpty()
                ? ChatMessage.assistant(content, null, usage)
                : ChatMessage.assistantWithTools(content, null, toolCalls, usage);

        return new ChatResponse(id, model, chatMessage, usage, finishReason, toolCalls);
    }"""

    if old_parse in content:
        content = content.replace(old_parse, new_parse, 1)

    # parseChunk 方法
    old_chunk = """    private ChatChunk parseChunk(String data) {
        try {
            Map<String, Object> obj = Json.parseMap(data);
            String id = obj.getString("id");
            String model = obj.getString("model");
            List<Object> choices = obj.getJSONArray("choices");
            if (choices == null || choices.isEmpty()) {
                return null;
            }
            Map<String, Object> choice = choices.getJSONObject(0);
            Map<String, Object> delta = choice.getJSONObject("delta");
            String finishReason = choice.getString("finish_reason");
            String content = delta != null ? delta.getString("content") : null;

            TokenUsage usage = null;
            if (obj.containsKey("usage") && obj.get("usage") != null) {
                Map<String, Object> usageObj = obj.getJSONObject("usage");
                usage = new TokenUsage(
                        usageObj.getIntValue("prompt_tokens", 0),
                        usageObj.getIntValue("completion_tokens", 0));
            }"""

    new_chunk = """    private ChatChunk parseChunk(String data) {
        try {
            JsonObject obj = Json.parseObjectToJsonObject(data);
            String id = obj.getString("id");
            String model = obj.getString("model");
            JsonArray choices = obj.getJSONArray("choices");
            if (choices == null || choices.isEmpty()) {
                return null;
            }
            JsonObject choice = choices.getJSONObject(0);
            JsonObject delta = choice.getJSONObject("delta");
            String finishReason = choice.getString("finish_reason");
            String content = delta != null ? delta.getString("content") : null;

            TokenUsage usage = null;
            if (obj.containsKey("usage") && obj.get("usage") != null) {
                JsonObject usageObj = obj.getJSONObject("usage");
                usage = new TokenUsage(
                        usageObj.getIntValue("prompt_tokens", 0),
                        usageObj.getIntValue("completion_tokens", 0));
            }"""

    if old_chunk in content:
        content = content.replace(old_chunk, new_chunk, 1)

    f.write_text(content, encoding="utf-8")
    print(f"[OK] {f.name}: 改用 JsonObject API")


def fix_openai_embedding_client():
    """修复 OpenAiEmbeddingClient.java - 把 Map<String, Object> 改为 JsonObject。"""
    f = ROOT / "ydsz-backend/ydsz-agent/ydsz-agent-infra/src/main/java/com/njydsz/agent/infra/rag/OpenAiEmbeddingClient.java"
    content = f.read_text(encoding="utf-8")

    # 添加 import
    if "import com.njydsz.common.json.object.JsonArray;" not in content:
        content = content.replace(
            "import com.njydsz.common.json.Json;",
            "import com.njydsz.common.json.Json;\nimport com.njydsz.common.json.object.JsonArray;\nimport com.njydsz.common.json.object.JsonObject;",
            1,
        )

    old = """    private List<List<Float>> parseEmbeddings(String json) {
        Map<String, Object> obj = Json.parseMap(json);
        List<Object> data = obj.getJSONArray("data");
        if (data == null || data.isEmpty()) {
            throw new LlmException("Embedding 响应无 data", LlmException.ErrorType.INVALID_RESPONSE);
        }
        List<List<Float>> result = new ArrayList<>(data.size());
        for (int i = 0; i < data.size(); i++) {
            Map<String, Object> item = data.getJSONObject(i);
            List<Object> embedding = item.getJSONArray("embedding");
            List<Float> vector = new ArrayList<>(embedding.size());
            for (int j = 0; j < embedding.size(); j++) {
                vector.add(embedding.getFloatValue(j));
            }
            result.add(vector);
        }
        return result;
    }"""

    new = """    private List<List<Float>> parseEmbeddings(String json) {
        JsonObject obj = Json.parseObjectToJsonObject(json);
        JsonArray data = obj.getJSONArray("data");
        if (data == null || data.isEmpty()) {
            throw new LlmException("Embedding 响应无 data", LlmException.ErrorType.INVALID_RESPONSE);
        }
        List<List<Float>> result = new ArrayList<>(data.size());
        for (int i = 0; i < data.size(); i++) {
            JsonObject item = data.getJSONObject(i);
            JsonArray embedding = item.getJSONArray("embedding");
            List<Float> vector = new ArrayList<>(embedding.size());
            for (int j = 0; j < embedding.size(); j++) {
                vector.add(embedding.getFloatValue(j));
            }
            result.add(vector);
        }
        return result;
    }"""

    if old in content:
        content = content.replace(old, new, 1)
        f.write_text(content, encoding="utf-8")
        print(f"[OK] {f.name}: 改用 JsonObject API")
    else:
        print(f"[SKIP] {f.name}: 未找到目标方法")


def create_system_constants():
    """创建 SystemConstants.java - 该文件在 debrand 过程中丢失。

    原 com.njydsz.pmis.common.constant.SystemConstants (在 ydsz-pmis-common-core 模块)
    debrand 后应为 com.njydsz.common.constant.SystemConstants
    """
    target = ROOT / "ydsz-backend/ydsz-common/ydsz-common-core/src/main/java/com/njydsz/common/constant/SystemConstants.java"
    target.parent.mkdir(parents=True, exist_ok=True)

    # 按项目记忆：SYSTEM_USER_ID = "SYSTEM"（不是 "0"）
    content = '''package com.njydsz.common.constant;

/**
 * 系统级常量定义。
 *
 * <p>包含系统用户 ID、系统模块名称等全局共享常量。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public final class SystemConstants {

    private SystemConstants() {
        throw new UnsupportedOperationException("Constants class");
    }

    /** 系统用户 ID（用于标识系统自动操作或无具体用户上下文的场景） */
    public static final String SYSTEM_USER_ID = "SYSTEM";

    /** 系统模块名称 */
    public static final String SYSTEM_MODULE = "system";

    /** 默认租户 ID */
    public static final String DEFAULT_TENANT_ID = "1";
}
'''
    target.write_text(content, encoding="utf-8")
    print(f"[OK] 创建 {target.relative_to(ROOT)}")


def main():
    print("=== 修复 debrand 编译错误 ===\n")
    create_system_constants()
    fix_index_rebuild_service()
    fix_pg_search_engine()
    fix_minio_storage()
    fix_openai_compatible_client()
    fix_openai_embedding_client()
    print("\n=== 修复完成，请重新运行 mvn compile 验证 ===")


if __name__ == "__main__":
    main()
