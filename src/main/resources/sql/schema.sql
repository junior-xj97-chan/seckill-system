-- =============================================
-- 秒杀系统数据库初始化脚本
-- =============================================

CREATE DATABASE IF NOT EXISTS seckill_db DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE seckill_db;

-- -------------------------------------------
-- 1. 用户表
-- -------------------------------------------
DROP TABLE IF EXISTS `seckill_user`;
CREATE TABLE `seckill_user` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '用户ID',
    `nickname` VARCHAR(50) NOT NULL COMMENT '昵称',
    `phone` VARCHAR(20) NOT NULL COMMENT '手机号',
    `password` VARCHAR(100) NOT NULL COMMENT '密码(BCrypt加密)',
    `salt` VARCHAR(50) NOT NULL COMMENT '盐值',
    `avatar` VARCHAR(255) DEFAULT NULL COMMENT '头像URL',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_phone` (`phone`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- -------------------------------------------
-- 2. 商品表
-- -------------------------------------------
DROP TABLE IF EXISTS `seckill_goods`;
CREATE TABLE `seckill_goods` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '商品ID',
    `goods_name` VARCHAR(200) NOT NULL COMMENT '商品名称',
    `goods_title` VARCHAR(255) NOT NULL COMMENT '商品标题',
    `goods_img` VARCHAR(500) DEFAULT NULL COMMENT '商品图片',
    `goods_detail` TEXT COMMENT '商品详情',
    `price` DECIMAL(10,2) NOT NULL COMMENT '商品原价',
    `cost_price` DECIMAL(10,2) NOT NULL COMMENT '成本价',
    `stock_count` INT NOT NULL DEFAULT 0 COMMENT '库存数量',
    `seckill_price` DECIMAL(10,2) NOT NULL COMMENT '秒杀价格',
    `seckill_stock` INT NOT NULL DEFAULT 0 COMMENT '秒杀库存',
    `start_date` DATETIME NOT NULL COMMENT '秒杀开始时间',
    `end_date` DATETIME NOT NULL COMMENT '秒杀结束时间',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='秒杀商品表';

-- -------------------------------------------
-- 3. 秒杀订单表
-- -------------------------------------------
DROP TABLE IF EXISTS `seckill_order`;
CREATE TABLE `seckill_order` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '订单ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `goods_id` BIGINT NOT NULL COMMENT '商品ID',
    `order_no` VARCHAR(64) NOT NULL COMMENT '订单号',
    `seckill_price` DECIMAL(10,2) NOT NULL COMMENT '秒杀价格',
    `status` TINYINT NOT NULL DEFAULT 0 COMMENT '订单状态: 0-待支付 1-已支付 2-已取消 3-超时关闭',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `pay_time` DATETIME DEFAULT NULL COMMENT '支付时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_goods` (`user_id`, `goods_id`),
    KEY `idx_order_no` (`order_no`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='秒杀订单表';

-- -------------------------------------------
-- 4. 测试数据
-- -------------------------------------------

-- 插入测试用户（密码: 123456，BCrypt 加密值）
INSERT INTO `seckill_user` (`nickname`, `phone`, `password`, `salt`) VALUES
('测试用户A', '13800000001', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH', 'abc123'),
('测试用户B', '13800000002', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH', 'def456'),
('测试用户C', '13800000003', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH', 'ghi789');

-- 插入秒杀商品
INSERT INTO `seckill_goods` (`goods_name`, `goods_title`, `goods_img`, `goods_detail`, `price`, `cost_price`, `stock_count`, `seckill_price`, `seckill_stock`, `start_date`, `end_date`) VALUES
('iPhone 17 Pro', 'iPhone 17 Pro 秒杀专场', 'https://example.com/iphone17.jpg', '最新款 iPhone 17 Pro，A20 芯片，48MP 摄像头', 9999.00, 6000.00, 1000, 6999.00, 100, '2026-04-01 00:00:00', '2026-12-31 23:59:59'),
('MacBook Pro M5', 'MacBook Pro M5 限时秒杀', 'https://example.com/macbook.jpg', 'M5 芯片，16GB 内存，512GB SSD', 14999.00, 10000.00, 500, 11999.00, 50, '2026-04-01 00:00:00', '2026-12-31 23:59:59'),
('AirPods Pro 4', 'AirPods Pro 4 首发秒杀', 'https://example.com/airpods.jpg', '主动降噪，自适应音频，USB-C 充电', 1899.00, 1000.00, 500, 1499.00, 200, '2026-04-01 00:00:00', '2026-12-31 23:59:59'),
('Sony PS5 Pro', 'PS5 Pro 游戏主机秒杀', 'https://example.com/ps5.jpg', '次世代游戏主机，4K 120fps，8K 支持', 4999.00, 3000.00, 300, 3999.00, 80, '2026-04-01 00:00:00', '2026-12-31 23:59:59'),
('DJI Mini 5 Pro', '大疆 Mini 5 Pro 无人机秒杀', 'https://example.com/dji.jpg', '249g 轻巧折叠，4K/60fps，智能跟随', 5788.00, 3500.00, 200, 4688.00, 60, '2026-04-01 00:00:00', '2026-12-31 23:59:59');
