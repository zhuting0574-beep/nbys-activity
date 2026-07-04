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

      <div class="nav-actions">
        <button class="admin-link-btn" type="button" @click="goAdmin">去后管</button>
        <button class="action-btn" type="button" @click="$emit('enter-app')">一起行动</button>
      </div>
    </header>

    <div class="top-carousel-region carousel-region">
      <BackgroundCarousel :images="carouselImages('top')" label="首屏背景" eager />
    <section id="top" class="hero section-shell">
      <div class="hero-copy">
        <p class="plain-label">NINGBO YONGSHI / FIELD WARGAME</p>
        <h1>
          <span>宁波甬士</span>
          <span>军事模拟推演</span>
        </h1>
        <p class="hero-lead">
          我们是宁波的非盈利军事模拟推演兴趣团体。2016年开始，我们在本地组织周常、剧本、山地、远征和训练活动。
          第一次来不用懂很多术语，活动前会讲规则、安全边界、停止口令和任务目标。
        </p>
        <div class="hero-brief" aria-label="活动开始前会确认的事项">
          <span>FIRST RUN</span>
          <p>第一次来，先参加周常。听完 Briefing，跟队走一场，知道规则和安全距离就够了。</p>
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
          <h2>关于宁波甬士</h2>
          <p>
            我们从2016年开始在宁波组织军事模拟推演活动。大家因为装备、剧本、场地、山地路线和团队协作聚在一起。
          </p>
          <p>
            活动前会讲安全规则、击中判定、停止口令、装备限制和任务目标。第一次参加，听规则、跟队走，就能进入状态。
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
        <h2>这些年去过的地方</h2>
        <p>活动按时间排。能公开查看的记录，可以直接点开来源。</p>
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

    </section>

    <section id="fields" class="section-shell fields-section carousel-section">
      <BackgroundCarousel :images="carouselImages('fields')" label="场地板块背景" />
      <div class="section-kicker">Fields</div>
      <div class="section-heading">
        <h2>不同场地，不同体验</h2>
        <p>影视城、夜间街区、山地、校园和园区，各有自己的节奏。</p>
      </div>

      <div class="field-grid">
        <article v-for="venue in venues" :key="venue.name">
          <button
            class="field-photo"
            type="button"
            :aria-label="`查看完整图片：${venue.name}`"
            @click="openImage(venue)"
          >
            <img :src="venue.image" :alt="venue.name" loading="lazy" fetchpriority="low" decoding="async" />
          </button>
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
        <h2>常见活动类型</h2>
        <p>周常、主题剧本、山地徒步、外地交流、基础训练。</p>
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

    </section>

    <section id="safe" class="section-shell safe-section carousel-section">
      <BackgroundCarousel :images="carouselImages('safe')" label="安全保障板块背景" />
      <div class="section-kicker">Safe ops</div>
      <div class="safe-layout">
        <div>
          <h2>安全规则先讲清楚</h2>
          <p>
            进场前讲安全边界、停止口令、频道和撤离线。训练内容围绕规则、通讯、队形、掩体、CQB 入门和复盘展开。
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
        <h2>现场影像</h2>
        <p>照片、视频和公开记录放在这里。能点开的保留来源，普通照片只标时间和场地。</p>
      </div>

      <div class="video-carousel" aria-label="活动视频片段">
        <div class="video-board">
          <div class="video-track" :style="videoLoopStyle">
            <div v-for="copy in 2" :key="copy" class="video-loop-group" :aria-hidden="copy === 2">
              <figure
                v-for="(clip, clipIndex) in videoClips"
                :key="`${copy}-${clip.title}`"
                role="button"
                :tabindex="copy === 1 ? 0 : -1"
                :aria-label="`播放视频：${clip.title}`"
                @click="openVideo(clip)"
                @keydown.enter.prevent="openVideo(clip)"
                @keydown.space.prevent="openVideo(clip)"
              >
                <img
                  :src="clip.poster"
                  :alt="clip.title"
                  :loading="copy === 1 && clipIndex < videoCarouselVisible ? 'eager' : 'lazy'"
                  :fetchpriority="copy === 1 && clipIndex < videoCarouselVisible ? 'auto' : 'low'"
                  decoding="async"
                />
                <figcaption>
                  <small>FIELD VIDEO</small>
                  <strong>{{ clip.title }}</strong>
                  <span>点击封面完整播放视频</span>
                </figcaption>
              </figure>
            </div>
          </div>
        </div>
      </div>

      <div class="media-feature">
        <figure class="media-lead">
          <img :src="mediaLead.image" :alt="mediaLead.title" loading="lazy" fetchpriority="low" decoding="async" />
          <figcaption>
            <small>{{ mediaLead.tag }}</small>
            <strong>{{ mediaLead.title }}</strong>
            <span>{{ mediaLead.text }}</span>
          </figcaption>
        </figure>

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
          <em>点击查看来源 ↗</em>
        </a>
      </div>
    </section>

    <section id="cooperate" class="section-shell cooperate-section carousel-section">
      <BackgroundCarousel :images="carouselImages('cooperate')" label="合作板块背景" />
      <div class="section-kicker">Work with us</div>
      <div class="cooperate-layout">
        <div>
          <h2>从一场小活动开始</h2>
          <p>
            如果场地、学校、园区想先试一次，可以先做短流程：一段路线，一个任务，十几到几十人。
            入口、禁入区、旁观位置和影像范围，进场前说清楚。
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

    <aside
      class="contact-drawer"
      :class="{ open: contactOpen }"
      aria-label="联系我们"
      @mouseleave="contactOpen = false"
    >
      <button
        class="contact-drawer-tab"
        type="button"
        aria-controls="contact-drawer-panel"
        :aria-expanded="contactOpen"
        @click="contactOpen = !contactOpen"
      >
        <span>联系我们</span>
      </button>
      <div id="contact-drawer-panel" class="contact-drawer-panel">
        <div class="contact-drawer-heading">
          <span>CONTACT</span>
          <strong>扫码联系我们</strong>
        </div>
        <div class="contact-qrcodes">
          <button
            v-for="item in contactQrcodes"
            :key="item.hint"
            class="contact-qrcode-card"
            type="button"
            :aria-label="`查看${item.label}完整二维码`"
            @click="openContactPreview(item)"
          >
            <img :src="item.image" :alt="item.label" loading="lazy" fetchpriority="low" decoding="async" />
            <span class="contact-qrcode-caption">
              <strong>{{ item.label }}</strong>
              <span>{{ item.hint }}</span>
            </span>
          </button>
        </div>
      </div>
    </aside>

    <Teleport to="body">
      <div v-if="activeVideo" class="video-lightbox" role="dialog" aria-modal="true" :aria-label="activeVideo.title" @click.self="closeVideo">
        <div class="video-lightbox-panel">
          <button type="button" class="video-lightbox-close" aria-label="关闭视频" @click="closeVideo">×</button>
          <video :key="activeVideo.src" :src="activeVideo.src" :poster="activeVideo.poster" controls autoplay playsinline preload="metadata"></video>
          <div class="video-lightbox-copy">
            <small>FIELD VIDEO</small>
            <strong>{{ activeVideo.title }}</strong>
            <span>现场视频完整播放</span>
          </div>
        </div>
      </div>
      <Transition name="contact-preview">
        <div
          v-if="contactPreview"
          class="contact-preview-lightbox"
          role="dialog"
          aria-modal="true"
          :aria-label="`${contactPreview.label}完整二维码`"
          @click.self="closeContactPreview"
        >
          <div class="contact-preview-panel">
            <button type="button" class="contact-preview-close" aria-label="关闭二维码预览" @click="closeContactPreview">×</button>
            <img :src="contactPreview.image" :alt="`${contactPreview.label}完整二维码`" />
            <div>
              <strong>{{ contactPreview.label }}</strong>
              <span>{{ contactPreview.hint }}</span>
            </div>
          </div>
        </div>
      </Transition>
      <Transition name="image-preview">
        <div
          v-if="activeImage"
          class="image-lightbox"
          role="dialog"
          aria-modal="true"
          :aria-label="`完整图片：${activeImage.title}`"
          @click.self="closeImage"
        >
          <figure class="image-lightbox-panel">
            <button type="button" class="image-lightbox-close" aria-label="关闭图片预览" @click="closeImage">×</button>
            <img :src="activeImage.image" :alt="activeImage.title" />
            <figcaption>
              <small>{{ activeImage.tag }}</small>
              <strong>{{ activeImage.title }}</strong>
              <span>{{ activeImage.text }}</span>
            </figcaption>
          </figure>
        </div>
      </Transition>
    </Teleport>
  </main>
