<template>
  <div class="summary-page">
    <a-card class="summary-card">


      <div class="paper-stats" style="marginBottom: 24px">
        <a-statistic-group :title="'论文统计信息'">
          <a-statistic 
            title="本周新论文" 
            :value="this_week_papers.length" 
            :value-style="{ color: '#3f8600' }"
          />
          <a-statistic 
            title="总论文数" 
            :value="all_papers.length" 
            :value-style="{ color: '#1890ff' }"
          />
          <!-- <a-statistic 
            title="论文增长率" 
            :value="growthRate" 
            suffix="%" 
            :precision="1"
            :value-style="{ color: growthRate >= 0 ? '#3f8600' : '#cf1322' }"
          /> -->
        </a-statistic-group>
      </div>

      <a-divider />

      <!-- 论文列表 -->
      <div class="papers-section" style="marginBottom: 32px">
        <a-tabs default-active-key="thisWeek" @change="handleTabChange">
          <a-tab-pane tab="本周论文" key="thisWeek">
            <a-list
              :data-source="filteredPapers"
              :loading="loadingRSS"
              bordered
              @change="handlePaginationChange"
            >
              <template #header>
                <div style="display: flex; justify-content: space-between; align-items: center;">
                  <h3>本周IEEE最新论文</h3>
                  <a-input-search 
                    placeholder="搜索论文标题" 
                    v-model:value="searchText"
                    style="width: 300px"
                  />
                </div>
              </template>
              <template #renderItem="{ item }">
                <a-list-item>
                  <a-list-item-meta>
                    <template #title>
                      <a-tooltip :title="item.title" placement="topLeft">
                        <span class="paper-title">{{ item.title }}</span>
                      </a-tooltip>
                    </template>
                    <template #description>
                      <div class="paper-info">
                        <p class="paper-date">{{ formatDate(item.pubDate) }}</p>
                        <p class="paper-authors" v-if="item.authors">
                          <strong>作者:</strong> {{ item.authors }}
                        </p>
                        <p class="paper-desc" v-if="item.description">
                          {{ truncateText(item.description, 150) }}
                        </p>
                      </div>
                    </template>
                  </a-list-item-meta>
                </a-list-item>
              </template>
            </a-list>
          </a-tab-pane>
          <a-tab-pane tab="全部论文" key="all">
            <a-list
              :data-source="filteredAllPapers"
              :loading="loadingRSS"
              bordered
              @change="handleAllPaginationChange"
            >
              <template #header>
                <div style="display: flex; justify-content: space-between; align-items: center;">
                  <h3>IEEE全部论文</h3>
                  <a-input-search 
                    placeholder="搜索论文标题" 
                    v-model:value="searchTextAll"
                    style="width: 300px"
                  />
                </div>
              </template>
              <template #renderItem="{ item }">
                <a-list-item :class="{ 'this-week': isThisWeek(item.pubDate) }">
                  <template #actions>
                    <span v-if="isThisWeek(item.pubDate)">
                      <a-tag color="green">本周</a-tag>
                    </span>
                  </template>
                  <a-list-item-meta>
                    <template #title>
                      <a-tooltip :title="item.title" placement="topLeft">
                        <span class="paper-title">{{ item.title }}</span>
                      </a-tooltip>
                    </template>
                    <template #description>
                      <div class="paper-info">
                        <p class="paper-date">{{ formatDate(item.pubDate) }}</p>
                        <p class="paper-authors" v-if="item.authors">
                          <strong>作者:</strong> {{ item.authors }}
                        </p>
                        <p class="paper-desc" v-if="item.description">
                          {{ truncateText(item.description, 150) }}
                        </p>
                      </div>
                    </template>
                  </a-list-item-meta>
                </a-list-item>
              </template>
            </a-list>
          </a-tab-pane>
      <a-tab-pane tab="论文总结" key="summary">
