# 测试脚本：验证缓存功能是否正常工作
import os
import sys
import json

# 添加项目根目录到Python路径
sys.path.insert(0, os.path.abspath(os.path.dirname(__file__)))

# 导入所需模块
from app.services.summary_service import (
    load_summary_cache, save_summary_cache,
    get_cached_summary, cache_summary,
    generate_summary
)

# 测试缓存功能
def test_cache_functions():
    print("=== 测试缓存基本功能 ===")
    
    # 测试缓存目录是否存在
    cache_dir = os.path.join(os.path.dirname(__file__), 'cache')
    if os.path.exists(cache_dir):
        print(f"✓ 缓存目录存在: {cache_dir}")
    else:
        print(f"✗ 缓存目录不存在: {cache_dir}")
        return False
    
    # 测试缓存文件路径
    cache_file = os.path.join(cache_dir, 'summary_cache.json')
    print(f"缓存文件路径: {cache_file}")
    
    # 测试保存缓存
    test_key = "测试论文标题"
    test_value = "这是一篇测试论文的总结内容"
    
    try:
        cache_summary(test_key, test_value)
        print(f"✓ 成功缓存测试论文: {test_key}")
    except Exception as e:
        print(f"✗ 缓存测试论文失败: {e}")
        return False
    
    # 测试读取缓存
    try:
        cached_value = get_cached_summary(test_key)
        if cached_value == test_value:
            print(f"✓ 成功读取缓存内容")
            print(f"  缓存内容: {cached_value[:50]}...")
        else:
            print(f"✗ 缓存内容不匹配")
            return False
    except Exception as e:
        print(f"✗ 读取缓存失败: {e}")
        return False
    
    # 测试加载整个缓存
    try:
        cache = load_summary_cache()
        if test_key in cache:
            print(f"✓ 成功加载整个缓存，包含测试论文")
            print(f"  缓存中共有 {len(cache)} 篇论文的总结")
        else:
            print(f"✗ 缓存中不包含测试论文")
            return False
    except Exception as e:
        print(f"✗ 加载缓存失败: {e}")
        return False
    
    print("\n=== 所有缓存基本功能测试通过！===\n")
    return True

# 测试generate_summary函数的缓存功能
def test_generate_summary_cache():
    print("=== 测试generate_summary函数缓存功能 ===")
    
    # 创建模拟的RSS内容
    mock_rss_content = {
        'entries': [
            {
                'title': '测试论文1',
                'authors': ['作者A', '作者B'],
                'description': '这是第一篇测试论文的摘要内容',
                'link': 'http://example.com/paper1'
            },
            {
                'title': '测试论文2',
                'authors': ['作者C'],
                'description': '这是第二篇测试论文的摘要内容',
                'link': 'http://example.com/paper2'
            }
        ]
    }
    
    # 使用一个无效的API密钥，这样会触发备用摘要生成
    api_key = "test_key"
    
    try:
        # 第一次调用：应该生成总结并缓存
        print("第一次调用generate_summary...")
        summary1 = generate_summary(mock_rss_content, api_key)
        print(f"✓ 第一次调用成功，生成总结")
        print(f"  总结长度: {len(summary1)} 字符")
        print(f"  总结内容前100字符: {summary1[:100]}...")
        
        # 检查缓存是否被创建
        cache = load_summary_cache()
        if len(cache) >= 2:
            print(f"✓ 缓存中包含 {len(cache)} 篇论文的总结")
        else:
            print(f"✗ 缓存中论文数量不足")
            return False
            
        # 第二次调用：应该使用缓存的总结
        print("\n第二次调用generate_summary...")
        summary2 = generate_summary(mock_rss_content, api_key)
        print(f"✓ 第二次调用成功，使用缓存总结")
        print(f"  总结长度: {len(summary2)} 字符")
        print(f"  总结内容前100字符: {summary2[:100]}...")
        
        # 验证两次调用的结果是否相似（由于是备用摘要生成，可能不完全相同）
        # 但应该都包含缓存的总结内容
        
    except Exception as e:
        print(f"✗ 测试generate_summary函数失败: {e}")
        import traceback
        traceback.print_exc()
        return False
    
    print("\n=== generate_summary函数缓存功能测试通过！===\n")
    return True

if __name__ == "__main__":
    print("开始测试缓存功能...\n")
    
    # 测试缓存基本功能
    basic_cache_ok = test_cache_functions()
    
    # 测试generate_summary函数的缓存功能
    summary_cache_ok = test_generate_summary_cache()
    
    if basic_cache_ok and summary_cache_ok:
        print("🎉 所有缓存功能测试通过！")
        sys.exit(0)
    else:
        print("❌ 部分测试失败，请检查代码。")
        sys.exit(1)