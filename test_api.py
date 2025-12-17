import requests

# 测试根路径
try:
    # 禁用代理以避免连接问题
    response = requests.get('http://localhost:8001/', proxies={'http': None, 'https': None}, timeout=5)
    print(f'Root path - Status: {response.status_code}')
    print(f'Root path - Response: {response.text}')
except Exception as e:
    print(f'Root path - Error: {e}')

# 测试 /api/config 接口
try:
    # 禁用代理以避免连接问题
    response = requests.get('http://localhost:8001/api/config', proxies={'http': None, 'https': None}, timeout=5)
    print(f'\nConfig API - Status: {response.status_code}')
    print(f'Config API - Response: {response.text}')
except Exception as e:
    print(f'\nConfig API - Error: {e}')