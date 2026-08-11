const screens = [
  ['login','登录'],['register','注册'],['activities','活动首页'],['plan','策划投票'],['activity','活动详情'],
  ['activityRentals','活动发射器'],['rentals','个人发射器'],['notifications','通知'],['mine','我的']
]
const state = { screen: localStorage.getItem('prototypeScreen') || 'login', demoState: 'ready', me: null, dashboard: null, rentals: [], notices: [], token: localStorage.getItem('prototypeH5Token') || '', activeDialog: '' }
const app = document.querySelector('#app')
const dialog = document.querySelector('#prototypeDialog')
const dialogContent = document.querySelector('#dialogContent')
const toast = document.querySelector('#toast')
const picker = document.querySelector('#screenPicker')
const statePicker = document.querySelector('#statePicker')
picker.innerHTML = screens.map(([value,label]) => `<option value="${value}">${label}</option>`).join('')
picker.value = state.screen

const fixture = {
  me:{id:25,username:'菜鸟',callsign:'YS-025 菜鸟',avatar_url:'',invite_code:'NBYS-2026'},
  attendance_summary:{present_count:12,activity_total:15},
  activities:[
    {id:89,name:'甬士周常 · 城市攻防',display_status:'报名中',start_at:'2026-08-16 09:00',venue_name:'海鹰城训练场',creator_name:'夜枭',enroll_count:18,signup_limit:30,banner_url:'/h5-web/src/assets/site/esa-urban-training-01.jpg'},
    {id:88,name:'夜幕行动 · 山地搜索',display_status:'活动进行中',start_at:'2026-08-12 18:30',venue_name:'小九寨训练场',creator_name:'白泽',enroll_count:24,signup_limit:24,banner_url:'/h5-web/src/assets/site/xiaojiuzhai-training-01.jpg'}
  ],
  plans:[{id:31,name:'九月远征活动策划',display_status:'策划中',vote_deadline:'2026-08-20 22:00',creator_name:'夜枭',voter_count:16,banner_url:'/h5-web/src/assets/site/hengdian-01.jpg'}],
  notifications:[{id:1,title:'发射器租赁待确认',content:'菜鸟申请租赁 MWS 发射器',created_at:'2026-08-11 14:32',read_at:null},{id:2,title:'活动报名成功',content:'你已加入甬士周常 · 城市攻防',created_at:'2026-08-10 20:10',read_at:'2026-08-10'}],
  rentals:[{id:1,name:'MWS URGI',rent_fee:80,active:true,description:'已调校，含两只弹匣',photo_filename:'/h5-web/src/assets/site/patch-board.jpg'},{id:2,name:'AKM GBB',rent_fee:60,active:false,description:'木质护木版本',photo_filename:'/h5-web/src/assets/site/moto-01.jpg'}]
}

