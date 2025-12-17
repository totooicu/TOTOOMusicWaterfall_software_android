<template>
  <a-card title="系统配置" class="config-card">
    <a-form
      :model="formState"
      :rules="rules"
      ref="formRef"
      layout="vertical"
    >
      <a-form-item
        label="硅基流动API Key"
        name="apiKey"
      >
        <a-input-password v-model:value="formState.apiKey" placeholder="请输入API Key" />
      </a-form-item>

      <a-form-item
        label="RSS订阅地址"
        name="rssUrl"
      >
        <a-input v-model:value="formState.rssUrl" placeholder="请输入RSS订阅地址" />
      </a-form-item>

      <a-form-item
        label="QQ邮箱账号"
        name="qqEmail"
      >
        <a-input v-model:value="formState.qqEmail" placeholder="请输入QQ邮箱账号" />
      </a-form-item>

      <a-form-item
        label="QQ邮箱授权码"
        name="qqEmailPassword"
      >
        <a-input-password v-model:value="formState.qqEmailPassword" placeholder="请输入QQ邮箱授权码" />
      </a-form-item>

      <a-form-item
        label="接收邮件地址"
        name="targetEmail"
      >
        <a-input v-model:value="formState.targetEmail" placeholder="请输入接收邮件地址" />
      </a-form-item>

      <a-form-item>
        <a-button type="primary" htmlType="submit" :loading="loading">
          保存配置
        </a-button>
        <a-button style="marginLeft: 16px" @click="loadConfig">
          重置
        </a-button>
      </a-form-item>
    </a-form>
    
    <div style="marginTop: 24px, padding: 16px, backgroundColor: '#f5f5f5', borderRadius: 4">
      <h4>说明：</h4>
      <ul>
        <li>1. 硅基流动API Key用于调用大语言模型生成论文摘要</li>
        <li>2. RSS地址默认为IEEE Transactions on Geoscience and Remote Sensing的最新论文</li>
        <li>3. QQ邮箱需要开启SMTP服务并获取授权码</li>
        <li>4. 配置保存后可在论文总结页面生成并发送报告</li>
      </ul>
    </div>
  </a-card>
</template>

<script>
import { ref, reactive, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import { configAPI } from '../services/apiService'

export default {
  name: 'ConfigPage',
  setup() {
    const formRef = ref()
    const loading = ref(false)
    
    const formState = reactive({
      apiKey: '',
      rssUrl: 'https://ieeexplore.ieee.org/rss/TOC36.XML',
      qqEmail: '',
      qqEmailPassword: '',
      targetEmail: ''
    })

    const rules = {
      apiKey: [{ required: true, message: '请输入API Key', trigger: 'blur' }],
      rssUrl: [{ required: true, message: '请输入RSS订阅地址', trigger: 'blur' }],
      qqEmail: [{ required: true, message: '请输入QQ邮箱账号', trigger: 'blur' }],
      qqEmailPassword: [{ required: true, message: '请输入QQ邮箱授权码', trigger: 'blur' }],
      targetEmail: [{ required: true, message: '请输入接收邮件地址', trigger: 'blur' }]
    }

    // 初始化加载配置
    onMounted(() => {
      loadConfig()
    })

    const loadConfig = async () => {
      try {
        const response = await configAPI.getConfig()
        // getConfig返回的是直接的配置对象，不是包含status的包装对象
        formState.apiKey = response.api_key || ''
        formState.rssUrl = response.rss_url || 'https://ieeexplore.ieee.org/rss/TOC36.XML'
        formState.qqEmail = response.qq_email || ''
        formState.targetEmail = response.target_email || ''
      } catch (error) {
        message.error('加载配置失败')
        console.error('Load config error:', error)
      }
    }

    const handleSubmit = async (values) => {
      loading.value = true
      try {
        // 构建配置数据
        const configData = {
          api_key: formState.apiKey,
          rss_url: formState.rssUrl,
          qq_email: formState.qqEmail,
          qq_email_password: formState.qqEmailPassword,
          target_email: formState.targetEmail
        }
        
        const response = await configAPI.updateConfig(configData)
        if (response.status === 'success') {
          message.success('配置更新成功')
        } else {
          message.error('配置更新失败')
        }
      } catch (error) {
        message.error('配置更新失败')
        console.error('Update config error:', error)
      } finally {
        loading.value = false
      }
    }

    return {
      formRef,
      formState,
      rules,
      loading,
      loadConfig,
      handleSubmit
    }
  }
}
</script>