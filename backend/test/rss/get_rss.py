import feedparser
import requests
from datetime import datetime
import json

# RSS源URL
RSS_URL = "https://ieeexplore.ieee.org/rss/TOC36.XML"

# 代理设置
proxies = {
    'http': 'http://localhost:10800',
    'https': 'http://localhost:10800'
}

# 请求头
headers = {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36',
    'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8',
    'Accept-Language': 'zh-CN,zh;q=0.9,en;q=0.8'
}


def fetch_and_parse_rss(rss_url):
    """获取并解析RSS源"""
    print(f"开始爬取RSS: {rss_url}")
    
    try:
        # 获取RSS内容（不使用代理）
        response = requests.get(rss_url, headers=headers, timeout=30)
        response.raise_for_status()
        
        # 解析RSS
        feed = feedparser.parse(response.content)
        
        print(f"RSS解析完成，条目数量: {len(feed.entries)}")
        # 检查解析结果
        if feed.bozo:
            print(f"警告: {feed.bozo_exception}")
            
        # 提取内容
        items = []
        for idx, entry in enumerate(feed.entries):
            item = {
                'title': entry.get('title', 'Untitled'),
                'link': entry.get('link', '#'),
                'description': entry.get('description', ''),
                'published': entry.get('published', ''),
                'author': entry.get('author', ''),
                'categories': entry.get('categories', [])
            }
            items.append(item)
            
            # 打印前几个条目的基本信息
            if idx < 5:
                print(f"\n条目 #{idx + 1}:")
                print(f"标题: {item['title']}")
                print(f"链接: {item['link']}")
                print(f"发布时间: {item['published']}")
        
        return {
            'feed_info': {
                'title': feed.feed.get('title', ''),
                'link': feed.feed.get('link', ''),
                'description': feed.feed.get('description', ''),
                'updated': feed.feed.get('updated', '')
            },
            'items': items
        }
        
    except Exception as e:
        print(f"爬取失败: {str(e)}")
        return None


def save_to_file(data, filename):
    """将数据保存到JSON文件"""
    if data:
        with open(filename, 'w', encoding='utf-8') as f:
            json.dump(data, f, ensure_ascii=False, indent=2)
        print(f"\n数据已保存到: {filename}")


if __name__ == "__main__":
    print("=== RSS爬取脚本 ===")
    
    # 爬取RSS
    result = fetch_and_parse_rss(RSS_URL)
    
    if result:
        print(f"\n=== 爬取结果 ===")
        print(f"Feed标题: {result['feed_info']['title']}")
        print(f"条目总数: {len(result['items'])}")
        
        # 保存到文件
        timestamp = datetime.now().strftime('%Y%m%d_%H%M%S')
        filename = f'rss_data_{timestamp}.json'
        save_to_file(result, filename)
        
        print("\n爬取完成！")
    else:
        print("\n爬取失败！")
