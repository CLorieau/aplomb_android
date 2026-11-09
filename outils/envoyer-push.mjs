// Envoie un push FCM « data » à Aplomb (API FCM HTTP v1), sans dépendance : Node 20+.
//
//   node outils/envoyer-push.mjs <compte-de-service.json> <jeton FCM> [titre] [texte] [onglet] [releveId]
//
// <compte-de-service.json> : console Firebase › Paramètres du projet › Comptes de service › Générer une clé privée.
//   C'est un secret : jamais dans Git (même règle que la clé privée VAPID de la PWA).
// <jeton FCM> : bouton « Copier mon jeton FCM » dans l'onglet Relevés, ou Logcat (adb logcat -s Aplomb).
//
// Un message « data » est TOUJOURS livré à AplombMessagingService.onMessageReceived, même app tuée :
// c'est l'app qui construit la notification et choisit l'écran ouvert au clic.
// (La console Firebase, elle, envoie un message « notification », affiché sans passer par l'app.)
import { createSign } from "node:crypto";
import { readFile } from "node:fs/promises";

const [cheminCompte, jeton, titre = "Aplomb", texte = "Un message du serveur d'Aplomb.", onglet = "releves", releveId] =
  process.argv.slice(2);
if (!cheminCompte || !jeton) {
  console.error("Usage : node outils/envoyer-push.mjs <compte-de-service.json> <jeton FCM> [titre] [texte] [onglet] [releveId]");
  process.exit(1);
}
const compte = JSON.parse(await readFile(cheminCompte, "utf8"));

// 1. Un JWT signé avec la clé du compte de service (RS256), échangé contre un jeton d'accès OAuth 2.
const b64 = (o) => Buffer.from(JSON.stringify(o)).toString("base64url");
const maintenant = Math.floor(Date.now() / 1000);
const entete = b64({ alg: "RS256", typ: "JWT" });
const charge = b64({
  iss: compte.client_email,
  scope: "https://www.googleapis.com/auth/firebase.messaging",
  aud: "https://oauth2.googleapis.com/token",
  iat: maintenant,
  exp: maintenant + 3600,
});
const signature = createSign("RSA-SHA256").update(`${entete}.${charge}`).sign(compte.private_key, "base64url");
const oauth = await fetch("https://oauth2.googleapis.com/token", {
  method: "POST",
  headers: { "Content-Type": "application/x-www-form-urlencoded" },
  body: new URLSearchParams({
    grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
    assertion: `${entete}.${charge}.${signature}`,
  }),
}).then((r) => r.json());
if (!oauth.access_token) throw new Error(`OAuth refusé : ${JSON.stringify(oauth)}`);

// 2. Le message : uniquement des données (chaînes), et une priorité haute pour traverser Doze.
const data = { title: titre, body: texte, onglet };
if (releveId) data.releveId = releveId;
const reponse = await fetch(`https://fcm.googleapis.com/v1/projects/${compte.project_id}/messages:send`, {
  method: "POST",
  headers: { Authorization: `Bearer ${oauth.access_token}`, "Content-Type": "application/json" },
  body: JSON.stringify({ message: { token: jeton, data, android: { priority: "high" } } }),
});
console.log(reponse.status, await reponse.text());
