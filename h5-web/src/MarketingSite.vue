<template>
  <main class="ys-site">
    <header class="ys-nav" aria-label="甬士网页导航">
      <button class="ys-brand" type="button" @click="scrollToSection('top')" aria-label="回到顶部">
        <img :src="logo" alt="宁波甬士标志" />
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

    <section id="top" class="hero section-shell">
      <div class="hero-copy">
        <p class="plain-label">NINGBO YONGSHI / FIELD WARGAME</p>
        <h1>
          <span>把推演</span>
          <span>带到现场。</span>
        </h1>
        <p class="hero-lead">
          甬士是一支长期在宁波活动的军事模拟推演队伍。我们在影视城、街区、山地和园区做下场活动，
          也和外地队伍交流。每次进场前，先把边界、ROE、任务点和撤离路线讲清楚。
        </p>
        <div class="hero-brief" aria-label="活动开始前会确认的事项">
          <span>FIELD CHECK</span>
          <p>玩家可以从周常报名开始。场地、园区、学校和品牌活动，建议先从一场小规模试场开始。</p>
        </div>
        <div class="hero-actions">
          <button type="button" class="primary-cta" @click="scrollToSection('cooperate')">先试一场</button>
          <button type="button" class="secondary-cta" @click="$emit('enter-app')">进入报名</button>
        </div>
        <div class="hero-routes" aria-label="不同访客可以从哪里开始">
          <article v-for="route in visitorRoutes" :key="route.title">
            <strong>{{ route.title }}</strong>
            <span>{{ route.text }}</span>
          </article>
        </div>
      </div>

      <figure class="hero-media">
        <img :src="heroImage" alt="象山海影城夜间任务现场" width="1400" height="900" fetchpriority="high" decoding="async" />
        <figcaption>
          <span>2024 / 象山海影城</span>
          <strong>夜间任务现场</strong>
        </figcaption>
      </figure>
    </section>

    <section class="proof-strip section-shell" aria-label="甬士活动概况">
      <article v-for="item in metrics" :key="item.value">
        <strong>{{ item.value }}</strong>
        <span>{{ item.label }}</span>
      </article>
    </section>

    <section id="about" class="section-shell intro-section">
      <div class="section-kicker">About</div>
      <div class="about-board">
        <div class="about-copy">
          <h2>先看场地，再定玩法。</h2>
          <p>
            甬士做的不是坐在房间里的桌面推演。我们会先到现场看路、看门、看楼梯、看人能站在哪里。
            能跑到哪里，哪里必须停，观摩的人站在哪里，这些确认以后，再把任务放进去。
          </p>
          <div class="plain-list">
            <article v-for="item in aboutNotes" :key="item.title">
              <span>{{ item.title }}</span>
              <p>{{ item.text }}</p>
            </article>
          </div>
        </div>

        <div class="about-photos" aria-label="甬士活动照片">
          <figure v-for="photo in aboutPhotos" :key="photo.caption">
            <img :src="photo.image" :alt="photo.caption" width="900" height="1100" loading="lazy" decoding="async" />
            <figcaption>{{ photo.caption }}</figcaption>
          </figure>
        </div>
      </div>
    </section>

    <section id="records" class="section-shell records-section">
      <div class="section-kicker">Public record</div>
      <div class="section-heading">
        <h2>有些活动留下了照片和视频。</h2>
        <p>这里放的是能对上时间和地点的记录。早期训练、山地路线、影视城剧本、外地交流，都能看见一点当时的样子。</p>
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
        <img :src="featuredRecord.image" :alt="featuredRecord.title" width="1400" height="900" loading="lazy" decoding="async" />
        <div>
          <span>{{ featuredRecord.date }}</span>
          <h3>{{ featuredRecord.title }}</h3>
          <p>{{ featuredRecord.text }}</p>
        </div>
      </article>

      <div class="record-grid">
        <article v-for="item in activityLog" :key="item.title">
          <img :src="item.image" :alt="item.title" width="900" height="675" loading="lazy" decoding="async" />
          <div>
            <span>{{ item.date }}</span>
            <h3>{{ item.title }}</h3>
            <p>{{ item.text }}</p>
          </div>
        </article>
      </div>
    </section>

    <section id="fields" class="section-shell fields-section">
      <div class="section-kicker">Fields</div>
      <div class="section-heading">
        <h2>场地决定任务怎么跑。</h2>
        <p>影视城适合街区推进，山地先看路线和通讯，夜间街区要看灯光和观摩位置。场地不同，规则、人数和节奏都会变。</p>
      </div>

      <div class="field-grid">
        <article v-for="venue in venues" :key="venue.name" :class="{ featured: venue.featured }">
          <img :src="venue.image" :alt="venue.name" width="1200" height="750" loading="lazy" decoding="async" />
          <div>
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

    <section id="activities" class="section-shell activities-section">
      <div class="section-kicker">Activities</div>
      <div class="section-heading">
        <h2>平时下场，也做剧本和远征。</h2>
        <p>周常活动用来熟悉规则和队友。影视城剧本会有角色、任务点和撤离条件。去外地交流时，先按对方场地的规则来。</p>
      </div>

      <div class="activity-board">
        <figure class="activity-photo">
          <img :src="activityFeatureImage" alt="象山海影城任务简报现场" width="1200" height="900" loading="lazy" decoding="async" />
          <figcaption>
            <span>XIANGSHAN / SCRIPT</span>
            <strong>影视城剧本先从地图、角色和任务条件开始。</strong>
          </figcaption>
        </figure>

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
        <h3>一次活动怎么展开</h3>
        <ol>
          <li v-for="step in opFlow" :key="step.title">
            <strong>{{ step.title }}</strong>
            <span>{{ step.text }}</span>
          </li>
        </ol>
      </div>
    </section>

    <section id="safe" class="section-shell safe-section">
      <div class="section-kicker">Safe ops</div>
      <div class="safe-layout">
        <div>
          <h2>规则、通讯、复盘，先立住。</h2>
          <p>
            到陌生场地，最容易出问题的不是任务本身，而是边界、口令和通讯。
            所以平时会反复练 Briefing、ROE、通讯纪律、队形和 AAR。人多的时候，现场也不能靠喊。
          </p>
          <div class="safe-grid">
            <article v-for="item in trainingLoop" :key="item.title">
              <h3>{{ item.title }}</h3>
              <p>{{ item.text }}</p>
            </article>
          </div>
        </div>
        <img :src="esaRoom" alt="室内 Briefing 现场" width="900" height="675" loading="lazy" decoding="async" />
      </div>
    </section>

    <section id="media" class="section-shell media-section">
      <div class="section-kicker">Media</div>
      <div class="section-heading">
        <h2>现场影像。</h2>
        <p>照片按场景放，公开链接单独列出。普通照片只是记录；能点开的内容，会放在下面的公开视频入口里。</p>
      </div>

      <div class="media-wall">
        <figure v-for="asset in assetStories" :key="asset.title" :class="asset.size">
          <img :src="asset.image" :alt="asset.title" width="900" height="900" loading="lazy" decoding="async" />
          <figcaption>
            <small>{{ asset.tag }}</small>
            <strong>{{ asset.title }}</strong>
            <span>{{ asset.text }}</span>
          </figcaption>
        </figure>
      </div>

      <div class="public-links">
        <a v-for="source in publicSources" :key="source.title" :href="source.href" target="_blank" rel="noreferrer">
          <span>{{ source.platform }}</span>
          <strong>{{ source.title }}</strong>
          <em>点击打开</em>
        </a>
      </div>
    </section>

    <section id="cooperate" class="section-shell cooperate-section">
      <div class="section-kicker">Work with us</div>
      <div class="cooperate-layout">
        <div>
          <h2>想试场，先小规模跑一遍。</h2>
          <p>
            如果是场地、园区、学校或品牌第一次接触，不建议一上来做大场。
            先选一段路线、一个任务、十几到几十人的规模。当天把边界、安全、观摩位置和影像范围跑清楚，再决定下一场。
          </p>
          <div class="hero-actions">
            <button type="button" class="primary-cta" @click="$emit('enter-app')">查看报名入口</button>
            <button type="button" class="secondary-cta" @click="scrollToSection('media')">先看影像</button>
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
        <figure class="partner-photo">
          <img :src="cooperateImage" alt="横店影视城交流现场" width="1200" height="900" loading="eager" decoding="async" />
          <figcaption>
            <span>TRIAL RUN / FIELD CHECK</span>
            <strong>先走一遍现场。哪里能进，哪里停下，任务点放在哪里，当场看得最清楚。</strong>
          </figcaption>
        </figure>

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
import logo from './assets/nbys-logo.png'
import haiyingcheng01 from './assets/site/haiyingcheng-jujie-01.jpg'
import haiyingcheng02 from './assets/site/haiyingcheng-jujie-02.jpg'
import haiyingcheng03 from './assets/site/haiyingcheng-jujie-03.jpg'
import haiyingcheng04 from './assets/site/haiyingcheng-jujie-04.jpg'
import haiyingcheng05 from './assets/site/haiyingcheng-jujie-05.jpg'
import yingmengli02 from './assets/site/yingmengli-02.jpg'
import yingmengliDate01 from './assets/site/yingmengli-20231022-01.jpg'
import yingmengliDate02 from './assets/site/yingmengli-20231022-02.jpg'
import xiaojiuzhaiTraining02 from './assets/site/xiaojiuzhai-training-02.jpg'
import xiaojiuzhaiEscape01 from './assets/site/xiaojiuzhai-escape-20211031-01.jpg'
import esaRoom from './assets/site/esa-urban-training-07.jpg'
import esaUrban01 from './assets/site/esa-urban-training-02.jpg'
import esaUrban02 from './assets/site/esa-urban-training-05.jpg'
import patchBoard from './assets/site/patch-board.jpg'
import nightTeam from './assets/site/night-team-2022.jpg'
import moto01 from './assets/site/moto-01.jpg'
import moto02 from './assets/site/moto-02.jpg'
import xhsHengdianTeam from './assets/site/external/2026-03-15_xhs_hengdian-expedition_team.jpg'
import xhsHengdianStreet from './assets/site/external/2026-03-15_xhs_hengdian-expedition_street.jpg'
import xhsFieldGrass from './assets/site/external/2026-05-30_xhs_ningbo-yongshi_field-day_grass.jpg'
import xhsFieldTeam from './assets/site/external/2026-05-30_xhs_ningbo-yongshi_field-day_team.jpg'
import xhsWargameCover from './assets/site/external/2026-06-22_xhs_ningbo-yongshi_wargame-cover.jpg'
import xhsWargameFrame from './assets/site/external/2026-06-22_xhs_ningbo-yongshi_wargame-frame01.jpg'
import biliJujieFinalFrame from './assets/site/external/2024-06_bilibili_xiangshan-jujie-final_frame01.jpg'

