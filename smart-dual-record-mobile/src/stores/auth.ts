import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

export type UserRole = 'CUSTOMER' | 'AGENT' | 'ADVISOR' | 'ADMIN'

export interface UserInfo {
  id: string
  name: string
  phone?: string
  role: UserRole
  avatar?: string
  customerIdHash?: string  // 客户脱敏 ID
  agentId?: string          // 坐席 ID
  advisorId?: string        // 理财经理 ID
}

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string>('')
  const user = ref<UserInfo | null>(null)

  const isLoggedIn = computed(() => !!token.value)
  const isCustomer = computed(() => user.value?.role === 'CUSTOMER')
  const isAgent = computed(() => user.value?.role === 'AGENT')
  const isAdvisor = computed(() => user.value?.role === 'ADVISOR')

  function login(t: string, u: UserInfo) {
    token.value = t
    user.value = u
    localStorage.setItem('token', t)
    localStorage.setItem('user', JSON.stringify(u))
  }

  function restore() {
    const t = localStorage.getItem('token')
    const u = localStorage.getItem('user')
    if (t) token.value = t
    if (u) {
      try {
        user.value = JSON.parse(u)
      } catch {}
    }
  }

  function logout() {
    token.value = ''
    user.value = null
    localStorage.removeItem('token')
    localStorage.removeItem('user')
  }

  return { token, user, isLoggedIn, isCustomer, isAgent, isAdvisor, login, restore, logout }
})
