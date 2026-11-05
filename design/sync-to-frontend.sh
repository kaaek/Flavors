#!/usr/bin/env bash
# The Docker build only sees frontend/, so the frontend can't import ../design directly.
# This copies the design source of truth into the app. Run it after changing tokens or components,
# then commit both. Never edit the copies in frontend/src/styles by hand.
set -euo pipefail
cd "$(dirname "$0")/.."
python3 design/tokens/build-tokens.py
for f in tokens/tokens.css components.css; do
  { echo "/* COPIED from design/$f by design/sync-to-frontend.sh. Do not edit here. */"; cat "design/$f"; } > "frontend/src/styles/$(basename "$f")"
done
# CI runs prettier --check on src, so format the copies here; otherwise every sync fails CI.
(cd frontend && npx --no-install prettier --write src/styles/tokens.css src/styles/components.css >/dev/null)
cp design/logo/favicon.svg frontend/public/favicon.svg
cp design/logo/flavors-logo.svg frontend/public/flavors-logo.svg
cp design/logo/flavors-logo-dark.svg frontend/public/flavors-logo-dark.svg
cp design/logo/flavors-icon.svg frontend/public/flavors-icon.svg
cp design/logo/png/app-icon-192.png design/logo/png/app-icon-512.png frontend/public/
# The icon sprite and the art the pages use. The sprite's root is display:none in design/ (it is inlined
# there); as a separate file the pages <use> from, it must not be hidden.
sed 's/ style="display:none"//' design/icons/icons.svg > frontend/public/icons.svg
mkdir -p frontend/public/illustrations/spots
cp design/illustrations/hero.svg design/illustrations/pattern-cream.svg frontend/public/illustrations/
cp design/illustrations/spots/*.svg frontend/public/illustrations/spots/
echo "synced design -> frontend"
