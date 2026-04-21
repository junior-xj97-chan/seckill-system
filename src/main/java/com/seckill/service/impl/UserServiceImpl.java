package com.seckill.service.impl;

import cn.hutool.core.lang.UUID;
import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.seckill.common.BusinessException;
import com.seckill.dto.LoginDTO;
import com.seckill.dto.RegisterDTO;
import com.seckill.entity.SeckillUser;
import com.seckill.mapper.SeckillUserMapper;
import com.seckill.service.UserService;
import com.seckill.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final SeckillUserMapper userMapper;
    private final JwtUtil jwtUtil;

    @Override
    public void register(RegisterDTO dto) {
        // 检查手机号是否已注册
        Long count = userMapper.selectCount(
                new LambdaQueryWrapper<SeckillUser>().eq(SeckillUser::getPhone, dto.getPhone())
        );
        if (count > 0) {
            throw new BusinessException("该手机号已注册");
        }

        SeckillUser user = new SeckillUser();
        user.setNickname(dto.getNickname());
        user.setPhone(dto.getPhone());
        user.setSalt(UUID.fastUUID().toString(true).substring(0, 8));
        user.setPassword(BCrypt.hashpw(dto.getPassword()));
        userMapper.insert(user);
    }

    @Override
    public Map<String, Object> login(LoginDTO dto) {
        SeckillUser user = userMapper.selectOne(
                new LambdaQueryWrapper<SeckillUser>().eq(SeckillUser::getPhone, dto.getPhone())
        );
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        if (!BCrypt.checkpw(dto.getPassword(), user.getPassword())) {
            throw new BusinessException("密码错误");
        }

        String token = jwtUtil.generateToken(user.getId(), user.getPhone());

        Map<String, Object> result = new HashMap<>();
        result.put("token", token);
        result.put("userId", user.getId());
        result.put("nickname", user.getNickname());
        return result;
    }

    @Override
    public SeckillUser getById(Long id) {
        return userMapper.selectById(id);
    }
}
