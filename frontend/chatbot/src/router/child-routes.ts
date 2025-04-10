const LayoutDefault = () => import('@/components/Layout/default.vue')

const childrenRoutes: Array<RouteRecordRaw> = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login.vue')
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('@/views/register.vue')
  },
  {
    path: '/chat',
    component: LayoutDefault,
    name: 'ChatRoot',
    redirect: {
      name: 'ChatIndex'
    },
    children: [
      {
        path: '',
        name: 'ChatIndex',
        component: () => import('@/views/chat.vue')
      }
    ]
  }
]

export default childrenRoutes
