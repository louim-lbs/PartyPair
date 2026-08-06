# Publier le dépôt et obtenir l'APK — Windows, VS Code, GitHub

Comptez une vingtaine de minutes la première fois. Aucun outil de développement Android n'est nécessaire : c'est GitHub qui compile.

---

## 1. Installer les deux outils

**Git** — <https://git-scm.com/download/win>
Téléchargez, lancez, et acceptez toutes les options par défaut. C'est ce qui permet à VS Code de dialoguer avec GitHub.

**Visual Studio Code** — <https://code.visualstudio.com>
Même chose. Cochez « Ajouter à PATH » si l'option apparaît.

Redémarrez VS Code après avoir installé Git, sinon il ne le détectera pas.

## 2. Créer le compte et le dépôt GitHub

1. Créez un compte sur <https://github.com> si vous n'en avez pas.
2. Cliquez sur le **+** en haut à droite → **New repository**.
3. Nom : `party-pair`.
4. Laissez-le **Public** (nécessaire pour que GitHub Actions compile gratuitement sans limite).
5. **N'ajoutez ni README, ni .gitignore, ni licence** — ils sont déjà dans le projet.
6. **Create repository**.

Gardez la page ouverte : l'adresse du dépôt y figure, de la forme `https://github.com/votre-nom/party-pair.git`.

## 3. Préparer le dossier

Décompressez l'archive du projet quelque part de simple, par exemple `C:\Users\VotreNom\party-pair`.

Vérifiez que le dossier contient bien `README.md`, `settings.gradle.kts` et un dossier `app` **directement à la racine** — s'ils sont dans un sous-dossier, remontez-les d'un niveau.

## 4. Ouvrir le projet dans VS Code

**Fichier → Ouvrir le dossier**, puis choisissez `party-pair`.

Si VS Code propose d'installer des extensions recommandées, vous pouvez refuser : rien n'est nécessaire ici.

## 5. Publier

1. Dans la barre latérale gauche, cliquez sur l'icône **Contrôle de code source** (trois points reliés par des traits, ou `Ctrl+Shift+G`).
2. Cliquez sur **Initialiser le référentiel**.
3. Toutes les modifications apparaissent. Dans le champ de message en haut, écrivez `Première version`.
4. Cliquez sur **Valider** (Commit). Si VS Code demande de tout indexer d'abord, acceptez.
5. Cliquez sur **Publier la branche** (Publish Branch).
6. VS Code demande de se connecter à GitHub : acceptez, une page s'ouvre dans le navigateur, autorisez.
7. Choisissez le dépôt existant `party-pair`, ou laissez VS Code en créer un.

Si VS Code réclame un nom et un e-mail avant de valider, ouvrez le terminal (`Ctrl+ù` ou **Terminal → Nouveau terminal**) et saisissez :

```
git config --global user.name "Votre Nom"
git config --global user.email "votre@email.fr"
```

Puis reprenez à l'étape 4.

## 6. Laisser GitHub compiler

1. Ouvrez votre dépôt sur <https://github.com>.
2. Onglet **Actions**.
3. Un workflow nommé **APK** est en cours — pastille orange. Comptez 3 à 5 minutes.
4. Pastille verte : c'est compilé.

Si la pastille est rouge, cliquez dessus, puis sur l'étape en échec pour lire le message. Les erreurs de première compilation portent presque toujours sur une version d'outil ; le message indique laquelle.

## 7. Récupérer l'APK

1. Toujours dans **Actions**, cliquez sur le build terminé.
2. En bas de la page, section **Artifacts** : `party-pair-apk`.
3. Cliquez dessus pour télécharger un fichier `.zip`.
4. Décompressez-le : il contient `party-pair.apk`.

## 8. Installer sur le téléphone

**Par câble** — branchez le téléphone en USB, autorisez le transfert de fichiers, copiez l'APK dans *Téléchargements*. Depuis le téléphone, ouvrez l'application *Mes fichiers*, allez dans *Téléchargements*, appuyez sur l'APK.

**Sans câble** — envoyez-vous l'APK par e-mail ou déposez-le sur votre cloud, puis ouvrez-le depuis le téléphone.

Android demandera d'autoriser l'installation depuis cette source : c'est normal pour une application qui ne vient pas du Play Store. Un avertissement peut aussi signaler que l'application n'a pas été analysée — c'est le comportement habituel pour une compilation de développement.

## 9. Premier lancement

L'application demande l'accès Bluetooth, puis affiche la liste de vos appareils appairés. Désignez l'enceinte qui reçoit le son, puis celle qui la rejoint, et confirmez l'adresse du téléphone.

C'est tout : les fois suivantes, un appui sur le bouton suffit.

---

## Publier une mise à jour

Après avoir modifié quoi que ce soit :

1. Icône **Contrôle de code source** dans VS Code.
2. Message décrivant le changement.
3. **Valider**, puis **Synchroniser les modifications**.
4. GitHub recompile ; récupérez le nouvel APK dans **Actions**.

Installez-le par-dessus l'ancien : vos réglages d'enceintes sont conservés.

## Créer une version téléchargeable

Pour obtenir une page de téléchargement propre, à partager :

```
git tag v1.0
git push origin v1.0
```

à saisir dans le terminal de VS Code. GitHub crée alors une **Release** avec l'APK attaché, à l'adresse `https://github.com/votre-nom/party-pair/releases`.
