<template>
  <main class="ys-site">
    <header class="ys-nav" aria-label="甬士网页导航">
      <button class="ys-brand" type="button" @click="scrollToSection('top')" aria-label="回到顶部">
        <img :src="logo" alt="宁波甬士标志" width="256" height="256" />
        <span>
          <strong>宁波甬士</strong>
          <small>Ningbo Field Wargame</small>
        </span>
      </button>

      <nav class="ys-links" aria-label="页面目录">
        <button v-for="item in navItems" :key="item.id" type="button" @click="scrollToSection(item.id)">
          {{ item.label }}
        </button>
      </nav>

      <button class="action-btn" type="button" @click="$emit('enter-app')">一起行动</button>
    </header>

    <div class="top-carousel-region carousel-region">
      <BackgroundCarousel :images="carouselImages('top')" label="首屏背景" />
    <section id="top" class="hero section-shell">
      <div class="hero-copy">
        <p class="plain-label">NINGBO YONGSHI / FIELD WARGAME</p>
        <h1>
          <span>把场地</span>
          <span>变成任务。</span>
        </h1>
        <p class="hero-lead">
          甬士在宁波组织军事模拟推演活动。我们会先看一处场地的入口、边界、楼体、巷口和撤离线，
          再把它写成玩家能进入的任务。第一次来的人，先听 Briefing，跟队走完一场。
        </p>
        <div class="hero-brief" aria-label="活动开始前会确认的事项">
          <span>FIRST RUN</span>
          <p>第一次合作从短流程开始。一段路线，一个任务，十几到几十人，试完再复盘下一场。</p>
        </div>
        <div class="hero-actions">
          <button type="button" class="primary-cta" @click="scrollToSection('cooperate')">从小场开始</button>
          <button type="button" class="secondary-cta" @click="$emit('enter-app')">进入报名</button>
        </div>
        <div class="hero-routes" aria-label="不同访客可以从哪里开始">
          <article v-for="route in visitorRoutes" :key="route.title">
            <strong>{{ route.title }}</strong>
            <span>{{ route.text }}</span>
          </article>
        </div>
      </div>

    </section>

    <section class="proof-strip section-shell" aria-label="甬士活动概况">
      <article v-for="item in metrics" :key="item.value">
        <strong>{{ item.value }}</strong>
        <span>{{ item.label }}</span>
      </article>
    </section>
    </div>

    <section id="about" class="section-shell intro-section carousel-section">
      <BackgroundCarousel :images="carouselImages('about')" label="关于板块背景" />
      <div class="section-kicker">About</div>
      <div class="about-board">
        <div class="about-copy">
          <h2>活动从一张地图开始。</h2>
          <p>
            到场后先走一遍路线。入口、楼梯、巷口、死角、集合点、休息区，能不能用，现场决定。
            场地只适合短流程，就不硬做大任务。
          </p>
          <div class="plain-list">
            <article v-for="item in aboutNotes" :key="item.title">
              <span>{{ item.title }}</span>
              <p>{{ item.text }}</p>
            </article>
          </div>
        </div>

      </div>
    </section>

    <section id="records" class="section-shell records-section carousel-section">
      <BackgroundCarousel :images="carouselImages('records')" label="历史记录板块背景" />
      <div class="section-kicker">Public record</div>
      <div class="section-heading">
        <h2>这些年去过哪里。</h2>
        <p>早期训练、山地路线、影视城剧本和外地交流，都留下了照片或公开视频。连起来看，是甬士这些年走过的场地。</p>
      </div>

      <div class="timeline-list">
        <article v-for="item in history" :key="item.year">
          <time>{{ item.year }}</time>
          <div>
            <h3>{{ item.title }}</h3>
            <p>{{ item.text }}</p>
          </div>
        </article>
      </div>

      <article class="record-feature">
        <div>
          <span>{{ featuredRecord.date }}</span>
          <h3>{{ featuredRecord.title }}</h3>
          <p>{{ featuredRecord.text }}</p>
        </div>
      </article>

      <div class="record-grid">
        <article v-for="item in activityLog" :key="item.title">
          <div>
            <span>{{ item.date }}</span>
            <h3>{{ item.title }}</h3>
            <p>{{ item.text }}</p>
          </div>
        </article>
      </div>
    </section>

    <section id="fields" class="section-shell fields-section carousel-section">
      <BackgroundCarousel :images="carouselImages('fields')" label="场地板块背景" />
      <div class="section-kicker">Fields</div>
      <div class="section-heading">
        <h2>场地不同，玩法不同。</h2>
        <p>影视城看街区和楼体，山地看路线和通讯，夜间街区要留出旁观位置。场地先走明白，人数、任务和安全区才好定。</p>
      </div>

      <div class="field-grid">
        <article v-for="venue in venues" :key="venue.name" :class="{ featured: venue.featured }">
          <div class="field-card-copy">
            <span>{{ venue.type }}</span>
            <h3>{{ venue.name }}</h3>
            <p>{{ venue.text }}</p>
            <ul>
              <li v-for="point in venue.points" :key="point">{{ point }}</li>
            </ul>
          </div>
        </article>
      </div>
    </section>

    <section id="activities" class="section-shell activities-section carousel-section">
      <BackgroundCarousel :images="carouselImages('activities')" label="活动板块背景" />
      <div class="section-kicker">Activities</div>
      <div class="section-heading">
        <h2>周常、剧本、远征。</h2>
        <p>周常让新玩家熟悉规则和队伍节奏。剧本场会写阵营、任务点和撤离条件。外地交流按对方规则进场，回来再复盘差异。</p>
      </div>

      <div class="activity-board">
        <div class="activity-list">
          <article v-for="item in activityTypes" :key="item.title">
            <span>{{ item.code }}</span>
            <div>
              <h3>{{ item.title }}</h3>
              <p>{{ item.text }}</p>
            </div>
          </article>
        </div>
      </div>

      <div class="flow-panel">
        <h3>一场活动的基本顺序</h3>
        <ol>
          <li v-for="step in opFlow" :key="step.title">
            <strong>{{ step.title }}</strong>
            <span>{{ step.text }}</span>
          </li>
        </ol>
      </div>

      <div class="expedition-strip" aria-label="远征交流记录">
        <article v-for="item in expeditions" :key="item.title" class="text-only">
          <div class="expedition-mark"><span>{{ item.mark || 'FIELD LOG' }}</span></div>
          <div>
            <span>{{ item.date }}</span>
            <h3>{{ item.title }}</h3>
            <p>{{ item.text }}</p>
          </div>
        </article>
      </div>
    </section>

    <section id="safe" class="section-shell safe-section carousel-section">
      <BackgroundCarousel :images="carouselImages('safe')" label="安全保障板块背景" />
      <div class="section-kicker">Safe ops</div>
      <div class="safe-layout">
        <div>
          <h2>边界、频道、停止口令。</h2>
          <p>
            人一多，现场靠喊不够。进场前讲禁入区、停止口令、频道和撤离线；
            结束后复盘路线、节奏和卡住的位置。通讯、队形和口令平时练过，现场就少出岔子。
          </p>
          <div class="safe-grid">
            <article v-for="item in trainingLoop" :key="item.title">
              <h3>{{ item.title }}</h3>
              <p>{{ item.text }}</p>
            </article>
          </div>
        </div>
      </div>
    </section>

    <section id="media" class="section-shell media-section carousel-section">
      <BackgroundCarousel :images="carouselImages('media')" label="现场影像板块背景" />
      <div class="section-kicker">Media</div>
      <div class="section-heading">
        <h2>现场影像。</h2>
        <p>照片看场地，视频看节奏。公开视频保留出处，现场照片按时间和场地留档。</p>
      </div>

      <div class="video-board" aria-label="活动视频片段">
        <figure v-for="clip in videoClips" :key="clip.title">
          <video :src="clip.src" :poster="clip.poster" controls muted playsinline preload="metadata"></video>
          <figcaption>
            <small>{{ clip.tag }}</small>
            <strong>{{ clip.title }}</strong>
            <span>{{ clip.text }}</span>
          </figcaption>
        </figure>
      </div>

      <div class="media-feature">
        <article class="media-lead-copy">
            <small>{{ mediaLead.tag }}</small>
            <strong>{{ mediaLead.title }}</strong>
            <span>{{ mediaLead.text }}</span>
        </article>

        <div class="media-notes" aria-label="影像记录说明">
          <article v-for="item in mediaNotes" :key="item.title">
            <span>{{ item.tag }}</span>
            <h3>{{ item.title }}</h3>
            <p>{{ item.text }}</p>
          </article>
        </div>
      </div>

      <div class="public-links">
        <a v-for="source in publicSources" :key="source.title" :href="source.href" target="_blank" rel="noreferrer">
          <span>{{ source.platform }}</span>
          <strong>{{ source.title }}</strong>
          <em>查看来源</em>
        </a>
      </div>
    </section>

    <section id="cooperate" class="section-shell cooperate-section carousel-section">
      <BackgroundCarousel :images="carouselImages('cooperate')" label="合作板块背景" />
      <div class="section-kicker">Work with us</div>
      <div class="cooperate-layout">
        <div>
          <h2>从一个小场开始。</h2>
          <p>
            场地、园区、学校或活动方第一次接触，不必一上来做大场。
            先选一段路线和一个任务，十几到几十人试跑。入口、禁入区、旁观位置和影像发布范围，进场前确认清楚；试完再定下一场。
          </p>
          <div class="partner-fit" aria-label="适合合作的场景">
            <article v-for="item in partnerFit" :key="item.title">
              <span>{{ item.tag }}</span>
              <p>{{ item.title }}</p>
            </article>
          </div>
          <div class="hero-actions">
            <button type="button" class="primary-cta" @click="$emit('enter-app')">查看报名入口</button>
            <button type="button" class="secondary-cta" @click="scrollToSection('media')">看影像记录</button>
          </div>
        </div>
        <div class="trial-list">
          <article v-for="item in trialSteps" :key="item.title">
            <span>{{ item.code }}</span>
            <div>
              <h3>{{ item.title }}</h3>
              <p>{{ item.text }}</p>
            </div>
          </article>
        </div>
      </div>

      <div class="partner-board">
        <div class="partner-lanes">
          <article v-for="group in partnerNotes" :key="group.title">
            <span>{{ group.code }}</span>
            <h3>{{ group.title }}</h3>
            <ul>
              <li v-for="point in group.points" :key="point">{{ point }}</li>
            </ul>
          </article>
        </div>
      </div>
    </section>
  </main>
