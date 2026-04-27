# 高并发秒杀系统

> 基于 Spring Boot 3 + Redis + RabbitMQ 的高并发秒杀系统，采用多层防超卖策略，支持 500 并发线程零超卖。

---

## 技术栈

| 技术 | 版本 | 用途 |
|------|------|------|
| Spring Boot | 3.4.5 | 基础框架 |
| JDK | 25 | 运行环境 |
| MySQL | 8.x | 持久化存储 |
| Redis | 7.x | 库存预减 / 限流 / 结果缓存 |
| RabbitMQ | 3.x | 异步下单 / 削峰填谷 |
| MyBatis Plus | 3.5.9 | ORM 框架 |
| JWT (jjwt) | 0.12.6 | 用户认证 |
| Hutool | 5.8.34 | 工具库（BCrypt/MD5/UUID）|
| Lombok | 1.18.42 | 代码简化 |

---

## 项目结构

```
seckill-system/
├── docs/
│   ├── API.md                          # 接口文档说明（含 Apifox 导入指引）
│   ├── seckill-api.json                # OpenAPI 文件（可导入 Apifox）
│   └── 压测报告.md                     # 压测结果报告
├── stress_test.py                       # Python 压测脚本
└── src/main/
    ├── java/com/seckill/
    │   ├── SeckillApplication.java      # 启动类
    │   ├── common/
    │   │   ├── Result.java              # 统一响应封装 {code, message, data}
    │   │   ├── BusinessException.java   # 业务异常
    │   │   └── GlobalExceptionHandler.java  # 全局异常处理
    │   ├── config/
    │   │   ├── RabbitMQConfig.java      # Exchange / Queue / Binding 配置
    │   │   ├── RabbitMessageConverterConfig.java  # JSON 消息转换器
    │   │   ├── RedisConfig.java         # RedisTemplate 序列化配置
    │   │   ├── WebMvcConfig.java        # 拦截器 + 跨域配置
    │   │   └── MyBatisMetaHandler.java  # create_time / update_time 自动填充
    │   ├── controller/
    │   │   ├── UserController.java      # 注册 / 登录 / 用户信息
    │   │   ├── GoodsController.java     # 商品列表 / 商品详情
    │   │   ├── SeckillController.java   # 获取秒杀地址 / 执行秒杀 / 查询结果
    │   │   ├── OrderController.java     # 支付 / 取消 / 订单列表 / 订单详情
    │   │   └── StressTestController.java  # 压测辅助接口（仅开发环境）
    │   ├── service/impl/
    │   │   ├── UserServiceImpl.java     # BCrypt 加密 + JWT 签发
    │   │   ├── GoodsServiceImpl.java    # 商品查询 + Redis 库存预热
    │   │   ├── SeckillServiceImpl.java  # 秒杀核心逻辑（5步防护）
    │   │   ├── OrderServiceImpl.java    # 订单支付 / 取消 / 超时关闭
    │   │   └── StressTestServiceImpl.java  # 批量注册/登录/重置/清理
    │   ├── mq/
    │   │   ├── SeckillOrderConsumer.java  # RabbitMQ 消费者（手动 ACK）
    │   │   └── ScheduleTask.java        # 启动预热 + 每分钟超时订单检测
    │   ├── mapper/
    │   │   ├── SeckillGoodsMapper.java  # 含 updateStockTo（压测重置）
    │   │   ├── SeckillOrderMapper.java  # 含 reduceStock / recoverStock
    │   │   └── SeckillUserMapper.java
    │   ├── entity/                      # SeckillUser / SeckillGoods / SeckillOrder
    │   ├── dto/                         # LoginDTO / RegisterDTO / SeckillMessage
    │   ├── enums/OrderStatus.java       # 0待支付 1已支付 2已取消 3超时关闭
    │   ├── interceptor/AuthInterceptor.java  # JWT 认证拦截器
    │   └── util/
    │       ├── JwtUtil.java             # Token 生成 / 解析 / 校验
    │       ├── RateLimitAnnotation.java # 限流注解
    │       ├── RateLimitAspect.java     # 限流 AOP 切面（Redis Lua）
    │       └── UserContext.java         # ThreadLocal 用户上下文
    └── resources/
        ├── application.yml
        ├── lua/deduct_stock.lua         # 原子扣减库存 Lua 脚本
        └── sql/schema.sql              # 数据库初始化脚本（含测试数据）
```

---

## 核心设计：秒杀防超卖

### 整体流程

```
用户请求
  │
  ├── Step 1: 获取秒杀地址
  │     └── 校验时间窗口 → 生成 MD5(userId+goodsId+salt) → 存 Redis（5分钟 TTL）
  │
  └── Step 2: 执行秒杀（5层防护）
        ├── ① 验证 MD5 地址（防爆破，验证后立即删除，一次性使用）
        ├── ② Redis SETNX 去重（同一用户同一商品只能参与一次）
        ├── ③ Redis Lua 脚本原子预扣库存（防超卖第一道防线）
        │     └── 库存不足 → 释放用户标记 → 返回"商品已售罄"
        ├── ④ 发送 RabbitMQ 消息异步下单（削峰，发送失败自动回滚）
        │     └── 消费者：数据库乐观锁减库存 → 创建订单（唯一索引兜底防重）
        └── ⑤ 返回"排队中" → 客户端轮询结果接口
```

