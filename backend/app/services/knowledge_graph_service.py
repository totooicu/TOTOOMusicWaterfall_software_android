import networkx as nx
from typing import Dict, List, Any
import logging
import re
from collections import Counter
import nltk
from nltk.corpus import stopwords
from nltk.tokenize import word_tokenize
from app.services.summary_service import call_llm_api
from app.services import rss_service
from app.config.config import get_settings

# 配置日志
logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(name)s - %(levelname)s - %(message)s')
logger = logging.getLogger('knowledge_graph_service')

# 下载必要的NLTK数据
try:
    nltk.data.find('tokenizers/punkt')
    nltk.data.find('corpora/stopwords')
except LookupError:
    nltk.download('punkt')
    nltk.download('punkt_tab')
    nltk.download('stopwords')

# 获取英文停用词
stop_words = set(stopwords.words('english'))

# 方法相关的常见词根和前缀
METHOD_ROOT_WORDS = ['model', 'algorithm', 'network', 'method', 'approach', 'technique', 'framework', 
                     'system', 'tool', 'platform', 'mechanism', 'procedure', 'process', 'scheme']

# 领域相关的常见词根和前缀
DOMAIN_ROOT_WORDS = ['science', 'engineering', 'technology', 'application', 'field', 'discipline', 
                     'domain', 'area', 'study', 'research', 'analysis', 'investigation']

def extract_authors(paper: Dict[str, Any]) -> List[str]:
    """从论文中提取作者"""
    if 'authors' in paper and isinstance(paper['authors'], list):
        return paper['authors']
    return []

def extract_keywords_from_text(text: str, root_words: List[str], top_n: int = 20) -> List[str]:
    """从文本中提取关键词"""
    # 清理文本
    text = re.sub(r'[^a-zA-Z\s\-]', '', text)  # 保留字母、空格和连字符
    text = text.lower()
    
    # 分词
    tokens = word_tokenize(text)
    
    # 过滤停用词和短词
    filtered_tokens = [token for token in tokens if token not in stop_words and len(token) > 2]
    
    # 提取包含根词的短语（简单的n-gram分析）
    phrases = []
    for i in range(len(filtered_tokens)):
        # 检查当前词是否包含根词
        if any(root in filtered_tokens[i] for root in root_words):
            # 尝试构建2-gram或3-gram短语
            if i > 0:
                phrases.append(f"{filtered_tokens[i-1]} {filtered_tokens[i]}")
            if i < len(filtered_tokens) - 1:
                phrases.append(f"{filtered_tokens[i]} {filtered_tokens[i+1]}")
            if i > 0 and i < len(filtered_tokens) - 1:
                phrases.append(f"{filtered_tokens[i-1]} {filtered_tokens[i]} {filtered_tokens[i+1]}")
            # 也添加单个词
            phrases.append(filtered_tokens[i])
    
    # 统计短语频率
    phrase_counter = Counter(phrases)
    
    # 返回频率最高的top_n个短语
    return [phrase.capitalize() for phrase, count in phrase_counter.most_common(top_n)]

def extract_methods_from_papers(papers: List[Dict[str, Any]]) -> List[str]:
    """从多篇论文中提取方法关键词"""
    all_text = ""
    for paper in papers:
        paper_title = paper.get('title', '')
        paper_abstract = paper.get('description', '')
        paper_abstract = re.sub(r'<[^>]+>', '', paper_abstract)  # 清理HTML标签
        all_text += paper_title + ' ' + paper_abstract + ' '
    
    return extract_keywords_from_text(all_text, METHOD_ROOT_WORDS, top_n=20)

def extract_domains_from_papers(papers: List[Dict[str, Any]]) -> List[str]:
    """从多篇论文中提取领域关键词"""
    all_text = ""
    for paper in papers:
        paper_title = paper.get('title', '')
        paper_abstract = paper.get('description', '')
        paper_abstract = re.sub(r'<[^>]+>', '', paper_abstract)  # 清理HTML标签
        all_text += paper_title + ' ' + paper_abstract + ' '
    
    return extract_keywords_from_text(all_text, DOMAIN_ROOT_WORDS, top_n=20)

def extract_methods(paper_title: str, paper_abstract: str, method_keywords: List[str]) -> List[str]:
    """从论文标题和摘要中提取方法"""
    extracted_methods = []
    full_text = (paper_title + ' ' + paper_abstract).lower()
    
    for method in method_keywords:
        if method.lower() in full_text:
            extracted_methods.append(method)
    
    return list(set(extracted_methods))

def extract_domains(paper_title: str, paper_abstract: str, domain_keywords: List[str]) -> List[str]:
    """从论文标题和摘要中提取领域"""
    extracted_domains = []
    full_text = (paper_title + ' ' + paper_abstract).lower()
    
    for domain in domain_keywords:
        if domain.lower() in full_text:
            extracted_domains.append(domain)
    
    return list(set(extracted_domains))

