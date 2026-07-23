/**
 * @file AuBookPlayer 官网交互脚本
 * @description 处理截图大图灯箱预览功能
 */

document.addEventListener('DOMContentLoaded', () => {
  initLightbox();
});

/**
 * 初始化截图灯箱 (Lightbox) 全屏放大查看逻辑
 */
function initLightbox() {
  const lightbox = document.getElementById('lightbox');
  const lightboxImg = document.getElementById('lightbox-img');
  const closeBtn = document.querySelector('.lightbox-close');
  const cards = document.querySelectorAll('.screenshot-card');

  if (!lightbox || !lightboxImg || !closeBtn) return;

  /**
   * 打开大图灯箱
   * @param {string} src - 图片资源路径
   * @param {string} alt - 图片替代文本
   */
  const openLightbox = (src, alt) => {
    lightboxImg.src = src;
    lightboxImg.alt = alt || '截图预览';
    lightbox.classList.add('open');
    document.body.style.overflow = 'hidden';
  };

  /**
   * 关闭大图灯箱
   */
  const closeLightbox = () => {
    lightbox.classList.remove('open');
    document.body.style.overflow = '';
  };

  // 绑定每一个截图卡片的点击事件
  cards.forEach(card => {
    card.addEventListener('click', () => {
      const img = card.querySelector('img');
      if (img) {
        openLightbox(img.src, img.alt);
      }
    });
  });

  // 点击关闭按钮关闭
  closeBtn.addEventListener('click', closeLightbox);

  // 点击背景遮罩关闭
  lightbox.addEventListener('click', (e) => {
    if (e.target === lightbox) {
      closeLightbox();
    }
  });

  // 键盘 ESC 键关闭
  document.addEventListener('keydown', (e) => {
    if (e.key === 'Escape' && lightbox.classList.contains('open')) {
      closeLightbox();
    }
  });
}
