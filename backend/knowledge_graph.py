import networkx as nx
import matplotlib.pyplot as plt
from matplotlib.patches import FancyBboxPatch
import numpy as np

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
    
    # 跨论文关系
    ("Diffusion Model", "Prandtl's Theory", {"relation": "COMBINES_WITH", "weight": 1}),
    ("POSP Algorithm", "Aerosol\nRetrieval", {"relation": "APPLIES_TO", "weight": 2}),
]

for source, target, attributes in edges:
    G.add_edge(source, target, **attributes)

# 创建可视化
plt.figure(figsize=(16, 12))

# 使用不同的布局
pos = nx.spring_layout(G, k=3, iterations=50, seed=42)

# 按节点类型设置颜色和大小
node_colors = []
node_sizes = []
for node in G.nodes():
    node_type = G.nodes[node].get('type', 'Paper')
    node_colors.append(color_map.get(node_type, '#CCCCCC'))
    # 根据节点类型设置不同大小
    if node_type == 'Paper':
        node_sizes.append(3000)
    elif node_type == 'Method':
        node_sizes.append(2500)
    elif node_type == 'Domain':
        node_sizes.append(2800)
    elif node_type == 'Author':
        node_sizes.append(1800)
    else:
        node_sizes.append(2000)

# 绘制节点
nx.draw_networkx_nodes(G, pos, 
                       node_color=node_colors,
                       node_size=node_sizes,
                       alpha=0.9)

# 绘制边
edge_weights = [G[u][v]['weight'] for u, v in G.edges()]
nx.draw_networkx_edges(G, pos,
                       width=[w*0.8 for w in edge_weights],
                       alpha=0.6,
                       edge_color='gray',
                       arrowsize=20,
                       arrowstyle='->')

# 绘制标签 - 使用不同字体大小
labels = {}
for node in G.nodes():
    labels[node] = node

# 绘制节点标签
nx.draw_networkx_labels(G, pos, labels, font_size=9, font_weight='bold')

# 绘制边标签（关系类型）
edge_labels = {(u, v): d['relation'] for u, v, d in G.edges(data=True)}
nx.draw_networkx_edge_labels(G, pos, edge_labels, font_size=7, alpha=0.8)

# 添加图例
legend_elements = []
for node_type, color in color_map.items():
    legend_elements.append(plt.Line2D([0], [0], marker='o', color='w', 
                                     label=node_type, markerfacecolor=color, 
                                     markersize=10))

plt.legend(handles=legend_elements, loc='upper left', bbox_to_anchor=(1, 1))
plt.title("Remote Sensing & Meteorology Papers Knowledge Graph", fontsize=16, fontweight='bold')
plt.axis('off')
plt.tight_layout()
plt.show()