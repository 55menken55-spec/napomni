#!/usr/bin/env bash
# Заливка репозитория napomni на GitHub.
# Использование: ./tools/push_to_github.sh <github-username> <token> [private|public]
set -euo pipefail
USER_NAME="${1:?нужен github-username}"
TOKEN="${2:?нужен токен PAT (classic, scope repo)}"
VISIBILITY="${3:-private}"
cd "$(dirname "$0")/.."

# Если .git повреждён (снапшоты воркспейса вырезают .git/config) — пересоздаём историю.
if ! git rev-parse --git-dir >/dev/null 2>&1; then
  echo ".git повреждён — пересоздаю репозиторий..."
  rm -rf .git
  git init -b main
  git config user.name "Napomni"
  git config user.email "napomni@users.noreply.github.com"
  git add -A
  git commit -q -m "«Напомни» v1.1 — Android-приложение напоминаний (этапы M1–M6)

Точные напоминания (уведомление/будильник), отложение 1–180 мин, заметки с
чек-листами, история и статистика с календарём, резервная копия JSON,
виджет «Ближайшее», встроенная инструкция (10 глав), онбординг разрешений,
светлая/тёмная тема. Полностью офлайн (без разрешения INTERNET).
Тесты 30/30. APK: app-release-v1.1.apk, app-debug-v1.1.apk."
  git tag v1.1
fi

# 1) Создать репозиторий napomni (если ещё нет) через API
CODE=$(curl -s -o /tmp/gh_create.json -w "%{http_code}" -X POST \
  -H "Authorization: Bearer $TOKEN" -H "Accept: application/vnd.github+json" \
  https://api.github.com/user/repos \
  -d "{\"name\":\"napomni\",\"private\":$([ "$VISIBILITY" = private ] && echo true || echo false),\"description\":\"«Напомни» — Android-приложение точных напоминаний (офлайн)\"}")
echo "Создание репозитория: HTTP $CODE (201=создан, 422=уже существует — ок)"

# 2) Залить код. Токен — только в URL этого вызова; в remote не сохраняется.
git remote remove origin 2>/dev/null || true
git remote add origin "https://github.com/${USER_NAME}/napomni.git"
git push "https://x-access-token:${TOKEN}@github.com/${USER_NAME}/napomni.git" main --tags
git branch --set-upstream-to=origin/main main 2>/dev/null || true
rm -f /tmp/gh_create.json
echo "Готово: https://github.com/${USER_NAME}/napomni"
echo "Не забудьте отозвать токен: GitHub → Settings → Developer settings → Tokens"
