# 🎯 Guide d'Entretien Technique — SecureGuard Camera

Ce guide est conçu pour vous aider à "vendre" techniquement ce projet lors d'un entretien d'embauche. Il détaille le **pourquoi** derrière les choix techniques et comment briller sur les sujets complexes.

---

## 🕒 Le "Pitch" en 30 secondes
> "J'ai développé une application Android de certification de photos en temps réel. Le but est de garantir l'intégrité absolue d'une image dès sa capture. Pour cela, j'utilise l'Android Keystore pour générer des clés ECDSA isolées matériellement (StrongBox/TEE). Contrairement aux approches classiques qui signent un fichier après l'écriture sur disque, ma solution intercepte les octets bruts en mémoire via CameraX pour les signer avant toute persistance, empêchant toute altération intermédiaire par le système de fichiers ou un malware."

---

## 🛠️ Le fonctionnement en détails (Le pipeline)

### 1. Provisionnement des clés (Isolation Matérielle)
**Question possible :** *Pourquoi utiliser l'Android Keystore plutôt qu'une bibliothèque de chiffrement logicielle ?*
- **Réponse :** Dans une bibliothèque logicielle, la clé privée finit par charger en clair dans la RAM de la JVM. Dans l'Android Keystore, la clé est générée **DANS** le matériel sécurisé (TEE ou StrongBox). Elle ne quitte JAMAIS cet environnement protégé. Les opérations de signature sont déléguées au hardware. Même si le téléphone est rooté, la clé reste inacessible.

### 2. Capture de l'image (Flux de données)
**Question possible :** *Pourquoi utiliser `OnImageCapturedCallback` au lieu de `OnImageSavedCallback` de CameraX ?*
- **Réponse :** C'est un choix de design critique pour l'**intégrité**. `OnImageSavedCallback` écrit d'abord le fichier sur le stockage (potentiellement non sécurisé) puis vous donne le succès. Entre l'écriture et la signature, un attaquant pourrait modifier le fichier. Avec `OnImageCapturedCallback`, je récupère l'image dans un `ImageProxy` (en RAM), je signe les octets exacts, et seulement ensuite je prépare la persistence. C'est une approche "Zero-Trust".

---

## 🔏 Cryptographie & Sécurité

### Les algorithmes choisis
- **ECDSA (secp256r1)** : Pourquoi l'elliptique plutôt que RSA ?
    - Les clés sont plus petites (256 bits au lieu de 2048/4096 pour une sécurité équivalente).
    - C'est beaucoup plus performant sur mobile (consommation CPU/Batterie réduite).
    - C'est le standard pour les matériel sécurisés comme les enclaves.

- **SHA-256** : Utilisé comme fonction de hachage avant la signature pour garantir que même un changement d'un seul bit dans l'image (même un pixel invisible) invalide totalement la signature.

### StrongBox vs TEE
- **StrongBox** : Puce dédiée (Secure Element) comme le Titan M2 sur les Pixel. C'est le niveau "EAL5+ ready".
- **TEE (Trusted Execution Environment)** : Zone isolée du processeur principal (SoC).
- **Stratégie de fallback** : J'ai implémenté une logique de détection qui tente d'abord StrongBox, puis se replie sur le TEE si le matériel n'est pas présent, tout en refusant le stockage purement logiciel.

---

## 🏗️ Architecture & Clean Code

### Jetpack Compose & State Management
- Utilisation de **Compose** pour une UI déclarative et réactive.
- Le **ViewModel** gère l'état de la caméra et les résultats de signature via des `StateFlow` ou `MutableState`.
- Séparation des préoccupations : `CryptoManager` ne sait rien de l'UI, `CameraManager` ne sait rien de la crypto.

### Coroutines & Threading
- La signature cryptographique est une opération synchrone "bloquante" pour le CPU. J'utilise `withContext(Dispatchers.Default)` pour m'assurer que l'UI reste fluide pendant le calcul.
- CameraX utilise son propre `Executor` pour éviter les saccades lors du traitement d'image.

---

## 🚀 Améliorations futures (Pour montrer que vous voyez loin)
1. **Remote Attestation** : Envoyer la clé publique au serveur avec un "attestation certificate" signé par Google pour prouver que la clé vient bien d'un matériel certifié.
2. **Blockchain Anchoring** : Ancrer le hash de la photo sur une blockchain pour avoir une preuve d'existence temporelle (Timestapping) indiscutable.
3. **EXIF Injection** : Injecter la signature directement dans les métadonnées de l'image pour qu'elle reste "autonome".

---

## 💡 Conseil pour l'entretien
Si l'interviewer vous pose une colle sur un détail complexe du Keystore, n'hésitez pas à dire : *"C'est un domaine dense, j'ai choisi d'implémenter ce PoC pour confronter la théorie de la documentation Android Security aux contraintes réelles du hardware."* Cela montre votre curiosité et votre esprit pratique.
