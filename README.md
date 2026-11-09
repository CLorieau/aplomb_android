# Aplomb — l'app Android

Portage natif (Kotlin + Jetpack Compose) de la PWA **Aplomb** : niveau à bulle, boussole et journal de relevés synchronisé, utilisables sans réseau. L'app parle au même serveur que la PWA, avec la même API (`/api/releves`), et y ajoute ce qu'un navigateur ne permet pas : la **surveillance d'un aplomb écran éteint**.

- PWA d'origine : https://playful-kataifi-da7d5a.netlify.app — code : https://github.com/CLorieau/TP_aplomb
- Le détail des choix, de la fonctionnalité native et des problèmes rencontrés : [EXPLICATIONS.md](EXPLICATIONS.md)

## Télécharger l'APK

**[Dernière version (Releases)](https://github.com/CLorieau/aplomb_android/releases/latest)** — fichier `aplomb-1.2.apk`, à installer sur un téléphone Android 8 ou plus récent (autoriser l'installation depuis cette source si le téléphone le demande).

## Ce que fait l'app

- **Niveau à bulle** (à plat et sur chant, étalonnage) et **boussole** (cap magnétique, ou géographique avec la position).
- **Relevés** enregistrés avec une note, consultables hors ligne, envoyés au serveur au retour du réseau — même app fermée — avec un badge « En attente / Envoyé / Refusé ».
- **Surveillance écran éteint** : le téléphone posé sur un objet, l'app alerte si l'inclinaison dépasse un seuil (0,5 à 5°) ; tuile dans les réglages rapides.
- **Notifications** : relevés synchronisés, alertes de surveillance, push du serveur (FCM).
- L'URL de la PWA ouvre l'app (App Link vérifié) ; partage d'un relevé, réception d'un texte partagé.
- Mesure d'audience anonyme (Umami), seulement après consentement.

## Choix techniques

- **Couches** : écrans Compose → ViewModel (`StateFlow`) → `RelevesRepository` → Room / DataStore / Retrofit. **Room est la seule source de vérité** : l'écran ne lit que Room, le réseau n'écrit que dans Room ; l'app est hors ligne par construction.
- **Room ou DataStore** : les relevés (une liste, des requêtes, un schéma qui évolue) dans **Room** — base v3, migrations 1→2 et 2→3 écrites à la main, schémas exportés dans `app/schemas/`. Les valeurs isolées (identifiant d'appareil, étalonnage, seuil, consentement) dans **DataStore**.
- **Quand part l'envoi** : une **outbox** (table `outbox`) vidée par **WorkManager** — travail unique, une seule contrainte « réseau connecté » — garanti même app fermée, au moment choisi par l'OS. Requêtes `PUT` idempotentes : rejouer ne crée pas de doublon ; 400/413 = refus définitif, réseau ou 5xx = réessai.
- **Permissions** : la position (précise ou approximative) seulement au clic sur « Utiliser ma position » ; les notifications au lancement d'une surveillance. Jamais au démarrage, explication avant la seconde demande, réglages après un refus définitif ; l'app fonctionne sans.
- **Fonctionnalité native** : un **foreground service** (type `specialUse`, notification permanente, wake lock partiel limité à 2 h) qui lit l'accéléromètre écran éteint — impossible pour une page web.
- **Cycle de vie** : onglet et notes en cours dans le `SavedStateHandle`, backstack Navigation 3 sauvegardé : rien ne se perd à la rotation ni à la mort du process.
- **Analytics** : Umami n'ayant pas de SDK Android, appel direct de son API HTTP et file hors ligne dans Room, envoyée par lots par WorkManager.

## Lancer

Android Studio (ou la ligne de commande) avec un JDK 25 (`gradle/gradle-daemon-jvm.properties`) :

```bash
./gradlew :app:installDebug
```

```bash
./gradlew :app:testDebugUnitTest
```

`local.properties` (non commité) contient `sdk.dir`, et peut redéfinir :

```properties
PWA_HOST=playful-kataifi-da7d5a.netlify.app
SYNC_BASE_URL=https\://playful-kataifi-da7d5a.netlify.app/
```

Versions : celles du projet de départ du cours (Carnet), toutes dans `gradle/libs.versions.toml` — AGP 9.4.1, Kotlin 2.4.20, Compose BOM 2026.09.00, Navigation 3 1.2.0, Room 2.8.5, WorkManager 2.12.0, Retrofit 3.0.0, OkHttp 5.5.0, Firebase BoM 34.19.0. `minSdk 26`, `targetSdk 37`. Signature debug : la clé du cours (`debug.keystore`, SHA-256 `07:B4:8C:EF:…:00:CD`).

## App Link et push

### 1. App Link (TD A3 du Jour 4)

Ce que fait l'app, comme dans le TD A3 :
- **Partager** un relevé vers une autre app (`ACTION_SEND`, écran de détail) ;
- **Recevoir** un texte partagé depuis une autre app (intent-filter `SEND text/plain`) : il préremplit la note en cours ;
- **« Ouvrir la PWA »** (en-tête) : ouvre l'URL de la PWA dans le navigateur par défaut, même quand l'App Link est vérifié ;
- **App Link** : intent-filter `VIEW https://<PWA_HOST>` avec `autoVerify`, et `aplomb/public/.well-known/assetlinks.json` qui déclare `corsica.universita.aplomb` + l'empreinte de la clé debug du cours.

Après le push de la PWA sur Netlify :

```bash
curl -s https://playful-kataifi-da7d5a.netlify.app/.well-known/assetlinks.json
```

```bash
adb shell pm verify-app-links --re-verify corsica.universita.aplomb
```

```bash
adb shell pm get-app-links corsica.universita.aplomb
```

Attendu : `playful-kataifi-da7d5a.netlify.app: verified` (avant : `1024`, aucune preuve de propriété, le lien ouvrait le navigateur). Test, sans viser l'app (comme un lien touché dans Messages) :

```bash
adb shell am start -a android.intent.action.VIEW -c android.intent.category.BROWSABLE -d "https://playful-kataifi-da7d5a.netlify.app/#releves"
```

Aplomb s'ouvre sur l'onglet Relevés. Une URL tapée dans la barre d'adresse de Chrome, elle, reste dans Chrome.

### 2. Push FCM — `google-services.json`

Le push natif passe par Firebase Cloud Messaging (la PWA utilisait Web Push + VAPID). Dans la console Firebase : ajouter une app Android `corsica.universita.aplomb` (empreinte SHA-256 ci-dessus), télécharger `google-services.json` dans `app/` (ignoré par Git), recompiler. Sans ce fichier, l'app se construit et fonctionne : seul le push est désactivé (`BuildConfig.FCM_ACTIF = false`, message dans Logcat).

**Tester depuis la console Firebase** (checklist du TP : « notification reçue app tuée ; le clic ouvre le bon écran ») :

1. Installer l'app, accepter les notifications (onglet Relevés), puis « Copier mon jeton FCM » (ou `adb logcat -s Aplomb` → `Jeton FCM : …`).
2. Fermer l'app par geste (apps récentes).
3. Console Firebase › **Messaging** › Nouvelle campagne › **Notifications Firebase** : un titre, un texte.
4. **Options supplémentaires › Données personnalisées** : clé `onglet`, valeur `releves` (ou clé `releveId`, valeur l'id d'un relevé).
5. **Envoyer un message de test** › coller le jeton › Tester.

La notification arrive sur le canal « Messages », avec l'icône et la couleur d'Aplomb (`default_notification_*` dans le manifest). Au clic, Firebase lance `MainActivity` avec les données en extras bruts : l'app ouvre l'onglet ou le relevé demandé, et mesure `notification_opened` (type `push`).

Envoyer un push « data » (toujours traité par `AplombMessagingService`, même app tuée) :

```bash
node outils/envoyer-push.mjs compte-de-service.json <jeton> "Relevé vérifié" "Votre relevé a été contrôlé." releves
```

## Analytics — Umami

Umami n'a pas de SDK Android : l'app appelle son API HTTP (`POST /api/send`) et tient elle-même la file hors ligne (table Room `evenements`, envoyée par `MesureWorker`). Pour l'activer :

1. Sur [Umami Cloud](https://cloud.umami.is) (ou son Umami auto-hébergé) : **Ajouter un site** (nom « Aplomb Android », domaine `android.playful-kataifi-da7d5a.netlify.app` : Umami exige un vrai nom de domaine, et c'est le `hostname` envoyé par l'app), et copier son **Website ID** (un UUID).
2. Dans `local.properties` :
   ```properties
   UMAMI_WEBSITE_ID=xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx
   UMAMI_HOST=https\://gateway.umami.is/
   ```
   (Umami auto-hébergé : son adresse, par exemple `https\://umami.mondomaine.fr/`.)
3. Recompiler et installer. Au lancement, la carte « Mesure d'audience » demande le consentement ; rien n'est collecté avant « Accepter ». Le choix se change dans Guide › Vie privée.
4. Démo : tableau de bord Umami › **Temps réel**. En debug, les événements partent ~5 s après leur déclenchement (30 s en release, par lots) ; hors ligne, ils attendent et partent au retour du réseau, datés du moment où ils ont eu lieu.

```bash
adb logcat -s Aplomb | findstr Mesure
```

`UMAMI_WEBSITE_ID` vide : la mesure est désactivée (ni bandeau, ni collecte). Le plan de marquage est dans [EXPLICATIONS.md](EXPLICATIONS.md).

En debug seulement, `src/debug/res/xml/reseau_debug.xml` autorise le HTTP vers `10.0.2.2` (l'hôte vu de l'émulateur), pour tester contre un Umami local : `UMAMI_HOST=http\://10.0.2.2\:3000/`.

## Structure

```
app/src/main/java/corsica/universita/aplomb/
  AplombApp.kt              Application : le conteneur (base, réglages, API, repository, capteurs), les canaux
  MainActivity.kt           l'unique Activity : intents (App Link, notification, raccourcis, tuile)
  data/
    Releve.kt               le modèle vu par l'écran
    RelevesRepository.kt    la source de vérité (Room) et l'envoi de l'outbox (même algorithme que sync.js)
    Reseau.kt               en ligne / hors ligne (NET_CAPABILITY_VALIDATED)
    local/                  Room (releves, outbox, migration 1→2) et DataStore (Reglages)
    remote/AplombApi.kt     Retrofit + OkHttp vers /api/releves (PUT / DELETE, X-Device-Id, X-Differe)
  capteurs/                 calculs du niveau et de la boussole (portés de app.js) et capteurs en Flow
  position/Position.kt      position (Fused Location) et déclinaison magnétique (GeomagneticField)
  work/SyncWorker.kt        WorkManager : l'outbox part même app fermée
  notif/                    canaux, PendingIntent, réception FCM
  mesure/                   analytics Umami : Mesure (consentement, file), UmamiApi, MesureWorker (envoi par lots)
  surveillance/             LA fonctionnalité native : foreground service + tuile des réglages rapides
  ui/                       Compose : thème (variables CSS de la PWA), écrans, Navigation 3, PermissionFlow
app/schemas/                schémas Room exportés : 1.json, 2.json, 3.json (à commiter)
app/src/test/               tests unitaires des calculs (mêmes résultats que la PWA)
docs/captures/              captures sur émulateur
outils/envoyer-push.mjs     envoi d'un push FCM HTTP v1, sans dépendance
```
