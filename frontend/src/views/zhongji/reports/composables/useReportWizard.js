import { computed, getCurrentInstance, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { onBeforeRouteLeave, useRouter } from 'vue-router'
import request from '@/utils/request'
import { projectList, getProject } from '@/api/workProject'

export function useReportWizard() {
  const { proxy } = getCurrentInstance()
  const router = useRouter()
  const staff = reactive({ inspectors: [], reviewer: null, approver: null })
  const employees = ref([])
  const step = ref(0)
  const busy = ref(false)
  const projects = ref([])
  const projectId = ref()
  const project = ref()
  const credentials = ref([])
  const history = ref([])
  const uploads = ref('')
  const batchSystem = ref('')
  const batchDate = ref('')
  const previewOpen = ref(false)
  const previewUrl = ref('')
  let blob = ''
  let searchSequence = 0

  const states = { tested: '存在且已检测', absent: '不存在', untested: '存在但未检测' }
  const verdicts = { pending: '未判定', pass: '合格', fail: '不合格', na: '不适用' }
  const names = [
    '火灾自动报警系统', '消防给水系统', '消火栓系统', '自动喷水灭火系统',
    '防烟排烟系统', '消防应急照明和疏散指示系统', '防火分隔设施', '电气火灾监控系统',
    '消防电源监控系统', '火灾警报和消防应急广播系统', '消防电梯', '消防专用电话系统',
    '消防供配电设施', '建筑灭火器'
  ]
  function createForm() {
    return {
      reportNo: '',
      reportDate: '',
      inspectors: '',
      reviewer: '',
      approver: '',
      notes: '',
      credentialIds: [],
      systems: names.map(name => ({ name, status: '', description: '', items: [] })),
      photos: []
    }
  }
  const form = ref(createForm())
  let savedDraftSnapshot = ''
  let selectedProjectId = null

  function draftSnapshot() {
    return JSON.stringify({ form: form.value, staff: { ...staff } })
  }

  function hasUnsavedDraft() {
    return Boolean(savedDraftSnapshot) && draftSnapshot() !== savedDraftSnapshot
  }

  function markDraftSaved() {
    savedDraftSnapshot = draftSnapshot()
  }
  const tested = computed(() => form.value.systems.filter(system => system.status === 'tested' && system.name))

  function fileUrl(url) {
    return url.startsWith('/profile/') ? import.meta.env.VITE_APP_BASE_API + url : url
  }

  async function findEmployees(name) {
    employees.value = (await request({ url: '/management/employees/options', params: { name } })).data
  }

  async function find(name = '') {
    const sequence = ++searchSequence
    const result = await projectList({ projectType: 'JC', name, pageNum: 1, pageSize: 30 })
    if (sequence === searchSequence) projects.value = result.rows
  }

  async function choose(id) {
    if (selectedProjectId && String(selectedProjectId) !== String(id)) {
      if (hasUnsavedDraft()) {
        try {
          await proxy.$modal.confirm('切换项目会清除当前尚未生成的报告内容，是否继续？')
        } catch {
          projectId.value = selectedProjectId
          return
        }
      }
      form.value = createForm()
      Object.assign(staff, { inspectors: [], reviewer: null, approver: null })
      uploads.value = ''
    }
    busy.value = true
    try {
      project.value = (await getProject(id)).data
      form.value.reportNo = project.value.projectNo
      history.value = (await request({ url: '/inspection-reports/' + id })).data
      selectedProjectId = id
      markDraftSaved()
    } catch (error) {
      project.value = null
      history.value = []
      selectedProjectId = null
      projectId.value = null
      savedDraftSnapshot = ''
      proxy.$modal.msgError(error.message || '项目资料加载失败，请重试')
    } finally {
      busy.value = false
    }
  }

  function editProject() {
    router.push('/zhongji-project/' + projectId.value)
  }

  watch(uploads, value => {
    const urls = value.split(',').filter(Boolean)
    form.value.photos = urls.map(url => form.value.photos.find(photo => photo.url === url) || {
      url,
      system: '',
      caption: '',
      date: ''
    })
  })

  function batch() {
    for (const photo of form.value.photos) {
      if (!photo.system) photo.system = batchSystem.value
      if (!photo.date) photo.date = batchDate.value
    }
  }

  async function removeSystem(index) {
    await proxy.$modal.confirm('确认移除此系统？已关联照片需要重新归类。')
    form.value.systems.splice(index, 1)
  }

  function validateSystems() {
    if (!form.value.systems.length) throw Error('请至少保留一个检测系统')
    const systemNames = form.value.systems.map(system => system.name.trim())
    if (systemNames.some(name => !name) || new Set(systemNames).size !== systemNames.length) {
      throw Error('系统名称不能为空或重复，请检查系统列表')
    }
    for (const system of form.value.systems) {
      if (system.name.length > 100 || (system.description || '').length > 10000) {
        throw Error('系统名称最多100字，系统说明最多10000字')
      }
      if (!Object.prototype.hasOwnProperty.call(states, system.status)) {
        throw Error(`请确认“${system.name}”是否存在以及是否已检测`)
      }
      for (const item of system.items) {
        if (!(item.name || '').trim()) throw Error(`请填写“${system.name}”中的设备或检验项目名称`)
        if ((item.name || '').length > 2000 || (item.requirement || '').length > 2000 ||
          (item.location || '').length > 2000 || (item.result || '').length > 2000) {
          throw Error('设备名称、标准要求、检测部位和检验结果均不能超过2000字')
        }
        if (!Object.prototype.hasOwnProperty.call(states, item.status)) {
          throw Error(`请确认“${system.name} / ${item.name}”的检测状态`)
        }
        if (!Object.prototype.hasOwnProperty.call(verdicts, item.verdict)) {
          throw Error(`请确认“${system.name} / ${item.name}”的检测判定`)
        }
        if (item.status !== 'tested' && ['pass', 'fail'].includes(item.verdict)) {
          throw Error('未检测或不存在的设备不能判定为合格或不合格')
        }
      }
    }
  }

  function validatePhotos() {
    const invalid = form.value.photos.find(photo =>
      !tested.value.some(system => system.name === photo.system) || !photo.date
    )
    if (invalid) throw Error('请为每张照片指定已检测的所属系统和实际拍摄日期')
  }

  function validate() {
    if (!project.value || !form.value.reportNo.trim() || !form.value.reportDate) {
      throw Error('请选择项目并填写报告编号和日期')
    }
    validateSystems()
    validatePhotos()
  }

  async function next() {
    try {
      if (step.value === 0 && (!project.value || !form.value.reportNo.trim() || !form.value.reportDate)) {
        throw Error('请选择项目并填写报告编号和日期')
      }
      if (step.value === 2) validateSystems()
      if (step.value === 3) validatePhotos()
      step.value++
    } catch (error) {
      proxy.$modal.msgError(error.message)
    }
  }

  function payload() {
    validate()
    return {
      ...form.value,
      projectId: projectId.value,
      employeeRoleIds: {
        inspectors: staff.inspectors,
        reviewer: staff.reviewer,
        approver: staff.approver
      }
    }
  }

  function show(url) {
    if (blob) {
      URL.revokeObjectURL(blob)
      blob = ''
    }
    previewUrl.value = fileUrl(url)
    previewOpen.value = true
  }

  async function preview() {
    busy.value = true
    try {
      const pdf = await request({
        url: '/inspection-reports/preview',
        method: 'post',
        data: payload(),
        responseType: 'blob',
        timeout: 120000
      })
      if (pdf.type.includes('json')) throw Error(JSON.parse(await pdf.text()).msg || '生成失败')
      if (blob) URL.revokeObjectURL(blob)
      blob = URL.createObjectURL(pdf)
      previewUrl.value = blob
      previewOpen.value = true
    } catch (error) {
      proxy.$modal.msgError(error.message || '预览失败')
    } finally {
      busy.value = false
    }
  }

  async function generate() {
    busy.value = true
    try {
      const result = await request({ url: '/inspection-reports', method: 'post', data: payload(), timeout: 120000 })
      show(result.data.file)
      history.value = (await request({ url: '/inspection-reports/' + projectId.value })).data
      markDraftSaved()
      proxy.$modal.msgSuccess('报告已生成并归档')
    } catch (error) {
      proxy.$modal.msgError(error.message || '生成失败')
    } finally {
      busy.value = false
    }
  }

  onMounted(async () => {
    await find()
    credentials.value = (await request({ url: '/management/report-credentials' })).data
      .filter(credential => credential.valueType !== 'string' && credential.files)
  })

  onBeforeUnmount(() => {
    if (blob) URL.revokeObjectURL(blob)
  })

  onBeforeRouteLeave(async () => {
    if (!hasUnsavedDraft()) return true
    try {
      await proxy.$modal.confirm('当前报告有尚未生成的修改，离开将丢失这些内容。确认离开？')
      return true
    } catch {
      return false
    }
  })

  return {
    staff, employees, step, busy, projects, projectId, project, credentials, history, uploads, batchSystem, batchDate,
    previewOpen, previewUrl, states, verdicts, form, tested, fileUrl, findEmployees, find, choose, editProject,
    batch, removeSystem, next, show, preview, generate
  }
}
