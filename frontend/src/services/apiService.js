import axios from 'axios'
import { reactive } from 'vue'

// 创建axios实例
const api = axios.create({
  baseURL: '/api',  // 后端API基础路径，通过vite配置代理到http://localhost:8000
  timeout: 600000,  // 请求超时时间30秒
  headers: {
    'Content-Type': 'application/json'
  }
})

// 请求拦截器
api.interceptors.request.use(
  config => {
    // 可以在这里添加认证token等
    return config
  },
  error => {
    return Promise.reject(error)
  }
)

// 响应拦截器
api.interceptors.response.use(
  response => {
    return response.data
  },
  error => {
    // 统一错误处理
    console.error('API Error:', error)
    return Promise.reject(error)
  }
)

// 配置相关API
const configAPI = {
  // 获取当前配置
  getConfig: () => api.get('/config'),
  
  // 更新配置
  updateConfig: (configData) => api.post('/config', configData)
}

// RSS相关API
const rssAPI = {
  // 获取RSS内容
  getRSSContent: () => api.get('/rss'),
  // 获取原始RSS数据（包含所有论文和本周论文）
  getRawRSSData: () => api.get('/rss/raw')
}

// 总结相关API
const summaryAPI = {
  // 生成论文总结
  generateSummary: () => api.post('/summarize'),
  
  // 发送邮件
  sendEmail: () => api.post('/send-email'),
  
  // 执行周总结任务
  weeklySummary: () => api.post('/weekly-summary')
}

// 问答相关API
const qaAPI = {
  // 回答问题
  answerQuestion: (question, context = null) => api.post('/qa', {
    question,
    context
  })
}

// 创建API对象
const apiService = reactive({
  config: configAPI,
  rss: rssAPI,
  summary: summaryAPI,
  qa: qaAPI
})

// 导出API服务（支持Vue 3的provide/inject）
export default {
  install(app) {
    // 提供API服务给所有组件
    app.provide('api', apiService)
  },
  // 也支持直接导入使用
  ...apiService
}

// 单独导出各个API模块（保持向后兼容）
export { configAPI, rssAPI, summaryAPI, qaAPI }