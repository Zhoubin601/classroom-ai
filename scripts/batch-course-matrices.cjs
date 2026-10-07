// Explicitly invoked data operation, never a startup seeder. Credentials stay in process memory.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const { spawnSync } = require('node:child_process');
const BASE = process.env.MATRIX_API_URL || 'http://127.0.0.1:8080';
const VERSION = '示例矩阵-20261006-v1';
const PLAN = 'RECOMMENDED-12';
const PREFIX = '【示例草稿·待确认】';
const evidence = process.env.MATRIX_EVIDENCE_DIR
  ? path.resolve(process.env.MATRIX_EVIDENCE_DIR)
  : path.resolve(__dirname, '../docs/20261006-batch-course-matrix-evidence-v1');
const categories = ['工程知识', '问题分析', '设计/开发解决方案', '研究', '使用现代工具', '工程与社会', '环境和可持续发展', '职业规范', '个人和团队', '沟通', '项目管理', '终身学习'];
const dimensions = {
  theory: [1, 'H', '目标1'], analysis: [2, 'H', '目标2'], design: [3, 'H', '目标2'],
  experiment: [4, 'M', '目标2'], tools: [5, 'H', '目标2'], society: [6, 'M', '目标3'],
  sustainability: [7, 'L', '目标3'], ethics: [8, 'M', '目标3'], team: [9, 'M', '目标3'],
  communication: [10, 'M', '目标3'], management: [11, 'H', '目标3'], learning: [12, 'M', '目标3'],
};
// Proposed outcomes based on course themes; these are not imported official teaching requirements.
const profiles = {
  intro: {
    theory: '解释软件生命周期、需求、设计、测试与维护的基本概念及其联系。',
    analysis: '对小型软件案例识别需求歧义、过程风险和质量问题，给出分析依据。',
    design: '为案例制定需求到测试的基本开发流程，说明方案的约束与取舍。',
    tools: '使用版本管理、需求记录和测试工具完成基础软件工程练习。',
    ethics: '在案例讨论中识别软件知识产权、用户隐私与工程责任。',
    team: '通过分工完成小组案例，记录个人贡献和协作问题。',
    communication: '编写需求及设计说明，并向小组清楚解释方案。',
    learning: '比较传统与敏捷开发方法，自主查阅并总结适用条件。',
  },
  agile: {
    analysis: '识别迭代需求、优先级和交付风险，使用可验证的验收条件描述问题。',
    design: '设计可增量交付的软件功能并通过迭代反馈调整实现方案。',
    tools: '使用Git、任务看板、自动化测试和持续集成工具完成迭代。',
    ethics: '遵守代码来源、开源许可与数据隐私要求，记录工具辅助工作的边界。',
    team: '在需求、开发和测试角色间协作，记录任务贡献与评审结论。',
    communication: '通过迭代演示、评审和回顾清楚表达交付成果与改进建议。',
    management: '制定迭代计划，跟踪工作量、风险及交付进度，比较计划与实际偏差。',
    learning: '根据回顾自主学习新工具，并验证其对开发效率和质量的影响。',
  },
  hardware: {
    theory: '解释指令系统、处理器、存储层次及输入输出组织的工作原理。',
    analysis: '分析程序执行中的数据通路、控制信号及性能瓶颈。',
    design: '为给定指令设计数据通路与控制方案，说明正确性和资源取舍。',
    experiment: '设计处理器或存储访问实验，采集数据并解释性能差异。',
    tools: '使用仿真、硬件描述或性能分析工具验证计算机组成方案。',
    communication: '用结构图、时序图及实验报告解释硬件运行过程。',
    learning: '自主比较新型处理器和存储技术与基础体系结构的联系。',
  },
  logic: {
    theory: '运用逻辑代数、组合逻辑与时序逻辑知识说明数字系统行为。',
    analysis: '根据功能与时序约束分析逻辑电路的状态、竞争和边界条件。',
    design: '设计组合电路和有限状态机，比较器件数量、时序与可维护性。',
    experiment: '制定逻辑验证用例，分析仿真波形与实测结果的差异。',
    tools: '使用电路仿真或硬件描述工具验证数字系统设计。',
    team: '通过分工集成电路模块，完成接口核对与联合验证。',
    communication: '以真值表、状态图和时序图清楚表达电路设计。',
  },
  algorithms: {
    theory: '运用线性表、树、图和算法复杂度知识解释数据组织与算法行为。',
    analysis: '根据输入规模、时间和空间约束分析算法问题与边界情况。',
    design: '选择合适的数据结构并设计求解算法，说明正确性和复杂度。',
    experiment: '构造典型与极端输入，比较算法运行结果和性能。',
    tools: '使用调试、单元测试和性能测量工具验证算法实现。',
    communication: '通过伪代码、图示与实验报告解释算法及其取舍。',
    learning: '自主阅读新的算法方案，并通过实现与测量验证适用条件。',
  },
  cpp: {
    theory: '解释面向对象、模板、STL及资源管理机制，并应用于C++程序。',
    analysis: '识别类型、内存、生命周期和异常处理问题，分析错误产生原因。',
    design: '设计职责清晰的类与接口，使用泛型和标准库实现可维护程序。',
    tools: '使用编译器、调试器和自动化测试验证C++程序的行为与性能。',
    ethics: '遵守代码引用和开源许可要求，避免泄露练习数据或复制未经理解的代码。',
    team: '按模块分工开发小型程序，通过代码评审完成集成。',
    communication: '编写接口说明和测试报告，解释程序设计及缺陷修复。',
    learning: '自主查阅现代C++特性与标准库文档，并用示例验证理解。',
  },
  os: {
    theory: '解释进程线程、同步、虚拟内存、文件系统与资源调度机制。',
    analysis: '分析并发程序中的竞态、死锁、缺页和调度瓶颈。',
    design: '为给定约束设计同步、调度或内存管理方案并说明取舍。',
    experiment: '通过可重复实验观察并发和系统资源行为，分析测量结果。',
    tools: '使用系统调用、调试及跟踪工具定位操作系统相关问题。',
    ethics: '遵守系统访问权限与数据保护规范，说明资源使用和安全责任。',
    learning: '阅读操作系统技术资料，验证新的内核或调度机制。',
  },
  embedded: {
    theory: '解释ARM架构、嵌入式Linux启动、内核配置与设备驱动机制。',
    analysis: '分析目标设备的功能、资源、实时性和接口约束。',
    design: '设计内核裁剪、驱动与应用组合方案，并验证设备功能。',
    experiment: '制定板级验证和驱动测试实验，记录故障现象与定位依据。',
    tools: '使用交叉编译、调试及日志工具完成嵌入式系统构建与诊断。',
    sustainability: '比较不同配置的功耗和资源占用，提出节约资源的实现建议。',
    team: '通过驱动、应用与验证角色分工完成设备集成。',
    communication: '以接口说明、部署步骤和测试报告交付嵌入式系统成果。',
  },
  ai: {
    theory: '解释搜索、知识表示与机器学习基础，并辨别不同方法的适用问题。',
    analysis: '根据任务与数据特点识别人工智能问题的目标、约束和评价指标。',
    design: '选择并实现基础人工智能方法，比较求解质量与计算成本。',
    experiment: '构造可重复实验，比较不同方法并解释误差与局限。',
    tools: '使用人工智能开发工具完成实现、调试和结果展示。',
    society: '分析人工智能应用对用户和社会的影响，识别误用与偏差风险。',
    ethics: '遵守数据来源、隐私及算法结果表达规范，说明实验局限。',
    learning: '自主学习新方法，比较其与基础算法的联系和适用范围。',
  },
  ml: {
    theory: '解释分类、聚类、支持向量机和集成学习的基本原理。',
    analysis: '分析样本、特征、数据偏差与任务指标，识别数据泄漏和过拟合风险。',
    design: '构建特征处理、模型训练和验证流程，比较算法与参数方案。',
    experiment: '开展可重复的对比实验，使用合理评价指标解释结果与不确定性。',
    tools: '使用机器学习库和实验记录工具实现训练、验证与可视化。',
    society: '讨论模型偏差、可解释性与应用后果，提出合理使用边界。',
    ethics: '遵守数据授权与隐私要求，如实记录数据处理和实验结果。',
    communication: '撰写模型比较报告，清楚表达结果、误差与局限。',
  },
  collaboration: {
    analysis: '分析生成式AI辅助开发任务，识别需求歧义、错误输出与验证风险。',
    design: '设计人工决策与AI辅助结合的开发流程，明确验收和复核环节。',
    tools: '运用代码助手、版本管理和自动化测试完成可追溯的开发任务。',
    ethics: '识别AI代码来源、隐私和责任问题，记录人工复核与引用边界。',
    team: '在人机协同团队中承担明确角色，通过代码评审完成交付。',
    communication: '说明提示、复核证据和结果局限，组织迭代评审与回顾。',
    management: '安排迭代任务并跟踪效率、质量和风险，评估工具使用收益。',
    learning: '自主比较新的协同工具和方法，使用实践证据调整工作流程。',
  },
  devops: {
    analysis: '分析软件交付中的构建、环境差异、部署失败与运行故障。',
    design: '设计可重复构建、测试、部署和回滚的持续交付流程。',
    experiment: '通过故障注入或对照部署实验验证流水线与恢复方案。',
    tools: '使用容器、CI/CD和可观测性工具完成交付与问题定位。',
    sustainability: '比较构建及运行资源消耗，提出缓存或资源配置优化方案。',
    ethics: '遵守最小权限、密钥管理和制品来源规范，保护部署数据。',
    team: '协调开发、测试与运维分工，按共享验收条件完成交付。',
    communication: '编写流水线说明、运行手册与故障复盘报告。',
    management: '规划交付步骤和风险应对，比较交付周期与变更质量。',
  },
  network: {
    theory: '解释网络分层、TCP/IP协议及基础密码机制。',
    analysis: '分析连接、路由、传输和网络安全问题，依据报文与日志定位原因。',
    design: '设计满足功能与安全约束的网络配置和防护方案。',
    experiment: '开展受控网络通信与安全实验，记录现象并分析结果。',
    tools: '使用抓包、网络诊断及安全配置工具验证协议行为。',
    society: '分析网络服务中断和数据泄露对使用者的影响。',
    ethics: '在授权范围内开展实验，遵守网络访问和用户隐私规范。',
    communication: '用拓扑图、报文分析与测试报告解释网络方案。',
  },
  security: {
    analysis: '在授权实验环境中识别漏洞与攻击面，分析成因、影响和证据。',
    design: '设计修复、防护与验证方案，比较安全收益和业务约束。',
    experiment: '在隔离靶场复现安全问题，通过对照验证修复效果。',
    tools: '使用扫描、调试和日志分析工具完成受控安全测试。',
    society: '评估安全事件对使用者和服务的影响，提出降低危害的措施。',
    ethics: '遵守明确授权、数据保护和负责任披露要求，记录测试边界。',
    team: '按测试、分析、修复与复核角色协作完成安全实践。',
    communication: '撰写包含复现依据、风险和修复验证的安全报告。',
  },
  database: {
    theory: '解释关系模型、查询处理、事务、索引与存储管理机制。',
    analysis: '分析查询瓶颈、并发冲突和数据一致性问题，给出可验证依据。',
    design: '设计查询、索引或事务实现方案，比较正确性与性能。',
    experiment: '构造查询与并发负载，测量数据库方案并分析结果。',
    tools: '使用数据库、执行计划、调试和性能测量工具完成验证。',
    ethics: '遵守数据库访问权限、数据隐私和实验数据使用要求。',
    team: '分工完成存储、查询或事务模块，核对接口并集成测试。',
    communication: '编写模式、接口及性能实验报告，解释实现取舍。',
  },
  bigdata: {
    theory: '解释分布式数据处理、批流计算和容错机制的基本原理。',
    analysis: '分析大规模数据任务的分区、通信、数据倾斜和容错问题。',
    design: '设计分布式数据处理流程，比较效率、资源和可靠性。',
    experiment: '通过不同规模或配置的对比实验分析吞吐与资源占用。',
    tools: '使用Hadoop或Spark及监测工具实现和诊断计算任务。',
    sustainability: '评估计算与存储资源消耗，提出分区或配置优化措施。',
    ethics: '遵守数据访问、隐私和来源要求，如实记录处理与实验结果。',
    communication: '通过流程图、测量结果与技术报告解释计算方案。',
  },
  testing: {
    analysis: '根据功能、交互与风险设计测试场景，识别边界和易失败路径。',
    design: '设计稳定、可维护的自动化测试结构，明确断言与数据准备。',
    experiment: '复现失败并对比修复结果，分析测试覆盖与不稳定原因。',
    tools: '使用Playwright、浏览器调试及测试报告工具执行自动化验证。',
    ethics: '隔离测试数据和凭据，避免测试操作损坏用户业务内容。',
    team: '通过开发与测试分工复核缺陷，协作维护测试用例。',
    communication: '提供包含复现步骤、断言和证据的缺陷或测试报告。',
  },
};
const profileByCode = { CS1001: 'intro', CS4002: 'agile', CS2002: 'hardware', CS2003: 'logic', CS2001: 'algorithms', CS1002: 'cpp', CS3002: 'os', CS3008: 'embedded', AI3001: 'ai', AI3002: 'ml', SE3002: 'collaboration', SE3003: 'devops', CS3003: 'network', SEC3001: 'security', DS2001: 'database', DS3001: 'bigdata', 'DEMO-SE88': 'agile' };
function sql(query) {
  const result = spawnSync('docker', ['exec', '-i', 'classroom-mysql', 'sh', '-c', 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql --default-character-set=utf8mb4 -uroot classroom_ai --batch --raw --skip-column-names'], { input: query, encoding: 'utf8', maxBuffer: 20 * 1024 * 1024 });
  if (result.status !== 0) throw new Error(`读取数据库失败：${result.stderr}`);
  return result.stdout.trim() ? result.stdout.trim().split(/\r?\n/).map(line => JSON.parse(line)) : [];
}
function snapshot() {
  return {
    courses: sql("SELECT JSON_OBJECT('id',id,'courseCode',course_code,'courseName',course_name,'majorCode',major_code,'department',department,'description',description,'objectives',objectives,'assessmentMethod',assessment_method,'updatedAt',updated_at) FROM t_course ORDER BY id;"),
    syllabi: sql("SELECT JSON_OBJECT('id',id,'courseId',course_id,'version',version,'planVersion',plan_version,'status',status,'authorTeacher',author_teacher,'lockedBy',locked_by,'courseGoals',course_goals,'createdAt',created_at,'updatedAt',updated_at) FROM t_course_syllabus ORDER BY id;"),
    indicators: sql("SELECT JSON_OBJECT('id',id,'courseId',course_id,'syllabusId',syllabus_id,'indicatorCode',indicator_code,'requirementCategory',requirement_category,'indicatorDescription',indicator_description,'supportWeight',support_weight,'targetGoal',target_goal,'createdAt',created_at,'updatedAt',updated_at) FROM t_graduation_indicator ORDER BY id;"),
    directors: sql("SELECT JSON_OBJECT('username',username,'department',department) FROM t_user_account WHERE role='DIRECTOR' ORDER BY username;"),
  };
}
const hash = item => crypto.createHash('sha256').update(JSON.stringify(item)).digest('hex');
function latest(state, id) {
  // MySQL DESC places legacy NULL timestamps after actual timestamps.
  return state.syllabi.filter(s => s.courseId === id).sort((a, b) => String(b.createdAt || '').localeCompare(String(a.createdAt || '')) || b.id - a.id)[0];
}
function rowsFor(state, course, syllabus) {
  return state.indicators.filter(i => syllabus ? i.syllabusId === syllabus.id : i.courseId === course.id && !i.syllabusId);
}
function createPlan(state) {
  return state.courses.map(course => {
    const current = latest(state, course.id);
    const existing = rowsFor(state, course, current);
    if (current?.version === VERSION || existing.length >= 3) return { courseId: course.id, courseCode: course.courseCode, courseName: course.courseName, action: 'preserve', reason: current?.version === VERSION ? '本批次已存在' : '已有矩阵保留', existingRows: existing.length };
    const profileKey = course.courseCode.startsWith('PW-TEST-') ? 'testing' : profileByCode[course.courseCode];
    assert.ok(profileKey && profiles[profileKey], `未配置课程建议：${course.courseCode}`);
    assert.ok(course.majorCode && course.department, `课程专业或教研室缺失：${course.courseCode}`);
    const director = state.directors.find(d => d.department === course.department);
    assert.ok(director, `未找到对应主任：${course.department}`);
    const profile = profiles[profileKey];
    const proposed = Object.entries(profile).map(([dimension, text]) => {
      const [number, supportWeight, targetGoal] = dimensions[dimension];
      return { indicatorCode: `${number}-1`, requirementCategory: categories[number - 1], indicatorDescription: `${PREFIX}《${course.courseName}》建议：${text}`, supportWeight, targetGoal };
    });
    for (const original of existing) {
      const catalogIndex = categories.indexOf(original.requirementCategory.replace(/^\d+[.、\s]+/, ''));
      assert.ok(catalogIndex >= 0 && /^\d+-\d+$/.test(original.indicatorCode), '已有指标无法绑定示例目录，停止预检');
      const index = proposed.findIndex(i => i.indicatorCode === original.indicatorCode);
      const copy = Object.fromEntries(['indicatorCode', 'requirementCategory', 'indicatorDescription', 'supportWeight', 'targetGoal'].map(k => [k, original[k]]));
      assert.equal(copy.requirementCategory, categories[Number(copy.indicatorCode.split('-')[0]) - 1], '原指标类别与示例目录不同，停止预检');
      if (index >= 0) proposed[index] = copy; else proposed.push(copy);
    }
    proposed.sort((a, b) => Number(a.indicatorCode.split('-')[0]) - Number(b.indicatorCode.split('-')[0]));
    const goal1 = profile.theory || '解释本课程实践所使用的方法、概念与适用条件。';
    const goal2 = [profile.analysis, profile.design, profile.experiment, profile.tools].filter(Boolean).join(' ');
    const goal3 = [profile.ethics, profile.team, profile.communication, profile.management, profile.society, profile.sustainability, profile.learning].filter(Boolean).join(' ');
    const goals = `${PREFIX}\n目标1：${goal1}\n目标2：${goal2}\n目标3：${goal3}`;
    return { courseId: course.id, courseCode: course.courseCode, courseName: course.courseName, department: course.department, director: director.username, action: 'create-draft', existingSyllabusId: current?.id || null, copiedRows: existing.length,
      dto: { courseId: course.id, version: VERSION, planVersion: PLAN, status: 'DRAFT', courseGoals: goals, indicators: proposed } };
  });
}
async function request(endpoint, token, method = 'GET', data) {
  const response = await fetch(`${BASE}${endpoint}`, { method, headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) }, ...(data ? { body: JSON.stringify(data) } : {}), signal: AbortSignal.timeout(20000) });
  const body = await response.json();
  if (!response.ok || body.code !== 200) throw new Error(`${method} ${endpoint} 失败：${body.message || body.msg || response.status}`);
  return body.data;
}
function verifyHistory(before, after) {
  assert.equal(hash(before.courses), hash(after.courses), '课程档案被修改');
  for (const key of ['syllabi', 'indicators']) {
    const byId = new Map(after[key].map(row => [row.id, row]));
    for (const old of before[key]) assert.equal(hash(old), hash(byId.get(old.id)), `历史${key}记录${old.id}被修改`);
  }
}
async function main() {
  fs.mkdirSync(evidence, { recursive: true });
  if (process.argv.includes('--preview')) {
    const state = snapshot();
    const plan = createPlan(state);
    for (const file of ['before.json', 'plan.json']) assert.ok(!fs.existsSync(path.join(evidence, file)), '预检文件已存在，请使用新批次目录，不覆盖');
    fs.writeFileSync(path.join(evidence, 'before.json'), JSON.stringify(state, null, 2));
    fs.writeFileSync(path.join(evidence, 'plan.json'), JSON.stringify(plan, null, 2));
    console.log(JSON.stringify({ preview: true, courses: plan.length, createDrafts: plan.filter(p => p.action === 'create-draft').length, newRows: plan.filter(p => p.dto).reduce((sum, p) => sum + p.dto.indicators.length - p.copiedRows, 0), preserveCourses: plan.filter(p => p.action === 'preserve').map(p => p.courseCode) }));
    return;
  }
  assert.ok(process.argv.includes('--apply') || process.argv.includes('--verify'), '使用--preview、--apply或--verify');
  const before = JSON.parse(fs.readFileSync(path.join(evidence, 'before.json')));
  const plan = JSON.parse(fs.readFileSync(path.join(evidence, 'plan.json')));
  let state = snapshot();
  verifyHistory(before, state);
  const planned = plan.filter(p => p.action === 'create-draft');
  if (process.argv.includes('--apply')) {
    assert.ok(process.env.MATRIX_LOGIN_PASSWORD, '请通过进程环境提供演示账号密码');
    const tokens = new Map();
    // Preflight every affected account, scope and catalog before any syllabus write.
    for (const username of new Set(planned.map(p => p.director))) {
      const user = await request('/api/v1/auth/login', null, 'POST', { username, password: process.env.MATRIX_LOGIN_PASSWORD });
      assert.equal(user.role, 'DIRECTOR');
      tokens.set(username, user.token);
    }
    for (const item of planned) {
      const token = tokens.get(item.director);
      const remote = await request(`/api/v1/syllabus/course/${item.courseId}/latest`, token);
      const current = latest(state, item.courseId);
      assert.equal(remote?.id || null, current?.id || null, '界面最新大纲与预检不同');
      assert.ok(current?.version === VERSION || (current?.id || null) === item.existingSyllabusId, '预检后用户已创建大纲，停止');
      const catalog = await request(`/api/v1/syllabus/plans/${state.courses.find(c => c.id === item.courseId).majorCode}/${PLAN}/indicators`, token);
      for (const row of item.dto.indicators) {
        assert.ok(catalog.some(c => c.indicatorCode === row.indicatorCode && c.requirementCategory === row.requirementCategory));
        assert.ok(['H', 'M', 'L'].includes(row.supportWeight));
        assert.ok(row.targetGoal && row.targetGoal.split(/[、,，]/).every(g => item.dto.courseGoals.includes(g.trim() + '：')));
      }
    }
    const applied = [];
    for (const item of planned) {
      const token = tokens.get(item.director);
      const fresh = snapshot();
      verifyHistory(before, fresh);
      const current = latest(fresh, item.courseId);
      if (current?.version === VERSION) { applied.push({ courseId: item.courseId, action: 'already-applied', syllabusId: current.id }); continue; }
      assert.equal(hash(current || null), hash(latest(before, item.courseId) || null), '大纲已发生变化，停止');
      assert.equal(hash(rowsFor(fresh, fresh.courses.find(c => c.id === item.courseId), current)), hash(rowsFor(before, before.courses.find(c => c.id === item.courseId), latest(before, item.courseId))), '指标已发生变化，停止');
      const saved = await request('/api/v1/syllabus', token, 'POST', item.dto);
      assert.equal(saved.version, VERSION);
      assert.equal(saved.status, 'DRAFT');
      applied.push({ courseId: item.courseId, courseCode: item.courseCode, syllabusId: saved.id, indicators: item.dto.indicators.length });
      fs.writeFileSync(path.join(evidence, 'applied.json'), JSON.stringify(applied, null, 2));
      console.log(`已保存 ${item.courseCode}：${item.dto.indicators.length}条草稿指标`);
    }
  }
  state = snapshot();
  verifyHistory(before, state);
  const summary = [];
  for (const item of plan) {
    const current = latest(state, item.courseId);
    const rows = rowsFor(state, state.courses.find(c => c.id === item.courseId), current);
    assert.ok(rows.length > 0, `课程${item.courseCode}仍无指标矩阵`);
    if (item.dto) {
      assert.equal(current.version, VERSION);
      assert.equal(current.planVersion, PLAN);
      assert.equal(current.status, 'DRAFT');
      assert.equal(current.courseGoals, item.dto.courseGoals);
      const fields = ['indicatorCode', 'requirementCategory', 'indicatorDescription', 'supportWeight', 'targetGoal'];
      const expected = item.dto.indicators.map(r => fields.map(k => r[k])).sort();
      const actual = rows.map(r => fields.map(k => r[k])).sort();
      assert.deepEqual(actual, expected);
    }
    summary.push({ courseCode: item.courseCode, courseName: item.courseName, syllabusId: current.id, version: current.version, status: current.status, rows: rows.length });
  }
  const rerunCreates = createPlan(state).filter(p => p.action === 'create-draft').length;
  assert.equal(rerunCreates, 0, '重复预检仍拟新增，停止核验');
  const result = { status: 'passed', totalCourses: summary.length, createdDrafts: planned.length, addedRows: state.indicators.length - before.indicators.length, historicalSyllabiUnchanged: before.syllabi.length, historicalIndicatorsUnchanged: before.indicators.length, allCoursesHaveMatrix: true, rerunCreates, courses: summary };
  fs.writeFileSync(path.join(evidence, 'after.json'), JSON.stringify(state, null, 2));
  fs.writeFileSync(path.join(evidence, 'verification.json'), JSON.stringify(result, null, 2));
  console.log(JSON.stringify(result));
}
main().catch(error => { console.error(error.message); process.exitCode = 1; });
