package com.local.webcaster.web

object DetectionScript {
    val script = """
        (() => {
          if (window.__localCasterInstalled) return;
          window.__localCasterInstalled = true;
          const documentStartedAt = Date.now();

          const mediaHint = /(?:\.m3u8?|\.mpd|\.mp4|\.m4v|\.webm)(?:[?#]|${'$'})|(?:manifest|playlist|master)|(?:mime|type)=video(?:%2f|\/)/i;
          const mediaMime = /^(?:video|audio)\/|mpegurl|dash\+xml/i;
          let pageUsesDrm = false;

          const safeUrl = value => {
            try {
              const raw = String(value || '');
              if (/^blob:https?:\/\//i.test(raw) && raw.length <= 8192) return raw;
              const u = new URL(raw, document.baseURI);
              return (u.protocol === 'http:' || u.protocol === 'https:') && u.href.length <= 8192 ? u.href : null;
            } catch (_) { return null; }
          };

          const send = data => {
            try {
              const bridge = window.LocalCasterBridge;
              if (!bridge || !data.url) return;
              bridge.postMessage(JSON.stringify(Object.assign({type: 'mediaCandidate', documentStartedAt}, data)));
            } catch (_) {}
          };

          const report = (value, source, mime, element, drm) => {
            const url = safeUrl(value);
            if (!url) return;
            const mediaElement = element || document.querySelector('video,audio');
            const poster = safeUrl(mediaElement && (mediaElement.poster || mediaElement.getAttribute && mediaElement.getAttribute('poster')));
            const tracks = [];
            try {
              if (mediaElement && mediaElement.querySelectorAll) {
                mediaElement.querySelectorAll('track[kind="subtitles"],track[kind="captions"]').forEach(track => {
                  const trackUrl = safeUrl(track.src || track.getAttribute('src'));
                  if (trackUrl && tracks.length < 32) tracks.push({
                    url: trackUrl,
                    label: String(track.label || track.srclang || 'Subtitles').slice(0, 100),
                    language: String(track.srclang || '').slice(0, 35),
                    mime: String(track.type || 'text/vtt').slice(0, 100),
                    default: Boolean(track.default),
                  });
                });
              }
            } catch (_) {}
            send({
              url,
              source,
              mime: String(mime || '').slice(0, 200),
              title: String(document.title || '').slice(0, 500),
              width: Number(mediaElement && mediaElement.videoWidth || 0),
              height: Number(mediaElement && mediaElement.videoHeight || 0),
              durationMs: Number.isFinite(mediaElement && mediaElement.duration) ? Math.round(mediaElement.duration * 1000) : 0,
              drm: Boolean(drm || pageUsesDrm),
              poster,
              tracks,
            });
          };

          const scanElement = element => {
            if (!element || !element.matches || !element.matches('video,audio')) return;
            report(element.currentSrc, 'VIDEO_CURRENT_SRC', element.getAttribute('type'), element);
            report(element.getAttribute('src'), 'DOM', element.getAttribute('type'), element);
            element.querySelectorAll('source').forEach(source => {
              report(source.src || source.getAttribute('src'), 'SOURCE_ELEMENT', source.type, element);
            });
          };

          const scanNode = root => {
            const nodes = [];
            if (root && root.matches && root.matches('video,audio,source,track')) nodes.push(root);
            if (root && root.querySelectorAll) nodes.push(...root.querySelectorAll('video,audio,source,track'));
            nodes.forEach(element => {
              if (element.matches('video,audio')) scanElement(element);
              else if (element.matches('track')) scanElement(element.parentElement);
              else report(
                element.src || element.getAttribute('src'),
                'SOURCE_ELEMENT',
                element.type,
                element.parentElement
              );
            });
          };

          let scanTimer = 0;
          const scheduleScan = () => {
            clearTimeout(scanTimer);
            scanTimer = setTimeout(() => scanNode(document), 120);
          };

          const installDomDetection = () => {
            scanNode(document);
            ['loadedmetadata', 'durationchange', 'canplay', 'emptied'].forEach(name => {
              document.addEventListener(name, event => scanElement(event.target), true);
            });
            document.addEventListener('encrypted', event => {
              pageUsesDrm = true;
              const media = event.target;
              report(media && (media.currentSrc || media.src), 'ENCRYPTED_MEDIA', media && media.getAttribute('type'), media, true);
            }, true);
            try {
              new MutationObserver(mutations => {
                let relevant = false;
                for (const mutation of mutations) {
                  if (mutation.type === 'attributes') {
                    relevant = true;
                    continue;
                  }
                  if ([...mutation.addedNodes].some(node => node.nodeType === 1 &&
                      (node.matches?.('video,audio,source,track') || node.querySelector?.('video,audio,source,track')))) {
                    relevant = true;
                  }
                }
                if (relevant) scheduleScan();
              }).observe(document.documentElement, {
                subtree: true,
                childList: true,
                attributes: true,
                attributeFilter: ['src', 'type', 'poster', 'kind', 'srclang', 'label', 'default'],
              });
            } catch (_) {}
          };

          const originalFetch = window.fetch;
          if (typeof originalFetch === 'function') {
            window.fetch = function(input) {
              let requestedUrl = '';
              try {
                requestedUrl = typeof input === 'string' || input instanceof URL ? String(input) : input && input.url;
                if (requestedUrl && mediaHint.test(requestedUrl)) report(requestedUrl, 'FETCH', '', null);
              } catch (_) {}
              return originalFetch.apply(this, arguments).then(response => {
                try {
                  const mime = response.headers && response.headers.get('content-type') || '';
                  if (mediaMime.test(mime) || mediaHint.test(response.url || requestedUrl)) {
                    report(response.url || requestedUrl, 'FETCH', mime, null);
                  }
                } catch (_) {}
                return response;
              });
            };
          }

          const originalOpen = XMLHttpRequest.prototype.open;
          XMLHttpRequest.prototype.open = function(method, url) {
            try {
              const requestedUrl = String(url || '');
              if (requestedUrl && mediaHint.test(requestedUrl)) report(requestedUrl, 'XHR', '', null);
              this.addEventListener('loadend', () => {
                try {
                  const mime = this.getResponseHeader('Content-Type') || '';
                  if (mediaMime.test(mime) || mediaHint.test(this.responseURL || requestedUrl)) {
                    report(this.responseURL || requestedUrl, 'XHR', mime, null);
                  }
                } catch (_) {}
              }, {once: true});
            } catch (_) {}
            return originalOpen.apply(this, arguments);
          };

          const inspectPerformanceEntry = entry => {
            try {
              if (mediaHint.test(entry.name) || /^(?:video|audio)$/i.test(entry.initiatorType || '')) {
                report(entry.name, 'PERFORMANCE', '', null);
              }
            } catch (_) {}
          };
          const scanPerformance = () => {
            try { performance.getEntriesByType('resource').forEach(inspectPerformanceEntry); } catch (_) {}
          };
          const rescan = () => {
            try {
              scanNode(document);
              scanPerformance();
              document.querySelectorAll('iframe,frame').forEach(frame => {
                try { frame.contentWindow && frame.contentWindow.postMessage('__localCasterRescan', '*'); } catch (_) {}
              });
            } catch (_) {}
            return true;
          };
          window.__localCasterRescan = rescan;
          window.addEventListener('message', event => {
            if (event.data === '__localCasterRescan') rescan();
          });
          try {
            new PerformanceObserver(list => list.getEntries().forEach(inspectPerformanceEntry))
              .observe({type: 'resource', buffered: true});
          } catch (_) {}

          try {
            const originalCreateObjectUrl = URL.createObjectURL.bind(URL);
            URL.createObjectURL = function(object) {
              const url = originalCreateObjectUrl(object);
              try {
                if (typeof MediaSource !== 'undefined' && object instanceof MediaSource) {
                  report(url, 'DOM', 'application/x-mediasource', null);
                }
              } catch (_) {}
              return url;
            };
          } catch (_) {}

          try {
            const originalKeyAccess = navigator.requestMediaKeySystemAccess;
            if (typeof originalKeyAccess === 'function') {
              navigator.requestMediaKeySystemAccess = function() {
                pageUsesDrm = true;
                document.querySelectorAll('video,audio').forEach(media => {
                  report(media.currentSrc || media.src, 'ENCRYPTED_MEDIA', media.getAttribute('type'), media, true);
                });
                return originalKeyAccess.apply(this, arguments);
              };
            }
          } catch (_) {}

          if (document.readyState === 'loading') {
            document.addEventListener('DOMContentLoaded', () => {
              installDomDetection();
              setTimeout(scanPerformance, 300);
            }, {once: true});
          } else {
            installDomDetection();
            setTimeout(scanPerformance, 50);
          }
          window.addEventListener('load', () => setTimeout(() => {
            scanNode(document);
            scanPerformance();
          }, 500), {once: true});
        })();
    """.trimIndent()
}
