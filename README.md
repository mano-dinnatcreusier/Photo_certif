# 🛡️ SecureGuard Camera — Hardware-Backed Photo Certification

[![Kotlin](https://img.shields.io/badge/Kotlin-1.9+-7F52FF.svg?style=flat&logo=kotlin)](https://kotlinlang.org/)
[![Android](https://img.shields.io/badge/Android-API%2024+-3DDC84.svg?style=flat&logo=android)](https://developer.android.com/)
[![Security](https://img.shields.io/badge/Security-StrongBox%20%2F%20TEE-blue.svg?style=flat&logo=google-cloud)](https://developer.android.com/training/articles/keystore)

**Proof-of-Concept Android application demonstrating end-to-end image integrity by signing photos directly in hardware at the moment of capture.**

---

## 🚀 The Mission

In an era of deepfakes and advanced image editing, how can we trust that a photo represents the "original truth"? 

**SecureGuard Camera** solves this problem by creating a cryptographic link between the light hitting the sensor and a secure, hardware-isolated private key. It ensures that any modification to the image—even a single bit—will invalidate its certificate.

## 🛠️ Key Features

- **End-to-End Integrity**: Captures images using `CameraX` and signs the raw JPEG bytes *before* they are written to disk.
- **Hardware-Level Security**:
    - **StrongBox Support**: Leverages dedicated security chips (e.g., Titan M2) when available.
    - **TEE (Trusted Execution Environment)**: Falls back to secure SoC isolation for standard Android devices.
- **Asymmetric Cryptography**: Uses **ECDSA** (Elliptic Curve Digital Signature Algorithm) with the `secp256r1` curve (NIST P-256).
- **Public Key Transparency**: Each photo is bundled with its signature and the public key required for verification.
- **Modern Architecture**: Built with **Jetpack Compose**, **Coroutines**, and **CameraX**.

## 🏗️ Architecture

The project follows a clean, reactive architecture (MVVM):

- **`CryptoManager`**: Handles the Android Keystore lifecycle. It ensures the private key never leaves the secure hardware boundary.
- **`CameraManager`**: Manages the CameraX lifecycle and captures in-memory buffers to prevent disk-tampering during transit.
- **`MainViewModel`**: Orchestrates the state between the camera stream and the cryptographic output.

## 🔒 Security Rationale

Most "secure" apps sign files after they are saved. **SecureGuard Camera** is different:
1. **No Software Access**: The private key is generated *inside* the hardware. Even with root access, it cannot be extracted.
2. **Atomic Capture & Sign**: By using `ImageCapture.OnImageCapturedCallback`, we sign the buffer in RAM before any OS-level file system hooks can intercept it.

## 📖 Getting Started

1. Clone the repository.
2. Open in **Android Studio**.
3. Deploy to a physical device (Emulators do not support hardware-backed KeyStore operations effectively).
4. Take a photo and check the logs for the Base64 signature and hardware status.

---

*Note: This is a technical demonstration. In a production scenario, the public keys would be registered on a central server or a decentralized ledger (Blockchain) for remote verification.*
