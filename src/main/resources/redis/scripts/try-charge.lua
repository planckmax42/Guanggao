-- KEYS[1]: 计划当日已消耗预算 Key
-- KEYS[2]: 计划累计已消耗预算 Key
-- KEYS[3]: 事件扣费决定 Key
-- ARGV[1]: 本次扣费金额
-- ARGV[2]: 单日预算上限
-- ARGV[3]: 总预算上限
-- ARGV[4]: 当日预算 Key TTL，秒
-- ARGV[5]: 累计预算 Key TTL，秒
-- ARGV[6]: 事件扣费决定 Key TTL，秒

local dailyKey = KEYS[1]
local totalKey = KEYS[2]
local eventKey = KEYS[3]
local amount = tonumber(ARGV[1])
local dailyBudget = tonumber(ARGV[2])
local totalBudget = tonumber(ARGV[3])
local dailyTtl = tonumber(ARGV[4])
local totalTtl = tonumber(ARGV[5])
local eventTtl = tonumber(ARGV[6])

local previousDecision = redis.call('GET', eventKey)
if previousDecision then
    return tonumber(previousDecision)
end

local dailyCost = tonumber(redis.call('GET', dailyKey) or '0')
local totalCost = tonumber(redis.call('GET', totalKey) or '0')

if dailyCost + amount > dailyBudget then
    redis.call('SET', eventKey, '0', 'EX', eventTtl)
    return 0
end
if totalCost + amount > totalBudget then
    redis.call('SET', eventKey, '0', 'EX', eventTtl)
    return 0
end

redis.call('INCRBY', dailyKey, amount)
redis.call('INCRBY', totalKey, amount)
redis.call('EXPIRE', dailyKey, dailyTtl)
redis.call('EXPIRE', totalKey, totalTtl)
redis.call('SET', eventKey, '1', 'EX', eventTtl)
return 1
