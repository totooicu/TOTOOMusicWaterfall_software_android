<template>
  <div>
    <a-card title="论文问答">
      <template v-if="loadingContext">
        <div style="textAlign: center; padding: 20px">
          <a-spin tip="正在加载论文总结..." />
        </div>
      </template>
      <template v-else>
        <template v-if="!context">
          <div style="marginBottom: 16px">
            <a-button type="primary" @click="fetchContext">
              加载论文总结
            </a-button>
            <a-typography-text type="secondary" style="marginLeft: 8px">
              加载最新论文总结作为问答上下文
            </a-typography-text>
          </div>
        </template>

        <div class="qa-input-section" style="marginBottom: 24px">
          <a-textarea
            :rows="4"
            placeholder="请输入您的问题，例如：本周论文的主要研究方向是什么？"
            v-model:value="question"
            @pressEnter="handleSendQuestion"
            :disabled="loading"
          />
          <a-button
            type="primary"
            @click="handleSendQuestion"
            :loading="loading"
            icon="send"
            style="marginTop: 16px"
            :disabled="!question.trim()"
          >
            发送问题
          </a-button>
        </div>

        <div class="qa-list">
          <template v-if="qaList.length > 0">
            <a-list
              :data-source="qaList"
              :item-layout="'vertical'"
            >
              <template #item="item">
                <a-list-item style="marginBottom: 24px; padding: 16px; border: 1px solid #f0f0f0; borderRadius: 4px">
                  <div>
                    <div style="marginBottom: 8px">
                      <a-typography-text strong>Q: </a-typography-text>
                      <a-typography-text>{{ item.question }}</a-typography-text>
                    </div>
                    <div style="paddingLeft: 8px; borderLeft: 3px solid #1890ff">
                      <a-typography-text strong>A: </a-typography-text>
                      <a-typography-text style="whiteSpace: pre-wrap; lineHeight: 1.8">
                        {{ item.answer }}
                      </a-typography-text>
                    </div>
                  </div>
                </a-list-item>
              </template>
            </a-list>
          </template>
          <template v-else>
            <div style="textAlign: center; padding: 40px; color: #999">
              <p>暂无问答记录</p>
              <p style="marginTop: 8px">请输入问题开始问答</p>
            </div>
          </template>
        </div>
      </template>
    </a-card>

    <a-card title="问答说明" style="marginTop: 24px">
      <ul>
        <li>1. 首先需要加载论文总结作为问答的上下文</li>
        <li>2. 您可以提问关于论文内容、研究方向、主要发现等问题</li>
        <li>3. 示例问题：</li>
        <ul>
          <li>- 本周论文主要涉及哪些研究领域？</li>
          <li>- 有哪些值得关注的创新方法？</li>
          <li>- 某篇特定论文的主要结论是什么？</li>
        </ul>
        <li>4. 问答历史会显示在页面上，方便查看</li>
      </ul>
    </a-card>
  </div>
</template>

<script>
import { ref } from 'vue'
import { message } from 'ant-design-vue'
import { qaAPI, summaryAPI } from '../services/apiService'

export default {
  name: 'QAPage',
  setup() {
    const question = ref('')
    const qaList = ref([])
    const loading = ref(false)
    const context = ref(null)
    const loadingContext = ref(false)

    // 获取论文总结作为上下文
    const fetchContext = async () => {
      loadingContext.value = true
      try {
        const response = await summaryAPI.generateSummary()
        if (response.status === 'success') {
          context.value = response.summary
          message.success('已加载最新论文总结')
        } else {
          message.error('加载论文总结失败')
        }
      } catch (error) {
        message.error('加载上下文失败')
        console.error('Fetch context error:', error)
      } finally {
        loadingContext.value = false
      }
    }

    // 发送问题
    const handleSendQuestion = async () => {
      if (!question.value.trim()) {
        message.warning('请输入问题')
        return
      }

      loading.value = true
      try {
        // 如果没有上下文，先获取
        let currentContext = context.value
        if (!currentContext) {
          await fetchContext()
          // 再次检查是否获取到上下文
          if (!context.value) {
            throw new Error('无法获取上下文')
          }
          currentContext = context.value
        }

        const response = await qaAPI.answerQuestion(question.value, currentContext)
        if (response.status === 'success') {
          const newQA = {
            id: Date.now(),
            question: question.value,
            answer: response.answer
          }
          qaList.value = [newQA, ...qaList.value]
          question.value = ''
        } else {
          message.error('回答问题失败')
        }
      } catch (error) {
        message.error('问答失败，请检查配置')
        console.error('QA error:', error)
      } finally {
        loading.value = false
      }
    }

    return {
      question,
      qaList,
      loading,
      context,
      loadingContext,
      fetchContext,
      handleSendQuestion
    }
  }
}
</script>