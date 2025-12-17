#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
简单测试配置持久化功能
直接操作.env文件，然后检查后端是否正确读取
"""

import os
import time
import requests

# 定义路径
ENV_FILE_PATH = "e:/StudyData/trae/PaperSummarizer/backend/.env"
BACKEND_API = "http://localhost:8001/api/config"

def read_env_file():
    """读取.env文件内容"""
    try:
        with open(ENV_FILE_PATH, 'r', encoding='utf-8') as f:
            content = f.read()
        return content
    except Exception as e:
        print(f"读取.env文件失败: {e}")
        return None

def write_env_file(content):
    """写入.env文件内容"""
    try:
        with open(ENV_FILE_PATH, 'w', encoding='utf-8') as f:
            f.write(content)
        print("✓ 成功写入.env文件")
        return True
    except Exception as e:
        print(f"✗ 写入.env文件失败: {e}")
        return False

def get_backend_config():
    """获取后端配置"""
    try:
        response = requests.get(BACKEND_API, timeout=5)
        if response.status_code == 200:
            return response.json()
        else:
            print(f"✗ 获取后端配置失败，状态码: {response.status_code}")
            return None
    except Exception as e:
        print(f"✗ 获取后端配置时发生错误: {e}")
        return None

def main():
    print("=== 简单配置持久化测试 ===")
    
    # 1. 保存原始.env文件内容
    print("1. 保存原始.env文件内容...")
    original_content = read_env_file()
    if not original_content:
        return
    print("✓ 成功保存原始内容")
    
    # 2. 读取当前后端配置
    print("\n2. 读取当前后端配置...")
    current_config = get_backend_config()
    if current_config:
        print(f"当前后端配置: {current_config}")
    else:
        print("无法获取当前后端配置，跳过部分测试")
    
    # 3. 手动更新.env文件
    print("\n3. 手动更新.env文件...")
    # 修改配置值
    updated_content = original_content
    updated_content = updated_content.replace(
        "API_KEY=sk-tehmatwmtgfdzudmpltszecgqzcjlzoegscrapzzfjkwxqkf",
        "API_KEY=test_manual_update_123"
    )
    updated_content = updated_content.replace(
        "RSS_URL=https://ieeexplore.ieee.org/rss/TOC36.XML",
        "RSS_URL=https://example.com/manual-test.xml"
    )
    
    print("更新后的配置内容片段:")
    lines = updated_content.split('\n')
    for line in lines:
        if line.startswith(('API_KEY=', 'RSS_URL=')):
            print(f"  {line}")
    
    # 写入更新后的内容
    if write_env_file(updated_content):
        print("✓ .env文件已成功更新")
    else:
        return
    
    # 4. 重启后端服务（这里我们等待一段时间，让后端自动重新加载）
    print("\n4. 等待后端服务重新加载配置...")
    time.sleep(3)  # 等待3秒，让后端检测到文件变化并重新加载
    
    # 5. 再次读取后端配置，验证是否已更新
    print("\n5. 验证后端是否已加载更新后的配置...")
    new_config = get_backend_config()
    if new_config:
        print(f"更新后的后端配置: {new_config}")
        
        # 验证关键字段是否已更新
        if new_config.get("api_key") == "test_manual_update_123":
            print("✓ API_KEY已成功更新并被后端加载")
        else:
            print(f"✗ API_KEY未更新，当前值: {new_config.get('api_key')}")
        
        if new_config.get("rss_url") == "https://example.com/manual-test.xml":
            print("✓ RSS_URL已成功更新并被后端加载")
        else:
            print(f"✗ RSS_URL未更新，当前值: {new_config.get('rss_url')}")
    
    # 6. 恢复原始.env文件内容
    print("\n6. 恢复原始.env文件内容...")
    if write_env_file(original_content):
        print("✓ 原始配置已成功恢复")
    else:
        print("✗ 恢复原始配置失败")
    
    print("\n=== 测试完成 ===")
    print("\n总结:")
    print("1. 直接操作.env文件是可行的")
    print("2. 后端服务应该能够自动重新加载配置（如果启用了热重载）")
    print("3. 配置持久化功能的核心是正确读写.env文件")

if __name__ == "__main__":
    main()