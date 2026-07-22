-- KEYS[1]: 费用统计去重 Key
-- KEYS[2]: 每日多维统计 Hash Key
-- KEYS[3]: 当日待落库统计 Key 集合
-- ARGV[1]: 费用去重 Key TTL，秒
-- ARGV[2]: 本次计费金额
-- ARGV[3]: 统计 Key TTL，秒

local claimed = redis.call('SET', KEYS[1], '1', 'NX', 'EX', ARGV[1])
if not claimed then
    return 0
end

local amount = tonumber(ARGV[2])
if amount ~= 0 then
    redis.call('HINCRBY', KEYS[2], 'cost_amount', amount)
    redis.call('SADD', KEYS[3], KEYS[2])
    redis.call('EXPIRE', KEYS[2], ARGV[3])
    redis.call('EXPIRE', KEYS[3], ARGV[3])
end
return 1