</template>

<script>
import { api } from './api'
import BackgroundCarousel from './BackgroundCarousel.vue'
import logo from './assets/nbys-logo.png'
import archive20180701 from './assets/site/2018-07-01 171536.jpg'
import archive20180702 from './assets/site/2018-07-02 165748.jpg'
import archive20180703 from './assets/site/2018-07-03 192210.jpg'
import haiyingcheng01 from './assets/site/haiyingcheng-jujie-01.jpg'
import haiyingcheng02 from './assets/site/haiyingcheng-jujie-02.jpg'
import haiyingcheng03 from './assets/site/haiyingcheng-jujie-03.jpg'
import haiyingcheng04 from './assets/site/haiyingcheng-jujie-04.jpg'
import haiyingcheng05 from './assets/site/haiyingcheng-jujie-05.jpg'
import yingmengli01 from './assets/site/yingmengli-01.jpg'
import yingmengli02 from './assets/site/yingmengli-02.jpg'
import yingmengliDate01 from './assets/site/yingmengli-20231022-01.jpg'
import yingmengliDate02 from './assets/site/yingmengli-20231022-02.jpg'
import yingmengliDate03 from './assets/site/yingmengli-20231022-03.jpg'
import siteField from './assets/site/site-field.jpg'
import siteEsaSquad from './assets/site/site-esa-squad.jpg'
import siteEsaUrban from './assets/site/site-esa-urban.jpg'
import siteModern01 from './assets/site/site-modern-1.jpg'
import siteModern02 from './assets/site/site-modern-2.jpg'
import siteModern03 from './assets/site/site-modern-3.jpg'
import xiaojiuzhaiTraining01 from './assets/site/xiaojiuzhai-training-01.jpg'
import xiaojiuzhaiTraining02 from './assets/site/xiaojiuzhai-training-02.jpg'
import xiaojiuzhaiTraining03 from './assets/site/xiaojiuzhai-training-03.jpg'
import xiaojiuzhaiEscape01 from './assets/site/xiaojiuzhai-escape-20211031-01.jpg'
import xiaojiuzhaiEscape02 from './assets/site/xiaojiuzhai-escape-20211031-02.jpg'
import xiaojiuzhaiEscape03 from './assets/site/xiaojiuzhai-escape-20211031-03.jpg'
import esaTraining01 from './assets/site/esa-urban-training-01.jpg'
import esaRoom from './assets/site/esa-urban-training-07.jpg'
import esaUrban01 from './assets/site/esa-urban-training-02.jpg'
import esaTraining03 from './assets/site/esa-urban-training-03.jpg'
import esaDoorTraining from './assets/site/esa-urban-training-04.jpg'
import esaUrban02 from './assets/site/esa-urban-training-05.jpg'
import esaTraining06 from './assets/site/esa-urban-training-06.jpg'
import patchBoard from './assets/site/patch-board.jpg'
import moto01 from './assets/site/moto-01.jpg'
import moto02 from './assets/site/moto-02.jpg'
import mountainMoto01 from './assets/site/mountain-moto-01.jpg'
import mountainMoto02 from './assets/site/mountain-moto-02.jpg'
import nightTeam2022 from './assets/site/night-team-2022.jpg'
import hengdian01 from './assets/site/hengdian-01.jpg'
import hengdian02 from './assets/site/hengdian-02.jpg'
import hengdian03 from './assets/site/hengdian-03.jpg'
import hengdian04 from './assets/site/hengdian-04.jpg'
import hengdianStreetWide from './assets/site/hengdian-05.jpg'
import biliJujieFinalPoster from './assets/site/bili-jujie-final-2024.jpg'
import biliJujiePreviewPoster from './assets/site/bili-jujie-preview.jpg'
import biliXunshanPoster from './assets/site/bili-xunshan-2019.jpg'
import biliYangzhouPoster from './assets/site/bili-yangzhou-20210327.png'
import biliJujieFieldFrame from './assets/site/external/2024-06_bilibili_xiangshan-jujie-field_frame01.jpg'
import biliJujieFinalFrame from './assets/site/external/2024-06_bilibili_xiangshan-jujie-final_frame01.jpg'
import xhsHengdianTeam from './assets/site/external/2026-03-15_xhs_hengdian-expedition_team.jpg'
import xhsHengdianStreet from './assets/site/external/2026-03-15_xhs_hengdian-expedition_street.jpg'
import xhsFieldGrass from './assets/site/external/2026-05-30_xhs_ningbo-yongshi_field-day_grass.jpg'
import xhsFieldTeam from './assets/site/external/2026-05-30_xhs_ningbo-yongshi_field-day_team.jpg'
import xhsWargameCover from './assets/site/external/2026-06-22_xhs_ningbo-yongshi_wargame-cover.jpg'
import xhsWargameFrame from './assets/site/external/2026-06-22_xhs_ningbo-yongshi_wargame-frame01.jpg'
import contactDouyin from './assets/site/contact-douyin.jpg'
import contactWechatYouzi from './assets/site/contact-wechat-youzi.jpg'
import contactWechatWeijing from './assets/site/contact-wechat-weijing.jpg'

