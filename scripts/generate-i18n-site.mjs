/**
 * @file 生成 SEO 友好的 GitHub Pages 静态多语言 HTML 脚本
 * @description 根据多语言字典静态构建 docs/ 目录下所有语言分支的 index.html 与 hreflang 声明
 */

import fs from 'fs';
import path from 'path';

const DOCS_DIR = path.resolve(process.cwd(), 'docs');

const LANGUAGES = {
  'zh-CN': {
    code: 'zh-CN',
    path: '',
    name: '简体中文',
    title: 'AuBookPlayer - 简单好用的 Android 本地有声书播放器',
    description: 'AuBookPlayer 是一款简单的 Android 本地有声书播放器。基于开源 Auxio 改造，支持跳过头尾、倍速播放、定时关闭与进度记忆，无广告，专为简单好用而生。',
    keywords: 'AuBookPlayer, 有声书播放器, Android, 本地播放器, Auxio, 倍速播放, 跳过片头片尾, 开源',
    brand_tagline: '一个简单的 Android 本地有声书播放器',
    intro_p1: '基于开源音乐播放器 <a href="https://github.com/oxygencobalt/Auxio" target="_blank" rel="noopener noreferrer">Auxio</a> 改造而来，以适用于播放本地有声书的场景。',
    intro_p2: '很难想象，Android 上竟然没有一款播放本地有声书时好用的播放器，要么缺少功能（跳过头尾、倍速播放、定时关闭），要么复杂难用，要么广告太多。所以我基于开源音乐播放器 Auxio 开发出 AuBookPlayer，专为简单、好用地播放本地有声书。',
    download_apk: '下载 APK (Releases)',
    github_source: 'GitHub 源码',
    feature_speed: '倍速播放',
    feature_timer: '定时关闭',
    feature_skip: '智能跳过头尾',
    feature_playlist: '以播放列表为核心',
    feature_cover: '关键词自动提取封面',
    feature_memory: '记忆上次播放进度',
    feature_history: '已播放节目记录',
    feature_multiline: '多行显示超长标题',
    footer_text: '© 2026 AuBookPlayer · 基于 GPL-3.0 协议开源于 <a href="https://github.com/Qzrzz/AuBookPlayer" target="_blank" rel="noopener noreferrer">GitHub</a>'
  },
  'zh-TW': {
    code: 'zh-TW',
    path: 'zh-tw',
    name: '繁體中文',
    title: 'AuBookPlayer - 簡單好用的 Android 本地有聲書播放器',
    description: 'AuBookPlayer 是一款簡單的 Android 本地有聲書播放器。基於開源 Auxio 改造，支持跳過頭尾、倍速播放、定時關閉與進度記憶。',
    keywords: 'AuBookPlayer, 有聲書播放器, Android, 本地播放器, Auxio, 倍速播放, 開源',
    brand_tagline: '一個簡單的 Android 本地有聲書播放器',
    intro_p1: '基於開源音樂播放器 <a href="https://github.com/oxygencobalt/Auxio" target="_blank" rel="noopener noreferrer">Auxio</a> 改造而來，適用於播放本地有聲書的場景。',
    intro_p2: '很難想像，Android 上竟然沒有一款播放本地有聲書時好用的播放器，要麼缺少功能（跳過頭尾、倍速播放、定時關閉），要麼複雜難用，要麼廣告太多。所以我基於開源音樂播放器 Auxio 開發出 AuBookPlayer，專為簡單、好用地播放本地有聲書。',
    download_apk: '下載 APK (Releases)',
    github_source: 'GitHub 原碼',
    feature_speed: '倍速播放',
    feature_timer: '定時關閉',
    feature_skip: '智能跳過頭尾',
    feature_playlist: '以播放列表為核心',
    feature_cover: '關鍵詞自動提取封面',
    feature_memory: '記憶上次播放進度',
    feature_history: '已播放節目記錄',
    feature_multiline: '多行顯示超長標題',
    footer_text: '© 2026 AuBookPlayer · 基於 GPL-3.0 協議開源於 <a href="https://github.com/Qzrzz/AuBookPlayer" target="_blank" rel="noopener noreferrer">GitHub</a>'
  },
  'en': {
    code: 'en',
    path: 'en',
    name: 'English',
    title: 'AuBookPlayer - A Simple & Clean Local Audiobook Player for Android',
    description: 'AuBookPlayer is a simple local audiobook player for Android. Modified from Auxio, supporting skip intro/outro, playback speed, sleep timer, and progress memory.',
    keywords: 'AuBookPlayer, audiobook player, Android, local player, Auxio, playback speed, open source',
    brand_tagline: 'A simple local audiobook player for Android.',
    intro_p1: 'Modified from the open-source music player <a href="https://github.com/oxygencobalt/Auxio" target="_blank" rel="noopener noreferrer">Auxio</a> to fit local audiobook listening.',
    intro_p2: 'Hard to believe, but Android lacked a genuinely good local audiobook player—either missing key features (skip intro/outro, playback speed, sleep timer), overly complex, or cluttered with ads. So I developed AuBookPlayer based on Auxio, designed specifically to play local audiobooks simply and easily.',
    download_apk: 'Download APK (Releases)',
    github_source: 'GitHub Source',
    feature_speed: 'Playback Speed',
    feature_timer: 'Sleep Timer',
    feature_skip: 'Smart Skip Intro/Outro',
    feature_playlist: 'Playlist Centric',
    feature_cover: 'Keyword Auto Cover',
    feature_memory: 'Remember Progress',
    feature_history: 'Played History',
    feature_multiline: 'Multi-line Titles',
    footer_text: '© 2026 AuBookPlayer · Open source under GPL-3.0 on <a href="https://github.com/Qzrzz/AuBookPlayer" target="_blank" rel="noopener noreferrer">GitHub</a>'
  },
  'ja': {
    code: 'ja',
    path: 'ja',
    name: '日本語',
    title: 'AuBookPlayer - シンプルで使いやすい Android オーディオブックプレーヤー',
    description: 'AuBookPlayer は Android 向けのシンプルなローカルオーディオブックプレーヤーです。Auxio をベースに頭尾スキップ、倍速再生、スリープタイマーをサポート。',
    keywords: 'AuBookPlayer, オーディオブック, Android, プレーヤー, Auxio, 倍速再生, オープンソース',
    brand_tagline: 'シンプルで使いやすい Android ローカルオーディオブックプレーヤー',
    intro_p1: 'オープンソースの音楽プレーヤー <a href="https://github.com/oxygencobalt/Auxio" target="_blank" rel="noopener noreferrer">Auxio</a> をベースに、ローカルオーディオブックの再生に最適化して開発されました。',
    intro_p2: 'Android には使いやすいローカルオーディオブックプレーヤーが意外にも存在せず、機能不足（頭尾スキップ、倍速再生、スリープタイマー）、複雑で使いにくい、あるいは広告が多すぎました。そこで Auxio をベースに、シンプルで使いやすい AuBookPlayer を開発しました。',
    download_apk: 'APK をダウンロード (Releases)',
    github_source: 'GitHub ソースコード',
    feature_speed: '倍速再生',
    feature_timer: 'スリープタイマー',
    feature_skip: 'スマート頭尾スキップ',
    feature_playlist: 'プレイリスト中心',
    feature_cover: 'キーワード自動カバー',
    feature_memory: '再生進捗の記憶',
    feature_history: '再生済み履歴',
    feature_multiline: '長いタイトルの複数行表示',
    footer_text: '© 2026 AuBookPlayer · GPL-3.0 ライセンスで <a href="https://github.com/Qzrzz/Auxio" target="_blank" rel="noopener noreferrer">GitHub</a> に公開'
  },
  'ko': {
    code: 'ko',
    path: 'ko',
    name: '한국어',
    title: 'AuBookPlayer - 간단하고 깔끔한 Android 로컬 오디오북 플레이어',
    description: 'AuBookPlayer는 Android용 로컬 오디오북 플레이어입니다. Auxio 기반으로 건너뛰기, 배속 재생, 수면 타이머, 위치 기억을 지원합니다.',
    keywords: 'AuBookPlayer, 오디오북 플레이어, Android, Auxio, 배속 재생, 오픈소스',
    brand_tagline: '간단하고 사용하기 쉬운 Android 로컬 오디오북 플레이어',
    intro_p1: '오픈 소스 뮤직 플레이어 <a href="https://github.com/oxygencobalt/Auxio" target="_blank" rel="noopener noreferrer">Auxio</a>를 기반으로 로컬 오디오북 재생 환경에 맞게 개조되었습니다.',
    intro_p2: 'Android에는 제대로 된 로컬 오디오북 플레이어가 드물었습니다. 필수 기능(인트로/아웃트로 건너뛰기, 배속 재생, 수면 타이머)이 없거나 복잡하고 광고가 많았습니다. 그래서 Auxio를 기반으로 오직 편리한 오디오북 감상을 위해 AuBookPlayer를 개발했습니다.',
    download_apk: 'APK 다운로드 (Releases)',
    github_source: 'GitHub 소스 코드',
    feature_speed: '배속 재생',
    feature_timer: '수면 타이머',
    feature_skip: '스마트 건너뛰기',
    feature_playlist: '재생목록 중심',
    feature_cover: '키워드 자동 커버',
    feature_memory: '재생 위치 기억',
    feature_history: '재생 완료 기록',
    feature_multiline: '긴 제목 여러 줄 표시',
    footer_text: '© 2026 AuBookPlayer · GPL-3.0 라이선스 기반 <a href="https://github.com/Qzrzz/AuBookPlayer" target="_blank" rel="noopener noreferrer">GitHub</a> 오픈 소스'
  },
  'de': {
    code: 'de',
    path: 'de',
    name: 'Deutsch',
    title: 'AuBookPlayer - Einfacher lokaler Hörbuch-Player für Android',
    description: 'AuBookPlayer ist ein lokaler Hörbuch-Player für Android. Basiert auf Auxio, unterstützt Intro/Outro-Überspringen, Wiedergabegeschwindigkeit und Sleep-Timer.',
    keywords: 'AuBookPlayer, Hörbuch Player, Android, Auxio, Wiedergabegeschwindigkeit, Open Source',
    brand_tagline: 'Ein einfacher lokaler Hörbuch-Player für Android.',
    intro_p1: 'Basiert auf dem Open-Source-Musikplayer <a href="https://github.com/oxygencobalt/Auxio" target="_blank" rel="noopener noreferrer">Auxio</a>, optimiert für lokale Hörbücher.',
    intro_p2: 'Auf Android fehlte ein wirklich guter lokaler Hörbuch-Player – entweder fehlten Funktionen (Intro/Outro überspringen, Wiedergabegeschwindigkeit, Sleep-Timer), er war zu kompliziert oder voller Werbung. Aus diesem Grund wurde AuBookPlayer entwickelt.',
    download_apk: 'APK herunterladen (Releases)',
    github_source: 'GitHub Quellcode',
    feature_speed: 'Wiedergabegeschwindigkeit',
    feature_timer: 'Sleep-Timer',
    feature_skip: 'Intro/Outro überspringen',
    feature_playlist: 'Wiedergabelisten-Fokus',
    feature_cover: 'Auto Cover-Erstellung',
    feature_memory: 'Fortschritt speichern',
    feature_history: 'Wiedergabeverlauf',
    feature_multiline: 'Mehrzeilige Titel',
    footer_text: '© 2026 AuBookPlayer · Open Source unter GPL-3.0 auf <a href="https://github.com/Qzrzz/AuBookPlayer" target="_blank" rel="noopener noreferrer">GitHub</a>'
  },
  'fr': {
    code: 'fr',
    path: 'fr',
    name: 'Français',
    title: 'AuBookPlayer - Lecteur de livres audio locaux simple pour Android',
    description: 'AuBookPlayer est un lecteur de livres audio locaux pour Android. Basé sur Auxio, supportant la vitesse de lecture, le minuteur et le saut intro/outro.',
    keywords: 'AuBookPlayer, livre audio, Android, lecteur local, Auxio, vitesse de lecture, open source',
    brand_tagline: 'Un lecteur de livres audio locaux simple pour Android.',
    intro_p1: 'Basé sur le lecteur de musique open-source <a href="https://github.com/oxygencobalt/Auxio" target="_blank" rel="noopener noreferrer">Auxio</a>, adapté pour l\'écoute de livres audio locaux.',
    intro_p2: 'Android manquait d\'un bon lecteur de livres audio locaux — soit sans fonctionnalités clés (passer intro/outro, vitesse de lecture, minuteur), soit trop complexe, soit rempli de publicités. AuBookPlayer a été créé pour être simple et efficace.',
    download_apk: 'Télécharger l\'APK (Releases)',
    github_source: 'Code source GitHub',
    feature_speed: 'Vitesse de lecture',
    feature_timer: 'Minuteur de sommeil',
    feature_skip: 'Passer l\'intro/outro',
    feature_playlist: 'Centré sur les playlists',
    feature_cover: 'Pochette automatique',
    feature_memory: 'Mémorisation progression',
    feature_history: 'Historique d\'écoute',
    feature_multiline: 'Titres sur plusieurs lignes',
    footer_text: '© 2026 AuBookPlayer · Open source sous GPL-3.0 sur <a href="https://github.com/Qzrzz/AuBookPlayer" target="_blank" rel="noopener noreferrer">GitHub</a>'
  },
  'es': {
    code: 'es',
    path: 'es',
    name: 'Español',
    title: 'AuBookPlayer - Reproductor de audiolibros locales simple para Android',
    description: 'AuBookPlayer es un reproductor de audiolibros locales para Android. Basado en Auxio, soporta velocidad de reproducción, temporizador y salto de inicio/final.',
    keywords: 'AuBookPlayer, audiolibros, Android, reproductor local, Auxio, velocidad, código abierto',
    brand_tagline: 'Un reproductor de audiolibros locales simple para Android.',
    intro_p1: 'Basado en el reproductor de música de código abierto <a href="https://github.com/oxygencobalt/Auxio" target="_blank" rel="noopener noreferrer">Auxio</a>, adaptado para audiolibros locales.',
    intro_p2: 'En Android faltaba un buen reproductor de audiolibros locales: o faltaban funciones esenciales (saltar inicio/final, velocidad, temporizador), o era complejo, o tenía anuncios. Por eso nació AuBookPlayer.',
    download_apk: 'Descargar APK (Releases)',
    github_source: 'Código fuente GitHub',
    feature_speed: 'Velocidad de reproducción',
    feature_timer: 'Temporizador de apagado',
    feature_skip: 'Salto inicio/final',
    feature_playlist: 'Listas de reproducción',
    feature_cover: 'Portada automática',
    feature_memory: 'Recordar progreso',
    feature_history: 'Historial escuchados',
    feature_multiline: 'Títulos multilínea',
    footer_text: '© 2026 AuBookPlayer · Código abierto bajo GPL-3.0 en <a href="https://github.com/Qzrzz/AuBookPlayer" target="_blank" rel="noopener noreferrer">GitHub</a>'
  },
  'ru': {
    code: 'ru',
    path: 'ru',
    name: 'Русский',
    title: 'AuBookPlayer - Простой плеер локальных аудиокниг для Android',
    description: 'AuBookPlayer — локальный плеер аудиокниг для Android на основе Auxio. Поддерживает скорость воспроизведения, таймер сна и пропуск вступления.',
    keywords: 'AuBookPlayer, аудиокниги, Android, локальный плеер, Auxio, скорость воспроизведения, открытый код',
    brand_tagline: 'Простой локальный плеер аудиокниг для Android.',
    intro_p1: 'Создан на основе открытого музыкального плеера <a href="https://github.com/oxygencobalt/Auxio" target="_blank" rel="noopener noreferrer">Auxio</a> для прослушивания локальных аудиокниг.',
    intro_p2: 'На Android не хватало удобного плеера аудиокниг — либо отсутствовали нужные функции (пропуск вступления/концовки, скорость, таймер сна), либо приложение было сложным и с рекламой. Поэтому появился AuBookPlayer.',
    download_apk: 'Скачать APK (Releases)',
    github_source: 'Исходный код GitHub',
    feature_speed: 'Скорость воспроизведения',
    feature_timer: 'Таймер сна',
    feature_skip: 'Пропуск вступления/концовки',
    feature_playlist: 'Фокус на плейлистах',
    feature_cover: 'Автообложка',
    feature_memory: 'Запоминание прогресса',
    feature_history: 'История прослушиваний',
    feature_multiline: 'Многострочные названия',
    footer_text: '© 2026 AuBookPlayer · Открытый код по лицензии GPL-3.0 на <a href="https://github.com/Qzrzz/AuBookPlayer" target="_blank" rel="noopener noreferrer">GitHub</a>'
  }
};

