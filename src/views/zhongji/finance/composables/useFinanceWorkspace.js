import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { useRoute, onBeforeRouteLeave } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ledgerOverview, ledgerList, ledgerDetail, ledgerSave, ledgerVoid, ledgerOptions } from '@/api/finance/ledger'
import { expenseOptions, expenseDetail } from '@/api/finance/reimbursements'
import { money } from '@/utils/money'
import { financeToken } from '@/utils/financeToken'

export function useFinanceWorkspace() {
  const route = useRoute()
  const tabs = {
    overview: '经营概览',
    contracts: '合同对账',
    projects: '项目收支',
    accounts: '资金账户',
    transactions: '收支流水',
    invoices: '开票记录'
  }
  const directions = {
    income: '收入',
    expense: '支出',
    transfer_in: '内部转入',
    transfer_out: '内部转出'
  }
  const businessAmounts = [
    { key: 'receivedAmount', label: '实际收款' },
    { key: 'expenseAmount', label: '实际支出' },
    { key: 'invoicedAmount', label: '有效开票金额' }
  ]

  const tab = ref('overview')
  const summary = ref({})
  const rows = ref([])
  const total = ref(0)
  const loading = ref(false)
  const error = ref(false)
  const query = reactive({ name: '', status: '', direction: '', pageNum: 1, pageSize: 20 })
  const range = ref([])
  const contextLabel = ref('')
  let sequence = 0

  const detailsOpen = ref(false)
  const detailKind = ref('')
  const selected = ref(null)
  const formOpen = ref(false)
  const formKind = ref('')
  const formRef = ref()
  const draft = ref({})
  const saving = ref(false)
  const claimLabel = ref('')
  const invoiceLabel = ref('')
  const contractLabel = ref('')
  const projectLabel = ref('')
  let initial = ''

  const required = [{ required: true, message: '请填写此项', trigger: 'blur' }]
  const availableAccounts = computed(() => (summary.value.accounts || []).filter(account => account.active))
  const metrics = computed(() => [
    { label: '累计实际收入', amount: summary.value.cash?.income },
    { label: '累计实际支出', amount: summary.value.cash?.expense },
    { label: '已开有效发票', amount: summary.value.invoices?.amount },
    { label: '待处理报账', amount: summary.value.claims?.pendingAmount }
  ])
  const fieldLabels = {
    name: '名称',
    code: '编号',
    accountNumber: '账号 / 标识',
    holder: '户名',
    openingBalance: '期初余额',
    balance: '当前余额',
    active: '启用',
    amount: '金额 / 价税合计',
    invoiceNo: '发票号码',
    buyerName: '客户 / 购方',
    buyerTaxId: '购方税号',
    invoiceType: '发票类型',
    taxAmount: '税额',
    invoiceDate: '开票日期',
    transactionDate: '交易日期',
    accountName: '资金账户',
    direction: '交易方向',
    counterparty: '交易对方',
    contractName: '合同',
    projectName: '项目',
    reimbursementId: '对应报账ID',
    receivedAmount: '实际收款',
    expenseAmount: '实际支出',
    invoicedAmount: '有效开票',
    outstandingAmount: '合同未收余额',
    pendingAmount: '待处理报账',
    paymentPlan: '原合同付款计划',
    ownership: '合同归属',
    purpose: '用途',
    status: '状态',
    remark: '备注',
    createdBy: '录入人',
    createdAt: '录入时间',
    voidReason: '作废原因',
    voidBy: '作废人',
    voidAt: '作废时间'
  }
  const detailFields = computed(() => Object.entries(fieldLabels).filter(([key]) => selected.value?.[key] != null))

  function display(key, value) {
    if (['amount', 'taxAmount', 'openingBalance', 'balance', 'receivedAmount', 'expenseAmount', 'invoicedAmount', 'outstandingAmount', 'pendingAmount'].includes(key)) {
      return '¥ ' + money(value)
    }
    if (key === 'direction') return directions[value] || value
    if (key === 'status') return { posted: '有效', issued: '已开具', void: '已作废' }[value] || value
    if (key === 'active') return value ? '是' : '否'
    if (key === 'ownership') return { pending: '待确认', company: '公司', intermediary: '中间人' }[value] || value
    return value || '未填写'
  }

  async function refreshSummary() {
    summary.value = (await ledgerOverview()).data
  }

  async function load() {
    if (tab.value === 'overview') return
    const requestSequence = ++sequence
    loading.value = true
    error.value = false
    try {
      const result = await ledgerList(tab.value, {
        ...query,
        dateFrom: range.value?.[0] || '',
        dateTo: range.value?.[1] || ''
      })
      if (requestSequence === sequence) {
        rows.value = result.rows
        total.value = result.total
      }
    } catch {
      if (requestSequence === sequence) {
        error.value = true
        rows.value = []
      }
    } finally {
      if (requestSequence === sequence) loading.value = false
    }
  }

  function search() {
    query.pageNum = 1
    load()
  }

  function clean() {
    for (const key of ['accountId', 'contractId', 'projectId', 'invoiceId']) delete query[key]
    Object.assign(query, { name: '', status: '', direction: '', pageNum: 1 })
    range.value = []
    contextLabel.value = ''
  }

  function reset() {
    clean()
    load()
  }

  function changeTab() {
    sequence++
    clean()
    rows.value = []
    total.value = 0
    load()
  }

  function focus(kind, params, label) {
    tab.value = kind
    clean()
    Object.assign(query, params)
    contextLabel.value = label
    load()
  }

  async function detail(row) {
    detailKind.value = tab.value
    selected.value = ['invoices', 'transactions'].includes(tab.value)
      ? (await ledgerDetail(tab.value, row.id)).data
      : { ...row }
    detailsOpen.value = true
  }

  function today() {
    return new Date().toLocaleDateString('sv-SE')
  }

  function create(kind) {
    formKind.value = kind
    draft.value = kind === 'accounts'
      ? { name: '', holder: '', accountNumber: '', openingBalance: '0.00', active: true, remark: '' }
      : kind === 'invoices'
        ? {
            clientToken: financeToken(),
            invoiceNo: '',
            buyerName: '',
            buyerTaxId: '',
            invoiceType: '',
            amount: '',
            taxAmount: '0.00',
            invoiceDate: today(),
            contractId: null,
            projectId: null,
            files: '',
            remark: ''
          }
        : {
            clientToken: financeToken(),
            direction: 'income',
            accountId: null,
            toAccountId: null,
            amount: '',
            transactionDate: today(),
            purpose: '',
            counterparty: '',
            contractId: null,
            projectId: null,
            reimbursementId: null,
            invoiceId: null,
            files: '',
            remark: ''
          }
    claimLabel.value = ''
    invoiceLabel.value = ''
    contractLabel.value = ''
    projectLabel.value = ''
    initial = JSON.stringify(draft.value)
    formOpen.value = true
    nextTick(() => formRef.value?.clearValidate())
  }

  function editAccount() {
    create('accounts')
    draft.value = { ...selected.value, active: !!selected.value.active }
    initial = JSON.stringify(draft.value)
    detailsOpen.value = false
  }

  function changeDirection() {
    Object.assign(draft.value, {
      toAccountId: null,
      reimbursementId: null,
      invoiceId: null,
      contractId: null,
      projectId: null
    })
    claimLabel.value = ''
    invoiceLabel.value = ''
    contractLabel.value = ''
    projectLabel.value = ''
  }

  async function selectClaim(claim) {
    if (!claim) return
    const row = (await expenseDetail(claim.id)).data
    draft.value.amount = row.amount
    draft.value.projectId = row.projectId
    draft.value.contractId = null
    draft.value.purpose = '支付报账：' + row.purpose
    projectLabel.value = row.projectName || ''
    claimLabel.value = claim.name || row.purpose
  }

  async function invoiceChoices(name) {
    const result = await ledgerList('invoices', { name, status: 'issued', pageNum: 1, pageSize: 30 })
    return result.rows.map(row => ({
      ...row,
      name: row.invoiceNo + ' · ' + row.buyerName,
      code: '¥ ' + money(row.amount)
    }))
  }

  async function selectInvoice(invoice) {
    if (!invoice) return
    const row = (await ledgerDetail('invoices', invoice.id)).data
    Object.assign(draft.value, {
      invoiceId: row.id,
      contractId: row.contractId,
      projectId: row.projectId,
      counterparty: row.buyerName,
      purpose: '发票收款 ' + row.invoiceNo
    })
    contractLabel.value = row.contractName || ''
    projectLabel.value = row.projectName || ''
    invoiceLabel.value = row.invoiceNo
  }

  async function receiveInvoice() {
    const invoice = { ...selected.value }
    detailsOpen.value = false
    create('transactions')
    await selectInvoice(invoice)
  }

  async function closeForm(done) {
    if (saving.value) return
    if (initial !== JSON.stringify(draft.value)) {
      try {
        await ElMessageBox.confirm('放弃未保存的财务记录？', '离开确认')
      } catch {
        return
      }
    }
    done()
  }

  async function save() {
    if (saving.value || !await formRef.value.validate().catch(() => false)) return
    saving.value = true
    try {
      await ledgerSave(formKind.value, draft.value)
      ElMessage.success('记录已保存')
      formOpen.value = false
      await refreshSummary()
      if (tab.value !== 'overview') await load()
    } finally {
      saving.value = false
    }
  }

  async function cancelRecord() {
    try {
      const result = await ElMessageBox.prompt(
        selected.value.transferGroup
          ? '同时作废这笔转账的转出与转入，保留审计记录。'
          : '作废后不再计入有效金额，原始记录继续保留。',
        '作废记录',
        {
          inputPlaceholder: '必填：作废原因',
          inputValidator: value => !!value?.trim() && value.length <= 500 || '请填写最多500字的作废原因'
        }
      )
      saving.value = true
      await ledgerVoid(detailKind.value, selected.value.id, { reason: result.value, version: selected.value.version })
      selected.value = (await ledgerDetail(detailKind.value, selected.value.id)).data
      await refreshSummary()
      await load()
      ElMessage.success('记录已作废')
    } catch (error) {
      if (error !== 'cancel' && error !== 'close') console.error(error)
    } finally {
      saving.value = false
    }
  }

  onBeforeRouteLeave(async () => {
    if (saving.value) return false
    if (formOpen.value && initial !== JSON.stringify(draft.value)) {
      try {
        await ElMessageBox.confirm('离开并放弃未保存的财务记录？')
      } catch {
        return false
      }
    }
    return true
  })

  onMounted(async () => {
    await refreshSummary()
    if (route.query.claimId) {
      tab.value = 'transactions'
      await load()
      create('transactions')
      draft.value.direction = 'expense'
      draft.value.reimbursementId = Number(route.query.claimId)
      await selectClaim({ id: Number(route.query.claimId) })
    }
  })

  return {
    tabs, directions, businessAmounts, tab, summary, rows, total, loading, error, query, range, contextLabel,
    detailsOpen, detailKind, selected, formOpen, formKind, formRef, draft, saving,
    claimLabel, invoiceLabel, contractLabel, projectLabel, required, availableAccounts, metrics, detailFields,
    display, refreshSummary, load, search, clean, reset, changeTab, focus, detail, today, create, editAccount,
    changeDirection, selectClaim, invoiceChoices, selectInvoice, receiveInvoice, closeForm, save, cancelRecord,
    expenseOptions, ledgerOptions, money
  }
}
