import { defineStore } from 'pinia'
import { ref } from 'vue'

let socket: WebSocket | null = null
let reconnectTimer: any = null
let heartbeatTimer: any = null
let listeners: Map<string, Set<(data: any) => void>> = new Map()

export const useWebSocketStore = defineStore('ws', () => {
  const connected = ref(false)
  const businessId = ref<string>('')
  const lastError = ref<string>('')

  function url() {
    const base = (import.meta.env.VITE_WS_BASE || `ws://${location.host}`).replace(/\/$/, '')
    return `${base}/ws/recording?businessId=${businessId.value}`
  }

  function connect(bid: string) {
    businessId.value = bid
    if (socket && socket.readyState === WebSocket.OPEN) {
      // 已有连接, 切换 businessId 不重连
      return
    }
    try {
      socket = new WebSocket(url())
    } catch (e: any) {
      lastError.value = e.message
      scheduleReconnect()
      return
    }
    socket.onopen = () => {
      connected.value = true
      lastError.value = ''
      startHeartbeat()
    }
    socket.onmessage = (evt) => {
      try {
        const msg = JSON.parse(evt.data)
        const type = msg.type
        if (listeners.has(type)) {
          listeners.get(type)!.forEach(cb => cb(msg))
        }
      } catch {}
    }
    socket.onerror = () => {
      lastError.value = '连接错误'
    }
    socket.onclose = () => {
      connected.value = false
      stopHeartbeat()
      scheduleReconnect()
    }
  }

  function startHeartbeat() {
    stopHeartbeat()
    heartbeatTimer = setInterval(() => {
      if (socket?.readyState === WebSocket.OPEN) {
        socket.send(JSON.stringify({ type: 'PING' }))
      }
    }, 30000)
  }

  function stopHeartbeat() {
    if (heartbeatTimer) {
      clearInterval(heartbeatTimer)
      heartbeatTimer = null
    }
  }

  function scheduleReconnect() {
    if (reconnectTimer) return
    reconnectTimer = setTimeout(() => {
      reconnectTimer = null
      if (businessId.value) connect(businessId.value)
    }, 3000)
  }

  function send(type: string, data?: any) {
    if (socket?.readyState === WebSocket.OPEN) {
      socket.send(JSON.stringify({ type, ...data }))
    }
  }

  function on(type: string, cb: (data: any) => void) {
    if (!listeners.has(type)) listeners.set(type, new Set())
    listeners.get(type)!.add(cb)
    return () => listeners.get(type)?.delete(cb)
  }

  function disconnect() {
    if (socket) {
      socket.close()
      socket = null
    }
    stopHeartbeat()
    if (reconnectTimer) {
      clearTimeout(reconnectTimer)
      reconnectTimer = null
    }
    connected.value = false
  }

  return { connected, businessId, lastError, connect, send, on, disconnect }
})
