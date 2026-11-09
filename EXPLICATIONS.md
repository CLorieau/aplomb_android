# Aplomb Android — ce que fait l'app, et ce que la PWA ne pouvait pas faire

Portage de la PWA Aplomb en app Android Kotlin + Compose, avec une fonctionnalité impossible en PWA.

## 1. Ce que fait l'app

Aplomb transforme le téléphone en **niveau à bulle** et en **boussole**, et tient un **journal de relevés** synchronisé avec le serveur de la PWA. Mêmes écrans, mêmes données, même allure :

| PWA (`aplomb/public`) | App Android |
|---|---|
| En-tête « Aplomb », pastille *En ligne / Hors ligne*, bandeau hors ligne | Identiques (`ui/ecrans/Entete.kt`) |
| 4 onglets : Niveau, Boussole, Relevés, Guide | Identiques, mêmes couleurs (variables CSS de `styles.css` → `ui/theme/Theme.kt`), thème sombre compris |
| Niveau : fiole à plat, tube sur chant, étalonnage, seuil 0,5° | Mêmes formules, portées ligne à ligne de `app.js` (`capteurs/Mesures.kt`), vérifiées par des tests unitaires |
| Boussole : rose SVG qui tourne, cap + point cardinal | La même rose (coordonnées du SVG) dessinée en Canvas |
| Note de 140 caractères, « Enregistrer » | Identique ; la note en cours survit à la rotation et à la mort du process |
| Relevés : badge *En attente / Envoyé / Refusé*, « Synchroniser », « Supprimer » | Identiques ; un appui ouvre en plus le détail (partager, voir sur une carte) |
| Carte « Notifications » | Identique, branchée sur la permission Android |
| Guide (6 sections) | Le même guide, complété par ce que seule l'app sait faire |
| API `PUT/DELETE /api/releves/<id>`, en-têtes `X-Device-Id`, `X-Differe` | **La même API, le même serveur** : un relevé pris dans l'app arrive au même endroit |

Ce que l'app fait **en plus** :

1. **Surveillance d'aplomb écran éteint** (la fonctionnalité native, voir la section 4) : on pose le téléphone sur un cadre de porte qui sèche, une étagère qu'on charge, un meuble qu'on cale ; on éteint l'écran ; Aplomb alerte (notification haute priorité + vibration) si l'inclinaison s'écarte de plus de 0,5 / 1 / 2 / 5°. L'alerte est aussi enregistrée comme relevé et part au serveur.
2. **Tuile des réglages rapides** : elle montre si une surveillance tourne et l'arrête d'un appui ; éteinte, elle ouvre directement le niveau.
3. **Nord géographique** : avec la position (précise *ou* approximative), la boussole corrige la déclinaison magnétique du lieu grâce au modèle magnétique mondial embarqué dans Android (`GeomagneticField`). La PWA, elle, n'indiquait que le nord magnétique, sans correction de déclinaison.
4. **Précision du magnétomètre** : l'OS dit quand la boussole est mal calibrée (« décrivez un 8 »), ce que le web ne dit jamais.
5. **Raccourcis** sur l'icône (Niveau, Boussole, Relevés), **icône adaptative** et thématique, **splash screen**.

## 2. Les éléments techniques