### 防超卖双重保障

| 层次 | 方案 | 说明 |
|------|------|------|
| Redis 层 | Lua 脚本原子扣减 | 请求进入 MQ 前拦截，高性能 |
| 数据库层 | 乐观锁 `seckill_stock > 0` | MQ 消费时兜底，防止 Redis 与 DB 不一致 |
| 唯一索引 | `UNIQUE(user_id, goods_id)` | 数据库级别防重复下单 |

### Redis Key 体系

| Key | 用途 | TTL |
|-----|------|-----|
| `seckill:stock:{goodsId}` | 秒杀库存（预热写入） | 持久 |
| `seckill:path:{userId}:{goodsId}` | 秒杀地址 MD5 | 5 分钟 |
| `seckill:user:{userId}:{goodsId}` | 用户去重标记 | 1 小时 |
| `seckill:result:{userId}:{goodsId}` | 秒杀结果缓存 | 10 分钟 |
| `rate_limit:{key}:{ip}` | 接口限流计数 | 秒级滑动窗口 |

---

## 其他核心功能

### 接口限流
- 基于 AOP 自定义注解 `@RateLimitAnnotation(key, time, count)`
- 底层 Redis Lua 脚本计数器，按 IP 隔离
- 超出阈值返回 HTTP 429
- 秒杀接口：**100 次/秒/IP**

### 订单超时自动关闭
- 定时任务每分钟扫描待支付订单
- 超过 15 分钟未支付 → 状态改为「超时关闭」 → 恢复 DB + Redis 库存

### 启动预热
- 应用启动时 `@PostConstruct` 将所有商品库存从 DB 写入 Redis
- 避免冷启动时 Redis 空库存导致秒杀失败

---

## 数据库表结构

### seckill_user（用户表）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键（自增）|
| nickname | VARCHAR(50) | 昵称 |
| phone | VARCHAR(20) | 手机号（唯一索引）|
| password | VARCHAR(100) | BCrypt 加密密码 |
| salt | VARCHAR(50) | 盐值 |

### seckill_goods（秒杀商品表）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| goods_name / goods_title | VARCHAR | 商品名称/标题 |
| price / seckill_price | DECIMAL(10,2) | 原价 / 秒杀价 |
| stock_count | INT | 总库存 |
| seckill_stock | INT | 秒杀库存（乐观锁扣减对象）|
| start_date / end_date | DATETIME | 秒杀时间窗口 |

### seckill_order（秒杀订单表）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| user_id / goods_id | BIGINT | 关联用户/商品 |
| order_no | VARCHAR(64) | 订单号（索引）|
| seckill_price | DECIMAL(10,2) | 成交价 |
| status | TINYINT | 0待支付 / 1已支付 / 2已取消 / 3超时关闭 |
| UNIQUE(user_id, goods_id) | — | 数据库级防重复下单 |

---

## RabbitMQ 配置

| 组件 | 名称 |
|------|------|
| Exchange | `seckill.order.exchange`（Topic，持久化）|
| Queue | `seckill.order.queue`（持久化）|
| Routing Key | `seckill.order` |
| 消息格式 | JSON（Jackson2JsonMessageConverter）|
| ACK 模式 | 手动确认（manual）|
| Prefetch | 10 |

消费失败时执行 `basicNack` 重新入队，保证消息不丢失。

---

## API 接口

### 用户模块

| 接口 | 方法 | 需要登录 |
|------|------|---------|
| `POST /api/user/register` | 注册 | 否 |
| `POST /api/user/login` | 登录（返回 JWT Token）| 否 |
| `GET /api/user/info` | 获取当前用户信息 | **是** |

### 商品模块

| 接口 | 方法 | 需要登录 |
|------|------|---------|
| `GET /api/goods/list` | 秒杀商品列表 | 否 |
| `GET /api/goods/detail/{goodsId}` | 商品详情 | 否 |

### 秒杀模块

| 接口 | 方法 | 需要登录 |
|------|------|---------|
| `GET /api/seckill/path/{goodsId}` | 获取加密秒杀地址 | **是** |
| `POST /api/seckill/execute/{goodsId}?path=xxx` | 执行秒杀（限流 100次/秒/IP）| **是** |
| `GET /api/seckill/result/{goodsId}` | 查询秒杀结果（轮询）| **是** |

### 订单模块

| 接口 | 方法 | 需要登录 |
|------|------|---------|
| `GET /api/order/list` | 我的订单列表 | **是** |
| `GET /api/order/{orderNo}` | 订单详情 | **是** |
| `POST /api/order/pay/{orderNo}` | 模拟支付 | **是** |
| `POST /api/order/cancel/{orderNo}` | 取消订单 | **是** |

