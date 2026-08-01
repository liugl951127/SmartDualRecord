<template>
  <div class="file-push">
    <div class="page-header">
      <h1>文件推送</h1>
      <p>向客户推送电子文件 (说明书/合同/确认书)</p>
    </div>

    <div class="layout">
      <div class="left">
        <div class="card">
          <div class="card-header">
            <h3>📚 模板库</h3>
          </div>
          <div class="template-list">
            <div
              v-for="t in templates"
              :key="t.id"
              :class="['template-item', selected?.id === t.id && 'active']"
              @click="selected = t"
            >
              <div class="ti-icon">{{ t.icon }}</div>
              <div class="ti-info">
                <div class="ti-name">{{ t.name }}</div>
                <div class="ti-desc">{{ t.desc }}</div>
              </div>
            </div>
          </div>
        </div>

        <div class="card">
          <div class="card-header">
            <h3>📤 自定义上传</h3>
          </div>
          <input type="file" id="file-input" class="file-input" @change="onFileSelect" accept=".pdf,.png,.jpg,.jpeg" />
          <label for="file-input" class="upload-btn">
            <div v-if="!customFile">📁 点击选择文件</div>
            <div v-else class="uploaded">
              <span>{{ customFile.name }}</span>
              <button class="del-btn" @click.prevent="customFile = null">✕</button>
            </div>
          </label>
        </div>
      </div>

      <div class="right">
        <div class="card">
          <div class="card-header">
            <h3>📋 推送配置</h3>
          </div>
          <div v-if="!selected && !customFile" class="empty-tip">
            <div class="et-icon">👈</div>
            <p>从左侧选择模板或上传文件</p>
          </div>
          <div v-else class="config-form">
            <div class="form-item">
              <label>目标业务</label>
              <select v-model="config.businessId" class="form-input">
                <option value="">请选择业务</option>
                <option v-for="b in businesses" :key="b.id" :value="b.id">
                  {{ b.id }} - {{ b.customer }}
                </option>
              </select>
            </div>
            <div class="form-item">
              <label>文件名称</label>
              <input v-model="config.name" class="form-input" placeholder="显示给客户的文件名" />
            </div>
            <div class="form-item">
              <label>附加说明</label>
              <textarea v-model="config.note" class="form-input" rows="3" placeholder="可选, 推送给客户时附带的说明"></textarea>
            </div>
            <button class="push-btn" :disabled="!canPush" @click="onPush">📤 推送给客户</button>
          </div>
        </div>

        <div class="card">
          <div class="card-header">
            <h3>📊 推送历史</h3>
            <span class="badge">{{ pushedFiles.length }}</span>
          </div>
          <table class="data-table">
            <thead>
              <tr><th>文件</th><th>客户</th><th>状态</th><th>时间</th></tr>
            </thead>
            <tbody>
              <tr v-for="f in pushedFiles" :key="f.id">
                <td>{{ f.name }}</td>
                <td>{{ f.customer }}</td>
                <td>
                  <span :class="['status', `status-${f.statusCls}`]">{{ f.statusLabel }}</span>
                </td>
                <td class="mono">{{ f.time }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { showToast, showDialog } from 'vant'

const templates = [
  { id: 't1', name: '产品说明书', icon: '📄', desc: 'PDF · 通用模板' },
  { id: 't2', name: '风险揭示书', icon: '⚠️', desc: 'PDF · 风险等级匹配' },
  { id: 't3', name: '理财合同', icon: '📝', desc: 'PDF · 标准合同' },
  { id: 't4', name: '电子签名授权书', icon: '✍️', desc: 'PDF · 签名授权' },
  { id: 't5', name: '收益走势图', icon: '📊', desc: 'PNG · 业绩展示' }
]

const selected = ref<any>(null)
const customFile = ref<File | null>(null)

const config = ref({
  businessId: '',
  name: '',
  note: ''
})

const businesses = ref([
  { id: 'BNK20260801-900001', customer: '张志强' },
  { id: 'BNK20260801-900003', customer: '王明华' },
  { id: 'FND20260801-900004', customer: '赵晓东' }
])

const pushedFiles = ref([
  { id: 'f001', name: '产品说明书.pdf', customer: '张志强', status: 'SIGNED', statusLabel: '已签署', statusCls: 'success', time: '07-20 10:30' },
  { id: 'f002', name: '风险揭示书.pdf', customer: '李建国', status: 'VIEWED', statusLabel: '已查阅', statusCls: 'info', time: '07-25 14:32' },
  { id: 'f003', name: '理财合同.pdf', customer: '张志强', status: 'PUSHED', statusLabel: '已推送', statusCls: 'warning', time: '07-20 10:35' }
])

const canPush = computed(() => {
  return (selected.value || customFile.value) && config.value.businessId && config.value.name
})

function onFileSelect(e: Event) {
  const file = (e.target as HTMLInputElement).files?.[0]
  if (file) {
    if (file.size > 10 * 1024 * 1024) {
      showToast('文件大小不能超过 10MB')
      return
    }
    customFile.value = file
    config.value.name = file.name
    selected.value = null
  }
}

function onPush() {
  showDialog({ title: '确认推送', message: `将 ${config.value.name} 推送给客户?`, showCancelButton: true })
    .then(() => {
      pushedFiles.value.unshift({
        id: 'f' + Date.now(),
        name: config.value.name,
        customer: businesses.value.find(b => b.id === config.value.businessId)?.customer || '',
        status: 'PUSHED',
        statusLabel: '已推送',
        statusCls: 'warning',
        time: new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
      })
      showToast('推送成功')
      // 重置
      config.value = { businessId: '', name: '', note: '' }
      selected.value = null
      customFile.value = null
    })
    .catch(() => {})
}
</script>

<style lang="scss" scoped>
.file-push { padding: 24px; }
.page-header { margin-bottom: 16px; h1 { font-size: 22px; font-weight: 600; margin: 0 0 4px; } p { font-size: 13px; color: var(--text-3); margin: 0; } }

.layout {
  display: grid;
  grid-template-columns: 360px 1fr;
  gap: 16px;
}
.left, .right { display: flex; flex-direction: column; gap: 16px; }
.card { background: white; border-radius: 12px; padding: 16px; box-shadow: 0 1px 4px rgba(0,0,0,0.05); }
.card-header { margin-bottom: 12px; h3 { font-size: 14px; font-weight: 600; margin: 0; } }
.badge { background: var(--accent); color: white; font-size: 11px; padding: 2px 8px; border-radius: 10px; }

.template-list { display: flex; flex-direction: column; gap: 8px; }
.template-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
  border: 1px solid var(--border);
  border-radius: 8px;
  cursor: pointer;
  &:hover { background: var(--bg); }
  &.active { border-color: var(--accent); background: rgba(184,134,11,0.05); }
}
.ti-icon { font-size: 28px; }
.ti-name { font-size: 14px; font-weight: 500; }
.ti-desc { font-size: 12px; color: var(--text-3); margin-top: 2px; }

