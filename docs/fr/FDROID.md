# Publier sur F-Droid

F-Droid n'héberge pas votre APK : il **recompile l'application depuis les sources** et la signe avec sa propre clé. C'est ce qui fait sa valeur — personne n'a à vous faire confiance — et c'est aussi ce qui impose quelques ajustements.

Comptez une bonne demi-journée de préparation, puis un délai variable pour la revue.

---

## Vue d'ensemble

| Étape | Où | Durée |
|---|---|---|
| 1. Préparer le projet | votre dépôt | 1–2 h |
| 2. Écrire les métadonnées | votre dépôt | 30 min |
| 3. Publier une version taguée | votre dépôt | 10 min |
| 4. Tester la compilation comme F-Droid | votre machine | 1 h |
| 5. Soumettre | GitLab | 30 min |
| 6. Revue | mainteneurs F-Droid | quelques jours à quelques semaines |

---

## Étape 1 — Préparer le projet

### 1.1 Retirer la clé de signature du type `release`

C'est le point bloquant. Le projet fait signer la variante `release` avec la clé de débogage versionnée, or **F-Droid signe lui-même**.

Dans `app/build.gradle.kts` :

```kotlin
buildTypes {
    debug {
        signingConfig = signingConfigs.getByName("debug")
    }
    release {
        isMinifyEnabled = false
        // Aucun signingConfig : F-Droid appose la sienne.
        proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
}
```

> **Conséquence à accepter.** Les APK publiés sur GitHub gardent la clé de débogage, ceux de F-Droid portent la sienne. **Les deux ne peuvent pas se mettre à jour l'un l'autre** : passer de l'un à l'autre impose une désinstallation, et donc la perte des réglages. Prévenez-en vos utilisateurs dans les notes de version.

### 1.2 Créer une variante sans mise à jour automatique

L'application interroge l'API GitHub et propose de télécharger un APK. F-Droid gère lui-même les mises à jour et voit d'un très mauvais œil les applications qui s'installent toutes seules : c'est un motif de rejet fréquent.

Ajoutez deux variantes :

```kotlin
flavorDimensions += "distribution"
productFlavors {
    create("github") {
        dimension = "distribution"
        buildConfigField("boolean", "SELF_UPDATE", "true")
    }
    create("fdroid") {
        dimension = "distribution"
        buildConfigField("boolean", "SELF_UPDATE", "false")
    }
}
```

Puis conditionnez les appels, dans `MainActivity.kt` :

```kotlin
if (BuildConfig.SELF_UPDATE) {
    UpdateChecker.checkQuietly(this@MainActivity)
}
```

et masquez l'entrée *Vérifier les mises à jour* dans `SettingsScreen.kt` de la même façon.

Les tâches Gradle deviennent alors `assembleGithubDebug`, `assembleFdroidRelease`, etc. **Pensez à corriger le workflow GitHub Actions**, qui appelle encore `assembleDebug` et ne trouvera plus le chemin de sortie.

### 1.3 Retirer la permission devenue inutile

Dans la variante F-Droid, `REQUEST_INSTALL_PACKAGES` n'a plus d'objet. Créez `app/src/fdroid/AndroidManifest.xml` :

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">
    <uses-permission
        android:name="android.permission.REQUEST_INSTALL_PACKAGES"
        tools:node="remove" />
</manifest>
```

Une permission en moins sur la fiche, et une question de moins en revue.

### 1.4 Vérifier l'absence de dépendances propriétaires

```bash
./gradlew :app:dependencies --configuration fdroidReleaseRuntimeClasspath | grep -iE "google|firebase|gms|crashlytics"
```

Le projet n'utilise que AndroidX et Compose, tous libres. Rien ne devrait remonter.

---

## Étape 2 — Écrire les métadonnées

F-Droid lit la description et les captures **depuis votre dépôt**, selon une arborescence stricte :

```
fastlane/metadata/android/
├── en-US/
│   ├── title.txt
│   ├── short_description.txt
│   ├── full_description.txt
│   ├── images/
│   │   ├── icon.png                 512×512
│   │   └── phoneScreenshots/
│   │       ├── 1.png
│   │       └── 2.png
│   └── changelogs/
│       └── 25.txt
└── fr-FR/
    └── (même structure)