const videoAssets = import.meta.glob('./assets/site/videos/*.{jpg,mp4}', { eager: true, query: '?url', import: 'default' })
const videoTitleOverrides = {
  '10月15日巨蟹行动': '象山影视城-巨蟹行动',
  '11月13日天宫庄园': '天宫庄园周常',
  '先导片 提亮版本': '森屿湖',
  '3月3日 天宫': '天宫庄园周常',
  '双子行动 预告 final': '天宫庄园-双子行动',
  '4月28日': '奉化梅里达广场周常',
  '9月11日 (1)': '应梦里周常'
}
const videoClips = Object.entries(videoAssets)
  .filter(([path]) => path.endsWith('.mp4'))
  .map(([path, src]) => {
    const fileStem = path.split('/').pop().replace(/\.mp4$/i, '')
    return {
      title: videoTitleOverrides[fileStem] || fileStem,
      src,
      poster: videoAssets[`./assets/site/videos/${fileStem}.jpg`]
    }
  })
  .filter(clip => clip.poster)
  .sort((a, b) => a.title.localeCompare(b.title, 'zh-CN', { numeric: true }))

const defaultHomepageCarousels = {
  top: [nightTeam2022, siteModern03, mountainMoto02, siteEsaSquad],
  about: [siteField, siteModern01, hengdian01],
  records: [patchBoard, moto02, esaTraining03, yingmengliDate02],
  fields: [xiaojiuzhaiTraining01, yingmengli01, hengdian02, mountainMoto01],
  activities: [haiyingcheng02, xhsWargameCover, xhsFieldGrass, hengdian04],
  safe: [esaTraining01, esaDoorTraining, esaUrban02, esaTraining06],
  media: [biliJujieFinalPoster, biliJujiePreviewPoster, biliXunshanPoster, biliYangzhouPoster],
  cooperate: [hengdianStreetWide, xhsHengdianTeam, siteEsaUrban]
}

