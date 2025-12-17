<template>
  <div class="word-cloud-container" ref="containerRef">
    <div v-if="loading" class="word-cloud-loading">
      <a-spin size="large" />
    </div>
    <div v-else-if="filteredData.length === 0" class="word-cloud-empty">
      暂无数据
    </div>
    <canvas 
      v-else 
      ref="canvasRef" 
      class="word-cloud"
      :width="canvasWidth"
      :height="height"
      @mousemove="handleMouseMove"
      @mouseout="handleMouseOut"
    ></canvas>
    <!-- Tooltip for showing word frequency -->
    <div 
      v-if="tooltipVisible" 
      class="word-cloud-tooltip"
      :style="{ left: tooltipX + 'px', top: tooltipY + 'px' }"
    >
      {{ tooltipWord }}: {{ tooltipFrequency }}%
    </div>
  </div>
</template>

<script>
import { ref, onMounted, watch, nextTick, computed } from 'vue'
import { Spin } from 'ant-design-vue'

export default {
  name: 'WordCloud',
  components: {
    [Spin.name]: Spin
  },
  props: {
    data: {
      type: Array,
      default: () => []
    },
    width: {
      type: [String, Number],
      default: '100%'
    },
    height: {
      type: Number,
      default: 500
    },
    loading: {
      type: Boolean,
      default: false
    },
    stopWords: {
      type: Array,
      default: () => [
        // 中文停用词
        '的', '了', '是', '在', '我', '有', '和', '就', '不', '人', '都', '一', '一个', '上', '也', '很', '到', '说', '要', '去', '你', '会', '着', '没有', '看', '好', '自己', '这', '那', '他', '她', '它', '们', '来', '去', '回', '做', '给', '对', '以', '可', '能', '又', '但', '而', '或', '与', '因为', '所以', '然而', '但是', '而且', '还是', '不过', '如果', '虽然', '尽管', '即使', '既然', '于是', '因此', '所以', '才', '就', '都', '还', '只', '也', '又', '再', '更', '最', '太', '非常', '很', '十分', '极', '相当', '比较', '略微', '稍微', '几乎', '几乎', '差不多', '大约', '大概', '恐怕', '也许', '或许', '可能', '好像', '似乎', '仿佛', '如同', '像', '一样', '同样', '类似', '比如', '例如', '诸如', '至于', '关于', '对于', '对', '就', '向', '朝', '往', '从', '自', '由', '由', '被', '把', '将', '使', '让', '叫', '被', '给', '为', '为了', '因为', '由于', '通过', '经过', '按照', '根据', '凭', '靠', '用', '以', '拿', '对', '对于', '关于', '至于', '在', '于', '当', '趁', '乘', '随', '随着', '当着', '沿着', '顺着', '朝', '向', '往', '从', '起', '自', '由', '到', '至', '直到', '在', '当', '于', '到', '至', '直到', '从', '自', '由', '由', '被', '把', '将', '使', '让', '叫', '被', '给', '为', '为了', '因为', '由于', '通过', '经过', '按照', '根据', '凭', '靠', '用', '以', '拿', '对', '对于', '关于', '至于', '在', '于', '当', '趁', '乘', '随', '随着', '当着', '沿着', '顺着', '朝', '向', '往', '从', '起', '自', '由', '到', '至', '直到',
        // 英文停用词
        'and', 'of', 'the', 'in', 'to', 'a', 'is', 'it', 'that', 'for', 'with', 'on', 'at', 'by', 'this', 'from', 'as', 'i', 'you', 'we', 'they', 'he', 'she', 'it', 'was', 'are', 'were', 'be', 'been', 'being', 'have', 'has', 'had', 'do', 'does', 'did', 'will', 'would', 'shall', 'should', 'can', 'could', 'may', 'might', 'must', 'shall', 'should', 'will', 'would', 'is', 'am', 'are', 'was', 'were', 'be', 'been', 'being', 'have', 'has', 'had', 'having', 'do', 'does', 'did', 'doing', 'a', 'an', 'the', 'and', 'but', 'or', 'so', 'for', 'nor', 'yet', 'at', 'by', 'with', 'in', 'on', 'into', 'from', 'up', 'down', 'over', 'under', 'above', 'below', 'to', 'of', 'off', 'for', 'out', 'between', 'through', 'during', 'before', 'after', 'about', 'against', 'along', 'among', 'around', 'because', 'before', 'behind', 'being', 'below', 'beside', 'between', 'beyond', 'but', 'by', 'concerning', 'considering', 'despite', 'down', 'during', 'except', 'excepting', 'excluding', 'following', 'for', 'from', 'in', 'inside', 'into', 'like', 'minus', 'near', 'of', 'off', 'on', 'onto', 'opposite', 'outside', 'over', 'past', 'per', 'plus', 'regarding', 'round', 'save', 'since', 'than', 'through', 'to', 'toward', 'towards', 'under', 'underneath', 'unlike', 'until', 'up', 'upon', 'versus', 'via', 'with', 'within', 'without', 'the', 'a', 'an', 'and', 'but', 'or', 'as', 'if', 'when', 'than', 'because', 'while', 'where', 'after', 'so', 'though', 'since', 'till', 'until', 'unless', 'before', 'whether', 'while', 'who', 'whom', 'whose', 'which', 'that', 'this', 'these', 'those', 'here', 'there', 'why', 'how', 'what', 'when', 'where', 'who', 'whom', 'whose', 'which', 'that', 'this', 'these', 'those', 'here', 'there', 'why', 'how', 'what', 'when', 'where', 'who', 'whom', 'whose', 'which'
      ]
    }
  },
  setup(props) {
    const canvasRef = ref(null)
    const containerRef = ref(null)
    const canvasWidth = ref(0)
    const wordPositions = ref([]) // Store word positions for hover detection
    const tooltipVisible = ref(false)
    const tooltipX = ref(0)
    const tooltipY = ref(0)
    const tooltipWord = ref('')
    const tooltipFrequency = ref(0)
    const hoveredWordIndex = ref(-1)
    const wordLayouts = ref(new Map()) // Store fixed layouts for each word
    const wordColors = ref(new Map()) // Store fixed colors for each word
    
    // Calculate total word count for frequency calculation
    const totalWordCount = computed(() => {
      return props.data.reduce((sum, item) => sum + item.value, 0)
    })

    // Filter out meaningless words
    const filteredData = computed(() => {
      return props.data.filter(item => {
        const word = item.text.toLowerCase().trim()
        return !props.stopWords.includes(word) && word.length > 1
      })
    })

    // 计算画布宽度
    const updateCanvasWidth = () => {
      if (typeof props.width === 'number') {
        canvasWidth.value = props.width
      } else {
        // 如果是字符串（如'100%'），则根据容器宽度计算
        const container = canvasRef.value?.parentElement
        if (container) {
          canvasWidth.value = container.clientWidth
        } else {
          canvasWidth.value = 800 // 默认宽度
        }
      }
    }

    // Simple hash function to generate consistent values from strings
    const hashString = (str) => {
      let hash = 0
      for (let i = 0; i < str.length; i++) {
        const char = str.charCodeAt(i)
        hash = ((hash << 5) - hash) + char
        hash = hash & hash // Convert to 32bit integer
      }
      return Math.abs(hash)
    }

    // 生成固定颜色
    const getWordColor = (word) => {
      // 检查是否已有固定颜色
      if (wordColors.value.has(word)) {
        return wordColors.value.get(word)
      }
      
      const colors = [
        '#1890ff', '#52c41a', '#faad14', '#f5222d', '#722ed1',
        '#13c2c2', '#eb2f96', '#fa8c16', '#a0d911', '#2f54eb'
      ]
      
      // 基于单词哈希值生成固定颜色
      const hash = hashString(word)
      const colorIndex = hash % colors.length
      const color = colors[colorIndex]
      
      // 保存颜色
      wordColors.value.set(word, color)
      return color
    }

    // 绘制单词
    const drawWord = (ctx, item, index, isHovered = false) => {
      // 计算最大和最小权重
      const weights = filteredData.value.map(item => item.value)
      const maxWeight = Math.max(...weights)
      const minWeight = Math.min(...weights)
      
      // 根据权重计算字体大小
      const fontSizeRange = [12, 64]
      let fontSize = fontSizeRange[0] + 
        (item.value - minWeight) / (maxWeight - minWeight) * 
        (fontSizeRange[1] - fontSizeRange[0])
      
      // 如果是悬停的单词，放大1.2倍
      if (isHovered) {
        fontSize *= 1.2
      }
      
      const color = isHovered ? '#ff4d4f' : getWordColor(item.text)
      ctx.font = `${fontSize}px sans-serif`
      ctx.fillStyle = color
      
      // 获取文本宽度
      const textWidth = ctx.measureText(item.text).width
      
      return { fontSize, color, textWidth }
    }

    // 绘制词云
    const drawWordCloud = () => {
      if (!canvasRef.value || filteredData.value.length === 0) return

      const canvas = canvasRef.value
      const ctx = canvas.getContext('2d')
      
      // 清空画布
      ctx.clearRect(0, 0, canvas.width, canvas.height)

      // 画布中心
      const centerX = canvas.width / 2
      const centerY = canvas.height / 2

      // 词云配置
      const angleStep = Math.PI / 180 // 角度步长
      const radiusStep = 5 // 半径步长

      // 存储已使用的位置，避免重叠
      const usedPositions = []
      wordPositions.value = [] // Reset word positions

      // 尝试放置单词
      const tryPlaceWord = (item, index) => {
        const isHovered = index === hoveredWordIndex.value
        const { fontSize, color, textWidth } = drawWord(ctx, item, index, isHovered)
        
        let angle, radius
        
        // 检查是否已有固定布局
        if (wordLayouts.value.has(item.text)) {
          const layout = wordLayouts.value.get(item.text)
          angle = layout.angle
          radius = layout.radius
        } else {
          // 基于单词哈希值生成固定的角度和半径
          const hash = hashString(item.text)
          angle = (hash % 360) * Math.PI / 180
          radius = (hash % Math.floor(Math.min(canvas.width, canvas.height) / 2))
          
          // 保存布局
          wordLayouts.value.set(item.text, { angle, radius })
        }
        
        // 计算单词位置
        const x = centerX + Math.cos(angle) * radius - textWidth / 2
        const y = centerY + Math.sin(angle) * radius + fontSize / 3
        
        // 检查是否与其他单词重叠
        for (const pos of usedPositions) {
          const distance = Math.sqrt(
            Math.pow(x - pos.x, 2) + Math.pow(y - pos.y, 2)
          )
          if (distance < (fontSize + pos.fontSize) / 2) {
            return false // 重叠
          }
        }
        
        // 绘制单词
        ctx.fillText(item.text, x, y)
        
        // 记录位置和尺寸
        usedPositions.push({ x, y, fontSize, width: textWidth })
        wordPositions.value.push({
          x, y, fontSize, width: textWidth, 
          text: item.text, value: item.value
        })
        return true
      }

      // 绘制所有单词
      filteredData.value.forEach((item, index) => {
        // 尝试多次放置单词
        let placed = false
        let attempt = 0
        const maxAttempts = 50
        
        while (!placed && attempt < maxAttempts) {
          if (tryPlaceWord(item, index)) {
            placed = true
          } else {
            // 如果重叠，稍微调整位置
            const layout = wordLayouts.value.get(item.text)
            layout.radius += radiusStep
            attempt++
          }
        }
      })
    }

    // Handle mouse move to show tooltip and highlight word
    const handleMouseMove = (event) => {
      if (!canvasRef.value || wordPositions.value.length === 0) return
      
      const canvas = canvasRef.value
      const rect = canvas.getBoundingClientRect()
      const mouseX = event.clientX - rect.left
      const mouseY = event.clientY - rect.top
      
      // Check if mouse is over any word
      for (let i = 0; i < wordPositions.value.length; i++) {
        const word = wordPositions.value[i]
        if (mouseX >= word.x && mouseX <= word.x + word.width &&
            mouseY >= word.y - word.fontSize && mouseY <= word.y) {
          // Show tooltip with frequency (percentage)
          tooltipVisible.value = true
          tooltipX.value = event.clientX - rect.left + 10
          tooltipY.value = event.clientY - rect.top - 30
          tooltipWord.value = word.text
          tooltipFrequency.value = ((word.value / totalWordCount.value) * 100).toFixed(2)
          
          // Highlight the word
          if (hoveredWordIndex.value !== i) {
            hoveredWordIndex.value = i
            drawWordCloud()
          }
          return
        }
      }
      
      // If not over any word, hide tooltip and reset highlight
      tooltipVisible.value = false
      if (hoveredWordIndex.value !== -1) {
        hoveredWordIndex.value = -1
        drawWordCloud()
      }
    }

    // Handle mouse out
    const handleMouseOut = () => {
      tooltipVisible.value = false
      if (hoveredWordIndex.value !== -1) {
        hoveredWordIndex.value = -1
        drawWordCloud()
      }
    }

    // 监听数据变化，重新绘制词云
    watch(
      () => props.data,
      () => {
        nextTick(() => {
          updateCanvasWidth()
          nextTick(() => {
            drawWordCloud()
          })
        })
      },
      { deep: true }
    )

    // 监听宽度和高度变化
    watch(
      [() => props.width, () => props.height],
      () => {
        nextTick(() => {
          updateCanvasWidth()
          nextTick(() => {
            drawWordCloud()
          })
        })
      }
    )

    // 组件挂载时初始化
    onMounted(() => {
      nextTick(() => {
        updateCanvasWidth()
        nextTick(() => {
          drawWordCloud()
        })
      })
    })

    return {
      canvasRef,
      containerRef,
      canvasWidth,
      filteredData,
      handleMouseMove,
      handleMouseOut,
      tooltipVisible,
      tooltipX,
      tooltipY,
      tooltipWord,
      tooltipFrequency,
      totalWordCount
    }
  }
}
</script>

<style scoped>
.word-cloud-container {
  position: relative;
  width: 100%;
}

.word-cloud {
  display: block;
  margin: 0 auto;
  cursor: default;
}

.word-cloud-loading {
  display: flex;
  justify-content: center;
  align-items: center;
  height: 500px;
}

.word-cloud-empty {
  display: flex;
  justify-content: center;
  align-items: center;
  height: 500px;
  color: #999;
  font-size: 18px;
}

.word-cloud-tooltip {
  position: absolute;
  background-color: rgba(0, 0, 0, 0.7);
  color: white;
  padding: 5px 10px;
  border-radius: 4px;
  font-size: 14px;
  pointer-events: none;
  z-index: 1000;
  white-space: nowrap;
}
</style>