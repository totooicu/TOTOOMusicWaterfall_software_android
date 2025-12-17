import requests
import os

print("Environment variables related to proxies:")
for key in os.environ:
    if 'proxy' in key.lower():
        print(f"{key}: {os.environ[key]}")

print("\nTesting different URL formats...")

# 测试不同的URL格式
urls = [
    'http://localhost:8001/api/config',
    'http://127.0.0.1:8001/api/config',
    'http://0.0.0.0:8001/api/config'
]

for url in urls:
    print(f"\nTesting {url}...")
    try:
        # 禁用代理
        response = requests.get(url, proxies={'http': None, 'https': None}, timeout=5)
        print(f"Status: {response.status_code}")
        print(f"Headers: {response.headers}")
        print(f"Response: {response.text}")
    except Exception as e:
        print(f"Error: {type(e).__name__}: {e}")