</template>

<script>
import { api } from './api'
import BackgroundCarousel from './BackgroundCarousel.vue'
import logo from './assets/nbys-logo.png'
import haiyingcheng01 from './assets/site/haiyingcheng-jujie-01.jpg'
import haiyingcheng02 from './assets/site/haiyingcheng-jujie-02.jpg'
import haiyingcheng03 from './assets/site/haiyingcheng-jujie-03.jpg'
import haiyingcheng04 from './assets/site/haiyingcheng-jujie-04.jpg'
import haiyingcheng05 from './assets/site/haiyingcheng-jujie-05.jpg'
import yingmengli02 from './assets/site/yingmengli-02.jpg'
import yingmengliDate01 from './assets/site/yingmengli-20231022-01.jpg'
import yingmengliDate02 from './assets/site/yingmengli-20231022-02.jpg'
import siteModern02 from './assets/site/site-modern-2.jpg'
import xiaojiuzhaiTraining02 from './assets/site/xiaojiuzhai-training-02.jpg'
import xiaojiuzhaiEscape01 from './assets/site/xiaojiuzhai-escape-20211031-01.jpg'
import esaRoom from './assets/site/esa-urban-training-07.jpg'
import esaUrban01 from './assets/site/esa-urban-training-02.jpg'
import esaDoorTraining from './assets/site/esa-urban-training-04.jpg'
import esaUrban02 from './assets/site/esa-urban-training-05.jpg'
import patchBoard from './assets/site/patch-board.jpg'
import moto01 from './assets/site/moto-01.jpg'
import hengdianStreetWide from './assets/site/hengdian-05.jpg'
import xhsHengdianTeam from './assets/site/external/2026-03-15_xhs_hengdian-expedition_team.jpg'
import xhsHengdianStreet from './assets/site/external/2026-03-15_xhs_hengdian-expedition_street.jpg'
import xhsFieldGrass from './assets/site/external/2026-05-30_xhs_ningbo-yongshi_field-day_grass.jpg'
import xhsFieldTeam from './assets/site/external/2026-05-30_xhs_ningbo-yongshi_field-day_team.jpg'
import xhsWargameCover from './assets/site/external/2026-06-22_xhs_ningbo-yongshi_wargame-cover.jpg'
import xhsWargameFrame from './assets/site/external/2026-06-22_xhs_ningbo-yongshi_wargame-frame01.jpg'
import yongshiActionClip from './assets/site/yongshi-action-clip.mp4'
import yongshiFieldClip from './assets/site/yongshi-field-clip.mp4'
import jujieFinalVideo from './assets/site/jujie-final-2024-haiyingcheng.mp4'

