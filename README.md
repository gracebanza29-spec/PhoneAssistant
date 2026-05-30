# 📞 Phone Assistant

Application téléphone complète pour Android avec identification des appelants inconnus.

## ✨ Fonctionnalités

- 📞 **Clavier** avec recherche T9 intelligente
- 🕐 **Historique** des appels (filtres : Tous / Manqués / Reçus / Émis)  
- 👥 **Contacts** avec recherche instantanée
- 🔍 **Identification des appelants inconnus** (opérateur, type de ligne, localisation)
- ⚠️ **Détection spam** automatique
- 🚫 **Blocage de numéros**
- 📝 **Notes personnelles** par contact
- 💬 **SMS rapide** depuis l'historique
- 🌙 **Thème sombre/clair** automatique
- 🔔 **Notifications enrichies** lors des appels entrants

---

## 🚀 MÉTHODE : Compiler via GitHub (sans Android Studio)

### Ce dont vous avez besoin
- Un compte **GitHub** gratuit : https://github.com
- Votre téléphone Android

---

### ÉTAPE 1 — Créer un compte GitHub

1. Aller sur **https://github.com**
2. Cliquer **Sign up** (inscription)
3. Choisir un identifiant, email, mot de passe
4. Vérifier votre email
5. Choisir le plan **Free** (gratuit)

---

### ÉTAPE 2 — Créer un dépôt et uploader le projet

1. Sur GitHub, cliquer le bouton **"New"** ou **"+"** → **New repository**
2. Nommer le dépôt : `PhoneAssistant`
3. Laisser **Public** (obligatoire pour les Actions gratuites)
4. Cliquer **Create repository**
5. Sur la page du dépôt, cliquer **"uploading an existing file"**
6. **Glisser-déposer tous les fichiers** du dossier `PhoneAssistant` extrait
   ⚠️ Important : uploader aussi le dossier `.github` (il contient le fichier de build)
7. Cliquer **Commit changes**

---

### ÉTAPE 3 — GitHub compile l'APK automatiquement ☁️

Après l'upload, GitHub va automatiquement :
1. Détecter le fichier `.github/workflows/build.yml`
2. Lancer la compilation sur ses serveurs (≈ 5-10 minutes)
3. Produire un fichier **APK** prêt à installer

Vous pouvez suivre la progression :
- Cliquer l'onglet **Actions** sur votre dépôt GitHub
- Cliquer sur le dernier job en cours
- Attendre ✅ **Build Phone Assistant APK** 

---

### ÉTAPE 4 — Télécharger l'APK

1. Dans l'onglet **Actions**, cliquer le build réussi (✅)
2. Tout en bas, section **Artifacts**
3. Cliquer **PhoneAssistant-APK** pour télécharger
4. Envoyer l'APK sur votre téléphone (email, WhatsApp, Google Drive, câble USB…)

---

### ÉTAPE 5 — Installer sur votre téléphone Android

1. **Activer les sources inconnues** sur votre Android :
   - `Paramètres > Sécurité > Installer des apps inconnues`
   - (varie selon la marque : Samsung, Xiaomi, Oppo…)
   - Autoriser votre application de fichiers ou navigateur

2. **Ouvrir l'APK** depuis votre téléphone
3. Appuyer **Installer**
4. Appuyer **Ouvrir**

✅ **Phone Assistant est installé !**

---

## 🔑 Activer l'identification des appelants (optionnel)

L'identification des numéros inconnus nécessite une clé API gratuite.

1. S'inscrire sur **https://numlookupapi.com** (gratuit, 250 requêtes/mois)
2. Copier votre clé API
3. Dans votre dépôt GitHub, ouvrir le fichier `app/build.gradle`
4. Cliquer l'icône ✏️ (modifier)
5. Remplacer `"VOTRE_CLE_API_ICI"` par votre clé
6. Cliquer **Commit changes**
7. GitHub recompile automatiquement → télécharger le nouvel APK

---

## ❓ Questions fréquentes

**L'upload échoue sur GitHub ?**  
Uploadez par groupes de fichiers, pas tous en même temps.
Commencez par les dossiers importants : `.github/`, `app/`, `build.gradle`, `settings.gradle`, `gradlew`, `gradle/`.

**Le build Actions échoue ?**  
- Vérifiez l'onglet **Actions** → cliquer le build rouge ❌ → voir l'erreur
- La plupart du temps : fichier manquant. Vérifiez que `gradlew` et `gradle/wrapper/` sont bien uploadés.

**"Sources inconnues" introuvable sur mon téléphone ?**  
Sur Android 8+ : `Paramètres > Applications > (votre navigateur ou Fichiers) > Installer des applis inconnues`

**L'app me demande des permissions ?**  
Toutes les permissions sont nécessaires au fonctionnement (contacts, appels, journal d'appels). Accordez-les pour une expérience complète.
