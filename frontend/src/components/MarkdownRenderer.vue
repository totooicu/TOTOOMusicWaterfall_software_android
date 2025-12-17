<!-- src/components/MarkdownRenderer.vue -->
<template>
  <div 
    class="markdown-renderer" 
    :class="customClass"
    v-html="safeHtml"
  />
</template>

<script setup>
import { computed } from 'vue';
import { useMarkdown } from '../composables/useMarkdown';

const props = defineProps({
  content: {
    type: String,
    default: ''
  },
  customClass: {
    type: String,
    default: ''
  }
});

const { htmlContent } = useMarkdown(computed(() => props.content));

const safeHtml = computed(() => htmlContent.value);
</script>

<style scoped>
.markdown-renderer {
  line-height: 1.6;
  color: #333;
}

.markdown-renderer :deep(h1) {
  font-size: 2em;
  margin: 0.67em 0;
  padding-bottom: 0.3em;
  border-bottom: 1px solid #eaecef;
}

.markdown-renderer :deep(h2) {
  font-size: 1.5em;
  margin: 0.83em 0;
  padding-bottom: 0.3em;
  border-bottom: 1px solid #eaecef;
}

.markdown-renderer :deep(h3) {
  font-size: 1.25em;
  margin: 1em 0;
}

.markdown-renderer :deep(p) {
  margin-bottom: 1em;
}

.markdown-renderer :deep(ul), 
.markdown-renderer :deep(ol) {
  padding-left: 2em;
  margin-bottom: 1em;
}

.markdown-renderer :deep(li) {
  margin-bottom: 0.25em;
}

.markdown-renderer :deep(blockquote) {
  padding: 0 1em;
  color: #6a737d;
  border-left: 0.25em solid #dfe2e5;
  margin: 0 0 1em 0;
}

.markdown-renderer :deep(code) {
  background-color: #f6f8fa;
  padding: 0.2em 0.4em;
  border-radius: 3px;
  font-size: 0.85em;
}

.markdown-renderer :deep(pre) {
  background-color: #f6f8fa;
  padding: 1em;
  border-radius: 6px;
  overflow: auto;
  margin-bottom: 1em;
}

.markdown-renderer :deep(pre code) {
  background: none;
  padding: 0;
}

.markdown-renderer :deep(a) {
  color: #0366d6;
  text-decoration: none;
}

.markdown-renderer :deep(a:hover) {
  text-decoration: underline;
}

.markdown-renderer :deep(table) {
  border-collapse: collapse;
  width: 100%;
  margin-bottom: 1em;
}

.markdown-renderer :deep(th),
.markdown-renderer :deep(td) {
  border: 1px solid #dfe2e5;
  padding: 0.5em 1em;
  text-align: left;
}

.markdown-renderer :deep(th) {
  background-color: #f6f8fa;
  font-weight: 600;
}
</style>