| Élément | Où | Ce qui a été fait |
|---|---|---|
| **Les couches** | `ui/` → `AplombViewModel` → `RelevesRepository` → Room / Retrofit | L'écran ne connaît ni SQL ni HTTP. Le repository sert **quatre clients** : l'écran, le `SyncWorker`, le service de surveillance et le service FCM. Conteneur à la main dans `AplombApp`. |
| **Une source de vérité** | `RelevesRepository` | L'écran ne lit que Room (un `Flow`) ; le réseau n'écrit que dans Room. Aucune branche « si hors ligne » dans l'UI : un relevé apparaît dès le clic, son badge change quand le worker écrit `ENVOYE`. |
| **Les données sur le disque** | `data/local/` | **Room** remplace IndexedDB, mêmes tables : `releves` et `outbox`. Relevé + ordre d'envoi écrits dans **une transaction** (`@Transaction`), comme `db.js`. **DataStore** remplace le store `meta` : identifiant d'appareil, étalonnage, seuil, jeton FCM, drapeaux de permission. |
| **Migration** | `Migrations.kt`, `app/schemas/1.json`, `2.json` | La v2 ajoute la position et le nord géographique : `Migration(1, 2)` écrite à la main (`ALTER TABLE`, même `DEFAULT 0` que l'entité). Jamais `fallbackToDestructiveMigration`. **Testée** : une vraie base v1 (construite depuis `1.json`, avec 2 relevés) installée sous l'app v2 → `user_version = 2`, relevés conservés, nouvelles colonnes à NULL/0. |
| **Coroutines et réseau** | `AplombApi.kt` | Retrofit (fonctions `suspend`) + un seul OkHttpClient (cache 10 Mo, journal Logcat, délais). DTO ≠ entité : la position n'est **jamais** envoyée au serveur. Le code HTTP est lu par le repository : 400/413 = refus définitif (badge « Refusé », la file n'est pas bloquée), 5xx/réseau = réessai — exactement `sync.js`. |
| **L'arrière-plan** | `work/SyncWorker.kt` | L'outbox part **même app fermée ou tuée**, même après un redémarrage. Un seul travail unique `"sync"` (`KEEP` : dix relevés = une demande), **une seule contrainte** (réseau connecté), backoff exponentiel 30 s. `REPLACE` pour le bouton « Synchroniser » et le retour du réseau. Testé : mode avion, 2 relevés, `am kill`, réseau rétabli → l'OS relance le process, `PUT … 200` ×2. |
| **Les notifications** | `notif/` | **4 canaux** créés dans `Application.onCreate` (Synchronisation LOW, Messages, Surveillance LOW, Alertes HIGH), réglables séparément. `PendingIntent` avec `requestCode` distinct par destination. « Relevés synchronisés » postée par le worker **seulement** pour des relevés pris hors ligne et si l'app n'est pas à l'écran (même règle que le push de la PWA). **Push FCM** reçu app fermée : `AplombMessagingService` traite des messages *data* (titre, texte, onglet, relevé visé, ou synchro silencieuse) ; le clic ouvre l'onglet ou le détail du relevé. API FCM 25 (`register()` / `onRegistered`). |
| **Une permission jusqu'au refus définitif** | `ui/permissions/PermissionFlow.kt` | Machine à 4 états (jamais demandée, refusée une fois, refusée définitivement, accordée), **un bouton par état**, état relu à `ON_RESUME`. Deux permissions : **position** (FINE + COARSE ensemble ; « Approximative » n'est pas un refus) et **notifications** (pour la surveillance et la carte Notifications). Demandées après une action, jamais au lancement. **Testé** : refus → explication → 2ᵉ refus → « Ouvrir les réglages » → autorisée dans les réglages → révoquée → l'app redemande proprement. |
| **Le cycle de vie** | `AplombViewModel`, Navigation 3 | Onglet affiché et notes en cours dans le `SavedStateHandle` ; backstack Navigation 3 sauvegardé ; relevés dans Room. **Testé** : rotation puis `am kill` → même onglet, même note en cours. Les capteurs s'arrêtent quand l'écran n'est plus visible (`WhileSubscribed` + `collectAsStateWithLifecycle`). |
| **L'App Link** | `AndroidManifest.xml`, `MainActivity.traiter()`, `Entete.kt`, `aplomb/public/.well-known/assetlinks.json` | Partager un relevé (`ACTION_SEND`) ; recevoir un texte partagé (`SEND text/plain` → note en cours) ; bouton « Ouvrir la PWA » qui vise le navigateur (sinon, une fois vérifié, l'URL reviendrait à l'app) avec `<queries>` ; intent-filter `VIEW https` `autoVerify` + `assetlinks.json` publié dans la PWA (package + empreinte de la clé debug). Les mêmes adresses que la PWA : `…/#releves` ouvre l'onglet Relevés. |
| **+ une fonctionnalité native** | `surveillance/` | La surveillance écran éteint (voir la section 4). |

## 2 bis. La mesure : analytics avec Umami

Umami est un outil de mesure d'audience sans cookie. Il n'a **pas de SDK Android** : l'app appelle son API HTTP (`POST /api/send`, `mesure/UmamiApi.kt`) et la file hors ligne est écrite à la main, sur le modèle de l'outbox des relevés :

`Mesure.evenement()` rend la main tout de suite → l'événement est écrit dans Room (table `evenements`, **base v3**, `Migration(2, 3)`) → `MesureWorker` (WorkManager, contrainte réseau) l'envoie **par lots** (5 s en debug, 30 s en release, ou dès 20 événements), app fermée comprise → Umami agrège. Chaque événement porte l'heure où il a eu lieu (`timestamp`) : pris hors ligne, il est daté correctement à son arrivée. File bornée à 1 000 (les plus anciens partent à la poubelle).

