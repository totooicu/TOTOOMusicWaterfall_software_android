#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
直接测试后端配置更新功能
"""

import requests
import os
import time

# 直接使用后端API地址
BACKEND_API_BASE = "http://localhost:8001/api"
ENV_FILE_PATH = "e:/StudyData/trae/PaperSummarizer/backend/.env"

def test_config_update_direct():
    """直接测试后端配置更新功能"""
    print("=== 直接测试后端配置更新功能 ===")
    
    # 1. 获取当前配置
    print("1. 获取当前配置...")
    try:
        response = requests.get(f"{BACKEND_API_BASE}/config")
        if response.status_code == 200:
            current_config = response.json()
            print("✓ 成功获取配置")
            print(f"当前配置: {current_config}")
        else:
            print(f"✗ 获取配置失败，状态码: {response.status_code}")
            return False
    except Exception as e:
        print(f"✗ 获取配置时发生错误: {e}")
        return False
    
    # 2. 备份当前.env文件内容
    print("\n2. 备份当前.env文件内容...")
    try:
        with open(ENV_FILE_PATH, 'r', encoding='utf-8') as f:
            original_env_content = f.read()
        print("✓ 成功备份.env文件")
    except Exception as e:
        print(f"✗ 备份.env文件失败: {e}")
        return False
    
    # 3. 更新配置
    print("\n3. 更新配置...")
    test_config = {
        "api_key": "test_direct_api_key_123",
        "rss_url": "https://example.com/direct-test.xml",
        "qq_email": "direct_test@qq.com",
        "qq_email_password": "direct_test_password",
        "target_email": "direct_test_target@example.com"
    }
    
    try:
        response = requests.post(f"{BACKEND_API_BASE}/config", json=test_config)
        if response.status_code == 200:
            result = response.json()
            if result.get("status") == "success":
                print("✓ 配置更新成功")
            else:
                print(f"✗ 配置更新失败: {result}")
                return False
        else:
            print(f"✗ 配置更新请求失败，状态码: {response.status_code}")
            print(f"响应内容: {response.text}")
            return False
    except Exception as e:
        print(f"✗ 更新配置时发生错误: {e}")
        return False
    
    # 4. 等待一秒，确保配置已写入文件
    time.sleep(1)
    
    # 5. 检查.env文件内容
    print("\n4. 检查.env文件内容...")
    try:
        with open(ENV_FILE_PATH, 'r', encoding='utf-8') as f:
            new_env_content = f.read()
        print("✓ 成功读取更新后的.env文件")
        print("更新后的.env文件内容:")
        print("-" * 40)
        print(new_env_content)
        print("-" * 40)
        
        # 验证关键字段是否已更新
        if ("TEST_DIRECT_API_KEY_123" in new_env_content and
            "https://example.com/direct-test.xml" in new_env_content and
            "direct_test@qq.com" in new_env_content and
            "direct_test_target@example.com" in new_env_content):
            print("✓ .env文件中的配置已成功更新")
        else:
            print("✗ .env文件中的配置未正确更新")
            return False
    except Exception as e:
        print(f"✗ 读取.env文件失败: {e}")
        return False
    
    # 6. 再次获取API配置，验证是否已更新
    print("\n5. 验证API返回的配置是否已更新...")
    try:
        response = requests.get(f"{BACKEND_API_BASE}/config")
        if response.status_code == 200:
            updated_config = response.json()
            print(f"更新后的API配置: {updated_config}")
            
            # 验证关键字段是否已更新
            if (updated_config.get("api_key") == test_config["api_key"] and
                updated_config.get("rss_url") == test_config["rss_url"] and
                updated_config.get("qq_email") == test_config["qq_email"] and
                updated_config.get("target_email") == test_config["target_email"]):
                print("✓ API返回的配置已成功更新")
            else:
                print("✗ API返回的配置未正确更新")
                return False
        else:
            print(f"✗ 验证配置时获取失败，状态码: {response.status_code}")
            return False
    except Exception as e:
        print(f"✗ 验证API配置时发生错误: {e}")
        return False
    
    print("\n✓ 所有测试通过！配置持久化功能正常工作。")
    return True

def main():
    print("开始直接测试后端配置更新功能...")
    print(f"后端API地址: {BACKEND_API_BASE}")
    print(f".env文件路径: {ENV_FILE_PATH}")
    print("=" * 60)
    
    # 测试配置更新功能
    test_result = test_config_update_direct()
    
    print("\n" + "=" * 60)
    print("测试完成！")
    if test_result:
        print("✓ 配置持久化功能测试通过")
        print("✓ 配置可以成功更新并保存到.env文件中")
    else:
        print("✗ 配置持久化功能测试失败")

if __name__ == "__main__":
    main()