### 压测工具（⚠️ 仅开发环境）

| 接口 | 方法 | 说明 |
|------|------|------|
| `POST /api/stress/register/{count}` | 批量注册用户（最多 5000）| — |
| `POST /api/stress/login/{count}` | 批量登录，返回 token 列表 | — |
| `POST /api/stress/reset-stock/{goodsId}/{stock}` | 重置指定商品库存 | — |
| `GET /api/stress/order-count/{goodsId}` | 查询订单数 | — |
| `GET /api/stress/redis-stock/{goodsId}` | 查询 Redis 剩余库存 | — |
| `DELETE /api/stress/cleanup` | 清理压测数据 | — |

---

## 认证方式

需要登录的接口，请求头携带：

```
Authorization: Bearer {token}
```

Token 通过 `POST /api/user/login` 获取，有效期 **24 小时**。

---

## 本地启动

### 前置依赖

| 服务 | 版本 | 说明 |
|------|------|------|
| MySQL | 8.x | 端口 3306 |
| Redis | 7.x | 端口 6379，密码 123456 |
| RabbitMQ | 3.x | 端口 5672，用户 guest/guest |

### 初始化数据库

```bash
mysql -u root -p < src/main/resources/sql/schema.sql
```

执行后自动创建：
- 数据库 `seckill_db`
- 3 张表：`seckill_user`、`seckill_goods`、`seckill_order`
- 3 个测试用户（手机号 13800000001~13800000003，密码 123456）
- 5 件秒杀商品（iPhone 17 Pro、MacBook Pro M5 等）

### 启动服务

```bash
mvn spring-boot:run
```

或 IDEA 直接运行 `SeckillApplication.java`，服务端口 **8080**。

---

## 接口测试（Apifox）

1. 打开 Apifox → 项目设置 → **导入数据**
2. 格式选择 **OpenAPI/Swagger**，上传 `docs/seckill-api.json`
3. 导入后自动生成 5 个接口分组

**秒杀测试流程：**

```
1. POST /api/user/login       → 获取 token
2. GET  /api/seckill/path/1   → 获取加密地址 path（携带 token）
3. POST /api/seckill/execute/1?path={上一步的值} → 执行秒杀
4. GET  /api/seckill/result/1  → 轮询结果（返回 "SUCCESS:订单号" 为止）
5. GET  /api/order/list        → 查看订单
```

---

## 压测

### 使用内置 Python 脚本

```bash
pip install requests
python stress_test.py
```

配置参数（`stress_test.py` 顶部）：

```python
BASE_URL     = "http://localhost:8080"
GOODS_ID     = 1       # 秒杀商品 ID
STOCK        = 100     # 秒杀库存
USER_COUNT   = 2000    # 注册用户数
THREAD_COUNT = 500     # 并发线程数
```

脚本自动执行：批量注册 → 批量登录 → 重置库存 → 并发抢购 → 输出报告。

### 压测结果

> 测试时间：2026-04-18，本地环境

| 指标 | 结果 |
|------|------|
| 并发用户数 | 2000 |
| 并发线程数 | 500 |
| 秒杀库存 | 100 |
| **QPS** | **75.8 req/s** |
| **平均响应时间** | **13ms** |
| **超卖数量** | **0（零超卖 ✅）**|
| 系统异常 | 0 次 |

**请求分布：**

| 类型 | 数量 |
|------|------|
| 进入 MQ 排队（成功） | 100 |
| 商品已售罄（正常拦截）| 173 |
| 限流拦截 | 227 |
| 重复请求 | 0 |

---

## 配置说明（application.yml）

```yaml
server:
  port: 8080

spring:
  datasource:
    url: jdbc:mysql://localhost:3306/seckill_db
    username: root
    password: root123

  data:
    redis:
      host: localhost
      port: 6379
      password: "123456"
      database: 1

  rabbitmq:
    host: localhost
    port: 5672
    username: guest
    password: guest

jwt:
  secret: SeckillSystemJwtSecretKey2026...
  expiration: 86400000   # 24小时

seckill:
  salt: "seckill_salt_2026"    # 地址加密盐值
  order-timeout: 15            # 订单超时（分钟）
  rate-limit: 500              # 每秒最大请求数
```

---

## 项目亮点

1. **多层防超卖**：Redis Lua 原子扣减（第一道）+ 数据库乐观锁（兜底）+ 唯一索引（最后防线）
2. **异步削峰**：RabbitMQ 手动 ACK，消费失败重入队，保证消息不丢失
3. **接口限流**：AOP + Redis Lua 自定义注解，按 IP 粒度滑动窗口限流
4. **安全防刷**：MD5 加密秒杀地址，验证后立即删除，一次性有效
5. **自动预热**：启动时将库存写入 Redis，定时任务关闭超时订单并恢复库存