export default {
  name: 'MarketingSite',
  emits: ['enter-app'],
  data() {
    return {
      logo,
      heroImage: haiyingcheng04,
      esaRoom,
      activityFeatureImage: haiyingcheng02,
      cooperateImage: xhsHengdianStreet,
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
        { title: '玩家', text: '从周常报名开始，先跟队熟悉规则。' },
        { title: '场地', text: '先看边界、观摩区和撤离路线。' },
        { title: '学校 / 园区', text: '适合从规则课和低强度试场开始。' }
      ],
      metrics: [
        { value: '2018', label: '早期训练记录' },
        { value: '6处', label: '常用和试过的场地' },
        { value: '30周', label: '基础训练安排' },
        { value: '宁波外', label: '横店、扬州等交流' }
      ],
      aboutNotes: [
        {
          title: '玩家怎么进来',
          text: '先报名参加周常。第一次主要熟悉规则、安全距离、停止口令和队伍节奏，不急着上强度。'
        },
        {
          title: '活动怎么开始',
          text: '到场先集合，讲边界和任务，再分组进场。结束后会复盘路线、通讯和当天出过的问题。'
        },
        {
          title: '合作怎么试',
          text: '场地如果想尝试，可以先做短流程。人数、区域、观摩位置和影像发布范围，当天就能看出是否合适。'
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
        { year: '2018', title: 'ESA 城市作战训练', text: '室内简报、贴墙移动、门口队形。很多早期训练照片来自这一批。' },
        { year: '2019', title: '“巡山”户外训练', text: '山路、林线、队伍行进。B 站保留了当时的视频。' },
        { year: '2021.03.27', title: '扬州 MILSIM 镭射交流', text: '到扬州和外地队伍同场，先按对方场地规则来。' },
        { year: '2021.10.31', title: '浙东小九寨“逃离荒野”', text: '山地路线，体力、通讯和队伍间距都很吃紧。' },
        { year: '2023.10.22', title: '应梦里活动', text: '夜间街区活动，灯光和街面条件比较适合短流程。' },
        { year: '2024.06', title: '象山海影城·巨蟹行动最终章', text: '影视城街区任务，围绕建筑、巷口和任务点推进。' },
        { year: '2026.03', title: '横店远征交流', text: '横店影视城交流，留下了队伍合影、街区照片和队员记录。' }
      ],
      activityLog: [
        { date: '2026.06.22 / 小红书', title: '宁波甬士下场视频', text: '队员视角的视频切片，能看到移动、掩护和接触距离。', image: xhsWargameFrame },
        { date: '2026.05.30 / 小红书', title: '5.30 下场日记', text: '草地场景、队伍合影，还有当天的行动照片。', image: xhsFieldTeam },
        { date: '2026.03.15 / 横店', title: '横店影视城交流', text: '外地影视城场地，街区尺度和宁波常用场地不一样。', image: xhsHengdianStreet },
        { date: '2021.10.31 / 山地', title: '浙东小九寨路线', text: '山路和林线里，队伍间距和通讯很容易拉开。', image: xiaojiuzhaiEscape01 }
      ],
      venues: [
        {
          name: '象山海影城',
          type: '影视城街区',
          image: haiyingcheng03,
          featured: true,
          text: '街道、楼体、巷口都能用。剧本推进、搜索、据点攻防和夜间任务都在这里跑过。',
          points: ['巨蟹行动', '街区推进', '夜间任务']
        },
        {
          name: '应梦里',
          type: '夜间街区',
          image: yingmengliDate01,
          featured: false,
          text: '夜间灯光足，街道完整。适合短流程任务，也方便旁边的人看清队伍怎么移动。',
          points: ['夜间街区', '短流程']
        },
        {
          name: '四明山野猫湾 / 浙东小九寨',
          type: '山地路线',
          image: xiaojiuzhaiEscape01,
          featured: false,
          text: '山地活动先看路线和通讯。体力、天气、队伍间距都会影响节奏。',
          points: ['路线规划', '通讯纪律']
        },
        {
          name: '迎春里',
          type: '街区空间',
          image: moto01,
          featured: false,
          text: '街区尺度小，动线短。器材能摆开，旁边也能留出看活动的位置。',
          points: ['短流程', '器材摆放']
        },
        {
          name: '天宫庄园',
          type: '园区活动',
          image: patchBoard,
          featured: false,
          text: '边界清楚，动线容易控制。人数不大的任务，可以先从这里试。',
          points: ['边界清楚', '低强度']
        },
        {
          name: '章水中心小学',
          type: '校园训练',
          image: esaRoom,
          featured: false,
          text: '适合做规则课、队形演示、口令和安全边界讲解。强度不高，但能把基础讲透。',
          points: ['规则讲解', '基础队形']
        }
      ],
      activityTypes: [
        { code: '周常', title: '周常活动', text: '新人先跟队走一场。老队员在这里磨通讯、队形和节奏。' },
        { code: '剧本', title: '影视城剧本', text: '有阵营、任务点、行动区域和撤离条件，任务跟着场地走。' },
        { code: '山地', title: '户外任务', text: '路线、体力、通讯和天气都会改变当天的安排。' },
        { code: '远征', title: '外地交流', text: '到外地场地，先听规则，再适应新的队伍和空间。' },
        { code: '试场', title: '合作试场', text: '先跑一段短流程。边界、动线和安全区，看现场最直接。' }
      ],
      opFlow: [
        { title: '集合', text: '点人数、看装备、分组，确认场地边界。' },
        { title: 'Briefing', text: '讲规则、任务点、集合点、撤离点和停止口令。' },
        { title: '进场', text: '按阵营或班组进场，保持通讯和队形。' },
        { title: '任务', text: '搜索、占点、护送、撤离，按当天剧本走。' },
        { title: 'AAR', text: '结束后复盘安全、通讯、路线和分工。' }
      ],
      trainingLoop: [
        { title: 'ROE', text: '可进入区域、禁入区域、安全距离、停止口令，先讲清楚。' },
        { title: '通讯', text: '呼号、位置报告、异常情况，对讲机里不要抢话。' },
        { title: '队形', text: '移动时保持距离，知道谁在前、谁在后、谁负责观察。' },
        { title: 'AAR', text: '结束后复盘路线、节奏、风险点，下次再改。' }
      ],
      assetStories: [
        { tag: 'XIANGSHAN / SCRIPT', title: '巨蟹行动现场', text: '夜色下来以后，街区的距离感会完全变掉。', image: haiyingcheng04, size: 'wide' },
        { tag: 'HENGDIAN / 2026', title: '横店远征交流', text: '队伍在影视城街区前合影，装备和旗帜都在。', image: xhsHengdianTeam, size: '' },
        { tag: 'YINGMENGLI / 2023', title: '应梦里夜间活动', text: '楼上视角能看清街区、射界和移动路线。', image: yingmengli02, size: '' },
        { tag: 'MOUNTAIN / FIELD', title: '浙东小九寨训练', text: '山地里最难的不是摆姿势，是队伍不要散。', image: xiaojiuzhaiTraining02, size: '' },
        { tag: 'ESA / URBAN', title: 'ESA 城市作战训练', text: '早期室内训练，重点在门口、墙边和队形。', image: esaUrban01, size: 'tall' },
        { tag: 'TEAM / NIGHT', title: '夜间队伍照片', text: '夜间集合时拍的队伍照，光线很少，气氛很足。', image: nightTeam, size: '' },
        { tag: 'XHS / 2026', title: '5.30 下场日记', text: '草地场景，能看见队员之间的距离。', image: xhsFieldGrass, size: 'wide' },
        { tag: 'URBAN / CQB', title: '室内街区训练', text: '门口处理、墙边移动和互相掩护。', image: esaUrban02, size: '' },
        { tag: 'YINGMENGLI / 2023', title: '应梦里活动', text: '2023 年 10 月 22 日，应梦里夜间场地。', image: yingmengliDate02, size: '' },
        { tag: 'XHS / 2026', title: '6.22 队员视频', text: '队员自己发的视频封面，视角更接近日常。', image: xhsWargameCover, size: '' },
        { tag: 'MOTO', title: '机车照片', text: '有些活动路程远，机车也会出现在记录里。', image: moto02, size: '' },
        { tag: 'CANCER', title: '巨蟹行动道具', text: '地图、文件和现场道具，会让任务更容易进入状态。', image: haiyingcheng01, size: '' }
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
        { code: '01', title: '先看场地', text: '地点、时间、可进入区、禁入区和预计人数，先说清楚。' },
        { code: '02', title: '划出边界', text: '集合点、观摩区、休息区、撤离路线和负责人，当场定下来。' },
        { code: '03', title: '跑短任务', text: '30-90 分钟，先跑一个清楚的任务，不急着把流程做大。' },
        { code: '04', title: '当天复盘', text: '人数、路线、任务、照片和视频怎么发，试完再调整。' }
      ],
      partnerNotes: [
        {
          code: 'A',
          title: '哪些地方适合先试',
          points: ['有街区或楼体的影视城', '边界清楚的园区', '旁边能站人观看的夜间街区', '能留出安全区的校园或营地']
        },
        {
          code: 'B',
          title: '到场前说清楚',
          points: ['哪些地方能进，哪些地方不能进', '人从哪里集合，观摩站在哪里', '停止口令、撤离路线和联络人', '照片和视频能发到什么范围']
        },
        {
          code: 'C',
          title: '试完怎么继续',
          points: ['当天照片和可公开片段', 'AAR 复盘记录', '下一场人数、路线和任务调整']
        }
      ]
    }
  },
  methods: {
    scrollToSection(id) {
      const target = id === 'top' ? document.getElementById('top') : document.getElementById(id)
      target?.scrollIntoView({ behavior: 'smooth', block: 'start' })
    }
  }
}
</script>