```

Trois règles à respecter :

- `short_description.txt` — **80 caractères maximum**, sur une seule ligne
- `full_description.txt` — texte simple, ni Markdown ni HTML
- `changelogs/25.txt` — le nom du fichier est le **`versionCode`**, pas le `versionName`

Exemple de `en-US/short_description.txt` :

```
Wake two JBL PartyBox speakers and link them in stereo, with one tap.
```

Exemple de `en-US/changelogs/25.txt` :

```
First F-Droid release.
```

---

## Étape 3 — Publier une version taguée

Chaque version F-Droid correspond à un tag Git.

```bash
git add .
git commit -m "Prepare F-Droid release"
git push
git tag v1.0.3
git push origin v1.0.3
```

Le tag doit porter exactement le `versionName`, et le commit taggé doit contenir le `versionCode` correspondant. F-Droid vérifie cette correspondance.

---

## Étape 4 — Tester la compilation comme F-Droid

**Ne sautez pas cette étape** : une compilation qui échoue chez eux est le premier motif de rejet.

```bash
pip install fdroidserver
git clone https://gitlab.com/fdroid/fdroiddata.git
cd fdroiddata
```

Créez `metadata/fr.boitedefete.yml` :

```yaml
Categories:
  - Multimedia
License: MIT
AuthorName: louim-lbs
SourceCode: https://github.com/louim-lbs/PartyPair
IssueTracker: https://github.com/louim-lbs/PartyPair/issues

AutoName: Party Pair

RepoType: git
Repo: https://github.com/louim-lbs/PartyPair.git

Builds:
  - versionName: 1.0.3
    versionCode: 25
    commit: v1.0.3
    subdir: app
    gradle:
      - fdroid

AutoUpdateMode: Version
UpdateCheckMode: Tags
CurrentVersion: 1.0.3
CurrentVersionCode: 25
```

Puis lancez la compilation :

```bash
fdroid build -v -l fr.boitedefete
```

La première exécution télécharge le SDK et peut prendre un long moment. Une compilation réussie dépose un APK dans `unsigned/`.

En cas d'échec, le message est généralement explicite : SDK manquant, tâche Gradle introuvable, dépendance non résolue.

---

## Étape 5 — Soumettre

1. Lisez la [politique d'inclusion](https://f-droid.org/docs/Inclusion_Policy/). C'est court, et ça évite un refus pour un motif évitable.
2. Créez un compte sur **GitLab** et forkez [`fdroiddata`](https://gitlab.com/fdroid/fdroiddata).
3. Créez une branche portant l'identifiant de l'application :

```bash
git checkout -b fr.boitedefete
git add metadata/fr.boitedefete.yml
git commit -m "New app: Party Pair"
git push origin fr.boitedefete
```

4. Ouvrez une **merge request** vers `fdroiddata`, en décrivant brièvement ce que fait l'application.

Une chaîne d'intégration automatique valide le fichier et tente la compilation. Corrigez ce qu'elle signale avant d'attendre une revue humaine.

### Alternative plus simple, mais plus lente

Ouvrez une demande dans la [file de soumission](https://gitlab.com/fdroid/rfp/-/issues) : les mainteneurs écrivent les métadonnées à votre place. Comptez plusieurs mois.

---

## Étape 6 — La revue

Ce que les mainteneurs regardent en priorité :

- la licence, présente et cohérente entre le dépôt et le fichier de métadonnées ;
- l'absence de dépendances propriétaires ;
- l'absence de mise à jour automatique — d'où l'étape 1.2 ;
- une compilation reproductible ;
- des permissions justifiées.

**Deux points appelleront sans doute une question** sur ce projet.

Le **service d'écoute de notifications** est déclaré, ce qui inquiète toujours. Expliquez qu'il ne lit aucune notification et sert uniquement à savoir quelle application détient la session média — c'est la seule voie qu'Android offre — et qu'il reste facultatif.

Les **composants exportés sans permission** peuvent aussi être soulevés. La réponse est dans [SECURITY.md](../SECURITY.md) : exiger une permission personnalisée rendrait impossible le pilotage par Home Assistant ou les routines Samsung.

Répondez posément, corrigez ce qui est demandé. Les mainteneurs sont bénévoles et débordés : une réponse claire fait gagner des semaines.

---

## Ensuite

Une fois la merge request fusionnée, l'application apparaît sous quelques jours. Pour publier une mise à jour, il suffit ensuite de pousser un tag : `AutoUpdateMode: Version` fait le reste, sans nouvelle merge request.

---

## Une alternative à considérer d'abord

Rien n'oblige à passer par le dépôt officiel. Un **dépôt F-Droid personnel** se met en place en une heure :

```bash
pip install fdroidserver
mkdir fdroid-repo && cd fdroid-repo
fdroid init
cp /chemin/vers/party-pair.apk repo/
fdroid update -c
```

Publiez le dossier `repo/` sur GitHub Pages, et vos utilisateurs ajoutent son adresse dans leur client F-Droid. Ils reçoivent les mises à jour automatiquement, sans file d'attente ni négociation — et vous gardez votre propre clé de signature, donc la continuité des mises à jour depuis les APK GitHub.

Pour un projet de cette taille, c'est probablement le meilleur rapport effort/bénéfice, au moins dans un premier temps.
