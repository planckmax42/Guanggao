-- KEYS[1]: 每日多维统计 Hash Key
-- KEYS[2]: 当日待落库统计 Key 集合
-- ARGV[1..4]: 曝光、点击、转化和费用字段名

local statsKey = KEYS[1]
local dirtyKey = KEYS[2]
local fields = {ARGV[1], ARGV[2], ARGV[3], ARGV[4]}
local values = {}
local total = 0

for i = 1, 4 do
    values[i] = tonumber(redis.call('HGET', statsKey, fields[i]) or '0')
    total = total + math.abs(values[i])
end

if total == 0 then
    redis.call('DEL', statsKey)
    redis.call('SREM', dirtyKey, statsKey)
    return values
end

for i = 1, 4 do
    if values[i] ~= 0 then
        redis.call('HINCRBY', statsKey, fields[i], -values[i])
    end
end

local remaining = 0
for i = 1, 4 do
    remaining = remaining + math.abs(tonumber(redis.call('HGET', statsKey, fields[i]) or '0'))
end

if remaining == 0 then
    redis.call('DEL', statsKey)
    redis.call('SREM', dirtyKey, statsKey)
end

return values
