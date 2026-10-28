#!/usr/bin/env bash
# Галерея «Кулинария — разделка и формы» на хабе галерей (../../Gallery Hub, README там же).
#   python -I build_gallery.py         — собрать site/ (вопросы в content.py, данные в cache/)
#   bash gallery_deploy.sh push        — выгрузить site/ (ответы не трогаются)
#   bash gallery_deploy.sh pull        — забрать ответы в review.json
#   bash gallery_deploy.sh link        — ссылки с ключом
set -euo pipefail
cd "$(dirname "$0")"
HUB="../../Gallery Hub/deploy.sh"
case "${1:-}" in
push) bash "$HUB" push culinary "$PWD/site" ;;
pull) bash "$HUB" pull culinary review "$PWD/review.json" ;;
link) bash "$HUB" link ;;
*) sed -n 2,6p "$0"; exit 1 ;;
esac
