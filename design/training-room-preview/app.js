const products = {
  h5: {
    title: '正式队员的日常训练入口',
    description: '验证导航分流、单人/多人房间、实时看板和完整成绩层级。',
    facts: [['入口', '正式队员显示训练屋'], ['房间', '最多 10 人 / 3 靶机'], ['互斥', '同房间一次一人训练']],
    screens: [['history', '训练屋首页'], ['mode', '开始训练'], ['rooms', '加入房间'], ['dashboard', '数据看板'], ['settings', '房间设置'], ['result', '训练成绩'], ['guest', '非正式队员通知']]
  },
  android: {
    title: '手机即智能靶机节点',
    description: '验证登录配房、摄像头校准、激光识别、连接恢复和训练反馈。',
    facts: [['采集', 'Camera2 / 目标 120 FPS'], ['识别', '设备本地完成'], ['可靠性', '断线缓存与补传']],
    screens: [['login', '会员登录'], ['join', '选择房间'], ['camera', '靶机主界面'], ['manual', '手动四点'], ['settings', '设备设置'], ['offline', '断线补传']]
  },
  admin: {
    title: '训练运营与异常处置工作台',
    description: '验证房间、设备、模式、成绩和不可删除审计的管理闭环。',
    facts: [['管理', '完整房间与设备控制'], ['成绩', '修改 / 业务删除'], ['审计', '保留前后快照与原因']],
    screens: [['rooms', '房间管理'], ['roomDetail', '房间详情'], ['nodes', '设备管理'], ['modes', '模式配置'], ['records', '成绩管理'], ['recordDetail', '成绩详情'], ['audits', '审计记录']]
  }
}

const states = {
  h5: [['ready', '正常'], ['loading', '加载中'], ['empty', '空数据'], ['error', '加载失败'], ['busy', '训练占用'], ['nodeOffline', '靶机离线']],
  android: [['ready', 'READY'], ['calibrating', '校准中'], ['training', '训练中'], ['calibrationError', '校准失败'], ['lowFps', '设备降级']],
  admin: [['ready', '正常'], ['loading', '加载中'], ['empty', '空数据'], ['error', '请求失败']]
}

const model = { product: 'h5', screen: 'history', demoState: 'ready', viewAll: false, mode: 'precision', activeTarget: 1, targetCount: 1, resultTargetCount: 3, roomClosed: false }
const app = document.querySelector('#app')
const device = document.querySelector('#device')
const productPicker = document.querySelector('#productPicker')
const screenPicker = document.querySelector('#screenPicker')
const statePicker = document.querySelector('#statePicker')
const dialog = document.querySelector('#dialog')
const dialogBody = document.querySelector('#dialogBody')
const toast = document.querySelector('#toast')

const hits = [
  { target: 1, shot: 1, time: '0.82', split: '0.82', ring: 10, accuracy: 98 },
  { target: 2, shot: 1, time: '0.96', split: '0.96', ring: 9, accuracy: 91 },
  { target: 3, shot: 1, time: '1.08', split: '1.08', ring: 10, accuracy: 96 },
  { target: 1, shot: 2, time: '1.47', split: '0.65', ring: 9, accuracy: 89 },
  { target: 2, shot: 2, time: '1.72', split: '0.76', ring: 8, accuracy: 83 },
  { target: 3, shot: 2, time: '1.91', split: '0.83', ring: 10, accuracy: 97 }
]

function notify(text) { toast.textContent = text; toast.classList.add('show'); clearTimeout(notify.timer); notify.timer = setTimeout(() => toast.classList.remove('show'), 1800) }
function openDialog(title, content, action = '确认') {
  dialogBody.innerHTML = `<div class="dialog-head"><div><span>PROTOTYPE ACTION</span><h2>${title}</h2></div><button data-close aria-label="关闭">×</button></div>${content}<div class="dialog-actions"><button class="secondary" data-close>取消</button><button class="primary" data-confirm>${action}</button></div>`
  dialog.showModal(); dialogBody.querySelectorAll('[data-close]').forEach(x => x.onclick = () => dialog.close())
  dialogBody.querySelector('[data-confirm]').onclick = () => { dialog.close(); notify(`${action}已模拟，未写入数据库`) }
}
function updatePickers() {
  const product = products[model.product]
  screenPicker.innerHTML = product.screens.map(([v, l]) => `<option value="${v}">${l}</option>`).join('')
  if (!product.screens.some(([v]) => v === model.screen)) model.screen = product.screens[0][0]
  screenPicker.value = model.screen
  statePicker.innerHTML = states[model.product].map(([v, l]) => `<option value="${v}">${l}</option>`).join('')
  if (!states[model.product].some(([v]) => v === model.demoState)) model.demoState = 'ready'
  statePicker.value = model.demoState
  document.querySelector('#reviewTitle').textContent = product.title
  document.querySelector('#reviewDescription').textContent = product.description
  document.querySelector('#reviewFacts').innerHTML = product.facts.map(([a, b]) => `<div><b>${a}</b><span>${b}</span></div>`).join('')
}

