#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# PhotoCertif — Script de vérification rapide (OpenSSL, sans dépendance Python)
#
# Usage :
#   ./tools/verify.sh <photo.jpg> <photo.proof.json>
#
# Exemple :
#   ./tools/verify.sh ~/output/CERTIF_20260407_092438.jpg \
#                     ~/output/CERTIF_20260407_092438.proof.json
# ─────────────────────────────────────────────────────────────────────────────
set -e

RED='\033[0;31m'; GREEN='\033[0;32m'; CYAN='\033[0;36m'; NC='\033[0m'

# ── Arguments ─────────────────────────────────────────────────────────────────
if [ "$#" -ne 2 ]; then
    echo "Usage : $0 <photo.jpg> <photo.proof.json>"
    exit 1
fi

IMAGE="$1"
PROOF="$2"

if [ ! -f "$IMAGE" ]; then echo -e "${RED}❌ Image introuvable : $IMAGE${NC}"; exit 1; fi
if [ ! -f "$PROOF" ]; then echo -e "${RED}❌ Preuve introuvable : $PROOF${NC}"; exit 1; fi

echo -e "\n${CYAN}📷  PhotoCertif — Vérificateur de signature${NC}"
echo "────────────────────────────────────────────"

# ── Extraction depuis le JSON ─────────────────────────────────────────────────
PUB_B64=$(python3 -c "import json,sys; d=json.load(open(sys.argv[1])); print(d['public_key_base64'])" "$PROOF")
SIG_B64=$(python3 -c "import json,sys; d=json.load(open(sys.argv[1])); print(d['signature_base64'])"  "$PROOF")
ALGO=$(python3    -c "import json,sys; d=json.load(open(sys.argv[1])); print(d.get('algorithm','?'))" "$PROOF")
TS=$(python3      -c "import json,sys; d=json.load(open(sys.argv[1])); print(d.get('timestamp_utc','?'))" "$PROOF")
HW=$(python3      -c "import json,sys; d=json.load(open(sys.argv[1])); print(d.get('hardware_backed','?'))" "$PROOF")
SB=$(python3      -c "import json,sys; d=json.load(open(sys.argv[1])); print(d.get('strongbox_backed','?'))" "$PROOF")

# ── Conversion en binaire ─────────────────────────────────────────────────────
TMPDIR=$(mktemp -d)
echo "$PUB_B64" | base64 -d > "$TMPDIR/pubkey.der"
echo "$SIG_B64" | base64 -d > "$TMPDIR/sig.bin"
openssl ec -pubin -inform DER -in "$TMPDIR/pubkey.der" -out "$TMPDIR/pubkey.pem" 2>/dev/null

# ── Vérification ──────────────────────────────────────────────────────────────
echo ""
if openssl dgst -sha256 -verify "$TMPDIR/pubkey.pem" -signature "$TMPDIR/sig.bin" "$IMAGE" 2>/dev/null | grep -q "OK"; then
    echo -e "${GREEN}✅  SIGNATURE VALIDE — L'image est authentique et intacte.${NC}"
    RESULT=0
else
    echo -e "${RED}❌  SIGNATURE INVALIDE — L'image a été modifiée ou corrompue.${NC}"
    RESULT=1
fi

# ── Détails ───────────────────────────────────────────────────────────────────
echo ""
echo "  Fichier image  : $(basename "$IMAGE")"
echo "  Algorithme     : $ALGO"
echo "  Horodatage UTC : $TS"
echo "  Matériel (TEE) : $HW"
echo "  StrongBox      : $SB"
echo ""

rm -rf "$TMPDIR"
exit $RESULT