def build_knowledge_graph() -> Dict[str, Any]:
    """动态构建知识图谱数据"""
    try:
        # 获取设置
        settings = get_settings()
        
        # 解析RSS获取论文数据
        logger.info(f"开始从RSS获取论文数据: {settings.rss_url}")
        all_papers, this_week_papers = rss_service.parse_rss(settings.rss_url)
        logger.info(f"获取到所有论文 {len(all_papers)} 篇，本周论文 {len(this_week_papers)} 篇")
        
        # 使用本周论文，如果没有则使用所有论文
        papers_to_use = this_week_papers if this_week_papers else all_papers
        
        # 尝试使用AI构建知识库
        try:
            logger.info("尝试使用AI构建知识库")
            ai_built_kg = build_knowledge_graph_with_ai(papers_to_use, settings.silicon_flow_api_key)
            if ai_built_kg and 'nodes' in ai_built_kg and 'links' in ai_built_kg:
                logger.info("AI知识库构建成功")
                return ai_built_kg
        except Exception as e:
            logger.warning(f"AI知识库构建失败，使用备用方法: {str(e)}")
        
        # 如果AI构建失败，使用传统方法
        logger.info("使用传统方法构建知识图谱")
        
        # 动态提取方法和领域关键词
        logger.info("开始动态提取方法和领域关键词")
        METHOD_KEYWORDS = extract_methods_from_papers(papers_to_use)
        DOMAIN_KEYWORDS = extract_domains_from_papers(papers_to_use)
        logger.info(f"动态提取的方法关键词: {METHOD_KEYWORDS}")
        logger.info(f"动态提取的领域关键词: {DOMAIN_KEYWORDS}")
        
        # 如果没有提取到足够的关键词，添加一些默认关键词
        if not METHOD_KEYWORDS:
            METHOD_KEYWORDS = ['Neural Network', 'Algorithm', 'Model', 'Method']
        if not DOMAIN_KEYWORDS:
            DOMAIN_KEYWORDS = ['Research', 'Science', 'Technology', 'Application']
        
        # 创建有向图
        G = nx.DiGraph()

        # 定义颜色映射
        color_map = {
            'Paper': '#FF6B6B',
            'Method': '#4ECDC4',
            'Domain': '#45B7D1',
            'Author': '#96CEB4',
            'Dataset': '#FFEAA7',
            'Metric': '#DDA0DD',
            'Challenge': '#98D8C8'
        }

        # 提取并添加节点
        extracted_authors = set()
        extracted_methods = set()
        extracted_domains = set()
        
        # 为每篇论文创建节点并提取相关实体
        for paper in papers_to_use:
            paper_title = paper.get('title', 'Untitled')
            paper_abstract = paper.get('description', '')
            
            # 清理HTML标签
            paper_abstract = re.sub(r'<[^>]+>', '', paper_abstract)
            
            # 添加论文节点
            G.add_node(paper_title, type='Paper', year=2025)  # 默认年份为当前年
            
            # 提取并添加作者节点
            authors = extract_authors(paper)
            for author in authors:
                if author not in extracted_authors:
                    G.add_node(author, type='Author')
                    extracted_authors.add(author)
                # 添加作者-论文关系
                G.add_edge(paper_title, author, relation='AUTHORED_BY', weight=1)
            
            # 提取并添加方法节点
            methods = extract_methods(paper_title, paper_abstract, METHOD_KEYWORDS)
            for method in methods:
                if method not in extracted_methods:
                    G.add_node(method, type='Method')
                    extracted_methods.add(method)
                # 添加论文-方法关系
                G.add_edge(paper_title, method, relation='PROPOSES', weight=3)
            
            # 提取并添加领域节点
            domains = extract_domains(paper_title, paper_abstract, DOMAIN_KEYWORDS)
            for domain in domains:
                if domain not in extracted_domains:
                    G.add_node(domain, type='Domain')
                    extracted_domains.add(domain)
                # 添加论文-领域关系
                G.add_edge(paper_title, domain, relation='ADDRESSES', weight=2)
        
        # 添加方法之间的关系（简单示例）
        for method in extracted_methods:
            if 'mtgan-kan' in method.lower():
                if any('wgan' in m.lower() for m in extracted_methods):
                    related_method = next(m for m in extracted_methods if 'wgan' in m.lower())
                    G.add_edge(method, related_method, relation='EXTENDS', weight=2)
                if any('kan' in m.lower() for m in extracted_methods):
                    related_method = next(m for m in extracted_methods if 'kan' in m.lower())
                    G.add_edge(method, related_method, relation='INTEGRATES', weight=2)
        
        # 添加方法-领域关系
        for method in extracted_methods:
            for domain in extracted_domains:
                if ('magnetotelluric' in domain.lower() and 'mtgan' in method.lower()) or \
                   ('aerosol' in domain.lower() and 'posp' in method.lower()) or \
                   ('precipitation' in domain.lower() and 'diffusion' in method.lower()):
                    G.add_edge(method, domain, relation='APPLIES_TO', weight=2)
        
        # 如果没有提取到足够的节点，添加一些默认节点以确保图谱可用性
        if len(G.nodes()) < 5:
            logger.info("提取的节点数量不足，添加默认节点")
            default_papers = [
                ("Default Paper 1", {"type": "Paper", "year": 2025}),
                ("Default Paper 2", {"type": "Paper", "year": 2025})
            ]
            default_methods = [
                ("Default Method 1", {"type": "Method", "category": "AI"}),
                ("Default Method 2", {"type": "Method", "category": "ML"})
            ]
            default_domains = [
                ("Default Domain", {"type": "Domain"})
            ]
            
            for node_id, attributes in default_papers + default_methods + default_domains:
                G.add_node(node_id, **attributes)
            
            # 添加默认边
            G.add_edge("Default Paper 1", "Default Method 1", relation="PROPOSES", weight=3)
            G.add_edge("Default Paper 2", "Default Method 2", relation="PROPOSES", weight=3)
            G.add_edge("Default Paper 1", "Default Domain", relation="ADDRESSES", weight=2)
            G.add_edge("Default Paper 2", "Default Domain", relation="ADDRESSES", weight=2)
        
        # 构建返回数据
        # 构建节点数据
        nodes = []
        for node in G.nodes():
            node_type = G.nodes[node]['type']
            nodes.append({
                'id': node,
                'type': node_type,
                'color': color_map.get(node_type, '#ccc')
            })
        
        # 构建边数据
        links = []
        for source, target, attrs in G.edges(data=True):
            links.append({
                'source': source,
                'target': target,
                'type': attrs.get('relation', 'RELATED'),
                'weight': attrs.get('weight', 1)
            })

        logger.info(f"知识图谱构建完成，节点数: {len(nodes)}, 边数: {len(links)}")
        
        return {
            "nodes": nodes,
            "links": links,  # 前端期望的字段名是links，不是edges
            "color_map": color_map
        }

    except Exception as e:
        logger.error(f"构建知识图谱失败: {str(e)}", exc_info=True)
        raise


