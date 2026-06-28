<template>
  <main class="ys-site">
    <header class="ys-nav" aria-label="甬士网页导航">
      <button class="ys-brand" type="button" @click="scrollToSection('top')" aria-label="回到顶部">
        <img :src="logo" alt="宁波甬士标志" />
        <span>
          <strong>宁波甬士</strong>
          <small>军事模拟推演活动</small>
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
        <h1>在真实场地里跑 wargame。</h1>
        <p class="hero-lead">
          甬士在宁波做周常下场、影视城剧本、山地任务和外地交流。
          场地边界、ROE、任务点和撤离路线先讲清楚，任务再开始。
        </p>
        <div class="hero-points" aria-label="甬士可以一起做什么">
          <span>影视城 / 园区：街区任务、夜间短流程、观摩动线</span>
          <span>学校 / 社群：规则课、低强度协作、队形演示</span>
          <span>品牌 / 内容：公开视频、现场照片、活动记录</span>
        </div>
        <div class="hero-actions">
          <button type="button" class="primary-cta" @click="scrollToSection('cooperate')">先试一场</button>
          <button type="button" class="secondary-cta" @click="$emit('enter-app')">进入报名</button>
        </div>
      </div>

      <figure class="hero-media">
        <video :src="heroVideo" autoplay muted loop playsinline preload="metadata"></video>
        <figcaption>
          <span>2024 / 象山海影城</span>
          <strong>巨蟹行动最终章</strong>
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
            影视城看街区和楼体，山地看路线和通讯，夜间街区看灯光和观摩位置。
            空间能不能跑、哪里不能进、撤离往哪走，这些先确认。
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
        <h2>这些年留下的记录。</h2>
        <p>B 站视频、小红书笔记、本地照片分开放。能点开的链接放在后面，普通图片就按照片看。</p>
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
        <h2>不同场地，不同跑法。</h2>
        <p>影视城、夜间街区、山地、园区、校园，强度和流程不一样。能不能观摩、怎么集合、哪里停，都要按场地来。</p>
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
        <h2>周常、剧本、远征分开做。</h2>
        <p>有的活动适合新人熟悉规则，有的活动适合跑剧情和阵营任务，也有外地交流和合作试场。</p>
      </div>

      <div class="activity-board">
        <figure class="activity-photo">
          <img :src="activityFeatureImage" alt="象山海影城任务现场" width="1200" height="900" loading="lazy" decoding="async" />
          <figcaption>
            <span>XIANGSHAN / SCRIPT</span>
            <strong>影视城剧本会提前讲清任务点、集合点和撤离条件。</strong>
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
            到陌生场地，最怕边界不清、口令听不懂、对讲机乱成一团。
            所以平时会练 ROE、Briefing、通讯纪律、队形和 AAR。
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
        <h2>照片是照片，链接是链接。</h2>
        <p>影像墙放现场照片。B 站视频和小红书公开笔记放在下方链接区，能点开的地方会写清楚。</p>
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
            第一次合作不用把场面做大。先选一段路线、一个任务、十几到几十人的规模，
            把边界、安全、观摩、影像范围跑明白，再决定下一场怎么放大。
          </p>
          <div class="hero-actions">
            <button type="button" class="primary-cta" @click="$emit('enter-app')">查看报名入口</button>
            <button type="button" class="secondary-cta" @click="scrollToSection('media')">先看影像</button>
          </div>
        </div>
        <div class="trial-list">
          <article v-for="item in trialSteps" :key="item.title">
            <span>{{ item.code }}</span>
            <h3>{{ item.title }}</h3>
            <p>{{ item.text }}</p>
          </article>
        </div>
      </div>

      <div class="partner-board">
        <figure class="partner-photo">
          <img :src="cooperateImage" alt="应梦里夜间活动现场" width="1200" height="900" loading="eager" decoding="async" />
          <figcaption>
            <span>TRIAL RUN / FIELD CHECK</span>
            <strong>夜间街区、影视城、园区和校园，先看可进入范围，再决定任务。</strong>
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
import heroVideo from './assets/site/jujie-final-2024-haiyingcheng.mp4'
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
      heroVideo,
      esaRoom,
      activityFeatureImage: haiyingcheng05,
      cooperateImage: yingmengliDate02,
      navItems: [
        { id: 'about', label: '关于' },
        { id: 'records', label: '记录' },
        { id: 'fields', label: '场地' },
        { id: 'activities', label: '活动' },
        { id: 'safe', label: '保障' },
        { id: 'media', label: '影像' },
        { id: 'cooperate', label: '合作' }
      ],
      metrics: [
        { value: '2018+', label: '活动影像' },
        { value: '6+', label: '跑过的场地' },
        { value: '30周', label: '训练教案' },
        { value: '多城', label: '外地交流' }
      ],
      aboutNotes: [
        {
          title: '周常',
          text: '集合、讲规则、分组、跑局、复盘。新人先跟一场，知道安全距离和停止口令。'
        },
        {
          title: '场地',
          text: '影视城看街区和楼体，山地看路线和通讯，园区、校园看边界和观摩位置。'
        },
        {
          title: '试场',
          text: '先用小规模人数跑一遍。动线、安全、节奏、影像发布范围，都现场确认。'
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
        { year: '2018', title: 'ESA 城市作战训练', text: '室内简报、贴墙移动、门口队形。早期训练照片主要来自这一批。' },
        { year: '2019', title: '“巡山”户外训练', text: '山路、林线、队伍行进。B 站还有公开视频。' },
        { year: '2021.03.27', title: '扬州 MILSIM 镭射交流', text: '到扬州和外地队伍同场交流，按对方场地规则跑。' },
        { year: '2021.10.31', title: '浙东小九寨“逃离荒野”', text: '山地路线，重点是体力、通讯、队伍间距。' },
        { year: '2023.10.22', title: '应梦里活动', text: '夜间街区，灯光、店铺外立面和观摩位置都比较清楚。' },
        { year: '2024.06', title: '象山海影城·巨蟹行动最终章', text: '影视城街区任务，围绕建筑、巷口和任务点推进。' },
        { year: '2026.03', title: '横店远征交流', text: '横店影视城交流，有队伍合影、街区照片和队员笔记。' }
      ],
      activityLog: [
        { date: '2026.06.22 / 小红书', title: '宁波甬士下场视频', text: '队员视角的视频切片，能看到一次下场的移动和接触距离。', image: xhsWargameFrame },
        { date: '2026.05.30 / 小红书', title: '5.30 下场日记', text: '草地场景、队伍合影、现场动作。', image: xhsFieldTeam },
        { date: '2026.03.15 / 横店', title: '横店影视城交流', text: '外地影视城场地，和其他队伍同场。', image: xhsHengdianStreet },
        { date: '2024.06 / 象山', title: '巨蟹行动最终章', text: '象山海影城街区任务，B 站有公开视频。', image: biliJujieFinalFrame }
      ],
      venues: [
        {
          name: '象山海影城',
          type: '影视城街区',
          image: haiyingcheng03,
          featured: true,
          text: '街道、楼体、巷口都能用。适合剧本推进、搜索、据点攻防和夜间任务。',
          points: ['巨蟹行动', '街区推进', '夜间任务']
        },
        {
          name: '应梦里',
          type: '夜间街区',
          image: yingmengliDate01,
          featured: false,
          text: '夜间灯光足，街道完整，适合轻剧本、观摩和短流程任务。',
          points: ['夜间街区', '观摩友好']
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
          text: '街区尺度小，适合短流程体验、器材摆放、公众观摩和内容拍摄。',
          points: ['轻量任务', '器材摆放']
        },
        {
          name: '天宫庄园',
          type: '园区活动',
          image: patchBoard,
          featured: false,
          text: '边界清楚，动线容易控制。适合团建、研学和低强度任务。',
          points: ['团建', '研学']
        },
        {
          name: '章水中心小学',
          type: '校园训练',
          image: esaRoom,
          featured: false,
          text: '适合低强度规则课、队形演示、口令和安全边界讲解。',
          points: ['规则讲解', '基础协作']
        }
      ],
      activityTypes: [
        { code: '周常', title: '周常活动', text: '新人从这里熟悉规则。老队员在这里磨通讯、队形和节奏。' },
        { code: '剧本', title: '影视城剧本', text: '有阵营、任务点、行动区域和撤离条件，按场地路线推进。' },
        { code: '山地', title: '户外任务', text: '路线、体力、通讯和天气都会影响当天安排。' },
        { code: '远征', title: '外地交流', text: '到外地场地，重新适应规则、队伍和空间。' },
        { code: '试场', title: '合作试场', text: '用小规模人数先验证边界、动线、安全和影像记录。' }
      ],
      opFlow: [
        { title: '集合', text: '点人数、看装备、分组，确认场地边界。' },
        { title: 'Briefing', text: '讲规则、任务点、集合点、撤离点和停止口令。' },
        { title: '进场', text: '按阵营或班组进场，保持通讯和队形。' },
        { title: '任务', text: '搜索、占点、护送、撤离，按当天任务走。' },
        { title: 'AAR', text: '结束后复盘安全、通讯、路线和分工。' }
      ],
      trainingLoop: [
        { title: 'ROE', text: '可进入区域、禁入区域、安全距离、停止口令，先讲清楚。' },
        { title: '通讯', text: '呼号、位置报告、异常情况，对讲机里不要抢话。' },
        { title: '队形', text: '移动时保持距离，知道谁在前、谁在后、谁负责观察。' },
        { title: 'AAR', text: '结束后复盘路线、节奏、风险点和下次调整。' }
      ],
      assetStories: [
        { tag: 'XIANGSHAN / SCRIPT', title: '巨蟹行动现场', text: '队伍沿象山海影城街区推进。', image: haiyingcheng04, size: 'wide' },
        { tag: 'HENGDIAN / 2026', title: '横店远征交流', text: '2026 年 3 月横店影视城交流。', image: xhsHengdianTeam, size: '' },
        { tag: 'YINGMENGLI / 2023', title: '应梦里夜间活动', text: '2023 年 10 月 22 日应梦里活动。', image: yingmengli02, size: '' },
        { tag: 'MOUNTAIN / FIELD', title: '浙东小九寨训练', text: '山地路线和通讯训练。', image: xiaojiuzhaiTraining02, size: '' },
        { tag: 'ESA / URBAN', title: 'ESA 城市作战训练', text: '2018 年城市作战训练照片。', image: esaUrban01, size: 'tall' },
        { tag: 'TEAM / NIGHT', title: '夜间队伍照片', text: '夜间集合和队伍合影。', image: nightTeam, size: '' },
        { tag: 'XHS / 2026', title: '5.30 下场日记', text: '队员在草地场景中行动。', image: xhsFieldGrass, size: 'wide' },
        { tag: 'URBAN / CQB', title: '室内街区训练', text: '门口、墙面和队形配合。', image: esaUrban02, size: '' },
        { tag: 'YINGMENGLI / 2023', title: '应梦里活动', text: '应梦里 2023 年 10 月 22 日活动现场。', image: yingmengliDate02, size: '' },
        { tag: 'XHS / 2026', title: '6.22 队员视频', text: '小红书队员视角视频切片。', image: xhsWargameCover, size: '' },
        { tag: 'MOTO', title: '机车照片', text: '外出活动中的机车照片。', image: moto02, size: '' },
        { tag: 'CANCER', title: '巨蟹行动道具', text: '活动道具和现场任务资料。', image: haiyingcheng01, size: '' }
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
        { code: '01', title: '发场地信息', text: '提供地点、可用时间、可进入区域、禁入区域和预计人数。' },
        { code: '02', title: '看边界和动线', text: '确认集合点、观摩区、休息区、撤离路线和现场负责人。' },
        { code: '03', title: '设计短流程', text: '先做 30-90 分钟的小规模任务，验证规则、安全和节奏。' },
        { code: '04', title: '复盘后调整', text: '根据当天情况调整人数、路线、任务和影像发布范围。' }
      ],
      partnerNotes: [
        {
          code: 'A',
          title: '适合先试的场景',
          points: ['影视城街区任务', '园区团建或研学', '夜间街区观摩', '小规模内容拍摄']
        },
        {
          code: 'B',
          title: '到场前先确认',
          points: ['可进入区和禁入区', '集合点、休息区、观摩区', '撤离路线和停止口令', '现场负责人和联络方式']
        },
        {
          code: 'C',
          title: '试完留下什么',
          points: ['当天照片和可公开视频', 'AAR 复盘记录', '下一场人数、路线和任务调整']
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
