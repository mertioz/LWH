# Local Web Caster

Application Android personnelle en Kotlin, Jetpack Compose et WebView pour détecter puis envoyer des médias Web vers un appareil Google Cast.

## Fonctions principales

- navigateur WebView avec navigation, recherche, blocage simple de domaines publicitaires et contrôle des popups ;
- détection combinée DOM, MutationObserver, fetch, XHR, Performance API et réseau WebView ;
- déduplication/scoring des flux HLS, DASH, MP4, WebM, vidéo et audio ;
- inspection HLS (master, variantes, résolution, débit, live/VOD et DRM) et inspection DRM DASH ;
- Cast direct via le Default Media Receiver avec mini-controller ;
- partage Android vers CASTER, onglets persistants avec seulement trois WebView actives et preferences par site ;
- Quick Cast, file d'attente Cast, sous-titres WebVTT/HLS compatibles et diagnostics expurges ;
- controleur Cast synchronise (receiver, progression, volume, pistes, artwork) avec notification officielle ;
- relay LAN temporaire et tokenisé pour les cookies/headers requis, avec Range/HEAD et réécriture HLS ;
- lecture locale Media3 (HLS, DASH, MP4/WebM) avec sous-titres et Picture-in-Picture ;
- aucune télémétrie, publicité, connexion utilisateur ou permission de stockage.

## Compiler

Le projet requiert Android SDK 36.1 et Java 17 ou plus récent.

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
```

L'APK est produit dans `app/build/outputs/apk/debug/app-debug.apk`.

## Test local de détection

Dans le menu de l'application, choisir **Ouvrir la page locale de test**. Cette page exerce les sources MP4, `<source>`, ajout dynamique, fetch, XHR et scénario blob/upstream sans proposer directement l'URL `blob:`.

Le relay est un fallback : l'URL originale est toujours essayée en premier. Les flux chiffrés/DRM sont signalés et ne sont ni déchiffrés ni relayés.
