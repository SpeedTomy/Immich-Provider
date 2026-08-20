# Immich Provider for Android

Accédez aux photos et vidéos de votre serveur Immich depuis le sélecteur de fichiers Android.

L’application ajoute une source **Immich** dans **Ouvrir à partir de**. Elle affiche la timeline dans le même ordre chronologique qu’Immich, fournit les miniatures, permet d’ouvrir les originaux et donne accès aux albums.

> Ce projet est communautaire et n’est pas affilié à Immich. Le logo Immich appartient au projet Immich.

## Télécharger l’APK

[**Télécharger la dernière version**](https://github.com/SpeedTomy/Immich-Provider/releases/latest/download/immich-provider.apk)

Android 10 ou plus récent est nécessaire. L’APK doit être autorisé comme application provenant d’une source externe lors de la première installation.

## Configuration

### 1. Créer une clé API Immich

Dans l’interface web Immich :

1. Ouvrez les paramètres de votre compte.
2. Ouvrez la section **Clés API**.
3. Créez une clé réservée à Immich Provider.
4. Accordez uniquement les droits de lecture nécessaires aux médias, miniatures, originaux et albums.
5. Copiez immédiatement la clé affichée.

Évitez d’utiliser une clé administrateur ou une clé possédant des droits de modification/suppression.

### 2. Configurer l’application

1. Installez et ouvrez **Immich Provider**.
2. Saisissez l’URL complète du serveur, par exemple :

   ```text
   https://photos.example.com
   ```

   ou, sur un réseau local de confiance :

   ```text
   http://192.168.1.201:2283
   ```

3. Collez la clé API puis appuyez sur **Enregistrer**.

La clé est chiffrée avec Android Keystore. L’application n’envoie les identifiants qu’au serveur configuré.

### 3. Sélectionner une photo

Depuis une application proposant **Joindre un fichier**, **Parcourir** ou **Ouvrir à partir de** :

1. Ouvrez le sélecteur de fichiers Android.
2. Affichez le volet des sources.
3. Choisissez **Immich** — l’adresse du serveur apparaît sous le logo.
4. Choisissez un média récent ou ouvrez le dossier **Albums**.

## Fonctionnalités

- timeline triée par date de prise de vue, du plus récent au plus ancien ;
- mêmes règles Immich pour la visibilité, les piles et les photos de partenaires ;
- aperçu des photos et vidéos ;
- téléchargement de l’original lors de l’ouverture ;
- navigation dans les albums Immich ;
- accès en lecture seule ;
- prise en charge des serveurs locaux HTTP ;
- clé API chiffrée avec Android Keystore ;
- icône et adresse du serveur dans le sélecteur Android.

## Limites connues

- La racine affiche actuellement les 250 médias les plus récents. Les albums restent accessibles intégralement.
- La timeline compacte d’Immich ne fournit pas les noms originaux : Android peut donc afficher l’identifiant Immich comme nom du document.
- L’original est d’abord téléchargé dans le cache de l’application ; il n’est pas encore diffusé en streaming.
- HTTP est autorisé pour les serveurs locaux. N’utilisez pas HTTP sur Internet ou sur un réseau non fiable.
- Le menu **Application multimédia cloud** du sélecteur photo Samsung utilise `CloudMediaProvider`, et non `DocumentsProvider`. Android réserve actuellement ce mécanisme aux applications approuvées par un constructeur/OEM ; une installation manuelle ne peut donc pas y ajouter Immich.

## Compatibilité vérifiée

- Android 10 et versions ultérieures (`minSdk 29`) ;
- Immich 3.1.0 ;
- Samsung Galaxy S24+ sous le sélecteur de fichiers Google/Samsung.

Les anciennes versions Immich peuvent fonctionner grâce aux routes de repli, sans garantie d’un ordre strictement identique.

## Sécurité et confidentialité

- La clé API est enregistrée dans `EncryptedSharedPreferences`, protégée par Android Keystore.
- Les fichiers sont exposés uniquement en lecture.
- Les aperçus et originaux sont conservés dans le cache privé de l’application.
- Aucune télémétrie ni service tiers n’est intégré.
- L’URL HTTP locale est volontairement acceptée. Préférez toujours HTTPS lorsqu’il est disponible.

## Compiler le projet

Prérequis : Android SDK, JDK 17 et un appareil ou émulateur Android 10+.

```sh
git clone https://github.com/SpeedTomy/Immich-Provider.git
cd Immich-Provider
./gradlew :app:testDebugUnitTest :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Architecture

- `ImmichDocumentsProvider.kt` : intégration Storage Access Framework ;
- `ImmichMediaSource.kt` : appels HTTP et adaptation de l’API Immich ;
- `DocumentId.kt` : identifiants stables des racines, albums et médias ;
- `ImmichSettings.kt` : URL et stockage chiffré de la clé API ;
- `MainActivity.kt` : écran de configuration.

La timeline utilise `GET /api/timeline/buckets` et `GET /api/timeline/bucket`. Les albums utilisent `GET /api/albums` et `GET /api/albums/{id}`. Les miniatures et originaux utilisent les routes `/api/assets/{id}/thumbnail` et `/api/assets/{id}/original`.

## Développement

```sh
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

Les releases GitHub sont construites par `.github/workflows/release.yml` et signées avec une clé stable conservée dans les secrets GitHub.

## Remerciements

[Immich](https://immich.app/) pour son excellente plateforme de gestion de photos et son API. Le logo vectoriel utilisé par cette application provient du dossier `design` du dépôt officiel Immich.
