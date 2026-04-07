Le tutoriel suivant a été généré par Gemini. A noter qu'il est aussi possible de simplement installer le .apk sur un téléphone pour tester, même si adb facilite le transfert (pour vérifier la certification également).


# 📖 Tutoriel d'utilisation — PhotoCertif

> Application Android de **certification photographique par signature cryptographique matérielle**.

---

## 🔧 Prérequis

| Outil | Version minimale | Installation |
|-------|-----------------|--------------|
| Android Studio | Hedgehog (2023.1+) | [developer.android.com/studio](https://developer.android.com/studio) |
| ADB | 1.0.40+ | `sudo apt install adb` |
| Python 3 | 3.8+ | `sudo apt install python3` |
| OpenSSL | 3.x | `sudo apt install openssl` |

---

## 📲 Option A — Installation rapide (APK pré-compilé)

> Si un fichier `release/PhotoCertif-debug.apk` est présent dans le dépôt :

```bash
# 1. Connecter ton téléphone en USB avec le débogage USB activé
adb devices                          # doit afficher "device"

# 2. Installer l'APK directement
adb install release/PhotoCertif-debug.apk

# 3. Lancer l'app
adb shell am start -n com.photocertif/.MainActivity
```

---

## 🏗️ Option B — Compiler et installer depuis les sources

```bash
# Cloner le projet
git clone https://github.com/<ton-username>/proj_photo_certif.git
cd proj_photo_certif

# Compiler et installer en une commande (téléphone branché requis)
chmod +x tools/build_and_deploy.sh
./tools/build_and_deploy.sh

# Pour générer uniquement l'APK sans installer :
./tools/build_and_deploy.sh --apk-only
# → APK disponible dans release/PhotoCertif-debug.apk
```

---

## 📷 Utiliser l'application

### 1. Premier lancement
- L'app demande la **permission caméra** → accorder
- Un badge en haut indique le niveau de sécurité détecté :
  - 🟢 **StrongBox SE** : puce sécurité dédiée (Titan M2, Samsung SE, etc.)
  - 🔵 **TEE Matériel** : zone sécurisée du SoC — clé inextractible

### 2. Prendre une photo certifiée
1. Appuyer sur le **bouton blanc central** (icône caméra)
2. L'app affiche successivement :
   - `📷 Capture en cours…`
   - `🔐 Signature TEE/StrongBox…`
3. Une fiche résultat verte apparaît avec les détails de la signature

### 3. Fichiers produits
Les fichiers sont enregistrés dans la mémoire interne du téléphone :
```
/sdcard/Android/data/com.photocertif/files/Pictures/
├── CERTIF_<timestamp>.jpg          ← la photo originale
└── CERTIF_<timestamp>.proof.json   ← la preuve cryptographique
```

---

## 🔍 Vérifier une signature

### Méthode 1 — Script Python (recommandée)

```bash
# Installer la dépendance
pip install cryptography

# Récupérer les fichiers depuis le téléphone
adb pull /sdcard/Android/data/com.photocertif/files/Pictures/ ./output/

# Vérifier
python3 tools/verify_signature.py output/CERTIF_xxx.jpg output/CERTIF_xxx.proof.json --verbose
```

**Sortie attendue :**
```
📷  PhotoCertif — Vérificateur de signature
────────────────────────────────────────────

✅  SIGNATURE VALIDE — L'image est authentique et intacte.

  Fichier image    : CERTIF_20260407_092438.jpg
  Algorithme       : SHA256withECDSA
  Courbe EC        : secp256r1
  Horodatage UTC   : 2026-04-07T07:24:38Z
  Matériel (TEE)   : True
  StrongBox        : True (StrongBox SE)
```

### Méthode 2 — Script Bash / OpenSSL (sans Python)

```bash
chmod +x tools/verify.sh
./tools/verify.sh output/CERTIF_xxx.jpg output/CERTIF_xxx.proof.json
```

---

## 🧪 Test de falsification (preuve de concept)

Pour démontrer que la signature détecte toute modification :

```bash
# Copier la photo et l'altérer
cp output/CERTIF_xxx.jpg /tmp/falsifiee.jpg
echo "tampered" >> /tmp/falsifiee.jpg

# Vérifier → doit échouer
python3 tools/verify_signature.py /tmp/falsifiee.jpg output/CERTIF_xxx.proof.json
# → ❌ SIGNATURE INVALIDE — L'image a été modifiée ou corrompue.
```

---

## 📁 Structure des fichiers de sortie

Le fichier `.proof.json` contient toutes les informations nécessaires à la vérification :

```json
{
  "version": "1.0",
  "image_file": "CERTIF_20260407_092438.jpg",
  "timestamp_utc": "2026-04-07T07:24:38Z",
  "algorithm": "SHA256withECDSA",
  "ec_curve": "secp256r1",
  "key_provider": "AndroidKeyStore",
  "hardware_backed": true,
  "strongbox_backed": true,
  "public_key_base64": "<Clé publique X.509 en Base64>",
  "signature_base64": "<Signature DER en Base64>"
}
```

---

## ⚠️ Notes importantes

- L'app nécessite **Android 8.0 minimum** (API 26)
- Le StrongBox n'est disponible que sur certains appareils (API 28+)
- La clé privée **ne quitte jamais** le matériel sécurisé — même avec root
- Les émulateurs Android ne supportent **pas** le Keystore matériel : tester sur un vrai téléphone
