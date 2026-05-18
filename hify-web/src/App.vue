<template>
  <el-container class="layout">
    <el-aside width="220px">
      <div class="logo">Hify</div>
      <el-menu
        :default-active="route.path"
        router
        background-color="var(--hify-bg-sidebar)"
        text-color="var(--hify-text-secondary)"
        active-text-color="var(--hify-primary-light)"
      >
        <el-menu-item index="/chat">
          <span>对话</span>
        </el-menu-item>
        <el-menu-item index="/agents">
          <span>Agent 管理</span>
        </el-menu-item>
        <el-menu-item index="/providers">
          <span>模型管理</span>
        </el-menu-item>
      </el-menu>
    </el-aside>
    <el-main>
      <router-view />
    </el-main>
  </el-container>
</template>

<script setup lang="ts">
import { useRoute } from 'vue-router'

const route = useRoute()
</script>

<style>
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

html, body {
  background-color: var(--hify-bg-page);
  color: var(--hify-text-primary);
}

.layout {
  height: 100vh;
}

.el-aside {
  background-color: var(--hify-bg-sidebar);
}

.el-main {
  background-color: var(--hify-bg-page);
}

.el-menu {
  border-right: none;
}

.logo {
  height: 60px;
  line-height: 60px;
  text-align: center;
  color: var(--hify-text-inverse);
  font-size: 20px;
  font-weight: bold;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}

.page {
  padding: 24px;
}
</style>
