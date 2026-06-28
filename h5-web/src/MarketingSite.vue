<template>
  <main class="ys-site" :class="`view-${activeView}`">
    <div class="site-grain" aria-hidden="true"></div>
    <div class="site-scanline" aria-hidden="true"></div>

    <header class="ys-nav" aria-label="宁波甬士组织展示站导航">
      <button class="ys-brand" type="button" @click="setView('overview')" aria-label="返回宁波甬士概览">
        <img :src="logo" alt="宁波甬士 NBYS 标志" />
        <span>
          <strong>宁波甬士</strong>
          <small>NBYS / Tactical Simulation Collective</small>
        </span>
      </button>

      <nav class="ys-links" aria-label="频道目录">
        <button
          v-for="item in navItems"
          :key="item.key"
          type="button"
          :class="{ active: activeView === item.key }"
          @click="setView(item.key)"
        >
          <span>{{ item.cn }}</span>
          <em>{{ item.en }}</em>
        </button>
      </nav>

      <button class="action-btn" type="button" @click="$emit('enter-app')">
        <span>一起行动</span>
        <em>JOIN THE OP</em>
      </button>
    </header>

    <section class="hero section-frame" :class="{ compact: activeView !== 'overview' }" aria-label="宁波甬士组织简介">
      <div class="hero-map" aria-hidden="true">
        <span class="grid-dot dot-a"></span>
        <span class="grid-dot dot-b"></span>
        <span class="grid-dot dot-c"></span>
      </div>

      <div class="hero-copy">
        <p class="eyebrow">PUBLIC BRIEFING / NINGBO / MILSIM & FIELD OPS</p>
        <h1>
          <span v-for="line in activeHero.lines" :key="line">{{ line }}</span>
        </h1>
        <p class="hero-lead">{{ activeHero.lead }}</p>
        <div class="hero-actions">
          <button type="button" class="primary-cta" @click="$emit('enter-app')">一起行动</button>
          <button type="button" class="ghost-cta" @click="setView('evidence')">看看过往活动</button>
        </div>
      </div>

      <aside class="hero-media" aria-label="巨蟹行动视频预览">
        <video :src="heroVideo" autoplay muted loop playsinline preload="metadata"></video>
        <div class="media-overlay">
          <span>OPERATION CANCER / 2024</span>
          <strong>象山海影城 · 巨蟹行动最终章</strong>
          <small>真实场地 / 夜间行动 / 阵营任务</small>
        </div>
      </aside>
    </section>

    <section v-if="activeView === 'overview'" class="mission-strip section-frame" aria-label="甬士活动摘要">
      <article v-for="metric in metrics" :key="metric.label">
        <strong>{{ metric.value }}</strong>
        <span>{{ metric.label }}</span>
        <em>{{ metric.en }}</em>
      </article>
    </section>

    <section class="workbench section-frame" aria-label="频道内容">
      <aside class="channel-rail" aria-label="频道切换">
        <p>CHANNEL INDEX</p>
        <button
          v-for="item in navItems"
          :key="`rail-${item.key}`"
          type="button"
          :class="{ active: activeView === item.key }"
          @click="setView(item.key)"
        >
          <span>{{ item.cn }}</span>
          <em>{{ item.en }}</em>
        </button>
      </aside>

      <Transition name="panel-swap" mode="out-in">
        <section :key="activeView" class="panel-stage" :aria-label="activePanel.title">
          <div class="panel-head">
            <p>{{ activePanel.code }}</p>
            <h2>{{ activePanel.title }}</h2>
            <span>{{ activePanel.subtitle }}</span>
          </div>

          <div v-if="activeView === 'overview'" class="overview-panel">
            <div class="manifesto">
              <p class="eyebrow">WHO WE ARE</p>
              <h3>甬士做军事模拟推演活动：集合、简报、分组、进场、任务推进、复盘。</h3>
              <p>
                场地可能是影视城街区、夜间街区、山地路线，也可能是学校、园区和开放街区。
                每次活动都会先讲规则、人数、边界和当天任务。
                新人可以先从观摩和周常开始，熟悉以后再参加远征或剧本行动。
              </p>
            </div>
            <div class="overview-gallery">
              <figure>
                <img :src="haiyingcheng03" alt="象山海影城巨蟹行动现场" />
                <figcaption>剧本行动 / 象山海影城</figcaption>
              </figure>
              <figure>
                <img :src="hengdian01" alt="横店影视城远征交流活动" />
                <figcaption>远征交流 / 横店影视城</figcaption>
              </figure>
              <figure>
                <img :src="xiaojiuzhaiTraining01" alt="浙东小九寨训练现场" />
                <figcaption>山地训练 / 浙东小九寨</figcaption>
              </figure>
            </div>
            <div class="activity-log">
              <article v-for="item in activityLog" :key="item.title">
                <img :src="item.image" :alt="item.title" />
                <div>
                  <span>{{ item.date }}</span>
                  <h4>{{ item.title }}</h4>
                  <p>{{ item.text }}</p>
                </div>
              </article>
            </div>
            <div class="quick-gallery">
              <figure v-for="photo in quickGallery" :key="photo.caption">
                <img :src="photo.image" :alt="photo.caption" />
                <figcaption>{{ photo.caption }}</figcaption>
              </figure>
            </div>
          </div>

          <div v-else-if="activeView === 'evidence'" class="evidence-panel">
            <div class="timeline">
              <article v-for="item in history" :key="item.year">
                <time>{{ item.year }}</time>
                <div>
                  <h3>{{ item.title }}</h3>
                  <p>{{ item.text }}</p>
                </div>
              </article>
            </div>
            <div class="source-grid">
              <a
                v-for="source in publicSources"
                :key="source.title"
                :href="source.href"
                target="_blank"
                rel="noreferrer"
                class="source-card"
              >
                <img :src="source.image" :alt="source.title" />
                <div>
                  <span>{{ source.platform }}</span>
                  <h3>{{ source.title }}</h3>
                  <p>{{ source.text }}</p>
                </div>
              </a>
            </div>
            <div class="evidence-note-grid">
              <article v-for="note in evidenceNotes" :key="note.title">
                <span>{{ note.kicker }}</span>
                <h3>{{ note.title }}</h3>
                <p>{{ note.text }}</p>
              </article>
            </div>
          </div>

          <div v-else-if="activeView === 'venues'" class="venues-panel">
            <div class="venue-grid">
              <article
                v-for="venue in venues"
                :key="venue.name"
                class="venue-card"
                :class="{ featured: venue.featured }"
              >
                <img :src="venue.image" :alt="venue.name + ' 活动照片'" />
                <div>
                  <small>{{ venue.type }}</small>
                  <h3>{{ venue.name }}</h3>
                  <p>{{ venue.text }}</p>
                  <ul>
                    <li v-for="point in venue.points" :key="point">{{ point }}</li>
                  </ul>
                  <span class="asset-note">{{ venue.note }}</span>
                </div>
              </article>
            </div>
            <div class="venue-albums">
              <article v-for="album in venueAlbums" :key="album.name">
                <div class="album-head">
                  <span>{{ album.code }}</span>
                  <h3>{{ album.name }}</h3>
                  <p>{{ album.text }}</p>
                </div>
                <div class="album-photos">
                  <figure v-for="photo in album.photos" :key="photo.caption">
                    <img :src="photo.image" :alt="photo.caption" />
                    <figcaption>{{ photo.caption }}</figcaption>
                  </figure>
                </div>
              </article>
            </div>
          </div>

          <div v-else-if="activeView === 'ops'" class="ops-panel">
            <div class="ops-grid">
              <article v-for="item in activityTypes" :key="item.title">
                <span>{{ item.code }}</span>
                <h3>{{ item.title }}</h3>
                <p>{{ item.text }}</p>
                <ul>
                  <li v-for="point in item.points" :key="point">{{ point }}</li>
                </ul>
              </article>
            </div>

            <div class="op-flow">
              <div class="board-title">
                <p>HOW AN OP RUNS</p>
                <h3>一次活动怎么展开</h3>
              </div>
              <article v-for="step in opFlow" :key="step.code">
                <span>{{ step.code }}</span>
                <h4>{{ step.title }}</h4>
                <p>{{ step.text }}</p>
              </article>
            </div>

            <div class="record-table">
              <div class="board-title">
                <p>ACTIVITY RECORDS</p>
                <h3>活动记录表</h3>
              </div>
              <article v-for="record in activityRecords" :key="record.name">
                <time>{{ record.time }}</time>
                <strong>{{ record.place }}</strong>
                <span>{{ record.type }}</span>
                <p>{{ record.name }}</p>
                <em>{{ record.note }}</em>
              </article>
            </div>

            <div class="expedition-board">
              <div class="board-title">
                <p>EXPEDITION EXCHANGE LOG</p>
                <h3>远征交流记录</h3>
              </div>
              <article v-for="item in expeditions" :key="item.title">
                <img :src="item.image" :alt="item.title" />
                <div>
                  <time>{{ item.time }}</time>
                  <h4>{{ item.title }}</h4>
                  <p>{{ item.text }}</p>
                </div>
              </article>
            </div>

            <div class="member-notes">
              <div class="board-title">
                <p>MEMBER NOTES / XIAOHONGSHU</p>
                <h3>队员视角</h3>
              </div>
              <a v-for="note in memberNotes" :key="note.title" :href="note.href" target="_blank" rel="noreferrer">
                <img :src="note.image" :alt="note.title" />
                <div>
                  <span>{{ note.date }} · {{ note.author }}</span>
                  <h4>{{ note.title }}</h4>
                  <p>{{ note.text }}</p>
                </div>
              </a>
            </div>

            <div class="script-feature">
              <video :src="fieldRecordVideo" autoplay muted loop playsinline preload="metadata"></video>
              <div>
                <span>IMMERSIVE SCRIPT</span>
                <h3>巨蟹行动：在影视城里推进一场夜间任务</h3>
                <p>
                  队伍按阵营进入街区，沿预设路线推进。
                  当天会有任务点、集合点、撤离点，也会根据现场情况调整节奏。
                  影视城的街道、门洞、楼体和灯光，会直接影响观察、隐蔽和移动。
                </p>
              </div>
            </div>
            <div class="ops-scenes">
              <article v-for="scene in opsScenes" :key="scene.title">
                <img :src="scene.image" :alt="scene.title" />
                <div>
                  <span>{{ scene.tag }}</span>
                  <h4>{{ scene.title }}</h4>
                  <p>{{ scene.text }}</p>
                </div>
              </article>
            </div>
          </div>

          <div v-else-if="activeView === 'doctrine'" class="doctrine-panel">
            <div class="doctrine-hero">
              <div>
                <p class="eyebrow">TRAINING DOCTRINE / INTERNAL SYSTEM</p>
                <h3>训练解决现场最基本的问题：听得懂指令，知道边界，能和队友配合。</h3>
                <p>
                  内容包括安全规则、口令、通讯、队形、CQB 基础、任务简报和 AAR 复盘。
                  新人先学规则和安全，再跟着队伍参加低强度活动。
                  老队员会继续练通讯、移动、掩护、指挥和复盘。
                </p>
              </div>
              <img :src="esaUrban01" alt="甬士城市作战训练现场" />
            </div>

            <div class="training-loop">
              <article v-for="step in trainingLoop" :key="step.title">
                <span>{{ step.code }}</span>
                <h4>{{ step.title }}</h4>
                <p>{{ step.text }}</p>
              </article>
            </div>

            <div class="training-scenes">
              <figure v-for="scene in trainingScenes" :key="scene.caption">
                <img :src="scene.image" :alt="scene.caption" />
                <figcaption>{{ scene.caption }}</figcaption>
              </figure>
            </div>

            <div class="phase-grid">
              <article v-for="phase in phases" :key="phase.code">
                <span>{{ phase.code }}</span>
                <h4>{{ phase.name }}</h4>
                <p>{{ phase.weeks }}</p>
              </article>
            </div>

            <div class="doctrine-vault">
              <article v-for="doc in doctrineDocs" :key="doc.name">
                <span>{{ doc.type }}</span>
                <h4>{{ doc.name }}</h4>
                <p>{{ doc.text }}</p>
              </article>
            </div>
          </div>

          <div v-else-if="activeView === 'media'" class="media-panel">
            <div class="media-stage">
              <article v-for="clip in mediaClips" :key="clip.title">
                <img :src="clip.image" :alt="clip.title" />
                <div>
                  <span>{{ clip.tag }}</span>
                  <h3>{{ clip.title }}</h3>
                  <p>{{ clip.text }}</p>
                </div>
              </article>
            </div>
            <div class="media-source-note">
              <article>
                <span>LOCAL PHOTOS</span>
                <strong>本地照片</strong>
                <p>训练、场地、活动现场照片。只展示，不跳转。</p>
              </article>
              <article>
                <span>MEMBER NOTES</span>
                <strong>小红书队员笔记</strong>
                <p>卡片右上角标注“点击打开”的，可以跳到公开笔记。</p>
              </article>
              <article>
                <span>PUBLIC VIDEOS</span>
                <strong>B 站公开视频</strong>
                <p>公开视频统一列出，点击后跳转到 B 站。</p>
              </article>
            </div>

            <div class="asset-wall">
              <figure v-for="asset in assetStories" :key="asset.title" :class="asset.size">
                <img :src="asset.image" :alt="asset.title" />
                <figcaption>
                  <small>{{ asset.tag }}</small>
                  <strong>{{ asset.title }}</strong>
                  <span>{{ asset.text }}</span>
                </figcaption>
              </figure>
            </div>
            <div class="public-link-board">
              <div class="board-title">
                <p>PUBLIC LINKS</p>
                <h3>公开视频和公开笔记</h3>
              </div>
              <a v-for="source in publicSources" :key="`media-${source.title}`" :href="source.href" target="_blank" rel="noreferrer">
                <span>{{ source.platform }}</span>
                <strong>{{ source.title }}</strong>
                <em>点击打开</em>
              </a>
            </div>
          </div>

          <div v-else class="partners-panel">
            <div class="partner-brief">
              <p class="eyebrow">PARTNER BRIEFING</p>
              <h3>有场地、活动计划或拍摄需求，可以先做一次小规模试场。</h3>
              <p>
                先确认人数、可进入区域、禁入区域、集合点、休息区、撤离路线和现场负责人。
                这些条件能对上，再安排分组、简报、任务和影像记录。
              </p>
              <div class="partner-actions">
                <button type="button" class="primary-cta" @click="$emit('enter-app')">进入报名系统</button>
                <button type="button" class="ghost-cta" @click="setView('media')">先看现场照片</button>
              </div>
            </div>
            <div class="partner-grid">
              <article v-for="item in partnerModes" :key="item.title">
                <span>{{ item.code }}</span>
                <h4>{{ item.title }}</h4>
                <p>{{ item.text }}</p>
              </article>
            </div>
            <div class="partner-checklist">
              <div class="board-title">
                <p>BEFORE WE START</p>
                <h3>试做前先确认这些</h3>
              </div>
              <article v-for="item in partnerChecklist" :key="item.title">
                <span>{{ item.code }}</span>
                <h4>{{ item.title }}</h4>
                <p>{{ item.text }}</p>
              </article>
            </div>
          </div>
        </section>
      </Transition>
    </section>
  </main>
