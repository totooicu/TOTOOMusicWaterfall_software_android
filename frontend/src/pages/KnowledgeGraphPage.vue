<template>
  <div class="knowledge-graph-container">
    <h1>论文知识图谱</h1>
    <div class="graph-explanation">
      <h3>知识图谱说明</h3>
      <p>本知识图谱展示了论文、方法、领域、作者等实体之间的关系。</p>
      <ul>
        <li><strong>节点类型：</strong>不同颜色代表不同类型的实体</li>
        <li><strong>边：</strong>带箭头的线条表示实体间的关系，线条粗细表示关系权重</li>
        <li><strong>交互方法：</strong>鼠标滚轮缩放视图，拖拽空白区域平移，拖拽节点重新布局</li>
      </ul>
    </div>
    <div class="graph-controls">
      <button @click="loadData" :disabled="isLoading">
        {{ isLoading ? '加载中...' : '加载数据' }}
      </button>
      <button @click="analyzeGraph" :disabled="isAnalyzing || !graphData">
        {{ isAnalyzing ? '分析中...' : 'AI解读图谱' }}
      </button>
    </div>
    <div class="graph-container">
      <svg id="graph"></svg>
    </div>
    <div class="graph-legend">
      <h3>图例</h3>
      <div class="legend-items">
        <div v-for="(color, type) in colorMap" :key="type" class="legend-item">
          <div class="legend-color" :style="{ backgroundColor: color }"></div>
          <span class="legend-text">{{ type }}</span>
        </div>
      </div>
    </div>
    <div v-if="analysisResult" class="graph-analysis">
      <h3>AI知识图谱解读</h3>
      <div class="analysis-content" v-html="formattedAnalysisResult"></div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, nextTick, computed } from 'vue'
import * as d3 from 'd3'
import { fetchKnowledgeGraphData, analyzeKnowledgeGraph } from '../services/knowledgeGraphAPI'
import { marked } from 'marked'
import DOMPurify from 'dompurify'

// 状态管理
const isLoading = ref(false)
const isAnalyzing = ref(false)
const analysisResult = ref(null)
let graphData = null
let svg = null
let simulation = null

// 计算属性：将Markdown转换为安全的HTML
const formattedAnalysisResult = computed(() => {
  if (!analysisResult.value) return ''
  // 使用marked将Markdown转换为HTML
  const rawHtml = marked(analysisResult.value)
  // 使用DOMPurify净化HTML以防止XSS攻击
  return DOMPurify.sanitize(rawHtml)
})

// 图表配置
const width = 1000
const height = 700
const nodeRadius = 10
const linkDistance = 100

// 颜色映射
const colorMap = {
  'Paper': '#FF6B6B',
  'Method': '#4ECDC4',
  'Domain': '#45B7D1',
  'Author': '#96CEB4',
  'Dataset': '#FFEAA7',
  'Metric': '#DDA0DD',
  'Challenge': '#98D8C8'
}

// 加载数据
const loadData = async () => {
  try {
    isLoading.value = true
    console.log('开始加载知识图谱数据...')
    graphData = await fetchKnowledgeGraphData()
    console.log('数据加载成功:', graphData)
    await nextTick()
    drawKnowledgeGraph()
    // 清除之前的分析结果
    analysisResult.value = null
  } catch (error) {
    console.error('加载数据失败:', error)
  } finally {
    isLoading.value = false
  }
}

// AI解读图谱
const analyzeGraph = async () => {
  try {
    isAnalyzing.value = true
    console.log('开始AI解读知识图谱...')
    const result = await analyzeKnowledgeGraph()
    console.log('AI解读完成:', result)
    analysisResult.value = result.analysis
  } catch (error) {
    console.error('AI解读失败:', error)
    alert('AI解读失败，请稍后重试')
  } finally {
    isAnalyzing.value = false
  }
}

