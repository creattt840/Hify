import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      redirect: '/chat',
    },
    {
      path: '/providers',
      name: 'providers',
      component: () => import('../views/Providers.vue'),
    },
    {
      path: '/agents',
      name: 'agents',
      component: () => import('../views/Agents.vue'),
    },
    {
      path: '/chat',
      name: 'chat',
      component: () => import('../views/Chat.vue'),
    },
  ],
})

export default router
