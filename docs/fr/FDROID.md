# Publier sur F-Droid

F-Droid ne se contente pas d'héberger un APK : il **recompile l'application depuis les sources** et la signe avec sa propre clé. C'est ce qui fait sa valeur, et c'est aussi ce qui impose quelques ajustements.

---

## Ce que le projet remplit déjà

- Licence libre — MIT, avec un fichier `LICENSE` à la racine
- Sources publiques et complètes, sans binaire opaque
- Aucune dépendance propriétaire : ni services Google, ni bibliothèque fermée
- Compilation Gradle standard, depuis `google()` et `mavenCentral()`

## Ce qu'il reste à faire

### 1. Retirer la clé de signature du type `release`

C'est le point bloquant. Le `build.gradle.kts` fait signer la variante `release` avec la clé de débogage versionnée, or **F-Droid signe lui-même**. Il faut donc ne rien imposer :

```kotlin
buildTypes {
    debug {
        signingConfig = signingConfigs.getByName("debug")
    }
    release {
        isMinifyEnabled = false
        // Pas de signingConfig : F-Droid appose la sienne.
        proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
}
```

Les APK publiés sur GitHub resteront signés par la clé de débogage ; ceux de F-Droid porteront la sienne. **Les deux ne pourront pas se mettre à jour l'un l'autre** — il faudra désinstaller pour passer de l'un à l'autre. C'est normal et sans remède.

### 2. Décider du sort du vérificateur de mise à jour

L'application interroge l'API GitHub et propose de télécharger l'APK. F-Droid gère lui-même les mises à jour, et voit d'un mauvais œil les applications qui s'installent toutes seules — c'est au minimum un motif de discussion avec les mainteneurs.

Le plus simple est d'ajouter une variante sans cette fonction :

```kotlin
flavorDimensions += "distribution"
productFlavors {
    create("github") { dimension = "distribution" }
    create("fdroid") {
        dimension = "distribution"
        buildConfigField("boolean", "SELF_UPDATE", "false")
    }
}
```

et de conditionner l'appel à `UpdateChecker` sur ce champ.

### 3. Taguer chaque version

Chaque publication doit correspondre à un tag Git portant le numéro de version. Pour `versionName = "1.0.0"`, le tag doit être `v1.0.0`. C'est déjà la pratique du dépôt, il suffit de s'y tenir.

### 4. Ajouter les métadonnées Fastlane

F-Droid lit la description et les captures depuis le dépôt lui-même, selon cette arborescence :

```
fastlane/metadata/android/
├── fr-FR/
│   ├── title.txt              Boîte de Fête
│   ├── short_description.txt  80 caractères maximum
│   ├── full_description.txt
│   └── images/phoneScreenshots/1.png
└── en-US/
    ├── title.txt              Party Pair
    ├── short_description.txt
    ├── full_description.txt
    └── images/phoneScreenshots/1.png
```

Un dossier `changelogs/` contenant `22.txt` (le `versionCode`) fournira les notes de version.

---

## La soumission

1. Lire la [politique d'inclusion](https://f-droid.org/docs/Inclusion_Policy/) et le guide de style. C'est court, et ça évite un refus.
2. Créer un compte sur **GitLab** et forker [`fdroiddata`](https://gitlab.com/fdroid/fdroiddata).
3. Créer une branche nommée comme l'identifiant de l'application : `fr.boitedefete`.
4. Ajouter `metadata/fr.boitedefete.yml` :

```yaml
Categories:
  - Multimedia
License: MIT
SourceCode: https://github.com/louim-lbs/PartyPair
IssueTracker: https://github.com/louim-lbs/PartyPair/issues

AutoName: Party Pair

RepoType: git
Repo: https://github.com/louim-lbs/PartyPair.git

Builds:
  - versionName: '1.0.0'
    versionCode: 22
    commit: v1.0.0
    subdir: app
    gradle:
      - fdroid

AutoUpdateMode: Version
UpdateCheckMode: Tags
CurrentVersion: '1.0.0'
CurrentVersionCode: 22
```

5. Ouvrir une **merge request** sur `fdroiddata` depuis cette branche.
6. Répondre aux questions des mainteneurs. Le délai varie : quelques jours pour une application simple, plusieurs semaines si la file est chargée.

Si vous préférez ne pas écrire les métadonnées vous-même, vous pouvez ouvrir une demande dans la [file de soumission](https://gitlab.com/fdroid/rfp/-/issues) — c'est plus simple, mais nettement plus lent, car tout le travail retombe sur les mainteneurs.

---

## Vérifier avant de soumettre

L'outil `fdroidserver` permet de rejouer la compilation exactement comme F-Droid le ferait :

```bash
pip install fdroidserver
git clone https://gitlab.com/fdroid/fdroiddata.git
cd fdroiddata
# après avoir déposé metadata/fr.boitedefete.yml
fdroid build -v -l fr.boitedefete
```

Une compilation qui échoue chez eux est le premier motif de rejet ; autant s'en assurer soi-même.

---

## Une alternative plus rapide

Rien n'oblige à passer par le dépôt officiel. Un **dépôt F-Droid personnel** se met en place en une heure avec `fdroid` et se publie sur GitHub Pages : les utilisateurs ajoutent son adresse dans leur client F-Droid et reçoivent les mises à jour automatiquement.

C'est sans doute le meilleur rapport effort/bénéfice pour un projet de cette taille, au moins dans un premier temps.