export default {
  name: 'MarketingSite',
  components: { BackgroundCarousel },
  emits: ['enter-app'],
  data() {
    return {
      logo,
      homepageCarousels: {},
      activeVideo: null,
      activeImage: null,
      contactPreview: null,
      contactOpen: false,
      contactQrcodes: [
        { label: '抖音官方账号', hint: '@Milsim_NB', image: contactDouyin },
        { label: '微信联系', hint: '柚子粑粑', image: contactWechatYouzi },
        { label: '微信联系', hint: '未竟', image: contactWechatWeijing }
      ],
      videoCarouselVisible: window.innerWidth <= 680 ? 1 : window.innerWidth <= 1080 ? 2 : 3,
      heroImage: haiyingcheng04,
      esaRoom,
      esaDoorTraining,
      activityFeatureImage: haiyingcheng02,
      cooperateImage: hengdianStreetWide,
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
        { title: '玩家', text: '想参加，先看报名入口；第一次可以从周常开始。' },
        { title: '场地', text: '看入口、禁入区、旁观位置和撤离线。' },
        { title: '学校 / 园区', text: '规则课、队形演示、低强度体验。' }
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
          text: '第一次来先听 Briefing，跟队走一场。'
        },
        {
          title: '下场流程',
          text: '集合、Briefing、进场、任务、AAR。'
        },
        {
          title: '试场',
          text: '一段路线，一个任务，十几到几十人。'
        }
      ],
      aboutPhotos: [
        {
          caption: '象山海影城 / 巨蟹行动',
          image: biliJujieFieldFrame
        },
        {
          caption: '横店影视城 / 远征交流',
          image: hengdian03
        },
        {
          caption: '宁波周常 / 队员记录',
          image: xiaojiuzhaiTraining03
        }
      ],
      history: [
        { year: '2018', title: 'ESA 城市作战训练', text: '室内简报、贴墙移动、门口队形。' },
        { year: '2019', title: '巡山户外训练', text: '山路、林线、队伍行进。公开视频见 B 站。' },
        { year: '2021.03.27', title: '扬州 MILSIM 镭射交流', text: '扬州同场交流，按对方规则进场。' },
        { year: '2021.10.31', title: '浙东小九寨逃离荒野', text: '山地路线、通讯、队伍间距。' },
        { year: '2023.10.22', title: '应梦里活动', text: '夜间街区、灯光、街面。' },
        { year: '2024.06', title: '象山海影城巨蟹行动最终章', text: '象山海影城街区任务。' },
        { year: '2026.03', title: '横店远征交流', text: '横店影视城街区交流。' }
      ],
      venues: [
        {
          name: '象山海影城',
          type: '影视城街区',
          image: haiyingcheng03,
          featured: true,
          text: '街道、楼体、巷口、开阔区。',
          points: ['巨蟹行动', '街区推进', '夜间任务']
        },
        {
          name: '应梦里',
          type: '夜间街区',
          image: yingmengliDate01,
          featured: false,
          text: '夜间街区，灯光足，适合短流程。',
          points: ['夜间街区', '短流程']
        },
        {
          name: '四明山野猫湾 / 浙东小九寨',
          type: '山地路线',
          image: xiaojiuzhaiEscape02,
          featured: false,
          text: '山路、林线、坡度、天气。',
          points: ['路线规划', '通讯纪律']
        },
        {
          name: '迎春里',
          type: '街区空间',
          image: moto01,
          featured: false,
          text: '街区尺度小，动线短。',
          points: ['短流程', '器材摆放']
        },
        {
          name: '天宫庄园',
          type: '边界清楚',
          image: siteModern02,
          featured: false,
          text: '边界清楚，适合低强度试场。',
          points: ['试场', '低强度']
        },
        {
          name: '章水中心小学',
          type: '校园训练',
          image: esaRoom,
          featured: false,
          text: '校园空间，适合规则讲解和队形演示。',
          points: ['规则讲解', '基础队形']
        }
      ],
      activityTypes: [
        { code: '周常', title: '周常活动', text: '固定下场，熟悉规则和安全距离。' },
        { code: '剧本', title: '主题剧本', text: '阵营、任务点、行动区域和撤离条件。' },
        { code: '山地', title: '山地徒步', text: '路线、体力、通讯和天气。' },
        { code: '远征', title: '外地交流', text: '去外地场地，按对方规则进场。' },
        { code: '试场', title: '试场体验', text: '短流程、低强度、小规模。' }
      ],
      opFlow: [
        { title: '集合', text: '点人数、看装备、分组，确认场地边界。' },
        { title: 'Briefing', text: '讲规则、任务点、集合点、撤离点和停止口令。' },
        { title: '进场', text: '按阵营或班组进场，保持通讯和队形。' },
        { title: '任务', text: '搜索、占点、护送、撤离，按当天剧本走。' },
        { title: 'AAR', text: '结束后复盘安全、通讯、路线和分工。' }
      ],
      trainingLoop: [
        { title: 'ROE', text: '安全边界、停止口令、装备限制。' },
        { title: '通讯', text: '呼号、位置、异常情况。' },
        { title: '队形', text: '队伍间距、移动顺序、掩护位置。' },
        { title: 'AAR', text: '活动后复盘路线、沟通和任务节奏。' }
      ],
      mediaLead: {
        tag: 'XIANGSHAN / 2024',
        title: '象山海影城夜间任务',
        text: '灯光、街道、建筑边缘。',
        image: haiyingcheng04
      },
      mediaNotes: [
        {
          tag: 'URBAN',
          title: '影视城和夜间街区',
          text: '象山海影城、应梦里。'
        },
        {
          tag: 'MOUNTAIN',
          title: '山地和户外路线',
          text: '四明山、浙东小九寨。'
        },
        {
          tag: 'TRAINING',
          title: '平时训练',
          text: 'Briefing、队形、口令、AAR。'
        }
      ],
      videoClips,
      assetStories: [
        { tag: 'XIANGSHAN / 2024', title: '巨蟹行动现场', text: '建筑入口。', image: haiyingcheng01, size: 'wide' },
        { tag: 'YINGMENGLI / 2023', title: '建筑窗口路线', text: '窗口、栏杆。', image: yingmengli02, size: '' },
        { tag: 'YINGMENGLI / 2023', title: '应梦里夜间活动', text: '街道、移动路线。', image: yingmengliDate03, size: '' },
        { tag: 'MOUNTAIN / FIELD', title: '浙东小九寨训练', text: '山地路线。', image: xiaojiuzhaiTraining02, size: '' },
        { tag: 'ESA / URBAN', title: 'ESA 城市作战训练', text: '门口、墙边、队形。', image: esaUrban01, size: '' },
        { tag: 'MOUNTAIN / FIELD', title: '浙东小九寨路线', text: '山路、林线。', image: xiaojiuzhaiEscape03, size: '' },
        { tag: 'ESA / 2018', title: '早期训练记录', text: '室内训练。', image: archive20180701, size: '' },
        { tag: 'ESA / 2018', title: '队形训练', text: '移动、门口、队形。', image: archive20180702, size: '' },
        { tag: 'BILIBILI / 2024', title: '巨蟹行动切片', text: '公开视频画面。', image: biliJujieFinalFrame, size: '' },
        { tag: 'ESA / 2018', title: '动作训练', text: '贴墙、移动。', image: archive20180703, size: '' }
      ],
      publicSources: [
        { platform: 'BILIBILI / 2019', title: '“巡山”行动——户外训练', href: 'https://www.bilibili.com/video/BV1hb411s7zL' },
        { platform: 'BILIBILI / 2021', title: '2021.03.27 扬州 MILSIM 镭射', href: 'https://www.bilibili.com/video/BV1SZ4y1c74a' },
        { platform: 'BILIBILI / 2024', title: '2024 巨蟹行动最终章', href: 'https://www.bilibili.com/video/BV1tr42177Yo' },
        { platform: 'BILIBILI / PREVIEW', title: '巨蟹行动活动预告', href: 'https://www.bilibili.com/video/BV11a411W7ci' },
        { platform: 'XIAOHONGSHU / 2026', title: '甬士横店征途', href: 'https://www.xiaohongshu.com/search_result?keyword=%E7%94%AC%E5%A3%AB%E6%A8%AA%E5%BA%97%E5%BE%81%E9%80%94&source=web_explore_feed' },
        { platform: 'XIAOHONGSHU / 2026', title: '甬士横店远征', href: 'https://www.xiaohongshu.com/search_result?keyword=%E7%94%AC%E5%A3%AB%E6%A8%AA%E5%BA%97%E8%BF%9C%E5%BE%81&source=web_explore_feed' }
      ],
      trialSteps: [
        { code: '01', title: '看场地', text: '地点、时间、可进入区、禁入区。' },
        { code: '02', title: '划出边界', text: '集合点、休息区、旁观位置、撤离路线。' },
        { code: '03', title: '跑短任务', text: '30-90 分钟，一段路线，一个任务。' },
        { code: '04', title: '当天复盘', text: '人数、路线、任务、照片和视频范围。' }
      ],
      partnerFit: [
        { tag: 'FIELD', title: '影视城、街区、园区' },
        { tag: 'CAMPUS', title: '校园规则课' },
        { tag: 'EVENT', title: '小型主题活动' },
        { tag: 'MEDIA', title: '照片、短片、公开记录' }
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
    window.addEventListener('keydown', this.handleVideoKeydown)
    window.addEventListener('resize', this.updateVideoCarouselVisible)
  },
  beforeUnmount() {
    window.removeEventListener('keydown', this.handleVideoKeydown)
    window.removeEventListener('resize', this.updateVideoCarouselVisible)
    document.body.style.overflow = ''
  },
  computed: {
    videoLoopStyle() {
      return {
        width: `${this.videoClips.length * 200 / this.videoCarouselVisible}%`,
        '--video-count': this.videoClips.length,
        '--video-loop-duration': `${Math.max(24, this.videoClips.length * 3.5)}s`
      }
    }
  },
  methods: {
    goAdmin() {
      const url = location.port === '5174' ? `${location.protocol}//${location.hostname}:5173/admin/` : '/admin/'
      window.location.href = url
    },
    updateVideoCarouselVisible() {
      this.videoCarouselVisible = window.innerWidth <= 680 ? 1 : window.innerWidth <= 1080 ? 2 : 3
    },
    openVideo(clip) {
      this.activeVideo = clip
      document.body.style.overflow = 'hidden'
    },
    closeVideo() {
      this.activeVideo = null
      document.body.style.overflow = ''
    },
    openImage(item) {
      this.activeImage = {
        ...item,
        title: item.title || item.name,
        tag: item.tag || item.type
      }
      document.body.style.overflow = 'hidden'
    },
    closeImage() {
      this.activeImage = null
      document.body.style.overflow = ''
    },
    openContactPreview(item) {
      this.contactPreview = item
      document.body.style.overflow = 'hidden'
    },
    closeContactPreview() {
      this.contactPreview = null
      document.body.style.overflow = ''
    },
    handleVideoKeydown(event) {
      if (event.key === 'Escape' && this.activeVideo) this.closeVideo()
      if (event.key === 'Escape' && this.activeImage) this.closeImage()
      if (event.key === 'Escape' && this.contactPreview) this.closeContactPreview()
    },
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
