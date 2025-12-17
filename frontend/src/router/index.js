import { createRouter, createWebHistory } from 'vue-router'
import SummaryPage from '../pages/SummaryPage.vue'
import ConfigPage from '../pages/ConfigPage.vue'
import QAPage from '../pages/QAPage.vue'
import KnowledgeGraphPage from '../pages/KnowledgeGraphPage.vue'

const routes = [
  {
    path: '/',
    name: 'Summary',
    component: SummaryPage
  },
  {
    path: '/config',
    name: 'Config',
    component: ConfigPage
  },


]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router