def build_knowledge_graph_with_ai(papers: List[Dict[str, Any]], api_key: str) -> Dict[str, Any]:
    """
    使用AI构建知识图谱
    
    Args:
        papers: 论文数据列表
        api_key: 硅基流动API密钥
        
    Returns:
        Dict[str, Any]: 知识图谱数据，包含nodes、links和color_map
    """
    try:
        logger.info("开始使用AI构建知识图谱")
        
        # 定义颜色映射
        color_map = {
            'Paper': '#FF6B6B',
            'Method': '#4ECDC4',
            'Domain': '#45B7D1',
            'Author': '#96CEB4',
            'Dataset': '#FFEAA7',
            'Metric': '#DDA0DD',
            'Challenge': '#98D8C8'
        }
        
        # 构建提示词 - 限制知识图谱的复杂度，确保返回完整JSON
        prompt = '''
请你作为一个知识图谱构建专家，根据以下{n_papers}篇论文数据，提取关键实体和关系。

### 核心要求：
1. 严格按照指定JSON格式返回结果，**仅包含JSON数据，不添加任何额外解释或说明**
2. 确保JSON格式**完全正确、完整**，没有语法错误，没有截断
3. 只提取最关键的实体和关系，**每个实体类型最多提取5个**

### 实体类型：
- Paper(论文)
- Method(方法)
- Domain(领域)
- Author(作者)

### 关系类型示例：
PROPOSES, AUTHORED_BY, APPLIES_TO, EXTENDS

### 颜色映射：
{color_map_json}

### 输出格式要求（必须严格遵守）：
```json
{{
    "nodes": [
        {{
            "id": "实体名称",
            "type": "实体类型",
            "color": "对应的颜色代码"
        }}
    ],
    "links": [
        {{
            "source": "源实体ID",
            "target": "目标实体ID",
            "type": "关系类型",
            "weight": 1-10的整数
        }}
    ]
}}
```

### 论文数据：
'''.format(n_papers=len(papers), color_map_json=json.dumps(color_map, ensure_ascii=False))
        
        # 添加论文数据
        for i, paper in enumerate(papers, 1):
            paper_title = paper.get('title', 'Untitled')
            authors = paper.get('authors', [])
            authors_str = ', '.join(authors) if authors else 'Unknown'
            paper_abstract = paper.get('description', '')
            paper_abstract = re.sub(r'<[^>]+>', '', paper_abstract)  # 清理HTML标签
            
            prompt += f"\n=== 论文 {i} ===\n"
            prompt += f"标题: {paper_title}\n"
            prompt += f"作者: {authors_str}\n"
            prompt += f"摘要: {paper_abstract}\n"
        
        prompt += f"\n=== 颜色映射 ===\n"
        prompt += json.dumps(color_map, ensure_ascii=False)
        
        # 调用LLM API构建知识图谱
        logger.info("调用AI构建知识图谱")
        ai_response = call_llm_api(prompt, api_key)
        
        # 解析AI响应
        if not ai_response or "API调用失败" in ai_response:
            logger.warning("AI调用失败，返回空结果")
            return None
        
        # 提取JSON部分
        json_start = ai_response.find('{')
        json_end = ai_response.rfind('}')
        if json_start == -1 or json_end == -1:
            logger.warning("AI响应中没有找到有效的JSON数据")
            logger.debug(f"AI响应内容: {ai_response[:1000]}...")
            return None
        
        json_str = ai_response[json_start:json_end+1]
        
        # 改进JSON解析，增加错误处理和调试信息
        try:
            kg_data = json.loads(json_str)
            logger.info(f"成功解析AI返回的JSON数据")
        except json.decoder.JSONDecodeError as e:
            logger.error(f"JSON解析失败: {str(e)}")
            logger.error(f"错误位置附近的JSON内容: {json_str[max(0, e.pos-20):min(len(json_str), e.pos+20)]}")
            logger.error(f"完整JSON字符串: {json_str}")
            return None
        except Exception as e:
            logger.error(f"解析JSON时发生意外错误: {str(e)}")
            logger.error(f"JSON字符串: {json_str[:1000]}...")
            return None
        
        # 验证数据结构
        if 'nodes' not in kg_data or 'links' not in kg_data:
            logger.warning("AI返回的数据结构不完整")
            return None
        
        # 添加color_map（如果AI没有返回）
        if 'color_map' not in kg_data:
            kg_data['color_map'] = color_map
        
        logger.info(f"AI构建的知识图谱：节点数={len(kg_data['nodes'])}, 边数={len(kg_data['links'])}")
        
        return kg_data
        
    except Exception as e:
        logger.error(f"使用AI构建知识图谱失败: {str(e)}", exc_info=True)
        return None