**Le consentement** : rien n'est écrit ni envoyé avant « Accepter » (carte au lancement) ; « Refuser » ou « Ne plus mesurer » (Guide) vide la file. L'app marche pareil.

**Les écrans** sont envoyés à la main (une seule Activity avec Compose), avec des noms écrits en dur (`/niveau`, `/boussole`, `/releves`, `/guide`, `/releve`) : R8 renommerait les classes.

**Le plan de marquage** — chaque événement répond à une question ; noms `objet_verbe` au passé, jamais renommés ; aucune donnée personnelle (ni note, ni position, ni identifiant d'appareil) :

| Étape | Événement | Paramètres | La question |
|---|---|---|---|
| Activation | `releve_saved` | `outil`, `offline` 0/1, `premier` 0/1, `nord_geographique` 0/1 | Les nouveaux arrivent-ils au premier relevé ? Mesure-t-on hors ligne ? |
| Rétention | `app_opened` | `source` : icone, notification, app_link, partage, tuile, raccourci | Revient-on ? Par où ? |
| Rétention | `notification_opened` | `type` : sync, alerte, message, surveillance | Les notifications font-elles revenir ? |
| Natif | `surveillance_started` | `seuil` | La surveillance écran éteint sert-elle ? |
| Natif | `surveillance_alerted` | `seuil`, `ecart` (degrés arrondis) | Réussit-elle : détecte-t-elle un mouvement ? |
| Natif | `surveillance_stopped` | `duree_min`, `alertes`, `fin` : utilisateur / delai_2h | Combien de temps tourne-t-elle ? |
| Natif | `surveillance_blocked` | `raison` : notifications_refusees | Combien la bloquent en refusant les notifications ? |
| Socle | `sync_done` | `count`, `delay_s` | Combien de temps un relevé attend-il le réseau ? |
| Socle | `north_corrected` | `result` : ok, no_fix, denied_forever | Le nord géographique sert-il ? Combien de refus de position ? |
| Écrans | page vue | `url` | Quels outils sont utilisés ? |

**Pièges évités** : le User-Agent d'OkHttp (`okhttp/5.5.0`) est classé robot par Umami (`isbot`) — tout aurait été ignoré en silence (réponse 200 `{"beep":"boop"}`) ; l'app envoie un User-Agent d'appareil Android (`… AplombAndroid/1.2`). Pas d'identifiant publicitaire (`AD_ID` absent du manifeste fusionné). Play Store : déclarer la mesure d'audience dans « Sécurité des données ».

## 3. Les choix, et pourquoi

- **Room pour les relevés, DataStore pour le reste.** Les relevés sont une liste, avec des requêtes (tri, outbox dans l'ordre) et un schéma qui évolue : une table. L'étalonnage ou l'identifiant sont des valeurs isolées : DataStore. La position du moment n'est **pas** stockée à part (« recalculée à chaque fois », sinon une seconde source de vérité) ; elle n'est gardée qu'avec le relevé qu'elle accompagne.
- **Garder l'outbox de la PWA plutôt qu'une colonne `synced`.** La PWA met aussi les *suppressions* en file (`op: delete`) et respecte l'ordre des actions. Une table `outbox` le refait à l'identique, et le serveur n'a rien à changer.
- **Quand part l'envoi ?** Pas dans le ViewModel (il meurt avec l'écran), pas avec un service permanent (la batterie, et Google Play, sanctionnent) : WorkManager. Garanti, mais à l'heure de l'OS (Doze) — d'où le badge « En attente » qui rend l'attente visible.
- **Quelle permission, et quand ?** La position, seulement quand l'utilisateur appuie sur « Utiliser ma position », sous le texte qui explique à quoi elle sert ; l'app marche sans (nord magnétique). Les notifications, seulement quand il lance une surveillance (sans elles, l'alerte n'aurait aucun sens) ou les active dans l'onglet Relevés.
- **Pourquoi `specialUse` pour le foreground service ?** Aucun type standard ne correspond (ce n'est ni de la localisation, ni de la musique, ni de la synchronisation). Le type est déclaré avec sa justification dans le manifest (`PROPERTY_SPECIAL_USE_FGS_SUBTYPE`) : Google Play la relit. Déclarer, c'est s'engager.
- **Batterie.** Le service lit l'accéléromètre à cadence `NORMAL` (≈ 5 Hz), garde un wake lock *partiel* (processeur éveillé, écran éteint) et s'arrête de lui-même après 2 h. À l'écran, les capteurs ne tournent que tant que l'onglet est visible.

## 4. Ce que l'OS accorde, et que la PWA ne pouvait pas faire

| Dans l'app | Pourquoi la PWA ne pouvait pas |
|---|---|
| **Surveiller l'aplomb écran éteint, app fermée, et alerter** (foreground service + wake lock + notification haute priorité) | Une page web perd ses capteurs dès qu'elle est cachée ou l'écran éteint : `deviceorientation` n'est plus émis, et il n'existe aucun « service » web qui tourne en continu. Le Service Worker ne s'exécute que brièvement, sur un événement (fetch, push, sync), et n'a pas accès aux capteurs. La Screen Wake Lock de la PWA garde l'écran allumé — elle ne fait rien écran éteint. |
| **Une tuile dans les réglages rapides** | Aucune API web pour s'intégrer au volet système. |
| **Envoyer l'outbox app fermée, partout, avec garantie** (WorkManager) | Background Sync n'existe que sur Chromium, et sans garantie de délai ni de réessai ; sur Safari et Firefox, l'envoi attendait la réouverture de l'app. |
| **« En ligne » vérifié** (`NET_CAPABILITY_VALIDATED`) | `navigator.onLine` répond « en ligne » sur un Wi-Fi d'hôtel sans Internet. |
| **Précision du magnétomètre**, cap fusionné par l'OS | Le web ne fournit que `alpha` (et `webkitCompassHeading` sur iOS), sans état de calibration. |
| **Nord géographique** (`GeomagneticField`, embarqué dans l'OS) | Il aurait fallu embarquer et maintenir le modèle magnétique mondial en JavaScript. |
| **Une base sans quota ni éviction** (Room dans le dossier privé) | IndexedDB peut être vidé par le navigateur sous pression de stockage (sauf `persist()` accordé), et Safari efface les données d'un site non visité depuis 7 jours (hors PWA installée). |
| **Push reçu par l'OS** : FCM via Play Services, même app tuée, message *data* traité par l'app | Web Push dépend du navigateur qui reçoit (sur iOS : seulement PWA installée, iOS 16.4+), et le Service Worker doit afficher une notification à chaque push (`userVisibleOnly`) : pas de travail silencieux. |
| **Canaux de notification** réglables séparément | Une PWA n'a qu'un interrupteur global « notifications du site ». |
| **App Link vérifié** : l'URL de la PWA ouvre l'app | Une PWA installée ne capte pas les liens de son domaine de façon fiable (dépend du navigateur). |
| **Raccourcis, icône thématique (monochrome), splash** | Raccourcis du manifest pris en charge partiellement selon le navigateur ; pas d'icône thématique. |

## 5. Les échecs vécus, et comment ils ont été réparés

1. **La permission « refusée définitivement » pendant que le dialogue est ouvert.** Première version : le drapeau « déjà demandée » était posé *avant* `launcher.launch()`. Pendant le dialogue, l'OS répond « non accordée, pas de rationale » et le drapeau est là → l'écran affichait « Ouvrir les réglages » derrière le dialogue. Réparé : le drapeau est posé au **premier refus** (quand `shouldShowRequestPermissionRationale` passe à `true`). Bonus : un dialogue simplement fermé ne compte plus comme un refus.
2. **La révocation dans les réglages tue le process.** Pour « redemander proprement » après une révocation, la première version gardait l'état précédent en mémoire… perdu, puisque l'OS tue l'app quand on révoque une permission. Réparé : dès que la permission est vue accordée, le drapeau est effacé **sur le disque** ; révoquée plus tard, elle redevient « jamais demandée ».
3. **L'OS qui refuse sans dialogue.** Quand la permission est bloquée par l'OS (refus figé), `launch()` répond `false` instantanément, sans dialogue : le bouton semblait ne rien faire. Réparé : une réponse en moins de 400 ms est traitée comme un refus définitif → « Ouvrir les réglages ».
4. **FCM 25 a déprécié `getToken()` / `onNewToken`.** Remplacés par `register()` / `onRegistered`… et plus aucun jeton sur le téléphone : Logcat disait « Inscription FCM impossible : API disabled ». La nouvelle API doit être activée dans le manifest (`firebase_messaging_installation_id_enabled = true`). Sans jeton, pas de push, et pas de bouton « Copier mon jeton FCM ».
5. **« Ouvrir la PWA » qui n'ouvrait pas de navigateur.** Le sélecteur `CATEGORY_APP_BROWSER`, la méthode habituelle, proposait, sur l'émulateur, Paramètres, Gmail, Agenda… Réparé : on demande à l'OS quelle app ouvrirait une URL que personne ne revendique (le navigateur par défaut), et on lui confie l'URL de la PWA (`setPackage`).
6. **Le push sans `google-services.json`.** Le plugin `google-services` fait échouer le build sans ce fichier (propre à un projet Firebase). Il n'est appliqué que si le fichier existe ; sinon l'app fonctionne, push désactivé, et le dit dans Logcat.
7. **Analytics : Umami prend OkHttp pour un robot.** Umami filtre les robots avec `isbot` d'après le User-Agent, et répond 200 `{"beep":"boop"}` sans rien enregistrer. Vérifié avec le vrai paquet `isbot` : `okhttp/5.5.0` est un robot. Réparé : un User-Agent d'appareil Android, et le worker signale dans Logcat une réponse « beep ».
8. **Le clic sur une notification de la console Firebase n'ouvrait pas le bon écran.** La console envoie un message *notification* : app en arrière-plan ou tuée, c'est la bibliothèque Firebase qui l'affiche, et au clic elle passe les données personnalisées à `MainActivity` sous leur nom brut (`onglet`, `releveId`), pas sous les clés de nos propres notifications. Réparé : `MainActivity` lit aussi ces extras (et reconnaît `google.message_id` pour la mesure) ; le manifest fixe le canal, l'icône et la couleur par défaut de ces notifications.

## 6. Ce qui a été vérifié (émulateur Pixel 7, Android 15, Google Play)

- Niveau à plat et sur chant, bulle dans le bon sens ; boussole ; thème sombre (captures dans `docs/captures/`).
- Relevé en ligne → `PUT /api/releves/<id>` **200** sur le serveur de la PWA ; position absente côté serveur.
- Mode avion, 2 relevés « En attente », `am kill`, réseau rétabli → envoyés par le worker, notification « Vos 2 relevés pris hors ligne sont bien arrivés au serveur » ; le clic ouvre l'onglet Relevés.
- Migration v1 → v2 sur une vraie base v1 : relevés conservés.
- Surveillance : écran éteint (`mWakefulness=Asleep`), service au premier plan de type specialUse ; inclinaison simulée → alerte « L'aplomb a bougé » et relevé d'alerte envoyé ; la tuile affiche « Surveillance active » et l'arrête.
- Permission de position : tout le parcours jusqu'au refus définitif, puis réglages, puis révocation ; « Approximative » accepté (« à 2 km près »).
- App Link (intent explicite) → onglet Boussole ; texte partagé depuis une autre app → note du niveau ; « Ouvrir la PWA » → Chrome ; rotation et `am kill` → onglet et note en cours conservés.
- 7 tests unitaires des calculs (`./gradlew :app:testDebugUnitTest`).
- Les relevés de test envoyés au serveur ont été supprimés ensuite.

- App Link **vérifié** après le déploiement de `assetlinks.json` : `pm get-app-links` → `playful-kataifi-da7d5a.netlify.app: verified` ; un lien `https://playful-kataifi-da7d5a.netlify.app/#releves` (sans viser l'app) ouvre Aplomb sur l'onglet Relevés.

- **Mise à jour par-dessus l'ancienne version** : la v3 (versionCode 3) installée par-dessus la v2 de l'émulateur, base v2 remplie → `Migration(2, 3)`, tous les relevés conservés.
- **Analytics** contre un faux Umami local reprenant la validation de la route `/api/send` et le filtre `isbot` : rien avant le consentement ; écrans, `releve_saved` (`premier: 1`), `sync_done` acceptés ; en mode avion, rien ne part, puis tout arrive au retour du réseau avec `offline: 1` et l'heure d'origine ; `surveillance_started` / `_alerted` / `_stopped` ; `notification_opened` (alerte) + `app_opened` (notification) ; après « Ne plus mesurer », plus rien.

**Non vérifié** : le push FCM réel (il faut un `google-services.json` du projet Firebase), et l'arrivée dans un vrai tableau de bord Umami (il faut l'identifiant de son site, voir README).
