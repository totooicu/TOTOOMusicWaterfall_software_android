import axios from 'axios';

// 创建axios实例
const apiClient = axios.create({
  baseURL: 'http://localhost:8001/api',
  timeout: 60000, // 请求超时时间增加到60秒以处理AI分析
  headers: {
    'Content-Type': 'application/json'
  }
});

// 获取知识图谱数据
export const fetchKnowledgeGraphData = async () => {
  try {
    const response = await apiClient.get('/knowledge-graph');
    return response.data;
  } catch (error) {
    console.error('获取知识图谱数据失败:', error);
    throw error;
  }
};

// 获取本周论文数据
export const fetchThisWeekPapers = async () => {
  try {
    const response = await apiClient.get('/rss');
    return response.data.this_week_papers;
  } catch (error) {
    console.error('获取本周论文数据失败:', error);
    throw error;
  }
};

// 调用AI解读知识图谱
export const analyzeKnowledgeGraph = async () => {
  try {
    const response = await apiClient.post('/knowledge-graph/analyze');
    return response.data;
  } catch (error) {
    console.error('AI解读知识图谱失败:', error);
    throw error;
  }
};