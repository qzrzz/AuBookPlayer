/**
 * @file AuBookPlayer 官网多语言 (I18n) 字典与逻辑模块
 * @description 支持 Android 软件原生的多语言切换与浏览器语言自动识别
 */

const I18N_DATA = {
  'zh-CN': {
    name: '简体中文',
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
    name: '繁體中文',
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
    name: 'English',
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
    name: '日本語',
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
    footer_text: '© 2026 AuBookPlayer · GPL-3.0 ライセンスで <a href="https://github.com/Qzrzz/AuBookPlayer" target="_blank" rel="noopener noreferrer">GitHub</a> に公開'
  },
  'ko': {
    name: '한국어',
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
    name: 'Deutsch',
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
    name: 'Français',
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
    name: 'Español',
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
    name: 'Русский',
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
 * 获取当前匹配的语言 code
 * @returns {string} 语言代码，如 'zh-CN', 'en'
 */
function getCurrentLang() {
  const saved = localStorage.getItem('aubookplayer_lang');
  if (saved && I18N_DATA[saved]) {
    return saved;
  }
  
  // 根据浏览器 navigator.language 自动判断
  const navLang = navigator.language || navigator.userLanguage || '';
  if (navLang.startsWith('zh')) {
    if (navLang.includes('TW') || navLang.includes('HK') || navLang.includes('Hant')) {
      return 'zh-TW';
    }
    return 'zh-CN';
  }
  
  const shortLang = navLang.split('-')[0].toLowerCase();
  if (I18N_DATA[shortLang]) {
    return shortLang;
  }
  
  return 'en'; // 默认回退为 English
}

/**
 * 切换并应用页面语言
 * @param {string} lang - 目标语言 Key
 */
function setLanguage(lang) {
  if (!I18N_DATA[lang]) return;
  
  localStorage.setItem('aubookplayer_lang', lang);
  document.documentElement.lang = lang;
  
  const dict = I18N_DATA[lang];
  
  // 更新包含 data-i18n 的普通文本节点
  document.querySelectorAll('[data-i18n]').forEach(el => {
    const key = el.getAttribute('data-i18n');
    if (dict[key]) {
      el.textContent = dict[key];
    }
  });
  
  // 更新包含 data-i18n-html 的富文本/HTML节点
  document.querySelectorAll('[data-i18n-html]').forEach(el => {
    const key = el.getAttribute('data-i18n-html');
    if (dict[key]) {
      el.innerHTML = dict[key];
    }
  });
  
  // 更新下拉菜单选中项
  const selector = document.getElementById('lang-select');
  if (selector) {
    selector.value = lang;
  }
}

/**
 * 初始化多语言下拉选择框与事件
 */
function initI18n() {
  const selector = document.getElementById('lang-select');
  if (selector) {
    // 动态构建 <option>
    selector.innerHTML = '';
    Object.keys(I18N_DATA).forEach(langKey => {
      const opt = document.createElement('option');
      opt.value = langKey;
      opt.textContent = I18N_DATA[langKey].name;
      selector.appendChild(opt);
    });
    
    selector.addEventListener('change', (e) => {
      setLanguage(e.target.value);
    });
  }
  
  // 初始化渲染当前语言
  const initialLang = getCurrentLang();
  setLanguage(initialLang);
}

document.addEventListener('DOMContentLoaded', () => {
  initI18n();
});
