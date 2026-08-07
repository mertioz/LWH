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
            send({
              url,
              source,
              mime: String(mime || '').slice(0, 200),
              title: String(document.title || '').slice(0, 500),
              width: Number(element && element.videoWidth || 0),
              height: Number(element && element.videoHeight || 0),
              drm: Boolean(drm || pageUsesDrm),
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
            if (root && root.matches && root.matches('video,audio,source')) nodes.push(root);
            if (root && root.querySelectorAll) nodes.push(...root.querySelectorAll('video,audio,source'));
            nodes.forEach(element => {
              if (element.matches('video,audio')) scanElement(element);
              else report(
                element.src || element.getAttribute('src'),
                'SOURCE_ELEMENT',
                element.type,
                element.parentElement
              );
            });
          };

          let scanTimer = 0;
          const scheduleScan = root => {
            clearTimeout(scanTimer);
            scanTimer = setTimeout(() => scanNode(root || document), 120);
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
                    scheduleScan(mutation.target);
                    return;
                  }
                  if ([...mutation.addedNodes].some(node => node.nodeType === 1 &&
                      (node.matches?.('video,audio,source') || node.querySelector?.('video,audio,source')))) {
                    relevant = true;
                  }
                }
                if (relevant) scheduleScan(document);
              }).observe(document.documentElement, {
                subtree: true,
                childList: true,
                attributes: true,
                attributeFilter: ['src', 'type'],
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
