<template>
  <a-layout class="min-h-screen">
    <a-layout-header class="header">
      <div class="logo">论文总结助手</div>
      <a-menu theme="dark" mode="horizontal" :default-selected-keys="['1']">
        <a-menu-item key="1">
          <router-link to="/">论文总结</router-link>
        </a-menu-item>
        <a-menu-item key="2">
          <router-link to="/config">系统配置</router-link>
        </a-menu-item>
      </a-menu>
    </a-layout-header>
    <a-layout-content class="content">
      <div class="container">
        <router-view v-slot="{ Component }">
          <transition name="fade" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </div>
    </a-layout-content>
    <a-layout-footer class="footer">
      论文总结助手 ©2023 Created by Trae AI
    </a-layout-footer>
  </a-layout>
</template>

<script>
export default {
  name: 'App'
}
</script>

<style>
.min-h-screen {
  min-height: 100vh;
}

.header {
  display: flex;
  align-items: center;
}

.logo {
  width: 120px;
  height: 31px;
  background: rgba(255, 255, 255, 0.2);
  margin: 0 24px 0 0;
  float: left;
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: bold;
}

.content {
  padding: 24px;
  margin: 0;
  min-height: 280px;
  background: #fff;
}

.container {
  max-width: 1200px;
  margin: 0 auto;
}

.footer {
  text-align: center;
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.3s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>