<!-- 论文总结 -->
      <div class="summary-content">
        <template v-if="loading">
          <div style="textAlign: center; padding: 60px;">
            <a-spin size="large" tip="正在生成论文总结..." />
          </div>
        </template>
        <template v-else-if="summary">
          <div class="summary-header" style="marginBottom: 16px;">
            <a-typography-title level="4">论文总结报告</a-typography-title>
            <div class="summary-meta">
              <span>生成时间: {{ formatDateTime(new Date()) }}</span>
              <span class="summary-count">包含 {{ paperCount }} 篇论文</span>
            </div>
          </div>
          
          <MarkdownRenderer :content="summary" custom-class="custom-preview"/>
        </template>
        <template v-else>
          <div style="textAlign: center; padding: 80px; color: #999;">
            <a-result
              status="empty"
              title="暂无论文总结"
              sub-title="点击上方按钮生成本周论文总结"
            />
          </div>
        </template>
      </div>


          </a-tab-pane> 
          <a-tab-pane tab="统计" key="statistics">
            <div class="statistics-content">
              <h3 style="margin-bottom: 20px;">近一周论文摘要词云</h3>
              <word-cloud 
                :data="wordCloudData" 
                :width="'100%'"
                :height="500"
                :loading="loadingRSS"
              />
            </div>
          </a-tab-pane>

          <a-tab-pane tab="知识图谱" key="knowledgeGraph">
            
            <KnowledgeGraph
              :data="knowledgeGraphData"
              :width="'100%'"
              :height="500"
              :loading="loadingRSS"
            />
          </a-tab-pane>
        </a-tabs>
      </div>

      <a-divider />

      
    </a-card>

  </div>
</template>

