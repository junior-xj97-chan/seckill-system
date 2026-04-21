package com.seckill.service.impl;

import cn.hutool.core.lang.UUID;
import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.seckill.dto.LoginDTO;
import com.seckill.entity.SeckillGoods;
import com.seckill.entity.SeckillOrder;
import com.seckill.entity.SeckillUser;
import com.seckill.mapper.SeckillGoodsMapper;
import com.seckill.mapper.SeckillOrderMapper;
import com.seckill.mapper.SeckillUserMapper;
import com.seckill.service.StressTestService;
import com.seckill.service.UserService;
import com.seckill.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 压测服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StressTestServiceImpl implements StressTestService {

    private final SeckillUserMapper userMapper;
    private final SeckillOrderMapper orderMapper;
    private final SeckillGoodsMapper goodsMapper;
    private final UserService userService;
    private final JwtUtil jwtUtil;
    private final StringRedisTemplate redisTemplate;

    // 压测用户手机号前缀
    private static final String STRESS_PHONE_PREFIX = "900";

    // Redis 库存 Key 前缀
    private static final String SECKILL_STOCK_KEY = "seckill:stock:";
    // 秒杀结果缓存前缀
    private static final String SECKILL_RESULT_KEY = "seckill:result:";
    // 用户去重前缀
    private static final String SECKILL_USER_KEY = "seckill:user:";

    @Override
    public int batchRegister(int count) {
        log.info("开始批量注册用户: count={}", count);
        int success = 0;
        long baseNum = System.currentTimeMillis() % 1000000;

        for (int i = 0; i < count; i++) {
            try {
                String phone = STRESS_PHONE_PREFIX + String.format("%010d", baseNum + i);
                String nickname = "stress_" + (baseNum + i);

                // 检查是否已存在
                Long exist = userMapper.selectCount(
                        new LambdaQueryWrapper<SeckillUser>().eq(SeckillUser::getPhone, phone)
                );
                if (exist > 0) {
                    // 已存在，跳过
                    continue;
                }

                SeckillUser user = new SeckillUser();
                user.setNickname(nickname);
                user.setPhone(phone);
                user.setSalt(UUID.fastUUID().toString(true).substring(0, 8));
                user.setPassword(BCrypt.hashpw("123456"));
                userMapper.insert(user);
                success++;
            } catch (Exception e) {
                log.warn("注册用户失败: {}", e.getMessage());
            }
        }

        log.info("批量注册完成: 成功={}, 跳过={}", success, count - success);
        return success;
    }

    @Override
    public List<Map<String, Object>> batchLogin(int count) {
        log.info("开始批量登录用户: count={}", count);

        // 查询所有压测用户
        List<SeckillUser> users = userMapper.selectList(
                new LambdaQueryWrapper<SeckillUser>()
                        .likeRight(SeckillUser::getPhone, STRESS_PHONE_PREFIX)
                        .orderByAsc(SeckillUser::getId)
                        .last("LIMIT " + count)
        );

        List<Map<String, Object>> results = new ArrayList<>();
        for (SeckillUser user : users) {
            String token = jwtUtil.generateToken(user.getId(), user.getPhone());
            Map<String, Object> item = new HashMap<>();
            item.put("userId", user.getId());
            item.put("token", token);
            results.add(item);
        }

        log.info("批量登录完成: 返回 {} 个用户 token", results.size());
        return results;
    }

    @Override
    public void resetStock(Long goodsId, int stock) {
        // 更新数据库库存
        goodsMapper.updateStockTo(goodsId, stock);

        // 重置 Redis 库存
        String stockKey = SECKILL_STOCK_KEY + goodsId;
        redisTemplate.opsForValue().set(stockKey, String.valueOf(stock));

        // 清理相关的 Redis 缓存
        var keys = redisTemplate.keys(SECKILL_USER_KEY + "*:" + goodsId);
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
        keys = redisTemplate.keys(SECKILL_RESULT_KEY + "*:" + goodsId);
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }

        log.info("库存已重置: goodsId={}, stock={}", goodsId, stock);
    }

    @Override
    public Long getOrderCount(Long goodsId) {
        return orderMapper.selectCount(
                new LambdaQueryWrapper<SeckillOrder>().eq(SeckillOrder::getGoodsId, goodsId)
        );
    }

    @Override
    public String getRedisStock(Long goodsId) {
        String stockKey = SECKILL_STOCK_KEY + goodsId;
        return redisTemplate.opsForValue().get(stockKey);
    }

    @Override
    public void cleanup() {
        // 删除压测用户
        userMapper.delete(
                new LambdaQueryWrapper<SeckillUser>()
                        .likeRight(SeckillUser::getPhone, STRESS_PHONE_PREFIX)
        );
        // 删除压测订单
        orderMapper.delete(
                new LambdaQueryWrapper<SeckillOrder>()
                        .like(SeckillOrder::getOrderNo, "")
        );
        log.info("压测数据已清理");
    }
}
