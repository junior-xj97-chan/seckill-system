# -*- coding: utf-8 -*-
"""
Seckill System Stress Test
===========================
Usage:
  1. Start seckill-system first
  2. pip install requests
  3. python stress_test.py
"""

import sys, io, os
os.environ["PYTHONIOENCODING"] = "utf-8"
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')
sys.stderr = io.TextIOWrapper(sys.stderr.buffer, encoding='utf-8', errors='replace')

import requests
import threading
import time
import json
import random

# ============ CONFIG ============
BASE_URL = "http://localhost:8080"
GOODS_ID = 1           # seckill goods id
STOCK = 100            # stock count
USER_COUNT = 2000      # how many users to register
THREAD_COUNT = 500     # concurrent threads
# ================================

success_count = 0
fail_count = 0
sold_out_count = 0
duplicate_count = 0
error_count = 0
lock = threading.Lock()


def batch_register():
    print(f"\n[Step 1] Batch register {USER_COUNT} users...")
    resp = requests.post(f"{BASE_URL}/api/stress/register/{USER_COUNT}")
    data = resp.json()
    print(f"  OK: {data['data']} new users registered")
    return data["data"]


def batch_login():
    print(f"\n[Step 2] Batch login {USER_COUNT} users...")
    resp = requests.post(f"{BASE_URL}/api/stress/login/{USER_COUNT}")
    data = resp.json()
    tokens = data["data"]
    print(f"  OK: {len(tokens)} users logged in")
    return tokens


def reset_stock():
    print(f"\n[Step 3] Reset goods {GOODS_ID} stock to {STOCK}...")
    resp = requests.post(f"{BASE_URL}/api/stress/reset-stock/{GOODS_ID}/{STOCK}")
    print(f"  OK: stock reset")

    stock = requests.get(f"{BASE_URL}/api/stress/redis-stock/{GOODS_ID}").json()["data"]
    print(f"  Current Redis stock: {stock}")

    # Record pre-test order count
    pre_count = requests.get(f"{BASE_URL}/api/stress/order-count/{GOODS_ID}").json()["data"]
    print(f"  Pre-test orders: {pre_count}")
    return stock, pre_count


def seckill_task(user_info, barrier, thread_id):
    global success_count, fail_count, sold_out_count, duplicate_count, error_count

    user_id = user_info["userId"]
    token = user_info["token"]
    headers = {"Authorization": f"Bearer {token}"}

    barrier.wait()

    start = time.time()

    try:
        # 1. Get seckill path
        path_resp = requests.get(
            f"{BASE_URL}/api/seckill/path/{GOODS_ID}",
            headers=headers, timeout=5
        )
        path_data = path_resp.json()

        if path_data.get("code") != 200:
            with lock:
                error_count += 1
                print(f"  [T{thread_id}] User{user_id} get path FAILED: {path_data.get('message')}")
            return

        path = path_data["data"]

        # 2. Execute seckill
        exec_resp = requests.post(
            f"{BASE_URL}/api/seckill/execute/{GOODS_ID}?path={path}",
            headers=headers, timeout=5
        )
        exec_data = exec_resp.json()
        cost = (time.time() - start) * 1000

        with lock:
            if exec_data.get("code") == 200:
                success_count += 1
                print(f"  [T{thread_id}] User{user_id} SUCCESS -> {exec_data['data']} ({cost:.0f}ms)")
            else:
                msg = exec_data.get("message", "unknown")
                if "sold" in msg.lower() or "售罄" in msg:
                    sold_out_count += 1
                elif "duplicate" in msg.lower() or "重复" in msg or "already" in msg.lower():
                    duplicate_count += 1
                else:
                    error_count += 1
                print(f"  [T{thread_id}] User{user_id} FAILED: {msg} ({cost:.0f}ms)")

    except Exception as e:
        with lock:
            fail_count += 1
            print(f"  [T{thread_id}] User{user_id} ERROR: {e}")


def wait_for_orders():
    print("\nWaiting for MQ to process (10s)...")
    time.sleep(10)

    resp = requests.get(f"{BASE_URL}/api/stress/order-count/{GOODS_ID}")
    order_count = resp.json()["data"]

    resp2 = requests.get(f"{BASE_URL}/api/stress/redis-stock/{GOODS_ID}")
    redis_stock = resp2.json()["data"]

    return order_count, redis_stock


def main():
    print("=" * 60)
    print("       SECKILL STRESS TEST")
    print("=" * 60)
    print(f"  Goods ID:      {GOODS_ID}")
    print(f"  Stock:         {STOCK}")
    print(f"  Users:         {USER_COUNT}")
    print(f"  Threads:       {THREAD_COUNT}")
    print("=" * 60)

    batch_register()
    tokens = batch_login()
    if len(tokens) < USER_COUNT:
        print(f"  WARNING: only {len(tokens)} users available")

    reset_result = reset_stock()
    stock_before = reset_result[0]
    pre_orders = reset_result[1]

    print(f"\n[Step 4] {THREAD_COUNT} threads racing...")
    print("-" * 60)

    barrier = threading.Barrier(THREAD_COUNT)
    threads = []
    selected_users = random.sample(tokens, min(THREAD_COUNT, len(tokens)))

    start_time = time.time()

    for i, user in enumerate(selected_users):
        t = threading.Thread(target=seckill_task, args=(user, barrier, i))
        threads.append(t)
        t.start()

    for t in threads:
        t.join()

    total_time = time.time() - start_time

    order_count, redis_stock = wait_for_orders()

    # Calculate net new orders
    new_orders = order_count - pre_orders

    # Report
    print("\n" + "=" * 60)
    print("       STRESS TEST REPORT")
    print("=" * 60)
    print(f"  Total requests:     {THREAD_COUNT}")
    print(f"  Total time:         {total_time:.2f}s")
    print(f"  Throughput:         {THREAD_COUNT / total_time:.1f} req/s")
    print(f"  Avg response time:  {total_time / THREAD_COUNT * 1000:.0f}ms")
    print("-" * 60)
    print(f"  Queued (entered MQ): {success_count}")
    print(f"  Sold out:           {sold_out_count}")
    print(f"  Duplicate:          {duplicate_count}")
    print(f"  Other errors:       {error_count}")
    print(f"  Request exceptions: {fail_count}")
    print("-" * 60)
    print(f"  Expected consumed:   {min(success_count, STOCK)}")
    print(f"  New orders (this run): {new_orders}")
    print(f"  Total DB orders:    {order_count}")
    print(f"  Redis remaining:     {redis_stock}")
    oversold = new_orders > STOCK
    print(f"  Oversold check:      {'FAIL - OVERSOLD!' if oversold else 'PASS - No oversell'}")
    print("=" * 60)


if __name__ == "__main__":
    main()
