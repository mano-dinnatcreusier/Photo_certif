#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# PhotoCertif — Build & Deploy rapide
#
# Ce script compile l'app et l'installe sur un téléphone Android connecté.
#
# Usage :
#   ./tools/build_and_deploy.sh           → compile + installe en debug
#   ./tools/build_and_deploy.sh --release → compile en release (APK signable)
#   ./tools/build_and_deploy.sh --apk-only → compile sans installer
# ─────────────────────────────────────────────────────────────────────────────
set -e

CYAN='\033[0;36m'; GREEN='\033[0;32m'; RED='\033[0;31m'; YELLOW='\033[1;33m'; NC='\033[0m'

SCRIPT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
RELEASE=0
APK_ONLY=0

for arg in "$@"; do
    case $arg in
        --release)  RELEASE=1 ;;
        --apk-only) APK_ONLY=1 ;;
    esac
done

echo -e "\n${CYAN}📦  PhotoCertif — Build & Deploy${NC}"
echo "────────────────────────────────"

# ── Vérification ADB ──────────────────────────────────────────────────────────
if [ "$APK_ONLY" -eq 0 ]; then
    if ! command -v adb &> /dev/null; then
        echo -e "${RED}❌ ADB non trouvé. Installez-le : sudo apt install adb${NC}"
        exit 1
    fi
    DEVICES=$(adb devices | grep -v "List of" | grep "device$" | wc -l)
    if [ "$DEVICES" -eq 0 ]; then
        echo -e "${RED}❌ Aucun téléphone Android détecté. Vérifiez la connexion USB et le débogage USB.${NC}"
        exit 1
    fi
    echo -e "${GREEN}✓ Téléphone détecté${NC}"
fi

# ── Compilation ───────────────────────────────────────────────────────────────
cd "$SCRIPT_DIR"

if [ "$RELEASE" -eq 1 ]; then
    echo -e "${YELLOW}⚙  Compilation en mode RELEASE…${NC}"
    ./gradlew assembleRelease
    APK_PATH="app/build/outputs/apk/release/app-release-unsigned.apk"
else
    echo -e "${YELLOW}⚙  Compilation en mode DEBUG…${NC}"
    ./gradlew assembleDebug
    APK_PATH="app/build/outputs/apk/debug/app-debug.apk"
fi

echo -e "${GREEN}✓ APK compilé : $APK_PATH${NC}"
echo "  Taille : $(du -h "$APK_PATH" | cut -f1)"

# Copie dans le dossier release/ pour distribution facile
mkdir -p release
cp "$APK_PATH" release/

if [ "$RELEASE" -eq 1 ]; then
    cp "$APK_PATH" release/PhotoCertif-release.apk
    echo -e "${GREEN}✓ APK copié dans release/PhotoCertif-release.apk${NC}"
else
    cp "$APK_PATH" release/PhotoCertif-debug.apk
    echo -e "${GREEN}✓ APK copié dans release/PhotoCertif-debug.apk${NC}"
fi

# ── Installation ──────────────────────────────────────────────────────────────
if [ "$APK_ONLY" -eq 0 ]; then
    echo -e "${YELLOW}📲  Installation sur le téléphone…${NC}"
    adb install -r "$APK_PATH"
    echo -e "${GREEN}✅  Installation terminée !${NC}"

    echo ""
    echo "  Pour voir les logs en temps réel :"
    echo "  adb logcat -s CryptoManager CameraManager MainViewModel"
fi

echo ""
