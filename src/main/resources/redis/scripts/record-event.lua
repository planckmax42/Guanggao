-- KEYS[1]: 事件统计去重 Key
-- KEYS[2]: 每日多维统计 Hash Key
-- KEYS[3]: 当日待落库统计 Key 集合
-- KEYS[4]: 用户对计划的当日曝光频控 Key
-- ARGV[1]: 事件去重 Key TTL，秒
-- ARGV[2]: 曝光增量
-- ARGV[3]: 点击增量
-- ARGV[4]: 转化增量
-- ARGV[5]: 统计 Key TTL，秒
-- ARGV[6]: 频控 Key TTL，秒

local claimed = redis.call('SET', KEYS[1], '1', 'NX', 'EX', ARGV[1])
if not claimed then
    return 0
end

redis.call('HINCRBY', KEYS[2], 'impression_count', ARGV[2])
redis.call('HINCRBY', KEYS[2], 'click_count', ARGV[3])
redis.call('HINCRBY', KEYS[2], 'conversion_count', ARGV[4])
redis.call('SADD', KEYS[3], KEYS[2])
redis.call('EXPIRE', KEYS[2], ARGV[5])
redis.call('EXPIRE', KEYS[3], ARGV[5])

if tonumber(ARGV[2]) > 0 then
    redis.call('INCRBY', KEYS[4], ARGV[2])
    redis.call('EXPIRE', KEYS[4], ARGV[6])
end
return 1
