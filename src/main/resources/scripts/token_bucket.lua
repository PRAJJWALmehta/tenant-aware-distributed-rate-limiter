local key = KEYS[1]
local requested = tonumber(ARGV[1])
local capacity = tonumber(ARGV[2])
local refill_rate = tonumber(ARGV[3])

if not requested or requested <= 0 then
    return 0
end

local time_res = redis.call('TIME')
local now_sec = tonumber(time_res[1])
local now_usec = tonumber(time_res[2])
local now_ms = now_sec * 1000 + math.floor(now_usec / 1000)

local bucket = redis.call('HMGET', key, 'tokens', 'last_refill_ts')
local current_tokens = tonumber(bucket[1])
local last_refill_ts = tonumber(bucket[2])

if not current_tokens or not last_refill_ts then
    current_tokens = capacity
    last_refill_ts = now_ms
else
    local elapsed_ms = math.max(0, now_ms - last_refill_ts)
    local tokens_to_add = math.floor(elapsed_ms * refill_rate / 1000)
    if tokens_to_add > 0 then
        current_tokens = math.min(capacity, current_tokens + tokens_to_add)
        if current_tokens >= capacity then
            last_refill_ts = now_ms
        else
            last_refill_ts = last_refill_ts + math.floor(tokens_to_add * 1000 / refill_rate)
        end
    end
end

local allowed = 0
if current_tokens >= requested then
    current_tokens = current_tokens - requested
    allowed = 1
end

redis.call('HSET', key, 'tokens', current_tokens, 'last_refill_ts', last_refill_ts)

local refill_time_sec = math.ceil(capacity / math.max(1, refill_rate))
local ttl = math.max(3600, refill_time_sec * 2)
redis.call('EXPIRE', key, ttl)

return allowed
