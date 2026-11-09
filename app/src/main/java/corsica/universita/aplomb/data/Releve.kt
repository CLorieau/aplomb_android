package corsica.universita.aplomb.data

/**
 * Le modèle d'un relevé tel que l'écran le voit (jamais l'entité Room ni le DTO réseau).
 * Mêmes champs que le store `releves` d'IndexedDB dans la PWA (db.js), plus la position (base v2).
 */
data class Releve(
    val id: String,          // UUID créé sur l'appareil : PUT /api/releves/<id> est idempotent
    val outil: Outil,
    val valeur: Double,      // degrés : écart pour le niveau, cap pour la boussole
    val mode: ModeNiveau?,   // niveau seulement
    val x: Double?,          // niveau à plat : gauche-droite
    val y: Double?,          // niveau à plat : haut-bas
    val note: String,
    val createdAt: Long,
    val etat: Etat,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val precisionM: Float? = null,
    val nordGeographique: Boolean = false,
)

enum class Outil(val cle: String) { NIVEAU("niveau"), BOUSSOLE("boussole") }

enum class ModeNiveau(val cle: String) { PLAT("plat"), CHANT("chant") }

/** Les trois états du badge, comme dans la PWA : « En attente », « Envoyé », « Refusé par le serveur ». */
enum class Etat { ATTENTE, ENVOYE, REFUSE }
