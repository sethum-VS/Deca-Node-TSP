#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────
# init-data.sh — Downloads the Sri Lanka OSM PBF file from
# Geofabrik if it doesn't already exist locally.
#
# Usage:  ./init-data.sh
# Run this BEFORE docker-compose up on a fresh clone.
# ─────────────────────────────────────────────────────────────
set -euo pipefail

DATA_DIR="java-routing-engine/data"
PBF_FILE="${DATA_DIR}/sri-lanka.osm.pbf"
DOWNLOAD_URL="https://download.geofabrik.de/asia/sri-lanka-latest.osm.pbf"

echo "╔══════════════════════════════════════════════╗"
echo "║   Deca-Node — OSM Data Initializer           ║"
echo "╚══════════════════════════════════════════════╝"

# 1. Create data directory
if [ ! -d "${DATA_DIR}" ]; then
    echo "📁 Creating data directory: ${DATA_DIR}"
    mkdir -p "${DATA_DIR}"
fi

# 2. Check if PBF file exists
if [ -f "${PBF_FILE}" ]; then
    FILE_SIZE=$(du -h "${PBF_FILE}" | cut -f1)
    echo "✅ OSM file already exists: ${PBF_FILE} (${FILE_SIZE})"
    echo "   Skipping download."
    exit 0
fi

# 3. Download the PBF file
echo "⬇️  Downloading Sri Lanka OSM data from Geofabrik..."
echo "   URL: ${DOWNLOAD_URL}"
echo "   Destination: ${PBF_FILE}"
echo ""

if command -v curl &> /dev/null; then
    curl -L --progress-bar -o "${PBF_FILE}" "${DOWNLOAD_URL}"
elif command -v wget &> /dev/null; then
    wget --show-progress -O "${PBF_FILE}" "${DOWNLOAD_URL}"
else
    echo "❌ Error: Neither curl nor wget is installed."
    echo "   Please install one and retry."
    exit 1
fi

FILE_SIZE=$(du -h "${PBF_FILE}" | cut -f1)
echo ""
echo "✅ Download complete: ${PBF_FILE} (${FILE_SIZE})"
echo ""
echo "🚀 You can now run: docker-compose up --build"
