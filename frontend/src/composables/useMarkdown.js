// src/composables/useMarkdown.js
import { ref, computed } from 'vue';
import { parseMarkdown, getPlainTextSummary } from '../utils/markdownParser';

/**
 * Markdown 相关功能的组合式函数
 * @param {import('vue').Ref<string>} markdownText - Markdown 文本的 ref
 * @returns {Object} Markdown 相关功能
 */
export function useMarkdown(markdownText) {
  // 渲染后的 HTML
  const htmlContent = computed(() => 
    parseMarkdown(markdownText.value)
  );

  // 纯文本摘要
  const plainSummary = computed(() => 
    getPlainTextSummary(markdownText.value)
  );

  // 字符统计
  const characterCount = computed(() => 
    markdownText.value.length
  );

  // 单词统计（简单实现）
  const wordCount = computed(() => 
    markdownText.value.trim() ? markdownText.value.trim().split(/\s+/).length : 0
  );

  return {
    htmlContent,
    plainSummary,
    characterCount,
    wordCount
  };
}