// 绘制知识图谱
const drawKnowledgeGraph = () => {
  if (!graphData || !graphData.nodes || !graphData.links) {
    console.error('无效的图谱数据')
    return
  }

  // 清除现有图表
  d3.select('#graph').selectAll('*').remove()

  // 创建SVG
  svg = d3.select('#graph')
    .attr('width', width)
    .attr('height', height)

  // 添加缩放平移功能
  const g = svg.append('g')
  
  const zoom = d3.zoom()
    .scaleExtent([0.1, 4]) // 缩放范围
    .on('zoom', (event) => {
      g.attr('transform', event.transform)
    })

  svg.call(zoom)

  // 创建箭头标记
  svg.append('defs').selectAll('marker')
    .data(['arrow'])
    .enter().append('marker')
    .attr('id', 'arrow')
    .attr('viewBox', '0 -5 10 10')
    .attr('refX', 25)
    .attr('refY', 0)
    .attr('markerWidth', 6)
    .attr('markerHeight', 6)
    .attr('orient', 'auto')
    .append('path')
    .attr('d', 'M0,-5L10,0L0,5')
    .attr('fill', '#999')

  // 创建边
  const links = g.append('g')
    .selectAll('line')
    .data(graphData.links)
    .enter().append('line')
    .attr('stroke', '#999')
    .attr('stroke-opacity', 0.6)
    .attr('stroke-width', d => Math.max(1, d.weight / 2))
    .attr('marker-end', 'url(#arrow)')

  // 创建节点组
  const nodeGroups = g.append('g')
    .selectAll('g')
    .data(graphData.nodes)
    .enter().append('g')
    .call(d3.drag()
      .on('start', dragstarted)
      .on('drag', dragged)
      .on('end', dragended))

  // 添加节点圆圈
  nodeGroups.append('circle')
    .attr('r', d => d.type === 'Paper' ? nodeRadius + 2 : nodeRadius)
    .attr('fill', d => colorMap[d.type] || '#ccc')
    .attr('stroke', '#fff')
    .attr('stroke-width', 2)

  // 添加节点标签
  nodeGroups.append('text')
    .text(d => d.label || d.id)  // 修复：使用label或id作为显示文本
    .attr('dx', 12)
    .attr('dy', '.35em')
    .attr('font-size', '12px')
    .attr('fill', '#333')

  // 创建力导向模拟 - 优化参数
  simulation = d3.forceSimulation(graphData.nodes)
    .force('link', d3.forceLink(graphData.links).id(d => d.id).distance(linkDistance))
    .force('charge', d3.forceManyBody().strength(-200))  // 减小排斥力
    .force('center', d3.forceCenter(width / 2, height / 2))
    .force('collision', d3.forceCollide().radius(d => d.type === 'Paper' ? nodeRadius + 15 : nodeRadius + 10))

  // 限制节点在可视范围内
  const forceX = d3.forceX().x(width / 2).strength(0.05)
  const forceY = d3.forceY().y(height / 2).strength(0.05)
  simulation.force('x', forceX).force('y', forceY)

  // 更新节点和边的位置
  simulation.on('tick', () => {
    links
      .attr('x1', d => d.source.x)
      .attr('y1', d => d.source.y)
      .attr('x2', d => d.target.x)
      .attr('y2', d => d.target.y)

    nodeGroups
      .attr('transform', d => `translate(${d.x},${d.y})`)
  })

  console.log('知识图谱绘制完成')
}

// 拖拽事件处理
function dragstarted(event, d) {
  if (!event.active) simulation.alphaTarget(0.3).restart()
  d.fx = d.x
  d.fy = d.y
}

function dragged(event, d) {
  d.fx = event.x
  d.fy = event.y
}

function dragended(event, d) {
  if (!event.active) simulation.alphaTarget(0)
  d.fx = null
  d.fy = null
}

// 页面挂载时初始化
onMounted(() => {
  console.log('知识图谱页面已挂载')
  loadData()
})
</script>

<style scoped>
.knowledge-graph-container {
  padding: 20px;
  max-width: 1200px;
  margin: 0 auto;
}

h1 {
  text-align: center;
  color: #333;
  margin-bottom: 20px;
}

.graph-controls {
  display: flex;
  justify-content: center;
  margin-bottom: 20px;
  gap: 15px;
}

button {
  padding: 10px 20px;
  font-size: 16px;
  background-color: #4ECDC4;
  color: white;
  border: none;
  border-radius: 5px;
  cursor: pointer;
  transition: background-color 0.3s;
}

button:hover {
  background-color: #3fb8af;
}

button:disabled {
  background-color: #ccc;
  cursor: not-allowed;
}

.graph-container {
  display: flex;
  justify-content: center;
}

#graph {
  border: 1px solid #eee;
  background-color: #fafafa;
  border-radius: 5px;
}

  /* 添加图例和说明文字的样式 */
  .graph-explanation {
    background-color: #f8f9fa;
    padding: 15px;
    margin-bottom: 20px;
    border-radius: 5px;
    border-left: 4px solid #4ECDC4;
  }

  .graph-explanation h3 {
    margin-top: 0;
    color: #333;
  }

  .graph-explanation p {
    margin-bottom: 10px;
    color: #666;
  }

  .graph-explanation ul {
    margin: 0;
    padding-left: 20px;
    color: #666;
  }

  .graph-explanation li {
    margin-bottom: 5px;
  }

  .graph-legend {
    background-color: #f8f9fa;
    padding: 15px;
    margin-top: 20px;
    border-radius: 5px;
    border-left: 4px solid #FF6B6B;
  }

  .graph-legend h3 {
    margin-top: 0;
    color: #333;
    margin-bottom: 15px;
  }

  .legend-items {
    display: flex;
    flex-wrap: wrap;
    gap: 15px;
  }

  .legend-item {
    display: flex;
    align-items: center;
    gap: 8px;
  }

  .legend-color {
    width: 20px;
    height: 20px;
    border-radius: 50%;
    border: 2px solid #fff;
    box-shadow: 0 1px 3px rgba(0,0,0,0.2);
  }

  .legend-text {
    font-size: 14px;
    color: #333;
    font-weight: 500;
  }

  /* AI分析结果样式 */
  .graph-analysis {
    background-color: #f0f8ff;
    padding: 20px;
    margin-top: 20px;
    border-radius: 5px;
    border-left: 4px solid #45B7D1;
    box-shadow: 0 2px 5px rgba(0,0,0,0.1);
  }

  .graph-analysis h3 {
    margin-top: 0;
    color: #333;
    margin-bottom: 15px;
  }

  .analysis-content {
    line-height: 1.6;
    color: #555;
    background-color: white;
    padding: 15px;
    border-radius: 3px;
    border: 1px solid #e0e0e0;
  }
</style>