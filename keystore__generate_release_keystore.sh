#!/usr/bin/env bash
set -euo pipefail
# Generates an independent, production-grade RSA 4096-bit Android Release Keystore
KEYSTORE_FILE="${1:-smart_receiver_pro_release.jks}"
KEY_ALIAS="${2:-smart_receiver_pro_key}"

if [ -f "$KEYSTORE_FILE" ]; then
    echo "Keystore $KEYSTORE_FILE already exists. Aborting to prevent overwrite."
    exit 1
fi

read -r -s -p "Enter new Keystore Password (min 8 chars): " STORE_PASS
echo ""
read -r -s -p "Enter new Key Password (min 8 chars): " KEY_PASS
echo ""

keytool -genkeypair -v \
  -keystore "$KEYSTORE_FILE" \
  -alias "$KEY_ALIAS" \
  -keyalg RSA \
  -keysize 4096 \
  -validity 10000 \
  -storepass "$STORE_PASS" \
  -keypass "$KEY_PASS" \
  -dname "CN=Smart Receiver Pro, OU=Mobile Engineering, O=SmartReceiverPro, L=Cairo, ST=Cairo, C=EG"

echo "Release Keystore generated at: $KEYSTORE_FILE"
echo "IMPORTANT: Store $KEYSTORE_FILE and keystore.properties outside Git version control."
