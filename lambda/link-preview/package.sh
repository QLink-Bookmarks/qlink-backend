#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
mkdir -p build
rm -rf build/staging
mkdir -p build/staging
cp -R index.mjs src package.json package-lock.json build/staging/
(
  cd build/staging
  npm ci --omit=dev --os=linux --cpu=x64 --libc=glibc
)
rm build/staging/package-lock.json
find build/staging -type f -exec touch -t 198001010000 {} +
rm -f build/link-preview.zip
(
  cd build/staging
  find . -type f -print | LC_ALL=C sort | zip -X -q ../link-preview.zip -@
)
rm -rf build/staging
