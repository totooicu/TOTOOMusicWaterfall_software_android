import requests
import json
import logging
import os
from typing import Dict, List, Any
# import rss_service
# from backend.app.services import rss_service


# 配置日志
logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(name)s - %(levelname)s - %(message)s')
logger = logging.getLogger('summary_service')

# 缓存配置
CACHE_DIR = os.path.join(os.path.dirname(os.path.dirname(os.path.dirname(__file__))), 'cache')
SUMMARY_CACHE_FILE = os.path.join(CACHE_DIR, 'paper_summaries.json')
LLM_CACHE_FILE = os.path.join(CACHE_DIR, 'llm_api_cache.json')


# 确保缓存目录存在
os.makedirs(CACHE_DIR, exist_ok=True)


def load_summary_cache() -> Dict[str, str]:
    """加载总结缓存"""
    try:
        if os.path.exists(SUMMARY_CACHE_FILE):
            with open(SUMMARY_CACHE_FILE, 'r', encoding='utf-8') as f:
                return json.load(f)
    except Exception as e:
        logger.error(f"加载缓存时发生错误: {e}")
    return {}


def save_summary_cache(cache: Dict[str, str]) -> bool:
    """保存总结缓存"""
    try:
        with open(SUMMARY_CACHE_FILE, 'w', encoding='utf-8') as f:
            json.dump(cache, f, ensure_ascii=False, indent=2)
        return True
    except Exception as e:
        logger.error(f"保存缓存时发生错误: {e}")
        return False


def get_cached_summary(paper_title: str) -> str:
    """获取指定论文的缓存总结"""
    cache = load_summary_cache()
    return cache.get(paper_title)


def cache_summary(paper_title: str, summary: str) -> bool:
    """缓存指定论文的总结"""
    cache = load_summary_cache()
    cache[paper_title] = summary
    return save_summary_cache(cache)

def generate_summary(rss_content: Dict[str, Any], api_key: str) -> str:
    """基于RSS内容生成论文总结，支持单篇论文缓存"""
    try:
        logger.info("开始生成论文总结")
        # 提取本周论文信息
        papers = rss_content.get('entries', [])
        logger.info(f"提取到 {len(papers)} 篇论文")
        
        if not papers:
            logger.info("没有找到论文，返回空摘要")
            return "本周没有新论文发布。"
        
        # 检查每篇论文的缓存情况
        cached_summaries = {}
        papers_to_summarize = []
        
        for paper in papers:
            paper_title = paper.get('title', 'Untitled')
            cached_summary = get_cached_summary(paper_title)
            if cached_summary:
                logger.info(f"找到缓存的论文总结: {paper_title}")
                cached_summaries[paper_title] = cached_summary
            else:
                papers_to_summarize.append(paper)
        
        # 如果所有论文都有缓存，直接生成综合报告
        if not papers_to_summarize:
            logger.info("所有论文都有缓存，直接生成综合报告")
            return generate_comprehensive_summary(papers, cached_summaries)
        
        # 为没有缓存的论文生成新的总结
        logger.info(f"需要为 {len(papers_to_summarize)} 篇论文生成新的总结")
        
        # 构建提示词
        prompt = f"请为以下{len(papers_to_summarize)}篇本周新发表的论文生成一份结构化的总结报告。"
        prompt += "\n\n每篇论文请包含：标题、作者、主要内容摘要和潜在的应用价值。"
        prompt += "\n最后，请总结本周论文的整体趋势和亮点。\n\n"
        
        for i, paper in enumerate(papers_to_summarize, 1):
            prompt += f"论文 {i}:\n"
            prompt += f"标题: {paper.get('title', 'Untitled')}\n"
            
            if 'authors' in paper:
                authors_str = ', '.join(paper['authors'])
                prompt += f"作者: {authors_str}\n"
            
            description = paper.get('description', '')
            # 清理HTML标签
            import re
            description = re.sub(r'<[^>]+>', '', description)
            
            prompt += f"摘要: {description}\n"
            prompt += f"链接: {paper.get('link', '#')}\n\n"
        
        # 调用硅基流动API
        logger.info("调用AI总结API")
        new_summary = call_llm_api(prompt, api_key)
        logger.info(f"API返回结果: {'成功' if new_summary and 'API调用失败' not in new_summary else '失败'}")
        
        # 如果API调用失败，使用备用方法
        if not new_summary or "API调用失败" in new_summary:
            logger.warning("AI API调用失败，使用备用摘要生成方法")
            new_summary = generate_fallback_summary(papers_to_summarize)
        
        # 缓存新生成的总结
        # 注意：这里我们将整个新生成的总结作为每篇新论文的缓存
        # 在实际应用中，可能需要更精细地从综合报告中提取单篇论文的总结
        for paper in papers_to_summarize:
            paper_title = paper.get('title', 'Untitled')
            cache_summary(paper_title, new_summary)
            cached_summaries[paper_title] = new_summary
            logger.info(f"成功缓存论文总结: {paper_title}")
        
        # 生成最终的综合报告
        return new_summary
    except Exception as e:
        logger.error(f"生成摘要时发生异常: {e}", exc_info=True)
        # 返回备用摘要
        return generate_fallback_summary(rss_content.get('entries', []))


