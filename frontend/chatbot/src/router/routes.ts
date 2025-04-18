import { RouteRecordRaw } from 'vue-router'
import childRoutes from '@/router/child-routes'

const routes: RouteRecordRaw[] = [
  {
    path: '/',
    name: 'Index',
    component: () => import('@/views/chat.vue'),
    meta: {
      title: '聊天'
    }
  },
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login.vue'),
    meta: {
      title: '登录'
    }
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('@/views/register.vue'),
    meta: {
      title: '注册'
    }
  },
  {
    path: '/setting',
    name: 'Setting',
    component: () => import('@/views/setting.vue'),
    meta: {
      title: '设置',
      requiresAuth: true
    }
  },
  {
    path: '/aihub',
    name: 'AIHub',
    component: () => import('@/views/aihub.vue'),
    meta: {
      title: '图书推荐',
      requiresAuth: true
    }
  },
  ...childRoutes,
  {
    path: '/:pathMatch(.*)',
    name: '404',
    component: () => import('@/components/404.vue')
  }
]

export default routes
