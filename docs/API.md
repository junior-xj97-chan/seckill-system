# 秒杀系统接口文档

## 导入 Apifox

1. 打开 Apifox → 项目设置 → **导入数据**
2. 选择 **OpenAPI/Swagger** 格式
3. 上传 `docs/seckill-api.json` 文件
4. 点击导入，即可自动生成所有接口

导入后会在左侧菜单看到 5 个分组：用户模块、商品模块、秒杀模块、订单模块、压测工具。

## 接口总览

| 模块 | 接口 | 方法 | 需要登录 |
|------|------|------|----------|
| 用户 | `/api/user/register` | POST | 否 |
| 用户 | `/api/user/login` | POST | 否 |
| 用户 | `/api/user/info` | GET | 是 |
| 商品 | `/api/goods/list` | GET | 否 |
| 商品 | `/api/goods/detail/{goodsId}` | GET | 否 |
| 秒杀 | `/api/seckill/path/{goodsId}` | GET | 是 |
| 秒杀 | `/api/seckill/execute/{goodsId}?path=xxx` | POST | 是 |
| 秒杀 | `/api/seckill/result/{goodsId}` | GET | 是 |
| 订单 | `/api/order/list` | GET | 是 |
| 订单 | `/api/order/{orderNo}` | GET | 是 |
| 订单 | `/api/order/pay/{orderNo}` | POST | 是 |
| 订单 | `/api/order/cancel/{orderNo}` | POST | 是 |
| 压测 | `/api/stress/register/{count}` | POST | 否 |
| 压测 | `/api/stress/login/{count}` | POST | 否 |
| 压测 | `/api/stress/reset-stock/{goodsId}/{stock}` | POST | 否 |
| 压测 | `/api/stress/order-count/{goodsId}` | GET | 否 |
| 压测 | `/api/stress/redis-stock/{goodsId}` | GET | 否 |
| 压测 | `/api/stress/cleanup` | DELETE | 否 |

## 认证方式

除标注"需要登录"的接口外，其他接口无需认证。

需要登录的接口在请求头中添加：

```
Authorization: Bearer {token}
```

Token 通过 `/api/user/login` 接口获取。

## 秒杀流程（Apifox 测试步骤）

1. **注册/登录** → 获取 token
2. **获取秒杀地址** → `GET /api/seckill/path/1`（携带 token）
3. **执行秒杀** → `POST /api/seckill/execute/1?path={上一步返回的MD5值}`（携带 token）
4. **查询结果** → `GET /api/seckill/result/1`（携带 token，轮询直到非"排队中"）
5. **查询订单** → `GET /api/order/list`（携带 token）