function logoHeader(title = '训练屋', subtitle = 'TRAINING ROOM') { return `<header class="mobile-header"><img src="/h5-web/src/assets/nbys-logo.png"><div><span>${subtitle}</span><strong>${title}</strong></div><button data-action="home">⌂</button></header>` }
function h5Nav(active = 'training') { return `<nav class="h5-nav">${[['activity', '⌂', '活动'], ['rental', '▣', '发射器租赁'], ['training', '◎', '训练屋'], ['mine', '◆', '我的']].map(([v, i, l]) => `<button class="${v === active ? 'active' : ''}"><b>${i}</b><span>${l}</span></button>`).join('')}</nav>` }
function hero() { return `<section class="training-hero"><div><span>LIVE FIRE / DRY TRAINING</span><h1>训练屋</h1><p>今日状态 · 3 台靶机在线</p></div><div class="hero-score"><b>94.3</b><small>平均精准度</small></div></section>` }
function statusView() {
  const copy = { loading: ['正在同步训练数据', '读取房间与历史成绩'], empty: ['暂无训练记录', '完成第一轮训练后，成绩会出现在这里'], error: ['训练屋加载失败', '请检查网络后重试'] }[model.demoState]
  if (!copy) return ''
  return `<div class="center-state"><i>${model.demoState === 'loading' ? '↻' : model.demoState === 'empty' ? '—' : '!'}</i><h2>${copy[0]}</h2><p>${copy[1]}</p><button data-state-ready>返回正常状态</button></div>`
}
function records() { return [
  ['08/12 20:46', '精准训练', '3 靶 · 15 发', '94.3', '7.84s', 3],
  ['08/10 19:31', '反应计时', '2 靶 · 第 3 次', '—', '2.62s', 2],
  ['08/08 21:02', '精准训练', '1 靶 · 10 发', '91.8', '9.13s', 1]
].map((r, i) => `<article class="record-card" data-screen="result" data-result-targets="${r[5]}"><div class="record-index">0${i + 1}</div><div><span>${r[0]}</span><h3>${r[1]}</h3><p>${r[2]}</p></div><div class="record-result"><b>${r[3]}</b><small>${r[4]}</small></div></article>`).join('') }
function h5History() { return `${logoHeader()}<main class="mobile-scroll">${hero()}<section class="quick-stats"><div><b>18</b><span>完成训练</span></div><div><b>286</b><span>有效命中</span></div><div><b>0.71s</b><span>最佳反应</span></div></section><div class="section-head"><div><span>TRAINING LOG</span><h2>历史成绩</h2></div><button data-screen="mode">开始训练</button></div><div class="filters"><button class="active">全部</button><button>精准训练</button><button>反应计时</button><button>本月</button></div><div class="record-list">${records()}</div></main>${h5Nav()}` }
function h5Mode() { return `${logoHeader('开始训练', 'SELECT TRAINING')}<main class="mobile-scroll"><section class="mode-intro"><span>CHOOSE YOUR MODE</span><h1>今天练什么？</h1><p>选择单人快速开始，或和队员共享靶机与数据看板。</p></section><div class="mode-grid"><article data-screen="dashboard"><b>01</b><div><span>SOLO SESSION</span><h2>单人练习</h2><p>创建私人房间，设置靶机后直接开始。</p></div><i>→</i></article><article data-dialog="multiplayer"><b>02</b><div><span>MULTIPLAYER</span><h2>多人练习</h2><p>创建最多 10 人的训练房间，或加入现有房间。</p></div><i>→</i></article></div><section class="rule-note"><b>ROOM RULE</b><p>同一房间一次只允许一人训练，谁点击开始，本轮成绩就归谁。</p></section></main>${h5Nav()}` }
function h5Rooms() { return `${logoHeader('加入房间', 'OPEN ROOMS')}<main class="mobile-scroll"><div class="section-head room-title"><div><span>MULTIPLAYER</span><h2>可加入房间</h2></div><button data-dialog="createRoom">＋ 创建</button></div><div class="room-list">${[['夜训反应组', '夜枭', '6/10', '3/3 READY', '空闲'], ['周三精准组', '白泽', '4/10', '2/3 READY', '等待设备'], ['新手基础组', '教官 K', '8/10', '3/3 READY', '训练中']].map((r, i) => `<article><div class="room-code">R-${i + 17}</div><div><h3>${r[0]}</h3><p>房主 ${r[1]} · ${r[2]} 人</p><span>${r[3]}</span></div><div><em class="s${i}">${r[4]}</em><button data-screen="dashboard">加入</button></div></article>`).join('')}</div></main>${h5Nav()}` }
function nodeCards() { return [1, 2, 3].slice(0, model.targetCount).map((n, i) => `<article class="node-card ${model.demoState === 'nodeOffline' && n === 2 ? 'offline' : ''}"><header><b>靶机 0${n}</b><span>${model.demoState === 'nodeOffline' && n === 2 ? 'OFFLINE' : 'READY'}</span></header><div class="target-mini"><i style="left:${42 + i * 6}%;top:${38 + i * 7}%"></i><i style="left:${55 - i * 3}%;top:${54}%"></i></div><footer><strong>5/5</strong><small>平均 ${[9.4, 8.8, 10][i]} 环</small></footer></article>`).join('') }
function h5Dashboard() { const busy = model.demoState === 'busy'; return `${logoHeader('夜训反应组', 'ROOM R-17')}<main class="mobile-scroll"><div class="room-tabs"><button class="active">数据看板</button><button data-screen="settings">设置</button></div><section class="live-status ${busy ? 'busy' : ''}"><div><span>${busy ? 'LIVE SESSION' : 'ROOM READY'}</span><h2>${busy ? '夜枭正在训练' : `${model.targetCount} 台靶机已就绪`}</h2><p>${busy ? '精准训练 · 每靶 5 次命中' : '房间空闲，任意成员可开始本人的训练'}</p></div><strong>${busy ? '03.46' : 'READY'}</strong></section><div class="scope-switch"><button class="${!model.viewAll ? 'active' : ''}" data-scope="mine">只看自己</button><button class="${model.viewAll ? 'active' : ''}" data-scope="all">查看全部</button></div><div class="node-grid">${nodeCards()}</div><section class="hit-feed"><header><div><span>HIT STREAM</span><h3>实时命中</h3></div><em>${hits.length} EVENTS</em></header>${hits.slice(0, 5).map(h => `<div><b>T${h.target}</b><span>#${h.shot}</span><strong>${h.time}s</strong><span>Split ${h.split}</span><em>${h.ring} 环</em></div>`).join('')}</section></main><div class="start-dock"><button data-dialog="leave">退出房间</button><button class="primary" data-dialog="start">${busy ? '训练进行中' : '开始训练'}</button></div>` }
function h5Settings() { return `${logoHeader('房间设置', 'ROOM CONFIG')}<main class="mobile-scroll"><div class="room-tabs"><button data-screen="dashboard">数据看板</button><button class="active">设置</button></div><section class="form-panel"><label><span>训练模式</span><div class="segmented"><button class="${model.mode === 'reaction' ? 'active' : ''}" data-mode="reaction">反应计时</button><button class="${model.mode === 'precision' ? 'active' : ''}" data-mode="precision">精准训练</button></div></label><label><span>启用靶机数量</span><div class="stepper"><button data-target-count="minus">−</button><b>${model.targetCount}</b><button data-target-count="plus">＋</button></div></label><p class="setting-help">进入房间默认启用 1 个靶机，可增加到最多 3 个；减少靶机不会删除历史数据。</p>${[1, 2, 3].slice(0, model.targetCount).map(n => `<div class="target-config"><div><strong>靶机 0${n} 命中次数</strong><small>命中 5 次后完成本靶训练</small></div><div class="quantity-stepper"><button>−</button><b>5</b><button>＋</button></div></div>`).join('')}<label><span>精准靶纸</span><select><option>Precision A4</option><option>Precision A3</option></select></label><label><span>蜂鸣前等待</span><div class="range-row"><input value="2.0"><i>至</i><input value="4.0"><i>秒</i></div><small class="setting-help">点击开始后先播放 “Are you ready?”，在这个范围内随机等待，再播放蜂鸣并开始计时，避免提前预判。</small></label><label><span>蜂鸣音量</span><input type="range" value="80"></label><button class="save primary" data-simulate="房间设置">保存设置</button></section><section class="danger-zone"><div><b>关闭房间</b><p>关闭后成员和靶机将退出，历史成绩保留。</p></div><button data-dialog="closeRoom">关闭</button></section></main>` }
function h5Result() { return `${logoHeader('训练成绩', 'SESSION REPORT')}<main class="mobile-scroll"><section class="result-hero"><span>PRECISION COMPLETE</span><h1>94.3</h1><p>平均精准度</p><div><b>7.84s</b><span>总用时</span><b>141</b><span>总环数</span><b>15</b><span>有效命中</span></div></section><div class="target-result-tabs">${[1, 2, 3].slice(0, model.resultTargetCount).map(n => `<button class="${model.activeTarget === n ? 'active' : ''}" data-target="${n}">靶机 0${n}</button>`).join('')}</div><section class="shot-map"><div class="rings">${[1,2,3,4,5].map(n=>`<i class="r${n}"></i>`).join('')}<b class="hit h1"></b><b class="hit h2"></b><b class="hit h3"></b><b class="hit h4"></b><b class="hit h5"></b></div><div><h3>命中分布</h3><p>目标 5 次 · 已完成</p><strong>平均 9.4 环</strong></div></section><section class="result-table"><header><b>#</b><b>SHOT</b><b>SPLIT</b><b>环数</b><b>精准度</b></header>${hits.filter(h => h.target === model.activeTarget).concat(hits.filter(h => h.target === 1).slice(0,3)).slice(0,5).map((h,i)=>`<div><span>${i+1}</span><strong>${(+h.time+i*.43).toFixed(2)}s</strong><span>${h.split}s</span><em>${h.ring}</em><b>${h.accuracy}%</b></div>`).join('')}</section></main><div class="start-dock"><button data-screen="history">返回历史</button><button class="primary" data-screen="dashboard">再练一次</button></div>` }
function guestNotice() { return `${logoHeader('通知', 'MESSAGE CENTER')}<main class="mobile-scroll"><section class="guest-banner"><span>GUEST MEMBER</span><h1>通知中心</h1><p>通过正式队员审核后，底部“通知”将替换为“训练屋”。</p></section><div class="notice-list"><article><i></i><div><h3>资料审核中</h3><p>你的正式队员申请已提交，请等待管理员审核。</p><small>今天 14:22</small></div></article><article><i></i><div><h3>活动报名成功</h3><p>你已报名“甬士周常 · 城市攻防”。</p><small>昨天 20:10</small></div></article></div></main>${h5Nav('notice').replace('训练屋', '通知')}` }

function androidHeader(title) { return `<header class="android-header"><div><span>NBYS TARGET NODE</span><strong>${title}</strong></div><em>V1.0 PROTO</em></header>` }
function androidLogin() { return `${androidHeader('靶机登录')}<main class="android-body login-body"><div class="android-mark">◎</div><span>SMART TARGET ACCESS</span><h1>连接训练屋</h1><p>使用正式队员账号登录，将这台 Android 手机变成智能靶机。</p><label><span>名字 / 呼号</span><input value="夜枭"></label><label><span>密码</span><input type="password" value="password"></label><button class="primary" data-screen="join">登录</button><small>非正式队员账号无法进入靶机模式</small></main>` }
function androidJoin() { return `${androidHeader('选择训练房间')}<main class="android-body"><section class="node-identity"><div>NODE</div><span><b>node-a1b2c3</b><small>账号：YS-008 夜枭</small></span><em>ONLINE</em></section><h2 class="android-title">开放房间</h2><div class="android-rooms"><article class="selected"><div><b>R-17</b><h3>夜训反应组</h3><p>6/10 人 · 3 个靶机位</p></div><i>✓</i></article><article><div><b>R-21</b><h3>周三精准组</h3><p>4/10 人 · 1 个空闲位</p></div></article></div><h2 class="android-title">选择靶机号</h2><div class="target-picker"><button class="active">01<small>空闲</small></button><button disabled>02<small>已占用</small></button><button>03<small>空闲</small></button></div><button class="primary wide" data-screen="camera">加入房间并打开相机</button></main>` }
function targetOverlay() { return `<div class="camera-feed"><div class="paper"><i class="marker tl"></i><i class="marker tr"></i><i class="marker br"></i><i class="marker bl"></i>${[1,2,3,4,5].map(n=>`<span class="ring r${n}"></span>`).join('')}<b class="laser"></b></div><div class="camera-data"><span>120 FPS</span><span>EV +0.3</span><span>CONF 0.97</span></div><div class="scan-line"></div></div>` }
function androidCamera() { const training = model.demoState === 'training'; const calibrating = model.demoState === 'calibrating'; return `${androidHeader('靶机 01 · R-17')}<main class="android-camera">${targetOverlay()}<section class="node-status ${training ? 'live' : ''}"><div><span>${calibrating ? 'CALIBRATING' : training ? 'LIVE SESSION' : 'TARGET READY'}</span><h2>${calibrating ? '正在锁定四个定位点' : training ? '精准训练 · 夜枭' : '校准完成，等待训练'}</h2></div><strong>${training ? '03 / 05' : calibrating ? '08 / 12' : 'READY'}</strong></section><div class="tool-grid"><button data-simulate="自动校准"><b>⌖</b><span>自动校准</span></button><button data-screen="manual"><b>⌗</b><span>手动4点</span></button><button data-simulate="自动对焦"><b>AF</b><span>自动对焦</span></button><button data-simulate="激光校准"><b>●</b><span>激光校准</span></button><button data-simulate="清空命中"><b>×</b><span>清空命中</span></button><button data-screen="settings"><b>⚙</b><span>设置</span></button></div><footer class="android-connection"><i></i><span>WebSocket 已连接 · 队列 0</span><button data-screen="join">退出房间</button></footer></main>` }
function androidManual() { return `${androidHeader('手动四点校准')}<main class="android-camera">${targetOverlay()}<section class="manual-guide"><span>STEP 2 / 4</span><h2>点击右上定位点中心</h2><p>顺序固定为：左上 → 右上 → 右下 → 左下</p><div><button data-screen="camera">取消</button><button class="primary" data-simulate="四点校准">确认四点</button></div></section></main>` }
function androidSettings() { return `${androidHeader('设备设置')}<main class="android-body settings-body"><section><h2>网络</h2><label><span>Server URL</span><input value="wss://server/ws/training"></label><label><span>Node ID</span><input value="node-a1b2c3" disabled></label></section><section><h2>靶面</h2><label><span>模式</span><select><option>Precision</option></select></label><label><span>纸张</span><select><option>A4</option><option>A3</option></select></label></section><section><h2>相机</h2><label><span>曝光补偿</span><input type="range"></label><label><span>手动焦距</span><input type="range"></label><label class="toggle"><span>优先 240 FPS</span><input type="checkbox"></label></section><section><h2>诊断</h2><button data-simulate="日志复制">复制运行日志</button><button data-simulate="连接测试">测试 WebSocket</button></section></main>` }
function androidOffline() { return `${androidHeader('网络恢复')}<main class="android-body offline-body"><div class="offline-icon">!</div><span>RECONNECTING</span><h1>训练检测仍在继续</h1><p>WebSocket 已断开，本地摄像头和激光识别不会停止。命中事件将在网络恢复后按序补传。</p><div class="queue-meter"><div><span>待上传事件</span><b>12 / 500</b></div><i><em></em></i></div><div class="offline-facts"><span><b>00:18</b>离线时长</span><span><b>#128</b>最后 ACK</span><span><b>2.4s</b>下次重试</span></div><button class="primary" data-screen="camera">模拟恢复连接</button></main>` }

function adminShell(content, active) { return `<aside class="admin-side"><div class="admin-logo"><img src="/h5-web/src/assets/nbys-logo.png"><span><b>NBYS OPS</b><small>TRAINING CENTER</small></span></div>${[['rooms','房间管理'],['nodes','设备管理'],['modes','模式配置'],['records','成绩管理'],['audits','审计记录']].map(([v,l])=>`<button data-screen="${v}" class="${active===v?'active':''}"><i>${{rooms:'⌂',nodes:'◎',modes:'⚙',records:'▤',audits:'◫'}[v]}</i>${l}</button>`).join('')}<footer>管理员 · Kevin</footer></aside><section class="admin-main">${content}</section>` }
function adminTop(eyebrow,title,actions='') { return `<header class="admin-top"><div><span>${eyebrow}</span><h1>${title}</h1></div><div>${actions}<button class="icon-only">↻</button><button class="avatar">K</button></div></header>` }
function adminStats(items) { return `<div class="admin-stats">${items.map(([n,l,s])=>`<article><span>${l}</span><b>${n}</b><small>${s}</small></article>`).join('')}</div>` }
function adminTable(headers, rows) { return `<div class="admin-table"><header>${headers.map(h=>`<b>${h}</b>`).join('')}</header>${rows.map(r=>`<div>${r.map(c=>`<span>${c}</span>`).join('')}</div>`).join('')}</div>` }
function adminRooms() { return adminShell(`${adminTop('TRAINING OPERATIONS','房间管理','<button class="primary" data-simulate="创建测试房间">＋ 创建房间</button>')}${adminStats([['12','开放房间','3 个训练中'],['28','在线队员','容量使用 42%'],['23','今日训练','完成率 91%'],['2','异常房间','需要处理']])}<div class="admin-filter"><input placeholder="房间号 / 名称 / 房主"><select><option>全部状态</option></select><select><option>全部类型</option></select><button>查询</button></div>${adminTable(['房间','房主 / 成员','靶机','状态','当前训练者','创建时间','操作'],[['R-17 夜训反应组','夜枭 · 6/10','3/3 READY','<em class="ok">空闲</em>','—','08-12 19:20','<button data-screen="roomDetail">详情</button>'],['R-21 周三精准组','白泽 · 4/10','2/3 READY','<em class="warn">等待设备</em>','—','08-12 18:42','<button data-screen="roomDetail">详情</button>'],['R-09 新手基础组','教官 K · 8/10','3/3 LIVE','<em class="live">训练中</em>','菜鸟','08-12 17:08','<button data-screen="roomDetail">详情</button>'],['R-03 单人训练','山猫 · 1/1','1/1 OFFLINE','<em class="bad">异常</em>','山猫','08-12 16:55','<button data-screen="roomDetail">详情</button>']])}<div class="pagination">共 12 条 <button>‹</button><b>1</b><button>2</button><button>›</button></div>`,'rooms') }
function adminRoomDetail() { return adminShell(`${adminTop('ROOM R-17','夜训反应组','<button class="danger" data-dialog="forceClose">强制关闭</button>')}<div class="detail-grid"><section class="admin-panel"><header><h2>房间信息</h2><em class="ok">空闲</em></header><dl><div><dt>房主</dt><dd>YS-008 夜枭</dd></div><div><dt>成员</dt><dd>6 / 10</dd></div><div><dt>模式</dt><dd>精准训练 · A4</dd></div><div><dt>随机延迟</dt><dd>2.0 - 4.0 秒</dd></div></dl></section><section class="admin-panel"><header><h2>启用靶机</h2><span>3 / 3 READY</span></header><div class="node-lines">${[1,2,3].map((n,i)=>`<div><b>靶机 0${n}</b><span>node-${['a1b2c3','d4e5f6','g7h8i9'][i]}</span><em class="ok">READY · N=5</em></div>`).join('')}</div></section></div><section class="admin-panel"><header><h2>房间成员</h2><span>当前无训练轮次</span></header>${adminTable(['队员','角色','在线状态','完成轮次','最近训练','操作'],[['夜枭','房主','<em class="ok">在线</em>','8','今天 20:46','—'],['白泽','成员','<em class="ok">在线</em>','5','今天 20:18','<button>移除</button>'],['菜鸟','成员','<em class="ok">在线</em>','3','今天 19:52','<button>移除</button>'],['山猫','成员','离线','2','昨天 21:10','<button>移除</button>']])}</section>`,'rooms') }
function adminNodes() { return adminShell(`${adminTop('TARGET NODE FLEET','设备管理')}${adminStats([['8','在线设备','6 台 READY'],['3','占用房间','覆盖 7 个靶位'],['1','待上传队列','12 条事件'],['1','停用设备','不可连接']])}<div class="admin-filter"><input placeholder="Node ID / 登录账号"><select><option>全部在线状态</option></select><select><option>全部校准状态</option></select><button>查询</button></div>${adminTable(['Node ID / 账号','位置','连接','采集','校准','最后心跳','操作'],[['node-a1b2c3 / 夜枭','R-17 · 01','<em class="ok">在线</em>','120 FPS','READY','2 秒前','<button data-dialog="disconnect">断开</button>'],['node-d4e5f6 / 白泽','R-17 · 02','<em class="ok">在线</em>','120 FPS','READY','1 秒前','<button data-dialog="disconnect">断开</button>'],['node-g7h8i9 / 菜鸟','R-17 · 03','<em class="ok">在线</em>','60 FPS','<em class="warn">降级</em>','3 秒前','<button data-dialog="disconnect">断开</button>'],['node-z9y8x7 / 山猫','R-03 · 01','<em class="bad">重连中</em>','—','待上传 12','18 秒前','<button data-dialog="disable">停用</button>']])}`,'nodes') }
function adminModes() { return adminShell(`${adminTop('TRAINING MODE CONFIG','模式配置','<button class="primary" data-simulate="模式配置">保存全部</button>')}<div class="mode-admin-grid"><section class="admin-panel mode-config"><header><div><span>REACTION TIMER</span><h2>反应计时</h2></div><label class="switch"><input type="checkbox" checked><i></i></label></header><label>默认 N<input value="3"></label><label>N 范围<div><input value="1"><i>至</i><input value="20"></div></label><label>随机延迟<div><input value="2.0"><i>至</i><input value="4.0"></div></label><p>从蜂鸣开始，到每台靶机分别达到第 N 次有效上靶。</p></section><section class="admin-panel mode-config"><header><div><span>PRECISION GROUP</span><h2>精准训练</h2></div><label class="switch"><input type="checkbox" checked><i></i></label></header><label>默认 N<input value="5"></label><label>N 范围<div><input value="1"><i>至</i><input value="30"></div></label><label>靶纸<select><option>A4 / A3</option></select></label><p>累计 N 次有效命中，统计环数、平均环数和精准度。</p></section></div><section class="admin-panel protocol"><header><h2>协议与几何版本</h2><em>只作用于新轮次</em></header><div><label>WebSocket 协议<input value="training-ws/1.0"></label><label>Precision 几何<input value="precision-geometry/1.0"></label><label>最多靶机<input value="3"></label><label>房间容量<input value="10"></label></div></section>`,'modes') }
function adminRecords() { return adminShell(`${adminTop('TRAINING RECORDS','成绩管理')}${adminStats([['23','今日完成','15 次精准训练'],['94.3','平均精准度','较上周 +2.1'],['0.71s','最佳反应','YS-008 夜枭'],['2','异常轮次','设备断线']])}<div class="admin-filter"><input placeholder="用户 / 房间 / 轮次号"><select><option>全部模式</option></select><select><option>全部状态</option></select><button>查询</button></div>${adminTable(['轮次 / 用户','模式','配置','结果','状态','完成时间','操作'],[['S-260812-046 / 夜枭','精准训练','3 靶 · N=5','94.3 · 7.84s','<em class="ok">完成</em>','20:46','<button data-screen="recordDetail">详情</button>'],['S-260812-045 / 白泽','反应计时','2 靶 · N=3','2.62s','<em class="ok">完成</em>','20:18','<button data-screen="recordDetail">详情</button>'],['S-260812-044 / 菜鸟','精准训练','3 靶 · N=5','89.7 · 9.42s','<em class="ok">完成</em>','19:52','<button data-screen="recordDetail">详情</button>'],['S-260812-043 / 山猫','精准训练','1 靶 · N=10','6 发已收','<em class="bad">异常</em>','19:31','<button data-screen="recordDetail">详情</button>']])}`,'records') }
function adminRecordDetail() { return adminShell(`${adminTop('SESSION S-260812-046','夜枭 · 精准训练','<button data-dialog="editRecord">修改成绩</button><button class="danger" data-dialog="deleteRecord">删除记录</button>')}<div class="admin-result"><article><span>平均精准度</span><b>94.3</b><small>15 次有效命中</small></article><article><span>总用时</span><b>7.84s</b><small>最后靶机完成</small></article><article><span>总环数</span><b>141</b><small>平均 9.4 环</small></article><article><span>状态</span><b class="text-ok">完成</b><small>无异常事件</small></article></div><section class="admin-panel"><header><h2>逐发数据</h2><span>按 Camera PTS 排序</span></header>${adminTable(['靶机','序号','Shot Time','Split','环数','精准度','置信度'],hits.map(h=>[`T${h.target}`,`#${h.shot}`,`${h.time}s`,`${h.split}s`,h.ring,`${h.accuracy}%`,'0.97']))}</section>`,'records') }
function adminAudits() { return adminShell(`${adminTop('IMMUTABLE AUDIT TRAIL','审计记录')}<div class="admin-filter"><input placeholder="操作人 / 轮次号"><select><option>全部操作类型</option></select><button>查询</button></div>${adminTable(['时间','操作人','对象','动作','原因','结果'],[['08-12 18:33','Kevin','S-260812-031','修改成绩','靶机重复识别，经视频复核','精准度 88.2 → 92.6'],['08-11 21:05','Kevin','S-260811-018','业务删除','测试轮次，不计入个人成绩','会员端已隐藏'],['08-10 20:14','Admin-02','R-05','强制关闭房间','房主设备离线且无法恢复','轮次异常终止'],['08-09 19:42','Kevin','node-old-03','停用设备','设备已报废','连接已拒绝']])}`,'audits') }

function render() {
  updatePickers()
  device.className = `device ${model.product}-device`
  if (['loading','empty','error'].includes(model.demoState)) {
    const center = statusView()
    app.innerHTML = model.product === 'h5' ? `${logoHeader()}<main class="mobile-scroll">${center}</main>${h5Nav()}` : model.product === 'admin' ? adminShell(`${adminTop('SYSTEM STATE','状态预览')}${center}`, model.screen) : `${androidHeader('状态预览')}<main class="android-body">${center}</main>`
    bind(); return
  }
  const views = {
    h5: { history:h5History, mode:h5Mode, rooms:h5Rooms, dashboard:h5Dashboard, settings:h5Settings, result:h5Result, guest:guestNotice },
    android: { login:androidLogin, join:androidJoin, camera:androidCamera, manual:androidManual, settings:androidSettings, offline:androidOffline },
    admin: { rooms:adminRooms, roomDetail:adminRoomDetail, nodes:adminNodes, modes:adminModes, records:adminRecords, recordDetail:adminRecordDetail, audits:adminAudits }
  }
  app.innerHTML = (views[model.product][model.screen] || Object.values(views[model.product])[0])()
  bind()
}

function bind() {
  app.querySelectorAll('[data-screen]').forEach(x => x.onclick = () => { if (x.dataset.resultTargets) { model.resultTargetCount = Number(x.dataset.resultTargets); model.activeTarget = 1 } model.screen = x.dataset.screen; model.demoState = 'ready'; render() })
  app.querySelectorAll('[data-state-ready]').forEach(x => x.onclick = () => { model.demoState = 'ready'; render() })
  app.querySelectorAll('[data-simulate]').forEach(x => x.onclick = () => notify(`${x.dataset.simulate}已模拟，未写入数据库`))
  app.querySelectorAll('[data-scope]').forEach(x => x.onclick = () => { model.viewAll = x.dataset.scope === 'all'; render() })
  app.querySelectorAll('[data-mode]').forEach(x => x.onclick = () => { model.mode = x.dataset.mode; render() })
  app.querySelectorAll('[data-target]').forEach(x => x.onclick = () => { model.activeTarget = +x.dataset.target; render() })
  app.querySelectorAll('[data-target-count]').forEach(x => x.onclick = () => { model.targetCount = Math.max(1, Math.min(3, model.targetCount + (x.dataset.targetCount === 'plus' ? 1 : -1))); render() })
  app.querySelectorAll('[data-dialog]').forEach(x => x.onclick = () => {
    const type = x.dataset.dialog
    const defs = {
      multiplayer:['多人练习','<div class="choice-grid"><button data-jump="rooms"><b>＋</b>创建房间</button><button data-jump="rooms"><b>→</b>加入房间</button></div>','继续'],
      createRoom:['创建多人房间','<label class="dialog-field">房间名称<input value="夜训反应组"></label><p>最多 10 人，创建后由你配置训练参数。</p>','创建并进入'],
      start:['开始本人训练','<div class="countdown-preview"><span>ARE YOU READY?</span><b>2—4s</b><p>随机延迟后播放蜂鸣，成绩归当前操作者。</p></div>','开始'],
      leave:['退出房间','<p>退出不会关闭房间，已经开始的训练轮次仍会继续。</p>','确认退出'],
      closeRoom:['关闭房间','<p>关闭后所有成员和靶机将退出，历史成绩会保留。</p>','关闭房间'],
      forceClose:['强制关闭房间','<label class="dialog-field">关闭原因<textarea>房间异常，管理员介入</textarea></label>','强制关闭'],
      disconnect:['断开设备','<p>设备连接会立即中断，Android 可在未停用时重新连接。</p>','断开连接'],
      disable:['停用设备','<label class="dialog-field">停用原因<textarea>设备异常待检修</textarea></label>','确认停用'],
      editRecord:['修改训练成绩','<div class="edit-pair"><label>原精准度<input value="94.3" disabled></label><label>修改后<input value="92.6"></label></div><label class="dialog-field">修改原因<textarea>靶机重复识别，经复核调整</textarea></label>','保存修改'],
      deleteRecord:['删除训练记录','<label class="dialog-field">删除原因<textarea>测试轮次，不计入个人成绩</textarea></label><p>这是业务删除，原始数据与审计记录仍保留。</p>','确认删除']
    }
    const def = defs[type] || ['操作确认','<p>此操作只在原型中模拟。</p>','确认']
    openDialog(...def)
    dialogBody.querySelectorAll('[data-jump]').forEach(b => b.onclick = () => { dialog.close(); model.screen = b.dataset.jump; render() })
  })
}

productPicker.onchange = () => { model.product = productPicker.value; model.screen = products[model.product].screens[0][0]; model.demoState = 'ready'; render() }
screenPicker.onchange = () => { model.screen = screenPicker.value; render() }
statePicker.onchange = () => { model.demoState = statePicker.value; render() }
dialog.onclick = e => { if (e.target === dialog) dialog.close() }
render()
