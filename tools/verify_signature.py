#!/usr/bin/env python3
"""
PhotoCertif — Outil de vérification de signature
=================================================
Vérifie qu'une photo signée par l'app PhotoCertif est authentique et intacte.

Usage :
    python3 verify_signature.py <photo.jpg> <photo.proof.json>

Exemples :
    python3 verify_signature.py CERTIF_20260407_092438.jpg CERTIF_20260407_092438.proof.json
    python3 verify_signature.py photo.jpg photo.proof.json --verbose

Dépendances :
    pip install cryptography
"""

import sys
import json
import base64
import argparse
from pathlib import Path

def verify(image_path: Path, proof_path: Path, verbose: bool = False) -> bool:
    try:
        from cryptography.hazmat.primitives import hashes, serialization
        from cryptography.hazmat.primitives.asymmetric import ec
        from cryptography.exceptions import InvalidSignature
    except ImportError:
        print("❌ Dépendance manquante. Installez-la avec :")
        print("   pip install cryptography")
        sys.exit(1)

    # ── 1. Charger le fichier de preuve JSON ──────────────────────────────────
    try:
        proof = json.loads(proof_path.read_text())
    except (FileNotFoundError, json.JSONDecodeError) as e:
        print(f"❌ Impossible de lire le fichier de preuve : {e}")
        return False

    # ── 2. Charger les bytes de l'image ───────────────────────────────────────
    try:
        image_bytes = image_path.read_bytes()
    except FileNotFoundError:
        print(f"❌ Image introuvable : {image_path}")
        return False

    # ── 3. Décoder clé publique et signature depuis le JSON ───────────────────
    try:
        public_key_der = base64.b64decode(proof["public_key_base64"])
        signature_der  = base64.b64decode(proof["signature_base64"])
    except KeyError as e:
        print(f"❌ Champ manquant dans le JSON de preuve : {e}")
        return False

    # ── 4. Charger la clé publique (format X.509 DER — SubjectPublicKeyInfo) ──
    try:
        public_key = serialization.load_der_public_key(public_key_der)
    except Exception as e:
        print(f"❌ Clé publique invalide : {e}")
        return False

    # ── 5. Vérifier la signature SHA256withECDSA ──────────────────────────────
    try:
        public_key.verify(signature_der, image_bytes, ec.ECDSA(hashes.SHA256()))
        verified = True
    except InvalidSignature:
        verified = False
    except Exception as e:
        print(f"❌ Erreur lors de la vérification : {e}")
        return False

    # ── 6. Affichage du résultat ──────────────────────────────────────────────
    print()
    if verified:
        print("✅  SIGNATURE VALIDE — L'image est authentique et intacte.")
    else:
        print("❌  SIGNATURE INVALIDE — L'image a été modifiée ou corrompue.")
    print()

    if verbose:
        hw  = "✓" if proof.get("hardware_backed") else "✗"
        sb  = "✓ (StrongBox SE)" if proof.get("strongbox_backed") else "✗ (TEE standard)"
        print(f"  Fichier image    : {image_path.name}")
        print(f"  Algorithme       : {proof.get('algorithm', 'N/A')}")
        print(f"  Courbe EC        : {proof.get('ec_curve', 'N/A')}")
        print(f"  Horodatage UTC   : {proof.get('timestamp_utc', 'N/A')}")
        print(f"  Matériel (TEE)   : {hw}")
        print(f"  StrongBox        : {sb}")
        print(f"  Clé (extrait)    : {proof['public_key_base64'][:40]}…")
        print(f"  Sig  (extrait)   : {proof['signature_base64'][:40]}…")
        print()

    return verified


def main():
    parser = argparse.ArgumentParser(
        description="Vérifie la signature cryptographique d'une photo PhotoCertif."
    )
    parser.add_argument("image",  type=Path, help="Chemin vers le fichier .jpg")
    parser.add_argument("proof",  type=Path, help="Chemin vers le fichier .proof.json")
    parser.add_argument("-v", "--verbose", action="store_true", help="Affiche les détails")
    args = parser.parse_args()

    print(f"\n📷  PhotoCertif — Vérificateur de signature")
    print(f"{'─' * 44}")

    ok = verify(args.image, args.proof, args.verbose)
    sys.exit(0 if ok else 1)


if __name__ == "__main__":
    main()
