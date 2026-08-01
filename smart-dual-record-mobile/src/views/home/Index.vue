<template>
  <div class="landing">
    <div class="hero">
      <div class="hero-bg">
        <div class="hero-circle c1"></div>
        <div class="hero-circle c2"></div>
        <div class="hero-circle c3"></div>
      </div>
      <div class="hero-content">
        <div class="logo">🏦</div>
        <h1>智能双录中台</h1>
        <p>合规 · 高效 · 全场景</p>
        <div class="hero-stats">
          <div class="stat"><div class="num">99.9%</div><div class="lbl">合规率</div></div>
          <div class="stat"><div class="num">32项</div><div class="lbl">录像检查</div></div>
          <div class="stat"><div class="num">8节点</div><div class="lbl">流程闭环</div></div>
        </div>
      </div>
    </div>

    <div class="entry-section">
      <h2 class="section-title">请选择身份</h2>
      <div class="entry-grid">
        <div class="entry-card" @click="$router.push('/login?role=CUSTOMER&redirect=/h5/home')">
          <div class="entry-icon">👤</div>
          <div class="entry-name">我是客户</div>
          <div class="entry-desc">手机 / 移动端</div>
          <div class="entry-tag">H5</div>
        </div>
        <div class="entry-card" @click="$router.push('/login?role=AGENT&redirect=/pc/dashboard')">
          <div class="entry-icon">💼</div>
          <div class="entry-name">我是坐席</div>
          <div class="entry-desc">PC 端 · 柜员 / 客服</div>
          <div class="entry-tag">PC</div>
        </div>
        <div class="entry-card" @click="$router.push('/login?role=ADVISOR&redirect=/pc/advisor')">
          <div class="entry-icon">💎</div>
          <div class="entry-name">理财经理</div>
          <div class="entry-desc">PC 端 · 财富顾问</div>
          <div class="entry-tag">PC</div>
        </div>
        <div class="entry-card" @click="$router.push('/h5/home')">
          <div class="entry-icon">📞</div>
          <div class="entry-name">预约回访</div>
          <div class="entry-desc">15 天犹豫期咨询</div>
          <div class="entry-tag">H5</div>
        </div>
      </div>
    </div>

    <div class="features">
      <h2 class="section-title">核心能力</h2>
      <div class="feature-list">
        <div class="feature">
          <span class="feature-icon">📹</span>
          <div class="feature-text">
            <div class="feature-name">双录合规</div>
            <div class="feature-desc">8 节点标准流程 · 32 项自动检查</div>
          </div>
        </div>
        <div class="feature">
          <span class="feature-icon">🤖</span>
          <div class="feature-text">
            <div class="feature-name">AI 智能质检</div>
            <div class="feature-desc">实时禁播词 + 人脸 + 画面 + 音频检测</div>
          </div>
        </div>
        <div class="feature">
          <span class="feature-icon">📋</span>
          <div class="feature-text">
            <div class="feature-name">产品话术</div>
            <div class="feature-desc">三层叠加 · 渠道差分 · 实时加载</div>
          </div>
        </div>
        <div class="feature">
          <span class="feature-icon">🔄</span>
          <div class="feature-text">
            <div class="feature-name">跨渠道补录</div>
            <div class="feature-desc">线下失败 · 线上继续 · 无缝衔接</div>
          </div>
        </div>
      </div>
    </div>

    <div class="footer">
      <p>智能双录一体化中台 · 财富管家 v1.0</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useRoute } from 'vue-router'
const route = useRoute()
console.log('Entry route:', route.query.role)
</script>

<style lang="scss" scoped>
.landing {
  min-height: 100vh;
  background: var(--bg);
  padding-bottom: 40px;
}

.hero {
  position: relative;
  background: linear-gradient(135deg, #1e2a47 0%, #2c3a5c 100%);
  color: white;
  padding: 60px 24px 80px;
  overflow: hidden;
}
.hero-bg { position: absolute; inset: 0; pointer-events: none; }
.hero-circle {
  position: absolute;
  border-radius: 50%;
  background: rgba(184, 134, 11, 0.15);
}
.c1 { width: 200px; height: 200px; top: -50px; right: -50px; }
.c2 { width: 140px; height: 140px; bottom: 20px; left: -30px; background: rgba(255,255,255,0.05); }
.c3 { width: 80px; height: 80px; top: 100px; right: 60px; background: rgba(255,255,255,0.08); }
.hero-content { position: relative; text-align: center; }
.logo { font-size: 56px; margin-bottom: 12px; }
.hero h1 { font-size: 28px; margin: 0 0 8px; font-weight: 700; }
.hero p { font-size: 14px; opacity: 0.85; margin: 0 0 32px; }
.hero-stats {
  display: flex;
  justify-content: space-around;
  background: rgba(255,255,255,0.1);
  border-radius: 12px;
  padding: 16px;
  backdrop-filter: blur(10px);
}
.stat .num { font-size: 24px; font-weight: 700; font-family: 'JetBrains Mono', monospace; color: var(--accent-light); }
.stat .lbl { font-size: 12px; opacity: 0.85; margin-top: 4px; }

.entry-section, .features {
  padding: 24px 16px 0;
}
.section-title {
  font-size: 18px;
  font-weight: 600;
  margin: 0 0 16px;
  color: var(--text-1);
  position: relative;
  padding-left: 12px;
}
.section-title::before {
  content: '';
  position: absolute;
  left: 0; top: 4px; bottom: 4px;
  width: 4px;
  background: var(--accent);
  border-radius: 2px;
}

.entry-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
}
.entry-card {
  position: relative;
  background: var(--card);
  border-radius: 12px;
  padding: 20px 12px;
  text-align: center;
  box-shadow: 0 1px 4px rgba(0,0,0,0.05);
  transition: all 0.2s;
  cursor: pointer;
}
.entry-card:active {
  transform: scale(0.97);
  box-shadow: 0 4px 12px rgba(0,0,0,0.1);
}
.entry-icon { font-size: 36px; margin-bottom: 8px; }
.entry-name { font-size: 15px; font-weight: 600; margin-bottom: 4px; }
.entry-desc { font-size: 11px; color: var(--text-3); }
.entry-tag {
  position: absolute;
  top: 8px; right: 8px;
  background: var(--accent);
  color: white;
  font-size: 10px;
  padding: 2px 6px;
  border-radius: 4px;
  font-weight: 600;
}

.feature-list {
  background: var(--card);
  border-radius: 12px;
  padding: 8px 0;
  box-shadow: 0 1px 4px rgba(0,0,0,0.05);
}
.feature {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  border-bottom: 1px solid var(--border);
}
.feature:last-child { border-bottom: none; }
.feature-icon { font-size: 24px; }
.feature-name { font-size: 14px; font-weight: 600; }
.feature-desc { font-size: 12px; color: var(--text-3); margin-top: 2px; }

.footer {
  text-align: center;
  padding: 24px;
  color: var(--text-3);
  font-size: 12px;
}
</style>
