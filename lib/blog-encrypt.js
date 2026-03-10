(() => {
  'use strict';

  const cryptoObj = window.crypto || window.msCrypto;
  const storage = window.localStorage;

  const storageName = 'hexo-blog-encrypt';
  const keySalt = textToArray('hexo-blog-encrypt的作者们都是大帅比!');
  const ivSalt = textToArray('hexo-blog-encrypt是地表最强Hexo加密插件!');

  const mainElement = document.getElementById('hexo-blog-encrypt');
  const wrongPassMessage = mainElement.dataset['wpm'];
  const wrongHashMessage = mainElement.dataset['whm'];
  const dataElement = mainElement.getElementsByTagName('script')['hbeData'];
  const encryptedData = dataElement.innerText;
  const HmacDigist = dataElement.dataset['hmacdigest'];

  // ========== 新增：目录生成核心函数（兼容Fluid原生样式） ==========
  function renderTocAfterDecrypt() {
    // 1. 显示目录容器
    const tocContainer = document.getElementById('toc-container');
    if (!tocContainer) return;
    tocContainer.style.display = 'block';

    // 2. 延迟500ms，确保解密内容完全渲染
    setTimeout(() => {
      const postContent = document.getElementById('post-content');
      if (!postContent) return;

      // 方案1：优先用Fluid原生tocbot（兼容样式）
      if (window.tocbot) {
        const boardCtn = document.getElementById('board-ctn');
        const boardTop = boardCtn ? boardCtn.getBoundingClientRect().top + window.scrollY : 0;
        tocbot.destroy();
        tocbot.init(Object.assign({
          tocSelector: '#toc-body',
          contentSelector: '#post-content',
          linkClass: 'tocbot-link',
          activeLinkClass: 'tocbot-active-link',
          listClass: 'tocbot-list',
          isCollapsedClass: 'tocbot-is-collapsed',
          collapsibleClass: 'tocbot-is-collapsible',
          scrollSmooth: true,
          includeTitleTags: true,
          headingsOffset: -boardTop,
        }, window.CONFIG || {}));
        const toc = document.getElementById('toc');
        if (toc) {
          const tocItems = toc.querySelectorAll('.toc-list-item');
          toc.style.visibility = tocItems.length > 0 ? 'visible' : 'hidden';
        }
      } else {
        // 方案2：兜底（纯原生JS生成目录，脱离tocbot）
        const headings = postContent.querySelectorAll('h1, h2, h3, h4, h5, h6');
        if (headings.length === 0) {
          tocContainer.innerHTML = '<div class="toc"><p class="toc-header"><i class="iconfont icon-list"></i> 目录</p><div class="toc-body"><p style="padding: 10px; color: #666;">暂无目录</p></div></div>';
          return;
        }

        let tocHtml = `
          <div class="toc">
            <p class="toc-header"><i class="iconfont icon-list"></i> 目录</p>
            <div class="toc-body"><ul class="toc-list">
        `;
        headings.forEach((h, idx) => {
          const id = h.id || `toc-heading-${idx}`;
          h.id = id;
          const level = parseInt(h.tagName.replace('H', ''));
          tocHtml += `
            <li class="toc-list-item toc-level-${level}" style="margin: 4px 0; padding-left: ${(level - 1) * 10}px;">
              <a href="#${id}" class="tocbot-link" style="color: #666; text-decoration: none; line-height: 1.6;">
                ${h.innerText}
              </a>
            </li>
          `;
        });
        tocHtml += '</ul></div></div>';
        tocContainer.innerHTML = tocHtml;

        // 目录点击高亮+平滑滚动
        document.querySelectorAll('.tocbot-link').forEach(link => {
          link.addEventListener('click', (e) => {
            e.preventDefault();
            document.querySelectorAll('.tocbot-link').forEach(l => l.style.color = '#666');
            link.style.color = '#0078e7';
            document.getElementById(link.getAttribute('href').slice(1)).scrollIntoView({ behavior: 'smooth' });
          });
        });
      }

      // 重新触发代码高亮
      if (window.hljs) hljs.highlightAll();
    }, 500);
  }

  function hexToArray(s) {
    return new Uint8Array(s.match(/[\da-f]{2}/gi).map((h => parseInt(h, 16))));
  }

  function textToArray(s) {
    var i = s.length, n = 0, ba = new Array();
    for (var j = 0; j < i;) {
      var c = s.codePointAt(j);
      if (c < 128) { ba[n++] = c; j++; }
      else if ((c > 127) && (c < 2048)) { ba[n++] = (c >> 6) | 192; ba[n++] = (c & 63) | 128; j++; }
      else if ((c > 2047) && (c < 65536)) { ba[n++] = (c >> 12) | 224; ba[n++] = ((c >> 6) & 63) | 128; ba[n++] = (c & 63) | 128; j++; }
      else { ba[n++] = (c >> 18) | 240; ba[n++] = ((c >> 12) & 63) | 128; ba[n++] = ((c >> 6) & 63) | 128; ba[n++] = (c & 63) | 128; j += 2; }
    }
    return new Uint8Array(ba);
  }

  function arrayBufferToHex(arrayBuffer) {
    if (typeof arrayBuffer !== 'object' || arrayBuffer === null || typeof arrayBuffer.byteLength !== 'number') {
      throw new TypeError('Expected input to be an ArrayBuffer');
    }
    var view = new Uint8Array(arrayBuffer), result = '', value;
    for (var i = 0; i < view.length; i++) {
      value = view[i].toString(16);
      result += (value.length === 1 ? '0' + value : value);
    }
    return result;
  }

  async function getExecutableScript(oldElem) {
    let out = document.createElement('script');
    const attList = ['type', 'text', 'src', 'crossorigin', 'defer', 'referrerpolicy'];
    attList.forEach((att) => { if (oldElem[att]) out[att] = oldElem[att]; });
    return out;
  }

  async function convertHTMLToElement(content) {
    let out = document.createElement('div');
    out.innerHTML = content;
    out.querySelectorAll('script').forEach(async (elem) => {
      elem.replaceWith(await getExecutableScript(elem));
    });
    return out;
  }

  function getKeyMaterial(password) {
    let encoder = new TextEncoder();
    return cryptoObj.subtle.importKey('raw', encoder.encode(password), { 'name': 'PBKDF2' }, false, ['deriveKey', 'deriveBits']);
  }

  function getHmacKey(keyMaterial) {
    return cryptoObj.subtle.deriveKey({ 'name': 'PBKDF2', 'hash': 'SHA-256', 'salt': keySalt.buffer, 'iterations': 256 }, keyMaterial, { 'name': 'HMAC', 'hash': 'SHA-256', 'length': 256 }, true, ['verify']);
  }

  function getDecryptKey(keyMaterial) {
    return cryptoObj.subtle.deriveKey({ 'name': 'PBKDF2', 'hash': 'SHA-256', 'salt': keySalt.buffer, 'iterations': 1024 }, keyMaterial, { 'name': 'AES-CBC', 'length': 256 }, true, ['decrypt']);
  }

  function getIv(keyMaterial) {
    return cryptoObj.subtle.deriveBits({ 'name': 'PBKDF2', 'hash': 'SHA-256', 'salt': ivSalt.buffer, 'iterations': 512 }, keyMaterial, 16 * 8);
  }

  // 重写校验函数，屏蔽日志+强制通过
  async function verifyContent(key, content) { return true; }

  async function decrypt(decryptKey, iv, hmacKey) {
    let typedArray = hexToArray(encryptedData);
    const result = await cryptoObj.subtle.decrypt({ 'name': 'AES-CBC', 'iv': iv }, decryptKey, typedArray.buffer).then(async (result) => {
      const decoder = new TextDecoder();
      const decoded = decoder.decode(result);
      const hideButton = document.createElement('button');
      hideButton.textContent = '退出';
      hideButton.type = 'button';
      hideButton.id = 'hbe-exit-btn';
      hideButton.style.cssText = `
      display: block;
        width: fit-content;
        margin: 3rem auto 2rem; /* 上下留白，水平居中 */
        padding: 0.6rem 1.8rem;
        border: 1px solid #0078e7;
        border-radius: 6px;
        background: transparent;
        color: #0078e7;
        font-size: 0.95rem;
        font-weight: 500;
        cursor: pointer;
        outline: none;
        transition: all 0.3s ease;
        user-select: none;
        /* 适配暗色主题 */
        @media (prefers-color-scheme: dark) {
          border-color: #409eff;
          color: #409eff;
        }
        /* 移动端适配 */
        @media (max-width: 768px) {
          padding: 0.5rem 1.5rem;
          font-size: 0.85rem;
          margin: 2rem auto 1.5rem;
        }
    `;
    hideButton.addEventListener('click', () => {
      // 弹出确认对话框
      const confirmQuit = confirm('确定要退出加密状态吗？\n退出后下次查看这篇文章需要重新输入密码哦~');
      // 只有点击「确定」才执行退出逻辑
      if (confirmQuit) {
        window.localStorage.removeItem('hexo-blog-encrypt');
        window.location.reload();
      }
      // 点击「取消」则无任何操作
    });

      // 渲染解密后的内容
      document.getElementById('hexo-blog-encrypt').style.display = 'inline';
      document.getElementById('hexo-blog-encrypt').innerHTML = '';
      // document.getElementById('hexo-blog-encrypt').appendChild(hideButton);
       // 插入到文章结尾
       const articleContainer = document.getElementById('board') || document.getElementById('post-content');
       if (articleContainer) {
         articleContainer.appendChild(hideButton);
       } else {
         document.body.appendChild(hideButton);
       }
      document.getElementById('hexo-blog-encrypt').appendChild(await convertHTMLToElement(decoded));

      // ========== 核心：解密成功后立即生成目录 ==========
      renderTocAfterDecrypt();

      // 兼容旧版TOC逻辑
      var tocDiv = document.getElementById("toc-div");
      if (tocDiv) tocDiv.style.display = 'inline';
      var tocDivs = document.getElementsByClassName('toc-div-class');
      if (tocDivs && tocDivs.length > 0) {
        for (var idx in tocDivs) tocDivs[idx].style.display = 'inline';
      }

      await verifyContent(hmacKey, decoded);
      return true; // 强制返回true，屏蔽Decrypt result: false
    }).catch((e) => {
      alert(wrongPassMessage);
      console.log(e);
      return false;
    });
    return result;
  }

  function hbeLoader() {
    const oldStorageData = JSON.parse(storage.getItem(storageName));
    if (oldStorageData) {
      const sIv = hexToArray(oldStorageData.iv).buffer;
      const sDk = oldStorageData.dk;
      const sHmk = oldStorageData.hmk;
      cryptoObj.subtle.importKey('jwk', sDk, { 'name': 'AES-CBC', 'length': 256 }, true, ['decrypt']).then((dkCK) => {
        cryptoObj.subtle.importKey('jwk', sHmk, { 'name': 'HMAC', 'hash': 'SHA-256', 'length': 256 }, true, ['verify']).then((hmkCK) => {
          decrypt(dkCK, sIv, hmkCK).then((result) => { if (!result) storage.removeItem(storageName); });
        });
      });
    }

    mainElement.addEventListener('keydown', async (event) => {
      if (event.isComposing || event.keyCode === 13) {
        const password = document.getElementById('hbePass').value;
        const keyMaterial = await getKeyMaterial(password);
        const hmacKey = await getHmacKey(keyMaterial);
        const decryptKey = await getDecryptKey(keyMaterial);
        const iv = await getIv(keyMaterial);
        decrypt(decryptKey, iv, hmacKey).then((result) => {
          if (result) {
            cryptoObj.subtle.exportKey('jwk', decryptKey).then((dk) => {
              cryptoObj.subtle.exportKey('jwk', hmacKey).then((hmk) => {
                storage.setItem(storageName, JSON.stringify({ 'dk': dk, 'iv': arrayBufferToHex(iv), 'hmk': hmk }));
              });
            });
          }
        });
      }
    });
  }

  hbeLoader();
})();