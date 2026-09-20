-- 离线消息原子入队脚本（PERF-003）
-- 等价于: LPUSH key argv[1]; LTRIM key 0 (argv[2]-1); EXPIRE key argv[3]
-- 单个命令原子化，避免多次 RTT

local key = KEYS[1]
local value = ARGV[1]
local maxCache = tonumber(ARGV[2])
local ttlSeconds = tonumber(ARGV[3])

redis.call('LPUSH', key, value)
redis.call('LTRIM', key, 0, maxCache - 1)
redis.call('EXPIRE', key, ttlSeconds)

return 1