/**
 * 生成带有静态文本、hreflang 和 SEO 标签的 HTML 内容
 * @param {string} langKey 语言 Key (例如 'zh-CN', 'en')
 * @returns {string} 渲染后的全量 HTML 字符串
 */
function renderHtmlForLang(langKey) {
  const lang = LANGUAGES[langKey];
  const isRoot = lang.path === '';
  const prefix = isRoot ? '.' : '..';

  // 构建 hreflang 链接
  const hreflangLinks = Object.keys(LANGUAGES).map(key => {
    const target = LANGUAGES[key];
    const relUrl = target.path === '' ? (isRoot ? './' : '../') : (isRoot ? `./${target.path}/` : `../${target.path}/`);
    return `  <link rel="alternate" hreflang="${target.code.toLowerCase()}" href="${relUrl}">`;
  }).join('\n');

  // 构建静态语言选择下拉 Option
  const langOptions = Object.keys(LANGUAGES).map(key => {
    const target = LANGUAGES[key];
    const relUrl = target.path === '' ? (isRoot ? './' : '../') : (isRoot ? `./${target.path}/` : `../${target.path}/`);
    const selected = key === langKey ? 'selected' : '';
    return `<option value="${relUrl}" ${selected}>${target.name}</option>`;
  }).join('\n        ');

  return `<!DOCTYPE html>
<html lang="${lang.code}">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>${lang.title}</title>
  <meta name="description" content="${lang.description}">
  <meta name="keywords" content="${lang.keywords}">
  
  <!-- SEO Hreflang Tags -->
${hreflangLinks}
  
  <!-- Open Graph -->
  <meta property="og:title" content="${lang.title}">
  <meta property="og:description" content="${lang.description}">
  <meta property="og:type" content="website">
  
  <!-- 字体预加载 -->
  <link rel="preconnect" href="https://fonts.googleapis.com">
  <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
  <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700;800&display=swap" rel="stylesheet">
  
  <link rel="icon" href="${prefix}/app-icon.png" type="image/png">
  <link rel="stylesheet" href="${prefix}/style.css">
</head>
<body>

  <div class="container">
    <!-- 语言切换下拉菜单 (SEO Friendly Static Link Selector) -->
    <div class="lang-selector-wrapper" title="Language / 语言">
      <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
        <circle cx="12" cy="12" r="10"/>
        <line x1="2" y1="12" x2="22" y2="12"/>
        <path d="M12 2a15.3 15.3 0 0 1 4 10 15.3 15.3 0 0 1-4 10 15.3 15.3 0 0 1-4-10 15.3 15.3 0 0 1 4-10z"/>
      </svg>
      <select id="lang-select" class="lang-select" aria-label="Select Language" onchange="location.href=this.value">
        ${langOptions}
      </select>
    </div>

    <!-- Hero 头部区域 -->
    <header class="hero-section">
      <div class="app-icon" title="AuBookPlayer">
        <img src="${prefix}/app-icon.png" alt="AuBookPlayer Icon">
      </div>
      
      <h1 class="brand-title">AuBookPlayer</h1>
      <p class="brand-tagline">${lang.brand_tagline}</p>
      
      <!-- 开发初衷与简介卡片 -->
      <div class="intro-box">
        <p>${lang.intro_p1}</p>
        <p>${lang.intro_p2}</p>
      </div>
      
      <div class="cta-group">
        <a href="https://github.com/Qzrzz/AuBookPlayer/releases" target="_blank" rel="noopener noreferrer" class="btn btn-primary">
          <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/>
            <polyline points="7 10 12 15 17 10"/>
            <line x1="12" y1="15" x2="12" y2="3"/>
          </svg>
          <span>${lang.download_apk}</span>
        </a>
        <a href="https://github.com/Qzrzz/AuBookPlayer" target="_blank" rel="noopener noreferrer" class="btn btn-secondary">
          <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <path d="M15 22v-4a4.8 4.8 0 0 0-1-3.5c3 0 6-2 6-5.5.08-1.25-.27-2.48-1-3.5.28-1.15.28-2.35 0-3.5 0 0-1 0-3 1.5-2.64-.5-5.36-.5-8 0C6 2 5 2 5 2c-.3 1.15-.3 2.35 0 3.5A5.403 5.403 0 0 0 4 9c0 3.5 3 5.5 6 5.5-.39.49-.68 1.05-.85 1.65-.17.6-.22 1.23-.15 1.85v4"/>
            <path d="M9 18c-4.51 2-5-2-7-2"/>
          </svg>
          <span>${lang.github_source}</span>
        </a>
      </div>
    </header>

    <!-- 特性标签区域 -->
    <section class="features-section" aria-label="Features">
      <div class="feature-grid">
        <div class="feature-chip">
          <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><polygon points="10 8 16 12 10 16 10 8"/></svg>
          <span>${lang.feature_speed}</span>
        </div>
        <div class="feature-chip">
          <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z"/></svg>
          <span>${lang.feature_timer}</span>
        </div>
        <div class="feature-chip">
          <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="6" cy="6" r="3"/><circle cx="6" cy="18" r="3"/><line x1="20" y1="4" x2="8.12" y2="15.88"/><line x1="14.47" y1="14.47" x2="20" y2="20"/><line x1="8.12" y1="8.12" x2="12" y2="12"/></svg>
          <span>${lang.feature_skip}</span>
        </div>
        <div class="feature-chip">
          <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="8" y1="6" x2="21" y2="6"/><line x1="8" y1="12" x2="21" y2="12"/><line x1="8" y1="18" x2="21" y2="18"/><line x1="3" y1="6" x2="3.01" y2="6"/><line x1="3" y1="12" x2="3.01" y2="12"/><line x1="3" y1="18" x2="3.01" y2="18"/></svg>
          <span>${lang.feature_playlist}</span>
        </div>
        <div class="feature-chip">
          <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="3" width="18" height="18" rx="2" ry="2"/><circle cx="8.5" cy="8.5" r="1.5"/><polyline points="21 15 16 10 5 21"/></svg>
          <span>${lang.feature_cover}</span>
        </div>
        <div class="feature-chip">
          <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/></svg>
          <span>${lang.feature_memory}</span>
        </div>
        <div class="feature-chip">
          <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/><polyline points="22 4 12 14.01 9 11.01"/></svg>
          <span>${lang.feature_history}</span>
        </div>
        <div class="feature-chip">
          <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="4 7 4 4 20 4 20 7"/><line x1="9" y1="20" x2="15" y2="20"/><line x1="12" y1="4" x2="12" y2="20"/></svg>
          <span>${lang.feature_multiline}</span>
        </div>
      </div>
    </section>

    <!-- 截图画廊展示区域 -->
    <main class="gallery-section">
      <div class="gallery-grid" id="gallery-grid">
        <!-- 带壳截图 (开头展示、无描边) -->
        <div class="screenshot-card frameless">
          <img src="${prefix}/images/s-01.png" alt="AuBookPlayer Player Interface" loading="lazy">
          <div class="card-overlay">
            <div class="view-btn">
              <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/><line x1="11" y1="8" x2="11" y2="14"/><line x1="8" y1="11" x2="14" y2="11"/></svg>
            </div>
          </div>
        </div>

        <div class="screenshot-card frameless">
          <img src="${prefix}/images/s-02.png" alt="AuBookPlayer Playlist" loading="lazy">
          <div class="card-overlay">
            <div class="view-btn">
              <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/><line x1="11" y1="8" x2="11" y2="14"/><line x1="8" y1="11" x2="14" y2="11"/></svg>
            </div>
          </div>
        </div>

        <!-- 其它截图 -->
        <div class="screenshot-card">
          <img src="${prefix}/images/s-00.jpg" alt="AuBookPlayer Light Main" loading="lazy">
          <div class="card-overlay">
            <div class="view-btn">
              <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/><line x1="11" y1="8" x2="11" y2="14"/><line x1="8" y1="11" x2="14" y2="11"/></svg>
            </div>
          </div>
        </div>

        <div class="screenshot-card">
          <img src="${prefix}/images/s-dark-00.jpg" alt="AuBookPlayer Dark Main" loading="lazy">
          <div class="card-overlay">
            <div class="view-btn">
              <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/><line x1="11" y1="8" x2="11" y2="14"/><line x1="8" y1="11" x2="14" y2="11"/></svg>
            </div>
          </div>
        </div>

        <div class="screenshot-card">
          <img src="${prefix}/images/s-dark-01.jpg" alt="AuBookPlayer Dark Player" loading="lazy">
          <div class="card-overlay">
            <div class="view-btn">
              <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/><line x1="11" y1="8" x2="11" y2="14"/><line x1="8" y1="11" x2="14" y2="11"/></svg>
            </div>
          </div>
        </div>

        <div class="screenshot-card">
          <img src="${prefix}/images/s-dark-02.jpg" alt="AuBookPlayer Dark Settings" loading="lazy">
          <div class="card-overlay">
            <div class="view-btn">
              <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/><line x1="11" y1="8" x2="11" y2="14"/><line x1="8" y1="11" x2="14" y2="11"/></svg>
            </div>
          </div>
        </div>
      </div>
    </main>

    <!-- 页脚 -->
    <footer class="footer">
      <p>${lang.footer_text}</p>
    </footer>
  </div>

  <!-- 大图预览 Lightbox Modal -->
  <div class="lightbox" id="lightbox" aria-hidden="true">
    <button class="lightbox-close" aria-label="Close">
      <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
    </button>
    <div class="lightbox-content">
      <img id="lightbox-img" src="" alt="Preview">
    </div>
  </div>

  <script src="${prefix}/script.js"></script>
</body>
</html>`;
}

// 构建全量 HTML 页面
Object.keys(LANGUAGES).forEach(langKey => {
  const lang = LANGUAGES[langKey];
  const htmlContent = renderHtmlForLang(langKey);

  if (lang.path === '') {
    // 默认根目录 index.html
    const filePath = path.join(DOCS_DIR, 'index.html');
    fs.writeFileSync(filePath, htmlContent, 'utf-8');
    console.log(`[SEO-i18n] Generated: docs/index.html (${langKey})`);
  } else {
    // 子语言目录 index.html
    const subDir = path.join(DOCS_DIR, lang.path);
    if (!fs.existsSync(subDir)) {
      fs.mkdirSync(subDir, { recursive: true });
    }
    const filePath = path.join(subDir, 'index.html');
    fs.writeFileSync(filePath, htmlContent, 'utf-8');
    console.log(`[SEO-i18n] Generated: docs/${lang.path}/index.html (${langKey})`);
  }
});

console.log('✅ All SEO-friendly static multilingual pages generated successfully!');