.file-input { display: none; }
.upload-btn {
  display: block;
  padding: 24px;
  text-align: center;
  border: 2px dashed var(--border);
  border-radius: 8px;
  cursor: pointer;
  color: var(--text-3);
  &:hover { border-color: var(--accent); color: var(--accent); }
}
.uploaded {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.del-btn { background: none; border: none; color: var(--danger); cursor: pointer; padding: 4px 8px; }

.empty-tip { text-align: center; padding: 60px 20px; color: var(--text-3); }
.et-icon { font-size: 48px; margin-bottom: 8px; opacity: 0.4; }

.config-form { }
.form-item { margin-bottom: 12px; }
.form-item label { display: block; font-size: 12px; color: var(--text-2); margin-bottom: 4px; }
.form-input {
  width: 100%;
  padding: 8px 12px;
  border: 1px solid var(--border);
  border-radius: 6px;
  font-size: 13px;
  font-family: inherit;
  &:focus { outline: none; border-color: var(--accent); }
}
textarea.form-input { resize: vertical; }

.push-btn {
  width: 100%;
  padding: 12px;
  background: linear-gradient(135deg, var(--primary) 0%, var(--primary-light) 100%);
  color: white;
  border: none;
  border-radius: 8px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  margin-top: 8px;
  &:disabled { opacity: 0.5; cursor: not-allowed; }
}

.data-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 12px;
  th, td { padding: 8px; text-align: left; border-bottom: 1px solid var(--border); }
  th { color: var(--text-3); font-weight: 500; background: var(--bg); }
}
.mono { font-family: monospace; }
.status { font-size: 11px; padding: 2px 6px; border-radius: 4px; }
.status-success { background: rgba(7,193,96,0.1); color: var(--success); }
.status-info { background: rgba(30,42,71,0.1); color: var(--primary); }
.status-warning { background: rgba(255,151,106,0.1); color: var(--warning); }
</style>
