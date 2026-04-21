-- Lua 脚本：原子性预扣库存
-- KEYS[1] = seckill:stock:{goodsId}
-- 返回值: 1 = 扣减成功, 0 = 库存不足

local stockKey = KEYS[1]
local stock = tonumber(redis.call('GET', stockKey))

if stock and stock > 0 then
    redis.call('DECR', stockKey)
    return 1
else
    return 0
end
