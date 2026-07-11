/**
 * @file EVM 挣值计算 Web Worker
 * @description P1-5: 将 EVM 指标计算（SPI/CPI/EAC/VAC/TCPI）放到 Web Worker 中执行，
 *   避免大数据量计算阻塞主线程 UI 交互。
 *
 * 计算指标：
 *   - PV (Planned Value): 计划价值
 *   - EV (Earned Value): 挣值
 *   - AC (Actual Cost): 实际成本
 *   - SV (Schedule Variance): 进度偏差 = EV - PV
 *   - CV (Cost Variance): 成本偏差 = EV - AC
 *   - SPI (Schedule Performance Index): 进度绩效指数 = EV / PV
 *   - CPI (Cost Performance Index): 成本绩效指数 = EV / AC
 *   - EAC (Estimate at Completion): 完工估算 = BAC / CPI
 *   - VAC (Variance at Completion): 完工偏差 = BAC - EAC
 *   - TCPI (To-Complete Performance Index): 完工尚需绩效指数 = (BAC - EV) / (BAC - AC)
 */

interface EvmInput {
  tasks: EvmTask[]
  bac: number // 完工预算 (Budget at Completion)
  measureDate: string
}

interface EvmTask {
  id: string
  plannedCost: number
  actualCost: number
  progressPct: number // 0-100
  plannedPct: number // 0-100
}

interface EvmResult {
  pv: number
  ev: number
  ac: number
  sv: number
  cv: number
  spi: number
  cpi: number
  eac: number
  vac: number
  tcpi: number
  status: 'GREEN' | 'YELLOW' | 'RED'
  measureDate: string
}

self.addEventListener('message', (e: MessageEvent<EvmInput>) => {
  const { tasks, bac, measureDate } = e.data

  let pv = 0
  let ev = 0
  let ac = 0

  for (const task of tasks) {
    pv += task.plannedCost * (task.plannedPct / 100)
    ev += task.plannedCost * (task.progressPct / 100)
    ac += task.actualCost
  }

  const sv = ev - pv
  const cv = ev - ac
  const spi = pv > 0 ? ev / pv : 1
  const cpi = ac > 0 ? ev / ac : 1
  const eac = cpi > 0 ? bac / cpi : bac
  const vac = bac - eac
  const tcpi = bac - ac > 0 ? (bac - ev) / (bac - ac) : 1

  // 状态判定: SPI/CPI >= 0.9 GREEN, >= 0.75 YELLOW, else RED
  const minIdx = Math.min(spi, cpi)
  const status: EvmResult['status'] = minIdx >= 0.9 ? 'GREEN' : minIdx >= 0.75 ? 'YELLOW' : 'RED'

  const result: EvmResult = {
    pv: Math.round(pv * 100) / 100,
    ev: Math.round(ev * 100) / 100,
    ac: Math.round(ac * 100) / 100,
    sv: Math.round(sv * 100) / 100,
    cv: Math.round(cv * 100) / 100,
    spi: Math.round(spi * 1000) / 1000,
    cpi: Math.round(cpi * 1000) / 1000,
    eac: Math.round(eac * 100) / 100,
    vac: Math.round(vac * 100) / 100,
    tcpi: Math.round(tcpi * 1000) / 1000,
    status,
    measureDate,
  }

  ;(self as unknown as Worker).postMessage(result)
})
