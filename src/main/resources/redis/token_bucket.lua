-- 令牌桶限流。放在 Lua 里是为了让“读余量 → 计算补充 → 扣减 → 写回”在 Redis 内原子执行，
-- 多个应用实例并发请求同一个桶时不会超发。时间取 Redis 服务器的 TIME，避免各实例时钟不一致。
-- KEYS[1]  桶的键
-- ARGV[1]  容量（最多攒多少个令牌）
-- ARGV[2]  每秒补充的令牌数
-- ARGV[3]  本次需要的令牌数
-- 返回 {是否放行 1/0, 剩余令牌（字符串）, 需要等待的毫秒数}
local capacity = tonumber(ARGV[1])
local rate = tonumber(ARGV[2])
local requested = tonumber(ARGV[3])

local t = redis.call('TIME')
local now = tonumber(t[1]) * 1000 + math.floor(tonumber(t[2]) / 1000)

local state = redis.call('HMGET', KEYS[1], 'tokens', 'ts')
local tokens = tonumber(state[1])
local ts = tonumber(state[2])
if tokens == nil or ts == nil then
  tokens = capacity
  ts = now
end

local elapsed = math.max(0, now - ts)
tokens = math.min(capacity, tokens + elapsed * rate / 1000)

local allowed = 0
local retry = 0
if tokens >= requested then
  tokens = tokens - requested
  allowed = 1
else
  retry = math.ceil((requested - tokens) * 1000 / rate)
end

redis.call('HSET', KEYS[1], 'tokens', tostring(tokens), 'ts', tostring(now))
-- 桶装满所需的时间之后，状态与“新桶”等价，可以过期
local ttl = math.min(86400, math.ceil(capacity / rate) + 1)
redis.call('EXPIRE', KEYS[1], ttl)

return {allowed, tostring(tokens), retry}
