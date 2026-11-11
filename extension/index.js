/**
 * App 管理扩展
 *
 * 提供给移动端的最小管理面：状态概览、模型列表、切换默认模型。
 * 路由已由主插件挂在 /api/ext/app-manager 并强制登录；写操作额外要求 CSRF。
 */
import fs from 'node:fs'
import os from 'node:os'

export default function register({ router, cfg, requireCsrf, logger }) {
  const getCfg = () => {
    try { return cfg.loadConfig() || {} } catch (_) { return {} }
  }

  const modelEntries = (c) => {
    const out = []
    const m = c && c.model
    if (!m || typeof m !== 'object') return out
    for (const [key, v] of Object.entries(m)) {
      if (key === 'default' || !v || typeof v !== 'object') continue
      out.push({ key, value: v })
    }
    return out
  }

  router.get('/status', (req, res) => {
    const c = getCfg()
    const models = modelEntries(c)
    res.json({
      ok: true,
      time: Date.now(),
      plugin: {
        agentEnabled: !!(c.agent && c.agent.enabled),
        defaultModel: (c.model && c.model.default) || '',
        modelCount: models.length,
        groupCount: Array.isArray(c.chat && c.chat.globalAIGroups) ? c.chat.globalAIGroups.length : 0
      }
    })
  })

  router.get('/models', (req, res) => {
    const c = getCfg()
    const def = (c.model && c.model.default) || ''
    const list = modelEntries(c).map(({ key, value }) => ({
      key,
      name: value.name || key,
      kind: value.kind || 'custom',
      vision: Array.isArray(value.scopes) ? value.scopes.includes('vision') : !!value.vision,
      hasKey: !!String(value.apiKey || '').trim(),
      isDefault: key === def
    }))
    res.json({ ok: true, default: def, list })
  })

  router.get('/metrics', (req, res) => {
    const cpuTimes = () => {
      const l = (fs.readFileSync('/proc/stat', 'utf8').split('\n')[0] || '').trim().split(/\s+/).slice(1).map(Number)
      return { idle: l[3] + (l[4] || 0), total: l.reduce((a, b) => a + b, 0) }
    }
    const t1 = cpuTimes()
    setTimeout(() => {
      const t2 = cpuTimes()
      const cpu = t2.total > t1.total ? Math.round(100 * (1 - (t2.idle - t1.idle) / (t2.total - t1.total))) : 0
      const mi = {}
      for (const line of fs.readFileSync('/proc/meminfo', 'utf8').split('\n')) {
        const m = line.match(/^(\w+):\s+(\d+)/)
        if (m) mi[m[1]] = +m[2]
      }
      const memTotal = (mi.MemTotal || 0) * 1024
      const memAvail = (mi.MemAvailable || 0) * 1024
      const load = parseFloat((fs.readFileSync('/proc/loadavg', 'utf8').split(' ')[0] || '0'))
      let disk = { total: 0, free: 0 }
      try { const sf = fs.statfsSync('/'); disk = { total: sf.blocks * sf.bsize, free: sf.bfree * sf.bsize } } catch (_) {}
      res.json({ ok: true, cpu, mem: { used: memTotal - memAvail, total: memTotal }, disk, load, uptime: Math.round(os.uptime()) })
    }, 250)
  })

  router.post('/default-model', requireCsrf, (req, res) => {
    const key = String((req.body && req.body.key) || '').trim()
    if (!key) return res.json({ ok: false, msg: '缺少模型 key' })
    const c = getCfg()
    if (!c.model || !c.model[key] || typeof c.model[key] !== 'object') {
      return res.json({ ok: false, msg: '模型不存在' })
    }
    const before = c.model.default || ''
    c.model.default = key
    let ok = false
    try { ok = cfg.saveConfig(c) } catch (e) { logger.warn(`[app-manager] 保存失败: ${e.message}`) }
    if (!ok) {
      c.model.default = before
      return res.json({ ok: false, msg: '保存失败，请检查 config.yaml 权限' })
    }
    res.json({ ok: true, msg: '已切换默认模型', key })
  })
}
