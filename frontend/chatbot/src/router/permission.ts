import NProgress from 'nprogress'
import type { Router } from 'vue-router'

NProgress.configure({
  showSpinner: false
})

// 白名单路由（不需要登录即可访问）
const whiteList = ['/login', '/register']

export function createRouterGuards(router: Router) {

  router.beforeEach(async (to, from, next) => {
    NProgress.start()
    
    // 获取token判断是否已登录
    const token = localStorage.getItem('token')
    
    // 已登录状态
    if (token) {
      if (to.path === '/login') {
        // 如果已登录，但访问的是登录页，则重定向到首页
        next({ path: '/' })
        NProgress.done()
      } else {
        // 已登录，访问其他页面，放行
        next()
      }
    } else {
      // 未登录状态
      if (whiteList.includes(to.path)) {
        // 未登录但在白名单中，放行
        next()
      } else {
        // 未登录且不在白名单，重定向到登录页
        next({ path: '/login', query: { redirect: to.fullPath } })
        NProgress.done()
      }
    }
  })

  router.afterEach(() => {
    NProgress.done()
  })
}
