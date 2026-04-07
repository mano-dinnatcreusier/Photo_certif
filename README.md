# PhotoCertif — Application android de certification photographique par signature cryptographique

[![Kotlin](https://img.shields.io/badge/Kotlin-1.9+-7F52FF.svg?style=flat&logo=kotlin)](https://kotlinlang.org/)
[![Android](https://img.shields.io/badge/Android-API%2024+-3DDC84.svg?style=flat&logo=android)](https://developer.android.com/)
[![Security](https://img.shields.io/badge/Security-StrongBox%20%2F%20TEE-blue.svg?style=flat&logo=google-cloud)](https://developer.android.com/training/articles/keystore)


---

## Objectifs

Avec une présence grandissante des deepfakes et photos retouchées ou générées par IA, pouvoir certifier l'authenticité d'une photo est devenu un enjeu majeur. Certaines grandes marques comme Leica ou Sony ont déjà commencé à inclure des mécanismes de certification au niveau matériel. En revanche, pour la grande majorité des smartphones, bien qu'il existe des solution prévues par les constructeurs, la certification de photos n'est à mon sens pas assez utilisée. Cette application permet une certification cryptographique difficile à contourner. 


## Principes de l'application

- **Intégrité de bout en bout** : capture les images en utilisant `CameraX` et signe les octets JPEG bruts avant qu'ils ne soient écrits sur le disque.
- **Deux niveaux de sécurité matérielles** :
    - **Support StrongBox** : utilise des puces de sécurité dédiées (par exemple Titan M2) lorsque disponibles (la solution prévue par les constructeurs dont je parlais plus haut).
    - **TEE (Trusted Execution Environment)** : utilise l'isolation sécurisée du SoC pour les appareils Android standard.
- **Cryptographie asymétrique** : utilise **ECDSA** (Elliptic Curve Digital Signature Algorithm) avec la courbe `secp256r1` (NIST P-256).
- **Transparence de la clé publique** : chaque photo est accompagnée de sa signature et de la clé publique nécessaire à la vérification.
- **Architecture moderne** : utilise **Jetpack Compose**, **Coroutines**, et **CameraX**.

## Architecture

Ce projet suit une architecture MVVM.

- **`CryptoManager`**: Gère le cycle de vie du Keystore. Il s'assure que la clé privée ne quitte jamais la frontière matérielle sécurisée.
- **`CameraManager`**: Gère le cycle de vie de CameraX et capture les tampons en mémoire pour éviter toute falsification du disque pendant le transit.
- **`MainViewModel`**: Orchestre l'état entre le flux de la caméra et la sortie cryptographique.

## Transparence

Une grande partie de ce projet a été conçue avec l'aide de l'IA. Le but était pour moi de réussir à produire une app fiable et testée, sans passer par une phase de développement longue et fastidieuse. J'ai néanmoins beaucoup appris au cours de ce projet, notamment sur les aspects cryptographiques, sur le developpement android moderne et les outils associées (android studio, gradle, adb, etc.). 
