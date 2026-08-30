#!/usr/bin/env bash
set -euo pipefail

PROJECT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$PROJECT_DIR"

if ! command -v docker >/dev/null 2>&1; then
  echo "docker コマンドが見つかりません。" >&2
  exit 1
fi

if ! docker info >/dev/null 2>&1; then
  echo "Docker daemon に接続できません。OrbStack または Docker Desktop を起動してください。" >&2
  exit 1
fi

echo "[1/4] プラグインをビルドしています"
mvn -q package

echo "[2/4] Paper テストサーバーを起動しています"
docker compose up -d

echo "[3/4] サーバーの起動を待っています"
ready=false
for _ in $(seq 1 90); do
  if docker compose logs --no-color --tail=200 paper 2>/dev/null | rg -q 'Done \('; then
    ready=true
    break
  fi

  if ! docker compose ps --status running --services 2>/dev/null | rg -q '^paper$'; then
    docker compose logs --no-color --tail=200 paper >&2 || true
    echo "Paper コンテナが停止しました。" >&2
    exit 1
  fi
  sleep 2
done

if [[ "$ready" != true ]]; then
  docker compose logs --no-color --tail=200 paper >&2 || true
  echo "Paper の起動がタイムアウトしました。" >&2
  exit 1
fi

echo "[4/4] RCON 経由でプラグインを確認しています"
plugins_output="$(docker compose exec -T paper rcon-cli plugins 2>&1)"
echo "$plugins_output"

if ! rg -q 'AuctionHouse' <<<"$plugins_output"; then
  echo "AuctionHouse が有効なプラグイン一覧にありません。" >&2
  exit 1
fi
if ! rg -q 'Vault' <<<"$plugins_output"; then
  echo "Vault が有効なプラグイン一覧にありません。" >&2
  exit 1
fi
if ! rg -q 'Essentials' <<<"$plugins_output"; then
  echo "EssentialsX が有効なプラグイン一覧にありません。" >&2
  exit 1
fi

plugin_errors="$(docker compose logs --no-color paper | rg -i '\[(AuctionHouse|Vault|Essentials)\].*(error|exception|failed|disabled|unsupported)' || true)"
if [[ -n "$plugin_errors" ]]; then
  echo "依存プラグインまたはAuctionHouseのエラーを検出しました:" >&2
  echo "$plugin_errors" >&2
  exit 1
fi

command_output="$(docker compose exec -T paper rcon-cli ah 2>&1 || true)"
echo "$command_output"
if ! rg -q 'プレイヤーのみ' <<<"$command_output"; then
  echo "/ah のプレイヤー専用チェックを確認できませんでした。" >&2
  exit 1
fi

database_file="$PROJECT_DIR/server-data-26.1.2/plugins/AuctionHouse/auction.db"
if [[ ! -f "$database_file" ]]; then
  echo "AuctionHouse の SQLite データベースが作成されていません: $database_file" >&2
  exit 1
fi

DATABASE_FILE="$database_file" python3 - <<'PY'
import os
import sqlite3

database_file = os.environ["DATABASE_FILE"]
with sqlite3.connect(database_file) as connection:
    tables = {
        row[0]
        for row in connection.execute(
            "SELECT name FROM sqlite_master WHERE type = 'table'"
        )
    }

required = {"listings", "bids", "vault_items", "notifications"}
missing = required - tables
if missing:
    raise SystemExit(f"SQLite テーブルが不足しています: {sorted(missing)}")

print(f"SQLite OK: {', '.join(sorted(required))}")
PY

echo "プラグインテストに成功しました。サーバーは起動したままです。"
