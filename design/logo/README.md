# Flavors logo set

Vector redraw of the logo from the pitch deck. The lettering and heart-V were traced from the
original artwork (so the letterforms are unchanged), cleaned up, and rebuilt as SVG with
gradients. The stray teal leaf fragment that sat inside the "O" in the deck was removed.
The wordmark is outlined paths: no font is needed to render it.

| File | Use |
|---|---|
| `flavors-logo.svg` | Primary wordmark, for light / cream backgrounds |
| `flavors-logo-dark.svg` | For dark teal backgrounds: cream letters, same heart |
| `flavors-logo-mono.svg` | One color via `currentColor`: print, stamps, watermark |
| `flavors-icon.svg`, `flavors-icon-mono.svg` | Heart-V alone (avatars, loading, empty states). One color heart for every background, light or dark; mono for single-color print |
| `favicon.svg`, `favicon.ico` | Browser tab (ico has 16/32/48) |
| `app-icon.svg`, `png/app-icon-{192,512}.png` | PWA / home-screen icon on cream |
| `png/` | Raster exports of the above |

## Rules
- **Clear space:** keep at least the height of the heart's notch (about 1/6 of the logo height) free on all sides.
- **Minimum size:** wordmark 96px wide; below that use the icon. Icon 24px; below that use the favicon.
- Use the light wordmark on Cream, White or Sand; the dark wordmark on Teal 700 or darker. Never put the
  light wordmark on teal or orange fills. The heart keeps its colors on both.
- Don't recolor, stretch, rotate, add shadows, or reorder the heart's colors.
- The heart replaces the "V" of FLAVORS. Never type the word with a normal "V" next to the heart.

## Not included, on purpose
- **Stacked layout:** the heart is the V in the wordmark, so a stacked version would spell "FLA ORS".
  Square spaces use the icon.
- **OG / social image:** needs the tagline in the brand typeface, so it comes with the type work in step 3.