function safeText(value=''){return String(value).replace(/[&<>'"]/g,char=>({'&':'&amp;','<':'&lt;','>':'&gt;',"'":'&#39;','"':'&quot;'}[char]))}
function shortName(value='甬'){return [...String(value)].slice(-2).join('')}
function formatDate(value){return value?String(value).replace('T',' ').slice(0,16):'待定'}
function notify(message){toast.textContent=message;toast.classList.add('show');clearTimeout(notify.timer);notify.timer=setTimeout(()=>toast.classList.remove('show'),1800)}

async function apiGet(path){
  const headers = state.token ? {Authorization:`Bearer ${state.token}`} : {}
  const response = await fetch(path,{headers,credentials:'include'})
  const result = await response.json().catch(()=>null)
  if(!response.ok||result?.code!==0) throw new Error(result?.message||`请求失败(${response.status})`)
  return result.data
}

async function login(account,password){
  const response=await fetch('/api/h5/auth/login',{method:'POST',headers:{'Content-Type':'application/json'},credentials:'include',body:JSON.stringify({account,password})})
  const result=await response.json().catch(()=>null)
  if(!response.ok||result?.code!==0) throw new Error(result?.message||'登录失败')
  state.token=result.data.token;localStorage.setItem('prototypeH5Token',state.token);await loadLiveData();go('activities')
}

async function loadLiveData(){
  if(!state.token)return
  try{
    const [me,dashboard]=await Promise.all([apiGet('/api/h5/me'),apiGet('/api/h5/activities/bootstrap')])
    state.me=me;state.dashboard=dashboard
  }catch(error){notify(`真实数据读取失败，已使用仿真内容：${error.message}`)}
}

function data(){return {me:state.me||fixture.me,dashboard:state.dashboard||fixture}}
function activities(){const d=data().dashboard;return [...(d.activities||fixture.activities).map(item=>({...item,kind:'activity'})),...(d.plans||fixture.plans).map(item=>({...item,kind:'plan',display_status:'策划中'}))]}
function currentActivity(){return activities().find(item=>item.kind==='activity')||fixture.activities[0]}
function currentPlan(){return activities().find(item=>item.kind==='plan')||fixture.plans[0]}

function header(title='会员中心',eyebrow='NINGBO WARGAME'){
  return `<header class="brand-header"><img class="brand-system-logo" src="/h5-web/src/assets/nbys-logo.png" alt="甬士 Logo" /><strong>宁波甬士活动管理系统</strong><button class="system-home-btn" data-go="activities">返回首页</button></header>`
}
function nav(active){return `<nav class="bottom-nav">${[['activities','⌂','活动'],['rentals','▣','发射器租赁'],['notifications','●','通知'],['mine','◆','我的']].map(([value,icon,label])=>`<button data-go="${value}" class="${active===value?'active':''}"><b>${icon}</b>${label}${value==='notifications'?'<i class="nav-dot"></i>':''}</button>`).join('')}</nav>`}
function shell(content,active='activities',showHeader=true){return `${showHeader?header():''}<div class="app-scroll">${content}</div>${nav(active)}`}
function stateView(){const copy={loading:['正在同步数据','读取活动与成员状态'],empty:['暂无相关内容','当前筛选条件下没有可展示的数据'],error:['页面加载失败','请检查本地后端服务后重试']}[state.demoState];return `<section class="state-view"><div><span class="state-icon">${state.demoState==='loading'?'↻':state.demoState==='empty'?'—':'!'}</span><h2>${copy[0]}</h2><p>${copy[1]}</p>${state.demoState==='loading'?'<div class="skeleton"></div>':'<button class="btn" data-state-ready>返回正常状态</button>'}</div></section>`}

function loginScreen(register=false){return `<div class="app-scroll no-nav"><section class="auth"><form class="auth-card" id="authForm"><span class="eyebrow">NBYS MEMBER ACCESS</span><h1>${register?'加入宁波甬士':'队员登录'}</h1><p>${register?'创建队员档案，正式审核通过后开放全部功能。':'活动、出勤、租赁与个人资料统一入口。'}</p><label class="field"><span>${register?'用户名':'名字 / 呼号'}</span><input name="account" required value="${register?'':'菜鸟'}" /></label>${register?'<label class="field"><span>呼号</span><input name="callsign" placeholder="选填" /></label>':''}<label class="field"><span>密码</span><input name="password" type="password" required /></label>${register?'<label class="field"><span>邀请码</span><input name="invite" /></label>':''}<button class="btn primary" type="submit">${register?'模拟注册':'登录并读取本地数据'}</button><div class="auth-links"><button type="button" data-go="${register?'login':'register'}">${register?'返回登录':'注册账号'}</button><button type="button" data-demo-login>直接预览</button></div></form></section></div>`}

function activityCards(){return activities().map(item=>`<article class="activity-card" data-go="${item.kind==='plan'?'plan':'activity'}"><div class="media" style="background-image:url('${safeText(item.banner_url||'/h5-web/src/assets/activity-default.jpg')}')"><span class="status ${item.kind==='plan'?'planning':''}">${safeText(item.display_status||'报名中')}</span><div class="media-title"><h3>${safeText(item.name)}</h3></div></div><div class="card-body"><div class="meta-grid">${item.kind==='plan'?`<span>投票截止<b>${formatDate(item.vote_deadline)}</b></span><span>已投票<b>${item.voter_count||0} 人</b></span>`:`<span>活动时间<b>${formatDate(item.start_at)}</b></span><span>报名进度<b>${item.enroll_count||0} / ${item.signup_limit||'-'}</b></span>`}<span>活动地点<b>${safeText(item.venue_name||'待定')}</b></span><span>发起人<b>${safeText(item.creator_name||'未设置')}</b></span></div><div class="progress"><i></i></div></div></article>`).join('')}
function activitiesScreen(){return shell(`<section class="escape-entry" role="button" aria-label="进入逃离西撇镇"></section><div class="section-head"><div><small>ACTIVE OPERATIONS</small><h2>近期活动</h2></div><span>${activities().length} 项进行中</span></div><div class="activity-list">${activityCards()}</div>`,'activities')}

function planScreen(){const item=currentPlan();return shell(`<section class="page-hero" style="background-image:url('${safeText(item.banner_url||'/h5-web/src/assets/activity-default.jpg')}')"><div><span class="eyebrow">ACTIVITY PLANNING</span><h1>${safeText(item.name)}</h1><p>发起人 ${safeText(item.creator_name||'未设置')} · ${item.voter_count||0} 人已投票</p></div></section><section class="detail-block"><h3>可选日期</h3><div class="option-list"><label class="option"><input type="checkbox" /><span><strong>2026-09-12 周六</strong><small>全天 · 8 票</small></span></label><label class="option"><input type="checkbox" /><span><strong>2026-09-19 周六</strong><small>全天 · 11 票</small></span></label></div></section><section class="detail-block"><h3>活动场地</h3><div class="option-list"><label class="option"><input type="checkbox" /><span><strong>横店圆明园</strong><small>浙江省金华市东阳市 · 9 票</small></span></label><label class="option"><input type="checkbox" /><span><strong>海鹰城训练场</strong><small>宁波市 · 7 票</small></span></label></div></section><section class="action-dock"><button class="btn" data-dialog="share">分享策划</button><button class="btn primary" data-simulate="投票已模拟提交">提交投票</button></section>`,'activities')}

function activityScreen(){const item=currentActivity();return shell(`<section class="page-hero" style="background-image:url('${safeText(item.banner_url||'/h5-web/src/assets/activity-default.jpg')}')"><div><span class="eyebrow">ACTIVITY BRIEFING</span><h1>${safeText(item.name)}</h1><p>${safeText(item.display_status||'报名中')} · ${item.enroll_count||0}/${item.signup_limit||'-'} 人</p></div></section><section class="detail-block"><div class="key-values"><div><span>活动时间</span><b>${formatDate(item.start_at)}</b></div><div><span>活动地点</span><b>${safeText(item.venue_name||'待定')}</b></div><div><span>发起人</span><b>${safeText(item.creator_name||'未设置')}</b></div><div><span>我的状态</span><b>已报名 · 未签到</b></div></div><p class="detail-copy">活动信息、报名状态和主要操作集中展示。阵营与小队信息在报名后开放。</p></section><section class="detail-block"><h3>已报名人员</h3><div class="stack"><div class="member-row"><span class="mini-avatar">夜</span><div><strong>YS-008 夜枭</strong><small>第 1 小队 · 队长</small></div><em>已签到</em></div><div class="member-row"><span class="mini-avatar">菜</span><div><strong>YS-025 菜鸟</strong><small>第 1 小队 · 队员</small></div><em>已报名</em></div></div></section><section class="action-dock"><button class="btn" data-dialog="moreActions">更多操作</button><button class="btn primary" data-dialog="checkin">签到 / 报名管理</button></section>`,'activities')}

function activityRentalsScreen(){return shell(`<section class="page-hero compact"><div><span class="eyebrow">FIELD EQUIPMENT</span><h1>活动发射器</h1><p>为当前活动选择可租用设备</p></div></section><div class="filter-tabs"><button class="active">全部</button><button>可租</button><button>已租</button></div><div class="stack">${rentalRows(true)}</div>`,'rentals')}
function rentalRows(activityMode=false){const rows=state.rentals.length?state.rentals:fixture.rentals;return rows.map(item=>`<article class="rental-card">${item.photo_filename?`<img src="${safeText(item.photo_filename)}" alt="${safeText(item.name)}" />`:'<div class="rental-art">NO IMG</div>'}<div><strong>${safeText(item.name)}</strong><small>${safeText(item.description||'暂无说明')}</small><small>租金 ¥${item.rent_fee||0}</small></div><button class="btn ${activityMode?'primary':''}" data-simulate="${activityMode?'租赁申请已模拟':'编辑操作已模拟'}">${activityMode?'租用':'编辑'}</button></article>`).join('')}
function rentalsScreen(){return shell(`<section class="page-hero compact"><div><span class="eyebrow">LAUNCHER RENTAL</span><h1>我的发射器</h1><p>维护个人设备与出租状态</p></div></section><div class="section-head"><div><small>MY EQUIPMENT</small><h2>设备列表</h2></div><button class="btn primary" data-dialog="rentalEdit">＋ 新增</button></div><div class="stack">${rentalRows()}</div>`,'rentals')}
function notificationsScreen(){const rows=state.notices.length?state.notices:(data().dashboard.notifications||fixture.notifications);return shell(`<section class="page-hero compact"><div><span class="eyebrow">MESSAGE CENTER</span><h1>通知</h1><p>活动、租赁与系统动态</p></div></section><div class="filter-tabs"><button class="active">全部</button><button>活动</button><button>租赁</button></div><div class="stack">${rows.map(item=>`<article class="notice ${item.read_at?'read':''}"><i></i><div><strong>${safeText(item.title)}</strong><p>${safeText(item.content)}</p></div><time>${formatDate(item.created_at).slice(5)}</time></article>`).join('')}</div>`,'notifications')}
function mineScreen(){const me=data().me;return shell(`<section class="identity-hero"><div class="identity-main">${me.avatar_url?`<img src="${safeText(me.avatar_url)}" alt="头像" />`:`<div class="avatar-fallback">${shortName(me.callsign||me.username)}</div>`}<div><span class="eyebrow">MEMBER DOSSIER</span><h1>${safeText(me.callsign||'未设置呼号')}</h1><p>${safeText(me.username||'宁波甬士队员')}</p></div></div></section><section class="detail-block"><h3>2026 年出勤</h3><p class="detail-copy">横向滑动查看本年度活动，绿色标记代表已签到。</p><div class="attendance"><table><thead><tr><th>出勤</th><th>城市攻防<br>08/16</th><th>山地搜索<br>08/12</th><th>周常训练<br>08/02</th><th>巨蟹行动<br>07/18</th></tr></thead><tbody><tr><td>12 次</td><td><i class="attendance-dot"></i></td><td><i class="attendance-dot"></i></td><td><i class="attendance-dot"></i></td><td>—</td></tr></tbody></table></div></section><section class="detail-block"><h3>账号设置</h3><div class="account-actions"><button class="btn" data-dialog="profile">修改资料</button><button class="btn" data-dialog="password">修改密码</button><button class="btn danger" data-simulate="已退出原型登录">退出登录</button></div></section><section class="detail-block"><h3>邀请好友</h3><p class="detail-copy">邀请码 ${safeText(me.invite_code||'NBYS-2026')}</p><button class="btn primary" data-dialog="invite">展示邀请二维码</button></section>`,'mine')}

function render(){
  picker.value=state.screen;statePicker.value=state.demoState;localStorage.setItem('prototypeScreen',state.screen)
  if(['login','register'].includes(state.screen)){app.innerHTML=loginScreen(state.screen==='register');bind();return}
  if(state.demoState!=='ready'){app.innerHTML=`${header()}<div class="app-scroll">${stateView()}</div>${nav(['activity','plan','activityRentals'].includes(state.screen)?'activities':state.screen)}`;bind();return}
  const views={activities:activitiesScreen,plan:planScreen,activity:activityScreen,activityRentals:activityRentalsScreen,rentals:rentalsScreen,notifications:notificationsScreen,mine:mineScreen}
  app.innerHTML=(views[state.screen]||activitiesScreen)();bind()
}

function go(screen){state.screen=screen;state.demoState='ready';render()}
function openDialog(type){state.activeDialog=type;const content={
  checkin:['CHECK-IN','活动签到','选择定位签到或扫描活动二维码','确认签到'],moreActions:['ENROLLMENT','报名管理','修改同行人数、打开活动发射器或取消报名','保存修改'],share:['SHARE','分享活动','生成分享卡片并复制活动链接','复制链接'],rentalEdit:['EQUIPMENT','新增发射器','录入名称、租金、说明和设备照片','模拟保存'],profile:['PROFILE','修改资料','更新用户名、呼号和头像','模拟保存'],password:['SECURITY','修改密码','设置至少 8 位的新密码','模拟修改'],invite:['INVITATION','邀请好友','邀请码 NBYS-2026 · 二维码预览','复制邀请链接']
  }[type]||['PROTOTYPE','操作确认','该操作仅在原型中模拟，不会修改数据库。','确认']
  dialogContent.innerHTML=`<div class="sheet-body"><div class="sheet-head"><div><span class="eyebrow">${content[0]}</span><h2>${content[1]}</h2></div><button class="icon-btn" data-close>×</button></div><p class="detail-copy">${content[2]}</p><label class="field"><span>示例输入</span><input value="原型数据" /></label><div class="dialog-actions"><button class="btn" data-close>取消</button><button class="btn primary" data-dialog-confirm>${content[3]}</button></div></div>`
  dialog.showModal();bindDialog()
}
function bindDialog(){dialogContent.querySelectorAll('[data-close]').forEach(el=>el.onclick=()=>dialog.close());const confirm=dialogContent.querySelector('[data-dialog-confirm]');if(confirm)confirm.onclick=()=>{dialog.close();notify('操作已在原型中模拟，未写入数据库')}}
function bind(){
  app.querySelectorAll('[data-go]').forEach(el=>el.onclick=()=>go(el.dataset.go))
  app.querySelectorAll('[data-dialog]').forEach(el=>el.onclick=()=>openDialog(el.dataset.dialog))
  app.querySelectorAll('[data-simulate]').forEach(el=>el.onclick=()=>notify(`${el.dataset.simulate}，未写入数据库`))
  app.querySelectorAll('[data-state-ready]').forEach(el=>el.onclick=()=>{state.demoState='ready';render()})
  const demo=app.querySelector('[data-demo-login]');if(demo)demo.onclick=()=>go('activities')
  const form=app.querySelector('#authForm');if(form)form.onsubmit=async event=>{event.preventDefault();if(state.screen==='register'){notify('注册已模拟完成，未写入数据库');go('login');return}const button=form.querySelector('button[type=submit]');button.disabled=true;button.textContent='登录中…';try{await login(form.account.value,form.password.value)}catch(error){notify(error.message)}finally{button.disabled=false;button.textContent='登录并读取本地数据'}}
}

picker.onchange=()=>go(picker.value)
statePicker.onchange=()=>{state.demoState=statePicker.value;render()}
dialog.addEventListener('click',event=>{if(event.target===dialog)dialog.close()})
loadLiveData().finally(render)
