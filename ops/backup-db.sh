#!/usr/bin/env bash
set -euo pipefail

CARWATCH_HOME="${CARWATCH_HOME:-/opt/carwatch}"
DB_FILE="${CARWATCH_DB_FILE:-$CARWATCH_HOME/data/carwatch.db}"
BACKUP_DIR="${CARWATCH_BACKUP_DIR:-$CARWATCH_HOME/backups}"
TIMESTAMP="$(date +%Y%m%d-%H%M%S)"

mkdir -p "$BACKUP_DIR"
cp "$DB_FILE" "$BACKUP_DIR/carwatch-$TIMESTAMP.db"

echo "Backup created: $BACKUP_DIR/carwatch-$TIMESTAMP.db"
