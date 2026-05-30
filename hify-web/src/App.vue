<template>
  <div class="app-root">
    <el-container class="layout">
      <el-aside width="220px" class="app-sidebar surface-dark">
        <div class="logo">
          <span class="logo-mark">H</span>
          <span class="logo-text">Hify</span>
        </div>
        <div class="nav-label">工作台</div>
        <el-menu :default-active="route.path" router class="app-menu">
          <el-menu-item index="/chat">
            <span class="nav-icon">💬</span>
            <span>对话</span>
          </el-menu-item>
          <el-menu-item index="/agents">
            <span class="nav-icon">🤖</span>
            <span>Agent 管理</span>
          </el-menu-item>
          <el-menu-item index="/knowledge-bases">
            <span class="nav-icon">📚</span>
            <span>知识库</span>
          </el-menu-item>
          <el-menu-item index="/workflows">
            <span class="nav-icon">🔀</span>
            <span>工作流</span>
          </el-menu-item>
          <el-menu-item index="/providers">
            <span class="nav-icon">⚡</span>
            <span>模型管理</span>
          </el-menu-item>
        </el-menu>
        <div class="sidebar-footer">
          <span class="sidebar-footer__dot" />
          内部 AI 平台
        </div>
      </el-aside>
      <el-main class="app-main">
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
  -webkit-font-smoothing: antialiased;
}

.layout {
  height: 100%;
}

/* ── 深色侧栏 ── */
.app-sidebar {
  display: flex;
  flex-direction: column;
  background: linear-gradient(180deg, #16171F 0%, #121318 100%);
  border-right: 1px solid var(--hify-border);
  box-shadow: 4px 0 24px rgba(0, 0, 0, 0.15);
}

.app-main {
  background: var(--hify-bg-page);
  padding: 0;
  overflow: hidden;
}

.app-menu {
  border-right: none;
  background: transparent;
  flex: 1;
  padding: 0 10px;
}

.app-menu .el-menu-item {
  display: flex;
  align-items: center;
  gap: 10px;
  color: var(--hify-text-secondary);
  margin: 3px 0;
  border-radius: 10px;
  font-size: 14px;
  font-weight: 500;
  height: 42px;
  transition: all var(--hify-transition-fast);
}

.nav-icon {
  font-size: 15px;
  line-height: 1;
  width: 20px;
  text-align: center;
}

.app-menu .el-menu-item:hover {
  background: var(--hify-bg-hover);
  color: var(--hify-text-primary);
}

.app-menu .el-menu-item.is-active {
  background: linear-gradient(135deg, var(--hify-primary) 0%, var(--hify-primary-dark) 100%);
  color: #fff;
  box-shadow: var(--hify-shadow-glow);
}

.nav-label {
  padding: 16px 20px 8px;
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--hify-text-placeholder);
}

.logo {
  height: 64px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  border-bottom: 1px solid var(--hify-border);
}

.logo-mark {
  width: 32px;
  height: 32px;
  border-radius: 9px;
  background: linear-gradient(135deg, var(--hify-primary) 0%, var(--hify-accent) 100%);
  color: #fff;
  font-size: 16px;
  font-weight: 800;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: var(--hify-shadow-glow);
}

.logo-text {
  font-size: 20px;
  font-weight: 700;
  color: var(--hify-text-primary);
  letter-spacing: -0.04em;
}

.sidebar-footer {
  padding: 16px 20px;
  border-top: 1px solid var(--hify-border);
  font-size: 12px;
  color: var(--hify-text-placeholder);
  display: flex;
  align-items: center;
  gap: 8px;
}

.sidebar-footer__dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--hify-success);
  box-shadow: 0 0 6px var(--hify-success);
}
</style>
