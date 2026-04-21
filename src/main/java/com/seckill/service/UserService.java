package com.seckill.service;

import com.seckill.dto.LoginDTO;
import com.seckill.dto.RegisterDTO;
import com.seckill.entity.SeckillUser;

import java.util.Map;

/**
 * 用户服务接口
 */
public interface UserService {

    /**
     * 注册
     */
    void register(RegisterDTO dto);

    /**
     * 登录
     */
    Map<String, Object> login(LoginDTO dto);

    /**
     * 根据ID查询用户
     */
    SeckillUser getById(Long id);
}