def analyze_knowledge_graph(graph_data: Dict[str, Any], api_key: str) -> str:
    """
    分析知识图谱并生成AI解读
    
    Args:
        graph_data: 知识图谱数据
        api_key: 硅基流动API密钥
        
    Returns:
        str: AI生成的知识图谱解读
    """
    try:
        logger.info("开始分析知识图谱")
        
        # 提取知识图谱中的关键信息
        nodes = graph_data.get('nodes', [])
        links = graph_data.get('links', [])
        
        # 统计节点类型
        node_types = {}
        for node in nodes:
            node_type = node.get('type')
            if node_type:
                node_types[node_type] = node_types.get(node_type, 0) + 1
        
        # 统计关系类型
        relation_types = {}
        for link in links:
            rel_type = link.get('type')
            if rel_type:
                relation_types[rel_type] = relation_types.get(rel_type, 0) + 1
        
        # 生成分析提示词
        prompt = f"""
        请分析以下知识图谱数据，生成一份详细的解读报告：
        
        ## 知识图谱概述
        - 总节点数：{len(nodes)}个
        - 总关系数：{len(links)}条
        
        ### 节点类型分布
        {', '.join([f'{typ}: {count}个' for typ, count in node_types.items()])}
        
        ### 关系类型分布
        {', '.join([f'{typ}: {count}条' for typ, count in relation_types.items()])}
        
        ## 详细内容
        {json.dumps({'nodes': nodes, 'links': links}, ensure_ascii=False, indent=2)}
        
        ## 分析要求
        1. 识别主要的研究领域和方法
        2. 找出研究热点和趋势
        3. 分析实体之间的关键关系
        4. 总结知识图谱的核心价值
        5. 以清晰、结构化的方式呈现分析结果
        """
        
        # 调用LLM API进行分析
        analysis = call_llm_api(prompt, api_key)
        
        logger.info("知识图谱分析完成")
        return analysis
        
    except Exception as e:
        logger.error(f"分析知识图谱失败: {str(e)}", exc_info=True)
        raise

# 导入json模块
import json

if __name__ == "__main__":
    # 测试构建知识图谱
    try:
        kg_data = build_knowledge_graph()
        print(f"节点数量: {len(kg_data['nodes'])}")
        print(f"边数量: {len(kg_data['links'])}")
        print("知识图谱构建成功!")
        print("节点:", [node['id'] for node in kg_data['nodes']])
    except Exception as e:
        print(f"测试失败: {str(e)}")