</template>

<script>
import logo from './assets/nbys-logo.png'
import heroVideo from './assets/site/jujie-final-2024-haiyingcheng.mp4'
import fieldRecordVideo from './assets/site/jujie-field-record.mp4'
import trailerVideo from './assets/site/jujie-trailer.mp4'
import haiyingcheng01 from './assets/site/haiyingcheng-jujie-01.jpg'
import haiyingcheng02 from './assets/site/haiyingcheng-jujie-02.jpg'
import haiyingcheng03 from './assets/site/haiyingcheng-jujie-03.jpg'
import haiyingcheng04 from './assets/site/haiyingcheng-jujie-04.jpg'
import haiyingcheng05 from './assets/site/haiyingcheng-jujie-05.jpg'
import hengdian01 from './assets/site/hengdian-01.jpg'
import hengdian02 from './assets/site/hengdian-02.jpg'
import hengdian03 from './assets/site/hengdian-03.jpg'
import hengdian04 from './assets/site/hengdian-04.jpg'
import hengdian05 from './assets/site/hengdian-05.jpg'
import yingmengli01 from './assets/site/yingmengli-01.jpg'
import yingmengli02 from './assets/site/yingmengli-02.jpg'
import yingmengliDate01 from './assets/site/yingmengli-20231022-01.jpg'
import yingmengliDate02 from './assets/site/yingmengli-20231022-02.jpg'
import yingmengliDate03 from './assets/site/yingmengli-20231022-03.jpg'
import xiaojiuzhaiTraining01 from './assets/site/xiaojiuzhai-training-01.jpg'
import xiaojiuzhaiTraining02 from './assets/site/xiaojiuzhai-training-02.jpg'
import xiaojiuzhaiTraining03 from './assets/site/xiaojiuzhai-training-03.jpg'
import xiaojiuzhaiEscape01 from './assets/site/xiaojiuzhai-escape-20211031-01.jpg'
import xiaojiuzhaiEscape02 from './assets/site/xiaojiuzhai-escape-20211031-02.jpg'
import xiaojiuzhaiEscape03 from './assets/site/xiaojiuzhai-escape-20211031-03.jpg'
import esaRoom from './assets/site/esa-urban-training-07.jpg'
import esaUrban01 from './assets/site/esa-urban-training-02.jpg'
import esaUrban02 from './assets/site/esa-urban-training-05.jpg'
import esaUrban03 from './assets/site/esa-urban-training-03.jpg'
import esaUrban04 from './assets/site/esa-urban-training-04.jpg'
import patchBoard from './assets/site/patch-board.jpg'
import nightTeam from './assets/site/night-team-2022.jpg'
import moto01 from './assets/site/moto-01.jpg'
import moto02 from './assets/site/moto-02.jpg'
import biliXunshan from './assets/site/bili-xunshan-2019.jpg'
import biliYangzhou from './assets/site/bili-yangzhou-20210327.png'
import biliJujieFinal from './assets/site/bili-jujie-final-2024.jpg'
import biliJujiePreview from './assets/site/bili-jujie-preview.jpg'
import xhsHengdianTeam from './assets/site/external/2026-03-15_xhs_hengdian-expedition_team.jpg'
import xhsHengdianStreet from './assets/site/external/2026-03-15_xhs_hengdian-expedition_street.jpg'
import xhsFieldGrass from './assets/site/external/2026-05-30_xhs_ningbo-yongshi_field-day_grass.jpg'
import xhsFieldTeam from './assets/site/external/2026-05-30_xhs_ningbo-yongshi_field-day_team.jpg'
import xhsWargameCover from './assets/site/external/2026-06-22_xhs_ningbo-yongshi_wargame-cover.jpg'
import xhsWargameFrame from './assets/site/external/2026-06-22_xhs_ningbo-yongshi_wargame-frame01.jpg'
import biliJujieFieldFrame from './assets/site/external/2024-06_bilibili_xiangshan-jujie-field_frame01.jpg'
import biliJujieFinalFrame from './assets/site/external/2024-06_bilibili_xiangshan-jujie-final_frame01.jpg'

