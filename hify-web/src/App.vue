<template>
  <div class="app-root hify-light">
    <el-container class="layout">
      <el-aside width="200px">
        <div class="logo">Hify</div>
        <el-menu
          :default-active="route.path"
          router
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
  </div>
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

html, body, #app, .app-root {
  height: 100%;
  background-color: var(--hify-bg-page);
  color: var(--hify-text-regular);
  font-family: var(--hify-font-family);
  font-size: var(--hify-font-size-base);
}

.layout {
  height: 100%;
}

.el-aside {
  background-color: var(--hify-bg-sidebar);
  border-right: 1px solid var(--hify-border);
}

.el-main {
  background-color: var(--hify-bg-page);
  padding: 0;
  overflow: hidden;
}

.el-menu {
  border-right: none;
  background-color: transparent;
}

.el-menu .el-menu-item {
  color: var(--hify-text-secondary);
  margin: 4px 8px;
  border-radius: 8px;
  font-size: 14px;
}

.el-menu .el-menu-item:hover {
  background-color: var(--hify-bg-hover);
  color: var(--hify-text-primary);
}

.el-menu .el-menu-item.is-active {
  background-color: var(--hify-primary);
  color: #fff;
}

.logo {
  height: 60px;
  line-height: 60px;
  text-align: center;
  font-size: 20px;
  font-weight: 700;
  color: var(--hify-primary);
  border-bottom: 1px solid var(--hify-border);
  letter-spacing: -0.5px;
}

.page {
  padding: 24px;
}
</style>
