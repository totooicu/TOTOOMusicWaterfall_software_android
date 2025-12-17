import requests
import json

# 禁用代理设置
proxies = {'http': None, 'https': None}
BASE_URL = 'http://localhost:8001'

def test_endpoint(name, method, url, data=None, expected_status=200):
    """测试单个API端点"""
    print(f"\n=== Testing {name} ===")
    print(f"URL: {url}")
    print(f"Method: {method}")
    if data:
        print(f"Data: {json.dumps(data, indent=2)}")
    
    try:
        if method == 'GET':
            response = requests.get(url, proxies=proxies, timeout=10)
        elif method == 'POST':
            response = requests.post(url, json=data, proxies=proxies, timeout=10)
        
        print(f"Status: {response.status_code}")
        print(f"Headers: {dict(response.headers)}")
        
        try:
            print(f"Response: {json.dumps(response.json(), ensure_ascii=False, indent=2)}")
        except json.JSONDecodeError:
            print(f"Response (raw): {response.text}")
        
        if response.status_code == expected_status:
            print("✅ Test PASSED")
            return True
        else:
            print(f"❌ Test FAILED - Expected status {expected_status}, got {response.status_code}")
            return False
    except Exception as e:
        print(f"❌ Test FAILED with error: {type(e).__name__}: {e}")
        return False

# 运行所有测试
def run_all_tests():
    """运行所有API端点测试"""
    print("🚀 Starting comprehensive API tests...")
    
    # 1. 测试根路径
    test_endpoint(
        name="Root Path",
        method="GET",
        url=f"{BASE_URL}/"
    )
    
    # 2. 测试获取配置
    config = test_endpoint(
        name="Get Config",
        method="GET",
        url=f"{BASE_URL}/api/config"
    )
    
    # 3. 测试更新配置
    test_endpoint(
        name="Update Config",
        method="POST",
        url=f"{BASE_URL}/api/config",
        data={
            "api_key": "test-api-key",
            "rss_url": "https://example.com/rss.xml",
            "qq_email": "test@example.com",
            "target_email": "target@example.com"
        }
    )
    
    # 4. 测试获取RSS
    test_endpoint(
        name="Get RSS",
        method="GET",
        url=f"{BASE_URL}/api/rss"
    )
    
    # 5. 测试论文摘要
    test_endpoint(
        name="Generate Summary",
        method="POST",
        url=f"{BASE_URL}/api/summarize",
        data={"url": "https://arxiv.org/abs/2301.00001"}
    )
    
    # 6. 测试发送邮件
    test_endpoint(
        name="Send Email",
        method="POST",
        url=f"{BASE_URL}/api/send-email",
        data={
            "to": "test@example.com",
            "subject": "Test Email",
            "body": "This is a test email."
        }
    )
    
    # 7. 测试问答功能
    test_endpoint(
        name="QA Function",
        method="POST",
        url=f"{BASE_URL}/api/qa",
        data={
            "question": "What is the capital of France?",
            "context": "France is a country in Europe. Its capital is Paris."
        }
    )
    
    # 8. 测试周报功能
    test_endpoint(
        name="Weekly Summary",
        method="POST",
        url=f"{BASE_URL}/api/weekly-summary"
    )
    
    print("\n🎉 All tests completed!")

if __name__ == "__main__":
    run_all_tests()