const viewKeys = ['overview', 'evidence', 'venues', 'ops', 'doctrine', 'media', 'partners']

function hashToView() {
  const raw = window.location.hash.replace(/^#\/?/, '')
  return viewKeys.includes(raw) ? raw : 'overview'
}

export default {
  name: 'MarketingSite',
  emits: ['enter-app'],
  data() {
    return {
      logo,
      heroVideo,
      fieldRecordVideo,
      trailerVideo,
      haiyingcheng01,
      hengdian01,
      xiaojiuzhaiTraining01,
      patchBoard,
      activeView: hashToView(),
      navItems: [
        { key: 'overview', cn: '概览', en: 'Overview' },
        { key: 'evidence', cn: '脉络', en: 'Public Trace' },
        { key: 'venues', cn: '场地', en: 'Field Network' },
        { key: 'ops', cn: '活动', en: 'Operations' },
        { key: 'doctrine', cn: '训练', en: 'Doctrine' },
        { key: 'media', cn: '影像', en: 'Media Wall' },
        { key: 'partners', cn: '合作', en: 'Partner Desk' }
      ],
      panels: {
        overview: {
          code: '00 / ABOUT NBYS',
          title: '宁波本地的军事模拟推演活动组织。',
          subtitle: '周常活动、山地训练、影视城剧本、外地交流，都有照片和公开视频。'
        },
        evidence: {
          code: '01 / FIELD NOTES',
          title: '按时间看，能看到甬士这些年去过哪里、做过什么。',
          subtitle: '2018 训练照片、2019 户外视频、2021 扬州、2024 象山、2026 横店。'
        },
        venues: {
          code: '02 / FIELD NETWORK',
          title: '不同场地，对应不同玩法。',
          subtitle: '影视城做剧本推进，山地做路线和通讯，园区、街区、校园做轻体验和训练。'
        },
        ops: {
          code: '03 / OPERATION PORTFOLIO',
          title: '活动按流程走：简报、分组、进场、任务、复盘。',
          subtitle: '周常、远征、剧本活动都从集合和简报开始，区别在场地、人数和当天任务。'
        },
        doctrine: {
          code: '04 / TRAINING DOCTRINE',
          title: '训练内容按规则、安全、通讯、队形、CQB 和复盘拆开。',
          subtitle: '新人先学 ROE 和安全边界，老队员继续练通讯、移动、掩护和 AAR。'
        },
        media: {
          code: '05 / MEDIA WALL',
          title: '训练、场地、远征和队员视角。',
          subtitle: '本地照片、小红书队员笔记和 B 站公开视频分开放。'
        },
        partners: {
          code: '06 / PARTNER DESK',
          title: '有场地、活动或拍摄计划，可以先做一次小规模试场。',
          subtitle: '先确认人数、边界、动线、安全、时长和影像记录，再决定是否扩大。'
        }
      },
      heroByView: {
        overview: {
          lines: ['宁波甬士', '军事模拟', '推演活动。'],
          lead: '我们在宁波组织 wargame、Milsim、剧本任务和训练活动。参加前会讲规则和安全，进场后按阵营、任务和路线推进，结束后复盘。'
        },
        evidence: {
          lines: ['从山地', '到影视城', '一路留下', '记录。'],
          lead: '这里按时间放公开记录：2019 年“巡山”户外训练、2021 年扬州 MILSIM 镭射、2024 年象山海影城巨蟹行动、2026 年横店远征。'
        },
        venues: {
          lines: ['场地一换', '玩法就变。'],
          lead: '象山海影城有街区和楼体，应梦里有夜间街区，浙东小九寨有山地路线。迎春里、天宫庄园、章水中心小学这类空间，可以安排短流程体验、展示和训练。'
        },
        ops: {
          lines: ['周常', '远征', '剧本。'],
          lead: '周常主要是集合、分组、跑局和复盘；远征是去外地同场交流；剧本行动会提前设定阵营、任务、路线和撤离条件。'
        },
        doctrine: {
          lines: ['会玩', '也要会收。'],
          lead: '训练先处理安全、口令、通讯、队形和复盘。新人从规则和低强度活动开始，再逐步进入通讯、队形和任务协同。'
        },
        media: {
          lines: ['先看现场。'],
          lead: '训练室、影视城、山地、夜间活动、横店远征和队员笔记按来源分开放。能点开的公开链接单独标出。'
        },
        partners: {
          lines: ['想一起做', '就来聊。'],
          lead: '景区、街区、园区、学校、品牌活动、内容拍摄和企业团建，都可以先从小规模活动试起。先确认场地能不能跑、哪里不能进、现场谁负责、照片和视频怎么留。'
        }
      },
      metrics: [
        { value: '2018+', label: '训练照片可追溯', en: 'Training Photos' },
        { value: '6+', label: '常用活动场地', en: 'Field Network' },
        { value: '30周', label: '训练大纲周期', en: 'Training Cycle' },
        { value: '多城', label: '远征交流半径', en: 'Expedition' }
      ],
      history: [
        { year: '2018', title: 'ESA 城市作战训练', text: '室内训练、街区移动、队形照片，记录的是一批早期训练画面。' },
        { year: '2019', title: '“巡山”行动', text: 'B 站保留了这支户外训练视频。画面里有山路、林线和队伍行进。' },
        { year: '2021.03.27', title: '扬州 MILSIM 镭射交流', text: '甬士到扬州参加 MILSIM 镭射交流。公开记录里能看到跨城同场活动。' },
        { year: '2021.10.31', title: '浙东小九寨“逃离荒野”', text: '户外山地活动，路线、体力、通讯和队伍间距都会影响当天结果。' },
        { year: '2023.10.22', title: '应梦里活动', text: '活动在夜间街区展开。照片里能看到灯光、街道和队员移动。' },
        { year: '2024.06', title: '象山海影城·巨蟹行动最终章', text: '巨蟹行动在象山海影城进行。街道、建筑和路线构成了当天的任务场。' },
        { year: '2026.03', title: '横店远征交流', text: '横店影视城交流，留下了队伍合影、街区行进照片和队员笔记。' }
      ],
      publicSources: [
        {
          platform: 'BILIBILI / 2019',
          title: '“巡山”行动——户外训练',
          text: '早期山地户外训练公开视频。',
          href: 'https://www.bilibili.com/video/BV1hb411s7zL',
          image: biliXunshan
        },
        {
          platform: 'BILIBILI / 2021',
          title: '2021.03.27 扬州 MILSIM 镭射',
          text: '2021 年跨城交流记录。',
          href: 'https://www.bilibili.com/video/BV1SZ4y1c74a',
          image: biliYangzhou
        },
        {
          platform: 'BILIBILI / 2024',
          title: '2024 巨蟹行动最终章',
          text: '象山海影城实景推演公开视频。',
          href: 'https://www.bilibili.com/video/BV1tr42177Yo',
          image: biliJujieFinalFrame
        },
        {
          platform: 'BILIBILI / PREVIEW',
          title: '巨蟹行动活动预告',
          text: '巨蟹行动筹备期留下的预告片。',
          href: 'https://www.bilibili.com/video/BV11a411W7ci',
          image: biliJujiePreview
        },
        {
          platform: 'XIAOHONGSHU / 2026',
          title: '甬士横店征途',
          text: '横店交流公开笔记。',
          href: 'https://www.xiaohongshu.com/search_result/69b587e1000000001e00cc1b',
          image: xhsHengdianTeam
        },
        {
          platform: 'XIAOHONGSHU / 2026',
          title: '甬士横店远征',
          text: '队员视角的横店远征记录。',
          href: 'https://www.xiaohongshu.com/search_result/69b956af000000002202686f',
          image: xhsHengdianStreet
        }
      ],
      mediaClips: [
        {
          tag: 'TRAILER',
          title: '巨蟹行动预告片',
          text: '巨蟹行动筹备期公开视频，画面来自象山海影城任务设定。',
          image: haiyingcheng01
        },
        {
          tag: 'FIELD RECORD',
          title: '象山海影城最终章',
          text: 'B 站公开记录，展示影视城街区、楼体、路线和任务推进。',
          image: biliJujieFieldFrame
        }
      ],
      activityLog: [
        { date: '2026.06.22 / 小红书', title: '宁波甬士下场视频', text: '队员发布的视频切片，画面来自一次下场活动。', image: xhsWargameFrame },
        { date: '2026.05.30 / 小红书', title: '5.30 下场日记', text: '队员视角记录了草地场景、队伍合影和现场动作。', image: xhsFieldTeam },
        { date: '2026.03.15 / 横店', title: '横店影视城交流', text: '队伍到横店与外地队伍同场交流。', image: xhsHengdianStreet },
        { date: '2024.06 / 象山', title: '巨蟹行动最终章', text: '象山海影城街区任务，公开视频已有记录。', image: biliJujieFinalFrame }
      ],
      quickGallery: [
        { image: yingmengliDate02, caption: '应梦里夜间活动现场。' },
        { image: esaUrban02, caption: '室内训练，门口和墙面附近配合。' },
        { image: xhsFieldGrass, caption: '2026 年 5 月 30 日下场日记。' },
        { image: nightTeam, caption: '夜间集合和队伍合影。' },
        { image: patchBoard, caption: '活动道具、臂章和装备标识。' },
        { image: moto02, caption: '外出活动中的机车照片。' }
      ],
      evidenceNotes: [
        { kicker: 'NEWCOMER', title: '第一次参加，先看集合和简报。', text: '新人到场后先听规则、安全边界、装备要求和当天任务，再跟队进入活动。' },
        { kicker: 'FIELD', title: '场地先看边界和动线。', text: '能不能跑、哪里不能进、观摩区放哪里、撤离路线怎么走，这些要先确认。' },
        { kicker: 'RECORD', title: '照片按活动留下来。', text: '时间、地点、活动名称和公开链接放在一起，方便新人和场地方回看。' }
      ],
      venues: [
        {
          name: '象山海影城',
          type: 'CINEMATIC URBAN FIELD',
          image: haiyingcheng02,
          featured: true,
          text: '这里有街道、楼体和巷口。队伍进场后，可以围绕转角、门洞和街面推进任务。',
          points: ['阵营推进', '搜索', '据点攻防', '夜间行动'],
          note: '已用于：巨蟹行动、夜间剧本、街区推进'
        },
        {
          name: '应梦里',
          type: 'NIGHT / STORY FIELD',
          image: yingmengliDate01,
          featured: false,
          text: '应梦里用于夜间街区任务。灯光、店铺外立面和人群背景都能进入现场设计。',
          points: ['夜间街区', '轻剧本', '观摩友好', '角色任务'],
          note: '已记录：2023 年 10 月 22 日应梦里活动'
        },
        {
          name: '四明山野猫湾 / 浙东小九寨',
          type: 'MOUNTAIN FIELD',
          image: xiaojiuzhaiEscape01,
          featured: false,
          text: '山里没有那么多固定边界。路线、体力、通讯都会直接影响当天的节奏。',
          points: ['路线规划', '山地穿插', '通讯纪律', '逃离荒野'],
          note: '已用于：浙东小九寨训练、逃离荒野'
        },
        {
          name: '迎春里',
          type: 'COMMUNITY / BLOCK FIELD',
          image: moto01,
          featured: false,
          text: '街区空间可以安排短流程活动。可以设置集合点、观摩区、小任务点和装备展示区。',
          points: ['公众观摩', '品牌活动', '城市微场景', '轻量任务'],
          note: '可做：短任务、观摩、装备展示、街区协作'
        },
        {
          name: '天宫庄园',
          type: 'PARK / EVENT FIELD',
          image: patchBoard,
          featured: false,
          text: '园区边界清楚，动线容易控制。规则讲解、展示、短任务和休息区可以放在同一条线上。',
          points: ['团建体验', '展示活动', '研学活动', '装备介绍'],
          note: '可做：团建体验、展示日、研学活动'
        },
        {
          name: '章水中心小学',
          type: 'CAMPUS TRAINING FIELD',
          image: esaRoom,
          featured: false,
          text: '校园场地用于低强度训练。先讲规则，再做队形、口令、协同和安全演示。',
          points: ['规则讲解', '队形演示', '基础协作', '安全边界'],
          note: '可做：规则课、队形训练、基础协作'
        }
      ],
      venueAlbums: [
        {
          code: 'XIANGSHAN / 2024',
          name: '象山海影城 · 巨蟹行动',
          text: '街道、门洞、楼体、巷口都进入了当天任务路线。',
          photos: [
            { image: haiyingcheng01, caption: '象山海影城，巨蟹行动现场。' },
            { image: haiyingcheng02, caption: '街区转角和门洞是主要观察点。' },
            { image: haiyingcheng03, caption: '队伍沿街区路线推进。' },
            { image: haiyingcheng04, caption: '队员沿影视城街区推进。' },
            { image: haiyingcheng05, caption: '任务后段，队伍进入更窄的街面。' }
          ]
        },
        {
          code: 'YINGMENGLI / 2023',
          name: '应梦里 · 夜间街区',
          text: '应梦里活动以夜间街区为背景，现场先讲规则，再分组进入任务。',
          photos: [
            { image: yingmengli01, caption: '夜间活动的第一步，是先把规则讲清楚。' },
            { image: yingmengli02, caption: '夜间街区任务，队员在路口附近集合。' },
            { image: yingmengliDate01, caption: '2023 年 10 月 22 日留下的现场记录。' },
            { image: yingmengliDate02, caption: '应梦里 2023 年 10 月 22 日活动现场。' },
            { image: yingmengliDate03, caption: '活动前的规则说明和任务简报。' }
          ]
        },
        {
          code: 'MOUNTAIN / 2021',
          name: '浙东小九寨 · 训练与逃离荒野',
          text: '山地活动主要看路线、体力、天气、通讯和队伍间距。',
          photos: [
            { image: xiaojiuzhaiTraining01, caption: '浙东小九寨山地训练。' },
            { image: xiaojiuzhaiTraining02, caption: '林地路线中的队伍移动。' },
            { image: xiaojiuzhaiTraining03, caption: '训练中的站位和路线确认。' },
            { image: xiaojiuzhaiEscape01, caption: '2021 年 10 月 31 日逃离荒野活动。' },
            { image: xiaojiuzhaiEscape03, caption: '山地活动中的队伍行进。' }
          ]
        },
        {
          code: 'HENGDIAN / 2026',
          name: '横店影视城 · 远征交流',
          text: '2026 年 3 月横店影视城交流，队员笔记和照片都已公开。',
          photos: [
            { image: xhsHengdianTeam, caption: '2026 年 3 月 15 日，横店影视城队伍合影。' },
            { image: hengdian02, caption: '横店影视城活动现场。' },
            { image: hengdian03, caption: '队伍在横店街区内移动。' },
            { image: xhsHengdianStreet, caption: '横店街区里的行进记录。' },
            { image: hengdian05, caption: '横店远征交流现场照片。' }
          ]
        }
      ],
      activityTypes: [
        {
          code: 'WEEKLY',
          title: '周常规活动',
          text: '周常活动以集合、热身、分组、跑局和 AAR 为主。新人先熟悉 ROE、装备要求和基本口令。',
          points: ['集合签到', 'ROE 说明', '分组跑局', 'AAR 复盘']
        },
        {
          code: 'EXPEDITION',
          title: '远征交流',
          text: '外地交流会更换场地、规则和对抗对象。队伍需要重新完成 Briefing、分组、通讯、推进和复盘。',
          points: ['2021 扬州 MILSIM', '2026 横店影视城', '跨地区同场', '外部规则适应']
        },
        {
          code: 'SCRIPT',
          title: '沉浸式剧本',
          text: '剧本行动会提前设定阵营、任务点、行动区域 AO、集结点和撤离条件。现场按 Briefing 推进。',
          points: ['阵营设定', '任务点', 'AO 边界', '撤离条件']
        }
      ],
      opsScenes: [
        { tag: 'WEEKLY', title: '周常规活动', text: '集合后先讲规则、分组和安全边界，再进入跑局。', image: xiaojiuzhaiTraining03 },
        { tag: 'SCRIPT', title: '巨蟹行动', text: '巨蟹行动在象山海影城展开，围绕任务点、集结点和撤离路线推进。', image: haiyingcheng05 },
        { tag: 'EXPEDITION', title: '横店远征', text: '横店远征包含队伍合影、街区行进和队员公开笔记。', image: xhsHengdianTeam },
        { tag: 'NIGHT', title: '夜间任务', text: '夜间任务主要考验识别、通讯和队形保持。', image: nightTeam }
      ],
      opFlow: [
        { code: '01', title: '集合 / Check-in', text: '确认人数、装备、分组和当天场地边界。' },
        { code: '02', title: 'Briefing', text: '说明 ROE、行动区域 AO、任务点、集结点和撤离点。' },
        { code: '03', title: '进场 / Step-off', text: '按阵营或班组进入场地，保持通讯纪律和队形间距。' },
        { code: '04', title: '任务推进', text: '围绕搜索、占点、护送、撤离或剧情节点推进。' },
        { code: '05', title: 'AAR 复盘', text: '活动结束后复盘安全、通讯、路线、队形和任务执行。' }
      ],
      activityRecords: [
        { time: '2019', place: '山地户外', type: '训练', name: '“巡山”行动', note: 'B 站公开视频' },
        { time: '2021.03.27', place: '扬州', type: '远征交流', name: 'MILSIM 镭射交流', note: 'B 站公开视频' },
        { time: '2021.10.31', place: '浙东小九寨', type: '户外活动', name: '逃离荒野', note: '本地照片' },
        { time: '2023.10.22', place: '应梦里', type: '夜间街区', name: '应梦里活动', note: '本地照片' },
        { time: '2024.06', place: '象山海影城', type: '剧本行动', name: '巨蟹行动最终章', note: 'B 站公开视频 / 本地视频' },
        { time: '2026.03.15', place: '横店影视城', type: '远征交流', name: '甬士横店征途', note: '小红书队员笔记' },
        { time: '2026.05.30', place: '宁波', type: '下场日记', name: '5.30 宁波甬士下场日记', note: '小红书队员笔记' },
        { time: '2026.06.22', place: '宁波', type: '视频记录', name: 'wargame 下场视频剪一剪', note: '小红书队员视频' }
      ],
      expeditions: [
        {
          time: '2021.03.27',
          title: '扬州 MILSIM 镭射交流',
          text: '公开视频里能看到甬士参与扬州活动。对队伍来说，这是一次很早的外地交流。',
          image: biliYangzhou
        },
        {
          time: '2024.06',
          title: '象山海影城·巨蟹行动最终章',
          text: '这是甬士的一场剧本活动。当天的路线和任务都围绕影视城街区展开。',
          image: biliJujieFieldFrame
        },
        {
          time: '2026.03.15 / 03.17',
          title: '横店征途 / 横店远征',
          text: '横店交流留下了队伍合影、街区照片和队员笔记。',
          image: xhsHengdianTeam
        }
      ],
      memberNotes: [
        {
          date: '2026-06-22',
          author: 'Y!Nu0',
          title: 'wargame下场视频剪一剪',
          text: '队员发布的下场视频。正文写着“宁波甬士在行动”。',
          href: 'https://www.xiaohongshu.com/search_result/6a381a3c000000001603e3e9?xsec_token=AB43XKJ4kGvFP83QErA-n3RJyfjpt4ir02W1JJYxmMmvo=&xsec_source=',
          image: xhsWargameFrame
        },
        {
          date: '2026-06-03',
          author: 'Y!Nu0',
          title: '5.30宁波甬士下场日记',
          text: '队员视角的下场日记，记录 5 月 30 日活动。',
          href: 'https://www.xiaohongshu.com/search_result/6a201ec9000000003601e991?xsec_token=ABoCjLpr8FxNv5zpHM6S3T1AZ4kOwy8VjXxftAK83joRo=&xsec_source=',
          image: xhsFieldGrass
        },
        {
          date: '2026-03-15',
          author: '宁波甬士遗忘',
          title: '甬士横店征途',
          text: '横店交流笔记，记录和各地区战队同场交流。',
          href: 'https://www.xiaohongshu.com/search_result/69b587e1000000001e00cc1b?xsec_token=ABwI48GDUC48eRQ-TcwGeCwCcnsY7a6HrJF7NF2kzsqcE=&xsec_source=',
          image: xhsHengdianTeam
        },
        {
          date: '2026-03-17',
          author: '宁波甬士遗忘',
          title: '甬士横店远征',
          text: '队员视角的横店远征记录。',
          href: 'https://www.xiaohongshu.com/search_result/69b956af000000002202686f',
          image: xhsHengdianStreet
        }
      ],
      trainingLoop: [
        { code: '01', title: 'Briefing', text: '任务、规则、角色和安全边界先对齐。' },
        { code: '02', title: 'Skill Drill', text: '一次只练一两件事，练明白再进场。' },
        { code: '03', title: 'Scenario', text: '把动作放回任务里，看能不能用出来。' },
        { code: '04', title: 'AAR', text: '活动结束后讲清楚哪里好、哪里乱、下次怎么改。' }
      ],
      phases: [
        { code: 'W1-W6', name: '地基建设', weeks: '统一语言、标准、安全与基础动作。' },
        { code: 'W7-W10', name: '单兵成型', weeks: '角度、移动、基础 CQB 和个人稳定性。' },
        { code: 'W11-W14', name: '班组磨合', weeks: 'Bounding Overwatch、通讯、双组协同。' },
        { code: 'W15-W18', name: '战术深化', weeks: 'SOP、进攻/防御、任务条件下执行。' },
        { code: 'W19-W24', name: '综合演练', weeks: '全对抗、观察员记录、评分与 AAR。' },
        { code: 'W25-W30', name: '综合准备', weeks: '任务推演、外部交流准备、指挥流程和 AAR。' }
      ],
      doctrineDocs: [
        { type: '总纲', name: '30 周训练总纲', text: '按 6 个阶段安排规则、安全、动作、通讯、协同和复盘。' },
        { type: '基础', name: '规则与安全', text: '先明确 ROE、场地边界、安全距离、护具要求和停止口令。' },
        { type: '教案', name: '阶段教案 01-06', text: '从基础动作到班组协同，再进入完整任务执行。' },
        { type: '专题', name: '通讯指挥 / 标靶训练', text: '练对讲机用语、目标识别、火力控制和安全距离。' },
        { type: '工具', name: 'SOP / 执行指南 / 速查表', text: '活动前用于分工和检查，活动后用于复盘和调整。' }
      ],
      trainingScenes: [
        { image: esaRoom, caption: '室内 Briefing：规则、安全边界和当天任务先讲清楚。' },
        { image: esaUrban01, caption: '城市街区训练，队员在建筑外侧移动。' },
        { image: esaUrban02, caption: '室内训练，队员在门口和墙面附近配合。' },
        { image: esaUrban03, caption: '训练结束后的队伍合影。' },
        { image: esaUrban04, caption: '室内训练：移动、停顿、观察和队形间距。' },
        { image: patchBoard, caption: '活动道具、臂章和装备标识。' }
      ],
      assetStories: [
        { tag: 'XIANGSHAN / SCRIPT', title: '巨蟹行动现场', text: '队伍沿象山海影城街区推进。', image: haiyingcheng03, size: 'wide' },
        { tag: 'HENGDIAN / EXPEDITION', title: '横店远征交流', text: '2026 年 3 月横店影视城交流。', image: xhsHengdianTeam, size: '' },
        { tag: 'YINGMENGLI / NIGHT', title: '应梦里夜间活动', text: '2023 年 10 月 22 日应梦里活动。', image: yingmengli02, size: '' },
        { tag: 'MOUNTAIN / FIELD', title: '浙东小九寨训练', text: '山地路线和通讯训练。', image: xiaojiuzhaiTraining02, size: '' },
        { tag: 'ESA / URBAN', title: 'ESA 城市作战训练', text: '2018 年 6 月城市作战训练照片。', image: esaUrban01, size: 'tall' },
        { tag: 'TEAM / 2022', title: '夜间队伍照片', text: '夜间集合和队伍合影。', image: nightTeam, size: '' },
        { tag: 'MOTO / MOBILITY', title: '机车照片', text: '外出活动中的机车照片。', image: moto02, size: '' },
        { tag: 'XHS / 2026', title: '5.30 下场日记', text: '队员在草地场景中行动。', image: xhsFieldGrass, size: 'wide' },
        { tag: 'CANCER / FINAL', title: '巨蟹行动最终章', text: '象山海影城街区中的任务推进画面。', image: haiyingcheng04, size: '' },
        { tag: 'URBAN / CQB', title: '室内街区训练', text: '门口、墙面和队形配合。', image: esaUrban02, size: '' },
        { tag: 'YINGMENGLI / 2023', title: '应梦里活动', text: '2023 年 10 月 22 日应梦里活动。', image: yingmengliDate02, size: '' },
        { tag: 'XHS / 2026', title: '6.22 队员视频', text: '小红书队员视角视频切片。', image: xhsWargameCover, size: '' },
        { tag: 'HENGDIAN / TEAM', title: '横店队员合影', text: '横店远征交流照片。', image: xhsHengdianTeam, size: '' }
      ],
      partnerModes: [
        { code: 'VENUE', title: '场地试场', text: '先看边界、动线、可进入区域、观摩区和撤离路线。' },
        { code: 'BRAND', title: '线下活动', text: '可以做短流程任务、装备展示、合影区和现场记录。' },
        { code: 'CONTENT', title: '内容拍摄', text: '提前确认镜头点、行动路线、可拍区域和人员授权。' },
        { code: 'TEAM', title: '团建/研学', text: '降低强度，保留规则、协作、通讯和复盘环节。' }
      ],
      partnerChecklist: [
        { code: '01', title: '人数与年龄', text: '确认参与人数、观摩人数、是否有未成年人和陪同人员。' },
        { code: '02', title: '场地边界', text: '标出可进入区域、禁入区域、集合点、休息区和撤离路线。' },
        { code: '03', title: '安全条件', text: '确认护具、道具、天气、照明、医疗点和现场负责人。' },
        { code: '04', title: '影像记录', text: '确认照片、视频、队员笔记和公开发布范围。' }
      ]
    }
  },
  computed: {
    activePanel() {
      return this.panels[this.activeView] || this.panels.overview
    },
    activeHero() {
      return this.heroByView[this.activeView] || this.heroByView.overview
    }
  },
  mounted() {
    window.addEventListener('hashchange', this.syncFromHash)
  },
  beforeUnmount() {
    window.removeEventListener('hashchange', this.syncFromHash)
  },
  methods: {
    setView(key) {
      if (!viewKeys.includes(key)) return
      this.activeView = key
      const targetHash = `#${key}`
      if (window.location.hash !== targetHash) {
        window.history.replaceState(null, '', `${window.location.pathname}${window.location.search}${targetHash}`)
      }
      window.scrollTo({ top: 0, behavior: 'smooth' })
    },
    syncFromHash() {
      this.activeView = hashToView()
    }
  }
}
</script>
