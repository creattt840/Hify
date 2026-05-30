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
    {
      path: '/knowledge-bases',
      name: 'knowledgeBases',
      component: () => import('../views/KnowledgeBases.vue'),
    },
    {
      path: '/knowledge-bases/:kbId/documents',
      name: 'documents',
      component: () => import('../views/Documents.vue'),
    },
    {
      path: '/workflows',
      name: 'workflows',
      component: () => import('../views/Workflows.vue'),
    },
    {
      path: '/workflows/create',
      name: 'workflowCreate',
      component: () => import('../views/WorkflowCreate.vue'),
    },
  ],
})

export default router
