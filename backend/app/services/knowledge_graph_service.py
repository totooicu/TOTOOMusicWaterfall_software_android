import networkx as nx
from typing import Dict, List, Any
import logging
from app.services.summary_service import call_llm_api

# 配置日志
logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(name)s - %(levelname)s - %(message)s')
logger = logging.getLogger('knowledge_graph_service')


def build_knowledge_graph() -> Dict[str, Any]:
    """
    构建知识图谱并返回节点和边的数据
    """
    try:
        # 创建有向图
        G = nx.DiGraph()

        # 定义颜色映射
        color_map = {
            'Paper': '#4A90E2',      # 蓝色
            'Method': '#7ED321',     # 绿色
            'Domain': '#F5A623',     # 橙色
            'Author': '#9013FE',     # 紫色
            'Dataset': '#50E3C2',    # 青色
            'Location': '#D0021B',   # 红色
            'Evaluation': '#F8E71C'  # 黄色
        }

        # 添加节点 - 论文
        papers = [
            ("MTGAN-KAN Paper", {"type": "Paper", "year": 2025}),
            ("Aerosol Algorithm Paper", {"type": "Paper", "year": 2025}),
            ("3D Polarized RT Paper", {"type": "Paper", "year": 2025}),
            ("Rainfusion Paper", {"type": "Paper", "year": 2025}),
            ("HSTGNN Paper", {"type": "Paper", "year": 2025}),
            ("TC Intensity Paper", {"type": "Paper", "year": 2025}),
            ("Carbon Emissions Paper", {"type": "Paper", "year": 2025})
        ]

        # 添加节点 - 方法
        methods = [
            ("MTGAN-KAN", {"type": "Method", "category": "Physics-Informed ML"}),
            ("WGAN", {"type": "Method", "category": "GAN"}),
            ("KAN", {"type": "Method", "category": "Neural Network"}),
            ("LBM", {"type": "Method", "category": "Numerical"}),
            ("Diffusion Model", {"type": "Method", "category": "Generative"}),
            ("Prandtl's Theory", {"type": "Method", "category": "Physics"}),
            ("HSTGNN", {"type": "Method", "category": "Graph NN"}),
            ("Dilated-CBAM", {"type": "Method", "category": "CNN"}),
            ("POSP Algorithm", {"type": "Method", "category": "Retrieval"})
        ]

        # 添加节点 - 领域
        domains = [
            ("Magnetotelluric\nInversion", {"type": "Domain"}),
            ("Aerosol\nRetrieval", {"type": "Domain"}),
            ("Polarized\nRadiative Transfer", {"type": "Domain"}),
            ("Precipitation\nNowcasting", {"type": "Domain"}),
            ("Meteorological\nForecasting", {"type": "Domain"}),
            ("TC Intensity\nEstimation", {"type": "Domain"}),
            ("Carbon Emissions\nMonitoring", {"type": "Domain"})
        ]

        # 添加节点 - 作者（部分）
        authors = [
            ("Fuying Yang", {"type": "Author"}),
            ("Zhe Ji", {"type": "Author"}),
            ("Mingqi Liu", {"type": "Author"}),
            ("Dawei Li", {"type": "Author"}),
            ("Sheng Li", {"type": "Author"}),
            ("Hyeyoon Jung", {"type": "Author"})
        ]

        # 添加所有节点
        all_nodes = papers + methods + domains + authors
        for node_id, attributes in all_nodes:
            G.add_node(node_id, **attributes)

        # 添加边（关系）
        edges = [
            # MTGAN-KAN 论文相关
            ("MTGAN-KAN Paper", "MTGAN-KAN", {"relation": "PROPOSES", "weight": 3}),
            ("MTGAN-KAN Paper", "Magnetotelluric\nInversion", {"relation": "ADDRESSES", "weight": 2}),
            ("MTGAN-KAN Paper", "Fuying Yang", {"relation": "AUTHORED_BY", "weight": 1}),
            ("MTGAN-KAN", "WGAN", {"relation": "EXTENDS", "weight": 2}),
            ("MTGAN-KAN", "KAN", {"relation": "INTEGRATES", "weight": 2}),
            
            # Aerosol 论文相关
            ("Aerosol Algorithm Paper", "POSP Algorithm", {"relation": "PROPOSES", "weight": 3}),
            ("Aerosol Algorithm Paper", "Aerosol\nRetrieval", {"relation": "ADDRESSES", "weight": 2}),
            ("Aerosol Algorithm Paper", "Zhe Ji", {"relation": "AUTHORED_BY", "weight": 1}),
            
            # 3D Polarized 论文相关
            ("3D Polarized RT Paper", "LBM", {"relation": "PROPOSES", "weight": 3}),
            ("3D Polarized RT Paper", "Polarized\nRadiative Transfer", {"relation": "ADDRESSES", "weight": 2}),
            ("3D Polarized RT Paper", "Mingqi Liu", {"relation": "AUTHORED_BY", "weight": 1}),
            
            # Rainfusion 论文相关
            ("Rainfusion Paper", "Diffusion Model", {"relation": "PROPOSES", "weight": 3}),
            ("Rainfusion Paper", "Prandtl's Theory", {"relation": "INTEGRATES", "weight": 2}),
            ("Rainfusion Paper", "Precipitation\nNowcasting", {"relation": "ADDRESSES", "weight": 2}),
            ("Rainfusion Paper", "Dawei Li", {"relation": "AUTHORED_BY", "weight": 1}),
            
            # HSTGNN 论文相关
            ("HSTGNN Paper", "HSTGNN", {"relation": "PROPOSES", "weight": 3}),
            ("HSTGNN Paper", "Meteorological\nForecasting", {"relation": "ADDRESSES", "weight": 2}),
            ("HSTGNN Paper", "Sheng Li", {"relation": "AUTHORED_BY", "weight": 1}),
            
            # TC Intensity 论文相关
            ("TC Intensity Paper", "TC Intensity\nEstimation", {"relation": "ADDRESSES", "weight": 2}),
            ("TC Intensity Paper", "Hyeyoon Jung", {"relation": "AUTHORED_BY", "weight": 1}),
            
            # 方法与领域的关系
            ("MTGAN-KAN", "Magnetotelluric\nInversion", {"relation": "APPLIES_TO", "weight": 2}),
            ("Diffusion Model", "Prandtl's Theory", {"relation": "COMBINES_WITH", "weight": 1}),
            ("POSP Algorithm", "Aerosol\nRetrieval", {"relation": "APPLIES_TO", "weight": 2}),
        ]

        for source, target, attributes in edges:
            G.add_edge(source, target, **attributes)

        # 计算节点位置（使用spring布局）
        pos = nx.spring_layout(G, k=3, iterations=50, seed=42)

        # 构建返回数据
        # 更新颜色映射以匹配前端图例
        color_map = {
            'Paper': '#FF6B6B',
            'Method': '#4ECDC4',
            'Domain': '#45B7D1',
            'Author': '#96CEB4',
            'Dataset': '#FFEAA7',
            'Metric': '#DDA0DD',
            'Challenge': '#98D8C8'
        }
        
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

        return {
            "nodes": nodes,
            "links": links,  # 前端期望的字段名是links，不是edges
            "color_map": color_map
        }

    except Exception as e:
        logger.error(f"构建知识图谱失败: {str(e)}", exc_info=True)
        raise

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
    except Exception as e:
        print(f"测试失败: {str(e)}")