<script>
import { ref, computed, onMounted, watch, nextTick } from 'vue'
import { message, Spin, Statistic, Tabs, List, Tag, Descriptions, Result } from 'ant-design-vue'
import { summaryAPI, rssAPI } from '../services/apiService'
import MarkdownRenderer from '../components/MarkdownRenderer.vue'
import WordCloud from '../components/WordCloud.vue'
import KnowledgeGraph from './KnowledgeGraphPage.vue'
export default {
  name: 'SummaryPage',
  components: {
    [Spin.name]: Spin,
    [Statistic.name]: Statistic,
    [Tabs.name]: Tabs,
    [List.name]: List,
    [Tag.name]: Tag,
    [Descriptions.name]: Descriptions,
    [Result.name]: Result,
    MarkdownRenderer,
    WordCloud,
    KnowledgeGraph
  },
  setup() {
    // 状态管理
    const summary = ref('example')
    const loading = ref(false)
    const loadingRSS = ref(false)
    const paperCount = ref(0)
    const all_papers = ref([])
    const this_week_papers = ref([])
    const searchText = ref('')
    const searchTextAll = ref('')
    const activeTab = ref('thisWeek')
    const wordCloudData = ref([])
    
    // 分页状态
    const thisWeekCurrentPage = ref(1)
    const thisWeekPageSize = ref(10)
    const allCurrentPage = ref(1)
    const allPageSize = ref(10)

    // 计算属性
    const growthRate = computed(() => {
      if (all_papers.value.length === 0) return 0
      return (this_week_papers.value.length / all_papers.value.length) * 100
    })

    const filteredPapers = computed(() => {
      if (!searchText.value) return this_week_papers.value
      const search = searchText.value.toLowerCase()
      return this_week_papers.value.filter(paper => 
        paper.title?.toLowerCase().includes(search) ||
        paper.description?.toLowerCase().includes(search) ||
        paper.authors?.toLowerCase().includes(search)
      )
    })

    const filteredAllPapers = computed(() => {
      if (!searchTextAll.value) return all_papers.value
      const search = searchTextAll.value.toLowerCase()
      return all_papers.value.filter(paper => 
        paper.title?.toLowerCase().includes(search) ||
        paper.description?.toLowerCase().includes(search) ||
        paper.authors?.toLowerCase().includes(search)
      )
    })

    // 计算当前页显示的数据（本周论文）
    const currentWeekPapers = computed(() => {
      const start = (thisWeekCurrentPage.value - 1) * thisWeekPageSize.value
      const end = start + thisWeekPageSize.value
      return filteredPapers.value.slice(start, end)
    })

    // 计算当前页显示的数据（全部论文）
    const currentAllPapers = computed(() => {
      const start = (allCurrentPage.value - 1) * allPageSize.value
      const end = start + allPageSize.value
      return filteredAllPapers.value.slice(start, end)
    })

    // 工具函数
    const formatDate = (dateStr) => {
      if (!dateStr) return ''
      try {
        const date = new Date(dateStr)
        return date.toLocaleDateString('zh-CN', {
          year: 'numeric',
          month: 'long',
          day: 'numeric'
        })
      } catch (e) {
        return dateStr
      }
    }

    const formatDateTime = (date) => {
      return date.toLocaleString('zh-CN', {
        year: 'numeric',
        month: 'long',
        day: 'numeric',
        hour: '2-digit',
        minute: '2-digit'
      })
    }

    const truncateText = (text, maxLength) => {
      if (!text) return ''
      if (text.length <= maxLength) return text
      return text.substring(0, maxLength) + '...'
    }

    const isThisWeek = (dateStr) => {
      if (!dateStr) return false
      try {
        const date = new Date(dateStr)
        const now = new Date()
        const weekAgo = new Date(now.getTime() - 7 * 24 * 60 * 60 * 1000*2)
        return date >= weekAgo && date <= now
      } catch (e) {
        return false
      }
    }

    // 标签页切换处理
    const handleTabChange = (key) => {
      activeTab.value = key
    }
    
    // 生成词云数据
    const generateWordCloudData = () => {
      if (!this_week_papers.value || this_week_papers.value.length === 0) {
        wordCloudData.value = [];
        return;
      }

      // 合并所有摘要
      const allSummaries = this_week_papers.value.map(paper => paper.description || '').join(' ');

      // 简单的中文分词（实际项目中建议使用更专业的分词库）
      const words = allSummaries.match(/[\u4e00-\u9fa5\w]+/g) || [];

      // 过滤停用词
      const stopWords = ['的', '了', '和', '是', '在', '我', '有', '不', '这', '那', '你', '他', '她', '我们', '你们', '他们', '她们', '但是', '如果', '因为', '所以', '对于', '关于', '虽然', '但是', '然而', '因此', '由于', '可以', '可能', '应该', '必须', '能够', '已经', '正在', '将要', '来自', '基于', '通过', '使用', '实现', '研究', '分析', '提出', '设计', '开发', '方法', '技术', '系统', '模型', '算法', '应用', '结果', '实验', '数据', '处理', '优化', '改进', '问题', '解决方案', '挑战', '趋势', '未来'];
      const filteredWords = words.filter(word => 
        word.length > 1 && !stopWords.includes(word)
      );

      // 统计词频
      const wordCount = {};
      filteredWords.forEach(word => {
        wordCount[word] = (wordCount[word] || 0) + 1;
      });

      // 转换为词云所需的数据格式
      const wordCloudDataArray = Object.entries(wordCount)
        .map(([text, value]) => ({ text, value }))
        .sort((a, b) => b.value - a.value)
        .slice(0, 100); // 只取前100个高频词

      wordCloudData.value = wordCloudDataArray;
    };
    
    // 监听this_week_papers变化，重新生成词云数据
    watch(this_week_papers, () => {
      generateWordCloudData()
    }, { deep: true })

    // 页面挂载时生成词云
    onMounted(() => {
      generateWordCloudData()
    })

    // 本周论文分页变化处理
    const handlePaginationChange = (pagination) => {
      thisWeekCurrentPage.value = pagination.current
      thisWeekPageSize.value = pagination.pageSize
    }

    // 全部论文分页变化处理
    const handleAllPaginationChange = (pagination) => {
      allCurrentPage.value = pagination.current
      allPageSize.value = pagination.pageSize
    }

    // 获取RSS数据
    const handleFetchRSSData = async () => {
      loadingRSS.value = true
      try {
        const response = await rssAPI.getRawRSSData()
        all_papers.value = response.all_papers || []
        this_week_papers.value = response.this_week_papers || []
        paperCount.value = this_week_papers.value.length
        message.success('论文数据刷新成功')
      } catch (error) {
        message.error('获取论文数据失败')
        console.error('Fetch RSS data error:', error)
      } finally {
        loadingRSS.value = false
      }
    }

    // 生成论文总结
    const handleGenerateSummary = async () => {
      loading.value = true
      try {
        const response = await summaryAPI.generateSummary()
        if (response.status === 'success') {
          summary.value = response.summary
          paperCount.value = response.paper_count || 0
          // 同步更新论文数据
          await handleFetchRSSData()
          message.success('论文总结生成成功')
        } else {
          message.error('论文总结生成失败')
        }
      } catch (error) {
        message.error('生成总结失败，请检查配置')
        console.error('Generate summary error:', error)
      } finally {
        loading.value = false
      }
    }

    // 发送邮件
    const handleSendEmail = async () => {
      loading.value = true
      try {
        const response = await summaryAPI.sendEmail()
        if (response.status === 'success') {
          message.success('邮件发送成功')
        } else {
          message.error('邮件发送失败')
        }
      } catch (error) {
        message.error('发送邮件失败，请检查邮箱配置')
        console.error('Send email error:', error)
      } finally {
        loading.value = false
      }
    }

    // 执行周总结任务
    const handleWeeklySummary = async () => {
      loading.value = true
      try {
        const response = await summaryAPI.weeklySummary()
        if (response.status === 'success') {
          summary.value = response.summary || summary.value
          paperCount.value = response.paper_count || 0
          // 同步更新论文数据
          await handleFetchRSSData()
          message.success('周总结生成并发送成功')
        } else {
          message.error('周总结执行失败')
        }
      } catch (error) {
        message.error('执行周总结失败，请检查配置')
        console.error('Weekly summary error:', error)
      } finally {
        loading.value = false
      }
    }

    // 监听搜索文本变化，重置页码
    watch([searchText, searchTextAll], () => {
      thisWeekCurrentPage.value = 1
      allCurrentPage.value = 1
    })

    // 组件挂载时自动获取RSS数据
    onMounted(() => {
      handleFetchRSSData()
      //handleGenerateSummary调用时间较长，异步处理
      handleGenerateSummary()
    })

    return {
      // 状态
      summary,
      loading,
      loadingRSS,
      paperCount,
      all_papers,
      this_week_papers,
      searchText,
      searchTextAll,
      activeTab,
      wordCloudData,
      // 分页状态
      thisWeekCurrentPage,
      thisWeekPageSize,
      allCurrentPage,
      allPageSize,
      // 计算属性
      growthRate,
      filteredPapers,
      filteredAllPapers,
      currentWeekPapers,
      currentAllPapers,
      // 方法
      handleGenerateSummary,
      handleSendEmail,
      handleWeeklySummary,
      handleFetchRSSData,
      handleTabChange,
      handlePaginationChange,
      handleAllPaginationChange,
      formatDate,
      formatDateTime,
      truncateText,
      isThisWeek
    }
  }
}
</script>

<style scoped>
.summary-page {
  max-width: 1200px;
  margin: 0 auto;
  padding: 20px;
}

.summary-card {
  margin-bottom: 20px;
}

.paper-title {
  font-weight: bold;
  color: #1890ff;
  font-size: 16px;
  line-height: 1.4;
}

.paper-info {
  color: #666;
}

.paper-date {
  color: #1890ff;
  font-weight: bold;
  margin-bottom: 4px;
}

.paper-authors {
  margin-bottom: 4px;
  color: #333;
}

.paper-desc {
  line-height: 1.5;
  color: #666;
}

.this-week {
  background-color: #f6ffed;
  border-left: 3px solid #52c41a;
}

.summary-header {
  border-bottom: 1px solid #f0f0f0;
  padding-bottom: 12px;
}

.summary-meta {
  display: flex;
  justify-content: space-between;
  color: #666;
  font-size: 14px;
}

.summary-count {
  color: #1890ff;
  font-weight: bold;
}

.summary-text {
  background-color: #fafafa;
  padding: 20px;
  border-radius: 4px;
  border: 1px solid #f0f0f0;
}
</style>