package com.njydsz.common.redis.service.ops;

import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;

/**
 * Pipeline 操作实现类
 *
 * <p>基于底层 RedisConnection 实现 RedisPipelineOps 接口，
 * 提供 Pipeline 模式下的简化操作。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class RedisPipelineOpsImpl implements RedisPipelineOps {

    private final RedisTemplate<String, Object> redisTemplate;
    private final RedisConnection connection;

    public RedisPipelineOpsImpl(RedisTemplate<String, Object> redisTemplate, RedisConnection connection) {
        this.redisTemplate = redisTemplate;
        this.connection = connection;
    }

    private byte[] serializeKey(String key) {
        return redisTemplate.getStringSerializer().serialize(key);
    }

    private byte[] serializeValue(Object value) {
        return ((RedisSerializer<Object>) redisTemplate.getValueSerializer()).serialize(value);
    }

    private byte[] serializeField(Object field) {
        return ((RedisSerializer<Object>) redisTemplate.getHashValueSerializer()).serialize(field);
    }

    @Override
    public void setString(String key, Object value) {
        byte[] rawKey = serializeKey(key);
        byte[] rawValue = serializeValue(value);
        if (rawKey != null && rawValue != null) {
            connection.stringCommands().set(rawKey, rawValue);
        }
    }

    @Override
    public void setString(String key, Object value, long expireSeconds) {
        byte[] rawKey = serializeKey(key);
        byte[] rawValue = serializeValue(value);
        if (rawKey != null && rawValue != null) {
            connection.stringCommands().set(rawKey, rawValue);
            connection.keyCommands().expire(rawKey, expireSeconds);
        }
    }

    @Override
    public void getString(String key) {
        byte[] rawKey = serializeKey(key);
        if (rawKey != null) {
            connection.stringCommands().get(rawKey);
        }
    }

    @Override
    public void delete(String key) {
        byte[] rawKey = serializeKey(key);
        if (rawKey != null) {
            connection.keyCommands().del(new byte[][]{rawKey});
        }
    }

    @Override
    public void exists(String key) {
        byte[] rawKey = serializeKey(key);
        if (rawKey != null) {
            connection.keyCommands().exists(rawKey);
        }
    }

    @Override
    public void hashPut(String key, String field, Object value) {
        byte[] rawKey = serializeKey(key);
        byte[] rawField = serializeField(field);
        byte[] rawValue = serializeValue(value);
        if (rawKey != null && rawField != null && rawValue != null) {
            connection.hashCommands().hSet(rawKey, rawField, rawValue);
        }
    }

    @Override
    public void hashGet(String key, String field) {
        byte[] rawKey = serializeKey(key);
        byte[] rawField = serializeField(field);
        if (rawKey != null && rawField != null) {
            connection.hashCommands().hGet(rawKey, rawField);
        }
    }

    @Override
    public void hashDelete(String key, Object... fields) {
        byte[] rawKey = serializeKey(key);
        if (rawKey != null && fields != null) {
            byte[][] rawFields = new byte[fields.length][];
            for (int i = 0; i < fields.length; i++) {
                rawFields[i] = serializeField(fields[i]);
            }
            connection.hashCommands().hDel(rawKey, rawFields);
        }
    }

    @Override
    public void listRightPush(String key, Object value) {
        byte[] rawKey = serializeKey(key);
        byte[] rawValue = serializeValue(value);
        if (rawKey != null && rawValue != null) {
            connection.listCommands().rPush(rawKey, rawValue);
        }
    }

    @Override
    public void listLeftPush(String key, Object value) {
        byte[] rawKey = serializeKey(key);
        byte[] rawValue = serializeValue(value);
        if (rawKey != null && rawValue != null) {
            connection.listCommands().lPush(rawKey, rawValue);
        }
    }

    @Override
    public void listRange(String key, long start, long end) {
        byte[] rawKey = serializeKey(key);
        if (rawKey != null) {
            connection.listCommands().lRange(rawKey, start, end);
        }
    }

    @Override
    public void setAdd(String key, Object... values) {
        byte[] rawKey = serializeKey(key);
        if (rawKey != null && values != null) {
            byte[][] rawValues = new byte[values.length][];
            for (int i = 0; i < values.length; i++) {
                rawValues[i] = serializeValue(values[i]);
            }
            connection.setCommands().sAdd(rawKey, rawValues);
        }
    }

    @Override
    public void setMembers(String key) {
        byte[] rawKey = serializeKey(key);
        if (rawKey != null) {
            connection.setCommands().sMembers(rawKey);
        }
    }

    @Override
    public void expire(String key, long expireSeconds) {
        byte[] rawKey = serializeKey(key);
        if (rawKey != null) {
            connection.keyCommands().expire(rawKey, expireSeconds);
        }
    }

    @Override
    public void incrBy(String key, long delta) {
        byte[] rawKey = serializeKey(key);
        if (rawKey != null) {
            connection.stringCommands().incrBy(rawKey, delta);
        }
    }

    @Override
    public void incrByFloat(String key, double delta) {
        byte[] rawKey = serializeKey(key);
        if (rawKey != null) {
            connection.stringCommands().incrBy(rawKey, delta);
        }
    }
}