def generate_comprehensive_summary(papers: List[Dict[str, Any]], summaries: Dict[str, str]) -> str:
    """基于单篇论文的总结生成综合报告"""
    logger.info("生成综合报告")
    
    if not papers:
        return "本周没有新论文。"
    
    # 检查是否所有论文都共享相同的总结（通常是缓存的情况）
    if len(set(summaries.values())) == 1 and papers:
        # 如果所有论文都共享同一个总结，直接返回该总结
        return next(iter(summaries.values()))
    
    # 如果每个论文有不同的总结，构建综合报告
    report = f"本周共有 {len(papers)} 篇新论文发布：\n\n"
    
    for i, paper in enumerate(papers, 1):
        paper_title = paper.get('title', 'Untitled')
        report += f"=== 论文 {i} ===\n"
        report += f"标题: {paper_title}\n"
        
        if 'authors' in paper:
            authors_str = ', '.join(paper['authors'])
            report += f"作者: {authors_str}\n"
        
        # 添加缓存的总结
        if paper_title in summaries:
            report += f"\n总结: {summaries[paper_title]}\n\n"
        
        report += "=" * 50 + "\n\n"
    
    report += "最后，请总结本周论文的整体趋势和亮点。"
    return report

def answer_question(question: str, context: str, api_key: str) -> str:
    """基于上下文回答问题"""
    try:
        prompt = f"基于以下论文总结内容，请回答问题：\n\n"
        prompt += f"上下文: {context}\n\n"
        prompt += f"问题: {question}\n\n"
        prompt += "请提供详细、准确的回答。"
        
        # 调用硅基流动API
        answer = call_llm_api(prompt, api_key)
        
        # 如果API调用失败，返回备用回答
        if not answer or "API调用失败" in answer:
            return f"无法回答问题：{question}。请检查API配置。"
        
        return answer
    except Exception as e:
        logger.error(f"回答问题时出错: {e}", exc_info=True)
        return f"回答问题时出错: {str(e)}"

def call_llm_api(prompt: str, api_key: str) -> str:
    #查询/cache/call_llm_api.json缓存
    cache={}
    try:
        with open(LLM_CACHE_FILE, 'r', encoding='utf-8') as f:
            cache = json.load(f)
            if prompt in cache:
                return cache[prompt]
    except FileNotFoundError:
        cache = {}
    
    """调用硅基流动API"""
    try:
        logger.info(f"准备调用硅基流动API，API密钥长度: {len(api_key) if api_key else 0}")
        url = "https://api.siliconflow.cn/v1/chat/completions"
        headers = {
            "Content-Type": "application/json",
            "Authorization": f"Bearer {api_key}"
        }
        
        data = {
            "model": "Qwen/QwQ-32B",  # 使用适合的模型
            "messages": [
                {"role": "system", "content": "你是一位专业的学术论文总结助手。请用中文回复。"},
                {"role": "user", "content": prompt}
            ],
            "max_tokens": 2000,
            "temperature": 0.3
        }
        
        logger.info("发送API请求...")
        # 禁用代理以避免连接问题
        session = requests.Session()
        session.trust_env = False
        response = session.post(url, headers=headers, data=json.dumps(data), timeout=600)
        
        logger.info(f"API响应状态码: {response.status_code}")
        logger.info(f"API响应内容: {response.text[:500]}...")
        
        response.raise_for_status()
        
        result = response.json()
        if "choices" in result and len(result["choices"]) > 0:
            cache[prompt]=result["choices"][0]["message"]["content"]
            # 写入缓存文件
            with open(LLM_CACHE_FILE, 'w', encoding='utf-8') as f:
                json.dump(cache, f, ensure_ascii=False, indent=2)
            return result["choices"][0]["message"]["content"]
        else:
            logger.warning("API返回成功但没有choices字段")
            return "API调用成功，但未返回内容。"
    except requests.exceptions.HTTPError as e:
        logger.error(f"HTTP错误: {e.response.status_code} - {e.response.text}")
        return "API调用失败 - HTTP错误"
    except requests.exceptions.ConnectionError as e:
        logger.error(f"连接错误: {e}")
        return "API调用失败 - 连接错误"
    except requests.exceptions.Timeout as e:
        logger.error(f"超时错误: {e}")
        return "API调用失败 - 超时错误"
    except Exception as e:
        logger.error(f"API调用失败: {e}", exc_info=True)
        return "API调用失败"

def generate_fallback_summary(papers: List[Dict[str, Any]]) -> str:
    """备用的摘要生成方法"""
    logger.info("使用备用摘要生成方法")
    if not papers:
        return "本周没有新论文。"
    
    summary = f"本周共有 {len(papers)} 篇新论文发布：\n\n"
    
    for i, paper in enumerate(papers, 1):
        summary += f"{i}. 标题: {paper.get('title', 'Untitled')}\n"
        
        if 'authors' in paper:
            authors_str = ', '.join(paper['authors'])
            summary += f"   作者: {authors_str}\n"
        
        # 截取描述的前200个字符
        description = paper.get('description', '')
        import re
        description = re.sub(r'<[^>]+>', '', description)
        if len(description) > 200:
            description = description[:200] + "..."
        
        summary += f"   摘要: {description}\n"
        summary += f"   链接: {paper.get('link', '#')}\n\n"
    
    summary += "由于API调用限制，无法生成详细的分析总结。"
    return summary

if __name__ == "__main__":
    # 从命令行参数获取配置文件路径
    try:
        import sys
        import os
        sys.path.append(os.path.dirname(os.path.dirname(os.path.dirname(__file__))))
        from app.services import rss_service
        from app.config.config import get_settings
        
        # 获取配置
        settings = get_settings()
        
        # 解析RSS
        all_papers, this_week_papers = rss_service.parse_rss(settings.rss_url)
        
        # 构建符合summary_service预期的格式
        rss_content_for_summary = {
            "entries": this_week_papers
        }
        
        # 生成总结
        summary = generate_summary(rss_content_for_summary, settings.silicon_flow_api_key)
        print(summary)
    except Exception as e:
        logger.error(f"测试运行失败: {e}", exc_info=True)
        print(f"测试运行失败: {str(e)}")