const defaultHomepageCarousels = {
  top: [haiyingcheng04, haiyingcheng03, xhsWargameCover, xhsFieldTeam],
  about: [haiyingcheng02, xhsHengdianTeam, xhsFieldGrass],
  records: [haiyingcheng05, xiaojiuzhaiEscape01, xhsHengdianStreet, yingmengliDate01],
  fields: [siteModern02, xiaojiuzhaiTraining02, yingmengli02, moto01],
  activities: [haiyingcheng02, xhsWargameFrame, xhsFieldTeam, xhsHengdianStreet],
  safe: [esaRoom, esaDoorTraining, esaUrban01, esaUrban02],
  media: [haiyingcheng04, yingmengliDate02, xhsWargameCover, patchBoard],
  cooperate: [hengdianStreetWide, xhsHengdianTeam, xhsFieldGrass]
}

export default {
  name: 'MarketingSite',
  components: { BackgroundCarousel },
  emits: ['enter-app'],
  data() {
    return {
      logo,
      homepageCarousels: {},
      heroImage: haiyingcheng04,
      esaRoom,
      esaDoorTraining,
      activityFeatureImage: haiyingcheng02,
      cooperateImage: hengdianStreetWide,
      featuredRecord: {
        date: '2024.06 / 象山海影城',
        title: '巨蟹行动最终章',
        text: '队伍从海影城街区进入，沿建筑和巷口推进。任务点、撤离条件和阵营关系，在进场前已经讲过一轮。',
        image: haiyingcheng05
      },
      navItems: [
        { id: 'about', label: '关于 About' },
        { id: 'records', label: '记录 Record' },
        { id: 'fields', label: '场地 Fields' },
        { id: 'activities', label: '活动 Ops' },
        { id: 'safe', label: '保障 Safe' },
        { id: 'media', label: '影像 Media' },
        { id: 'cooperate', label: '合作 Join' }
      ],
      visitorRoutes: [
        { title: '玩家', text: '报名后参加周常，听简报，跟队下场。' },
        { title: '场地', text: '先看入口、禁入区、旁观位置和撤离线。' },
        { title: '学校 / 园区', text: '可以从规则课、队形演示或低强度试场开始。' }
      ],
      metrics: [
        { value: '2018', label: '早期训练记录' },
        { value: '6处', label: '常用和试过的场地' },
        { value: 'AAR', label: '每场结束后复盘' },
        { value: '宁波外', label: '横店、扬州等交流' }
      ],
      aboutNotes: [
        {
          title: '新玩家',
          text: '从周常开始。第一次不用急着表现，听规则、跟队走，记住停止口令和安全距离。'
        },
        {
          title: '下场流程',
          text: '集合后讲边界和任务，再分组进场。结束后复盘路线、通讯和卡住的位置。'
        },
        {
          title: '试场合作',
          text: '从短流程开始。人数、区域、旁观位置和影像发布范围，当天定清楚。'
        }
      ],
      aboutPhotos: [
        {
          caption: '象山海影城 / 巨蟹行动',
          image: haiyingcheng02
        },
        {
          caption: '横店影视城 / 远征交流',
          image: xhsHengdianTeam
        },
        {
          caption: '宁波周常 / 队员记录',
          image: xhsFieldGrass
        }
      ],
      history: [
        { year: '2018', title: 'ESA 城市作战训练', text: '室内简报、贴墙移动、门口队形。早期照片里能看到当时的训练方式。' },
        { year: '2019', title: '“巡山”户外训练', text: '山路、林线、队伍行进。当时的视频还留在 B 站。' },
        { year: '2021.03.27', title: '扬州 MILSIM 镭射交流', text: '去扬州同场交流，按对方规则进场。' },
        { year: '2021.10.31', title: '浙东小九寨“逃离荒野”', text: '山地路线，体力、通讯和队伍间距都很吃紧。' },
        { year: '2023.10.22', title: '应梦里活动', text: '夜间街区活动。灯光够，街面完整，短流程能跑起来。' },
        { year: '2024.06', title: '象山海影城·巨蟹行动最终章', text: '在影视城街区跑任务，沿建筑、巷口和任务点推进。' },
        { year: '2026.03', title: '横店远征交流', text: '横店影视城交流。街区尺度、建筑距离和宁波常用场地不一样。' }
      ],
      activityLog: [
        { date: '2026.06.22 / 小红书', title: '宁波甬士下场视频', text: '队员视角，能看到移动、掩护和接触距离。', image: xhsWargameFrame },
        { date: '2026.05.30 / 小红书', title: '5.30 下场日记', text: '草地场景、队伍合影，还有当天照片。', image: xhsFieldTeam },
        { date: '2026.03.15 / 横店', title: '横店影视城交流', text: '外地影视城的街区尺度，和宁波常用场地不一样。', image: xhsHengdianStreet },
        { date: '2021.10.31 / 山地', title: '浙东小九寨路线', text: '山路和林线里，队伍间距很容易被拉开。', image: xiaojiuzhaiEscape01 }
      ],
      venues: [
        {
          name: '象山海影城',
          type: '影视城街区',
          image: haiyingcheng05,
          featured: true,
          text: '街道、楼体、巷口都能用。搜索、据点、撤离和夜间任务都跑过。',
          points: ['巨蟹行动', '街区推进', '夜间任务']
        },
        {
          name: '应梦里',
          type: '夜间街区',
          image: yingmengliDate01,
          featured: false,
          text: '夜间灯光足，街道完整。短流程能跑，旁边也能看清队伍移动。',
          points: ['夜间街区', '短流程']
        },
        {
          name: '四明山野猫湾 / 浙东小九寨',
          type: '山地路线',
          image: xiaojiuzhaiEscape01,
          featured: false,
          text: '山地看路线和通讯。体力、天气、队伍间距都会影响安排。',
          points: ['路线规划', '通讯纪律']
        },
        {
          name: '迎春里',
          type: '街区空间',
          image: moto01,
          featured: false,
          text: '街区尺度小，动线短。短任务好控制，也能留出观看位置。',
          points: ['短流程', '器材摆放']
        },
        {
          name: '天宫庄园',
          type: '边界清楚',
          image: patchBoard,
          featured: false,
          text: '这类场地先看边界和动线。人数不多时，可以从低强度任务开始试。',
          points: ['试场', '低强度']
        },
        {
          name: '章水中心小学',
          type: '校园训练',
          image: esaRoom,
          featured: false,
          text: '可以讲规则、演示队形、练口令和安全边界。强度不高，基础能讲清。',
          points: ['规则讲解', '基础队形']
        }
      ],
      activityTypes: [
        { code: '周常', title: '周常活动', text: '新人跟队走一场，先把规则和安全距离跑熟。老队员继续磨通讯、队形和复盘习惯。' },
        { code: '剧本', title: '影视城剧本', text: '有阵营、任务点、行动区域和撤离条件。巨蟹行动是在海影城街区里跑出来的。' },
        { code: '山地', title: '户外任务', text: '路线、体力、通讯和天气，都会改变当天安排。' },
        { code: '远征', title: '外地交流', text: '到外地场地，按对方规则来，再适应新的空间。' },
        { code: '试场', title: '合作试场', text: '跑一段短流程。边界、动线和安全区，现场看最清楚。' }
      ],
      expeditions: [
        {
          date: '2026.03.15 / 横店影视城',
          title: '横店远征交流',
          text: '街区尺度更大，窗口、楼体和开阔地的距离都要重新适应；同样的队形，换个场地就会有不同节奏。',
          image: xhsHengdianStreet
        },
        {
          date: '2021.03.27 / 扬州',
          title: 'MILSIM 镭射交流',
          text: '公开视频留在 B 站。时间、地点和交流内容都能对上。',
          mark: 'BILIBILI'
        }
      ],
      opFlow: [
        { title: '集合', text: '点人数、看装备、分组，确认场地边界。' },
        { title: 'Briefing', text: '讲规则、任务点、集合点、撤离点和停止口令。' },
        { title: '进场', text: '按阵营或班组进场，保持通讯和队形。' },
        { title: '任务', text: '搜索、占点、护送、撤离，按当天剧本走。' },
        { title: 'AAR', text: '结束后复盘安全、通讯、路线和分工。' }
      ],
      trainingLoop: [
        { title: 'ROE', text: '哪些地方能进，哪些地方不能进；什么情况下立刻停。' },
        { title: '通讯', text: '呼号、位置、异常情况，尽量短句。对讲机里不要抢话。' },
        { title: '队形', text: '移动时不要挤成一团。前后左右是谁，心里要有数。' },
        { title: 'AAR', text: '结束后把问题说出来。路线、节奏、风险点，下次改。' }
      ],
      mediaLead: {
        tag: 'XIANGSHAN / 2024',
        title: '象山海影城夜间任务',
        text: '灯一暗，街道、树影和建筑边缘会混在一起。队伍靠口令、手势和事先讲好的任务点往前推。',
        image: haiyingcheng04
      },
      mediaNotes: [
        {
          tag: 'URBAN',
          title: '影视城和夜间街区',
          text: '建筑、巷口和灯光会影响路线。象山海影城、应梦里这类场地，可以做搜索、占点和撤离。'
        },
        {
          tag: 'MOUNTAIN',
          title: '山地和户外路线',
          text: '山路里队伍容易拉开，通讯和体力比动作更重要。天气和路线会直接改变当天安排。'
        },
        {
          tag: 'TRAINING',
          title: '平时训练',
          text: '室内 Briefing、队形移动、口令和复盘。平时练过，现场才不容易散。'
        }
      ],
      videoClips: [
        {
          tag: 'XIANGSHAN / 2024',
          title: '巨蟹行动现场',
          text: '象山海影城的夜间场地，灯光和建筑边缘会改变判断。',
          src: jujieFinalVideo,
          poster: haiyingcheng04
        },
        {
          tag: 'LOCAL CLIP / FIELD',
          title: '下场片段',
          text: '移动、观察和队友距离，比静态照片更容易看出来。',
          src: yongshiFieldClip,
          poster: xhsWargameFrame
        },
        {
          tag: 'LOCAL CLIP / ACTION',
          title: '行动片段',
          text: '短片段保留现场节奏。队伍集合、移动和停顿都在里面。',
          src: yongshiActionClip,
          poster: xhsWargameCover
        }
      ],
      assetStories: [
        { tag: 'HENGDIAN / 2026', title: '横店远征交流', text: '影视城街区前的队伍合影。', image: xhsHengdianTeam, size: 'wide' },
        { tag: 'YINGMENGLI / 2023', title: '建筑窗口路线', text: '窗口、栏杆和队友位置都在画面里。', image: siteModern02, size: '' },
        { tag: 'YINGMENGLI / 2023', title: '应梦里夜间活动', text: '楼上视角能看清街道和移动路线。', image: yingmengli02, size: '' },
        { tag: 'MOUNTAIN / FIELD', title: '浙东小九寨训练', text: '山地路线里，队伍间距很容易被拉开。', image: xiaojiuzhaiTraining02, size: '' },
        { tag: 'ESA / URBAN', title: 'ESA 城市作战训练', text: '门口、墙边和队形处理。', image: esaUrban01, size: '' },
        { tag: 'XHS / 2026', title: '5.30 下场日记', text: '草地场景，队员距离能看得比较清楚。', image: xhsFieldGrass, size: '' },
        { tag: 'URBAN / CQB', title: '室内街区训练', text: '门口处理、墙边移动和互相掩护。', image: esaUrban02, size: '' },
        { tag: 'YINGMENGLI / 2023', title: '应梦里活动', text: '2023 年 10 月 22 日夜间场地。', image: yingmengliDate02, size: '' },
        { tag: 'XHS / 2026', title: '6.22 队员视频', text: '小红书公开视频封面。', image: xhsWargameCover, size: '' },
        { tag: 'CANCER', title: '巨蟹行动道具', text: '地图、文件和现场道具。', image: haiyingcheng01, size: '' }
      ],
      publicSources: [
        { platform: 'BILIBILI / 2019', title: '“巡山”行动——户外训练', href: 'https://www.bilibili.com/video/BV1hb411s7zL' },
        { platform: 'BILIBILI / 2021', title: '2021.03.27 扬州 MILSIM 镭射', href: 'https://www.bilibili.com/video/BV1SZ4y1c74a' },
        { platform: 'BILIBILI / 2024', title: '2024 巨蟹行动最终章', href: 'https://www.bilibili.com/video/BV1tr42177Yo' },
        { platform: 'BILIBILI / PREVIEW', title: '巨蟹行动活动预告', href: 'https://www.bilibili.com/video/BV11a411W7ci' },
        { platform: 'XIAOHONGSHU / 2026', title: '甬士横店征途', href: 'https://www.xiaohongshu.com/search_result/69b587e1000000001e00cc1b' },
        { platform: 'XIAOHONGSHU / 2026', title: '甬士横店远征', href: 'https://www.xiaohongshu.com/search_result/69b956af000000002202686f' }
      ],
      trialSteps: [
        { code: '01', title: '看场地', text: '地点、时间、可进入区、禁入区和预计人数，先对齐。' },
        { code: '02', title: '划出边界', text: '集合点、休息区、旁观位置、撤离路线和负责人，当场定下来。' },
        { code: '03', title: '跑短任务', text: '30-90 分钟，跑一个清楚的任务，不急着把流程做大。' },
        { code: '04', title: '当天复盘', text: '人数、路线、任务、照片和视频发布范围，试完再调整。' }
      ],
      partnerFit: [
        { tag: 'FIELD', title: '影视城、街区、园区试场' },
        { tag: 'CAMPUS', title: '校园规则课和低强度体验' },
        { tag: 'EVENT', title: '小型主题活动和路线任务' },
        { tag: 'MEDIA', title: '活动照片、短片和公开记录' }
      ],
      partnerNotes: [
        {
          code: 'A',
          title: '适合试场的地方',
          points: ['有街区或楼体的影视城', '边界清楚的园区', '旁边能站人观看的夜间街区', '能留出安全区的校园或营地']
        },
        {
          code: 'B',
          title: '到场前说清楚',
          points: ['哪些地方能进，哪些地方不能进', '人从哪里集合，旁边的人站在哪里', '停止口令、撤离路线和联络人', '照片和视频能发到什么范围']
        },
        {
          code: 'C',
          title: '试完后的下一步',
          points: ['当天照片和可公开片段', 'AAR 复盘记录', '下一场人数、路线和任务调整']
        }
      ]
    }
  },
  mounted() {
    this.loadHomepageCarousels()
  },
  methods: {
    async loadHomepageCarousels() {
      try {
        this.homepageCarousels = await api('/api/public/system-settings/homepage-carousels') || {}
      } catch {
        this.homepageCarousels = {}
      }
    },
    carouselImages(sectionKey) {
      const configured = this.homepageCarousels[sectionKey] || []
      return configured.length ? configured : (defaultHomepageCarousels[sectionKey] || [])
    },
    scrollToSection(id) {
      const target = id === 'top' ? document.getElementById('top') : document.getElementById(id)
      target?.scrollIntoView({ behavior: 'smooth', block: 'start' })
    }
  }
}
</script>
