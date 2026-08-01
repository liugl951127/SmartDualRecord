import { defineStore } from 'pinia'
import { ref } from 'vue'
import { recordingApi, scriptApi } from '@/api'

// 8 节点定义
export const NODES = [
  { id: '01-IDENTITY',     name: '身份核验', desc: '出示身份证 + 联网核查' },
  { id: '02-DISCLOSURE',   name: '产品披露', desc: '产品基本信息 / 风险等级' },
  { id: '03-PRODUCT',      name: '产品介绍', desc: '产品特点 / 收益 / 费用' },
  { id: '04-RIGHTS',       name: '权益告知', desc: '犹豫期 / 赎回 / 投诉' },
  { id: '05-TRUTH_TELL',   name: '如实告知', desc: '客户风险承受能力确认' },
  { id: '06-AFFIRMATIVE',  name: '明确肯定', desc: '客户明确购买意愿' },
  { id: '07-SIGN',         name: '电子签名', desc: '合同 + 风险揭示书签字' },
  { id: '08-FOLLOWUP',     name: '犹豫期',  desc: '15 天 3 次回访' }
] as const

export const useRecordingStore = defineStore('recording', () => {
  const businessId = ref<string>('')
  const recId = ref<string>('')
  const currentNodeIdx = ref(0)
  const nodeStartTime = ref<number>(0)
  const nodeElapsed = ref(0)
  const recording = ref(false)
  const mediaStream = ref<MediaStream | null>(null)
  const mediaRecorder = ref<MediaRecorder | null>(null)
  const chunks = ref<Blob[]>([])
  const scriptText = ref<string>('')
  const errors = ref<string[]>([])

  function setBusiness(id: string) {
    businessId.value = id
  }

  function setRec(id: string) {
    recId.value = id
  }

  async function loadScript(productId: string, channel?: string) {
    try {
      const res: any = await scriptApi.getTemplate(productId)
      // 拼接必播/必问/禁播到完整话术
      const md = res.mandatoryDisclosure || []
      const rq = res.requiredQuestions || []
      const fp = res.forbiddenPhrases || []
      scriptText.value = [
        '📋 必播项：',
        ...md.map((s: string, i: number) => `${i + 1}. ${s}`),
        '',
        '❓ 必问题：',
        ...rq.map((s: string, i: number) => `${i + 1}. ${s}`)
      ].join('\n')
      return scriptText.value
    } catch (e) {
      return ''
    }
  }

  function tickElapsed() {
    if (recording.value) {
      nodeElapsed.value = Date.now() - nodeStartTime.value
    }
  }

  async function startNode(nodeIdx: number) {
    currentNodeIdx.value = nodeIdx
    nodeStartTime.value = Date.now()
    nodeElapsed.value = 0
    await startCamera()
    await startRecording()
  }

  async function startCamera() {
    try {
      const stream = await navigator.mediaDevices.getUserMedia({
        video: { width: 1280, height: 720, facingMode: 'user' },
        audio: { echoCancellation: true, noiseSuppression: true }
      })
      mediaStream.value = stream
    } catch (e: any) {
      errors.value.push(`无法访问摄像头: ${e.message}`)
      throw e
    }
  }

  function startRecording() {
    if (!mediaStream.value) return
    try {
      const mr = new MediaRecorder(mediaStream.value, { mimeType: 'video/webm;codecs=vp9' })
      mr.ondataavailable = (e) => {
        if (e.data.size > 0) chunks.value.push(e.data)
      }
      mr.start(1000)
      mediaRecorder.value = mr
      recording.value = true
    } catch (e: any) {
      // 降级
      const mr = new MediaRecorder(mediaStream.value)
      mr.start(1000)
      mediaRecorder.value = mr
      recording.value = true
    }
  }

  function pauseRecording() {
    if (mediaRecorder.value && mediaRecorder.value.state === 'recording') {
      mediaRecorder.value.pause()
    }
  }

  function resumeRecording() {
    if (mediaRecorder.value && mediaRecorder.value.state === 'paused') {
      mediaRecorder.value.resume()
    }
  }

  async function completeNode(nodeId: string, extra?: any) {
    try {
      const blob = new Blob(chunks.value, { type: 'video/webm' })
      const formData = new FormData()
      formData.append('file', blob, `${nodeId}.webm`)
      // 完成节点
      await recordingApi.completeNode(businessId.value, {
        nodeId,
        durationMs: nodeElapsed.value,
        ...extra
      })
      chunks.value = []
      return true
    } catch (e) {
      return false
    }
  }

  function stop() {
    if (mediaRecorder.value && mediaRecorder.value.state !== 'inactive') {
      mediaRecorder.value.stop()
    }
    if (mediaStream.value) {
      mediaStream.value.getTracks().forEach(t => t.stop())
      mediaStream.value = null
    }
    recording.value = false
  }

  return {
    businessId, recId, currentNodeIdx, nodeStartTime, nodeElapsed,
    recording, mediaStream, mediaRecorder, chunks, scriptText, errors,
    NODES,
    setBusiness, setRec, loadScript, tickElapsed,
    startNode, startCamera, startRecording, pauseRecording, resumeRecording,
    completeNode, stop
  }
})
