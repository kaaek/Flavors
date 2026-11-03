# Flavors: brand foundations

Source of truth for the palette. Derived from the pitch deck: colors were sampled from the actual
logo files (`Pitch.pptx` → `image2.png` wordmark, `image13.png` heart-V), not guessed.
Tokens (`design/tokens/`) are generated from this later.

## Personality
Warm, appetizing, trustworthy. Teal = trust and money (escrow, verified). Orange/red = appetite
and action. Cream = the table we serve on. Playful in illustrations, calm in data screens.

## Core colors (sampled from the logo)

| Name | Hex | Where it comes from | Role |
|---|---|---|---|
| Paprika | `#CC3300` | wordmark letters | Brand red-orange: logo, primary CTA, key accents |
| Saffron | `#E58039` | heart-V top-right gradient stop | Highlights, illustrations, charts (never body text) |
| Beet | `#8E1341` | heart-V bottom gradient stop | Gradient end, rare deep accent |
| Teal 500 | `#007F7C` | pitch slide backgrounds | Primary UI color: nav, links, selected states |
| Teal 700 | `#006D6B` | heart leaf, dark stop | Hover/pressed, dark surfaces |
| Teal 400 | `#009383` | heart leaf, light stop | Gradient start, illustration fills |
| Cream | `#FBF6EE` | new | Page background (replaces stark white) |
| Ink | `#1B2422` | new (pitch used pure black) | Body text, softer than `#000` and teal-tinted |

Brand gradients (from the heart):
- **Heart-V:** `Saffron #E58039` → `Beet #8E1341` (top-right to bottom).
- **Leaf:** `Teal 400 #009383` → `Teal 700 #006D6B`.

## Scales (to be finalized in tokens)

- **Teal:** 50 `#E6F5F3` · 100 `#C2E7E2` · 300 `#4DB8AC` · 400 `#009383` · 500 `#007F7C` · 600 `#00776F` · 700 `#006D6B` · 800 `#00504F` · 900 `#003836`
- **Paprika:** 50 `#FDEBE5` · 300 `#F0907A` · 500 `#CC3300` · 700 `#992600`
- **Saffron:** 100 `#FBE3CF` · 500 `#E58039` · 700 `#A94E0C` (text-safe variant)
- **Neutrals:** cream `#FBF6EE` · sand `#F1E9DB` (cards, dividers) · ink-soft `#4A5755` · ink `#1B2422`
- **Semantic:** success = Teal 600 · warning = Saffron 700 on Saffron 100 · danger = Paprika 700 · info = Teal 500
  (the brand is already teal/orange, so semantic colors reuse it rather than adding green/yellow/blue).

## Contrast results (WCAG, measured)

| Pair | Ratio | Verdict |
|---|---|---|
| Ink on Cream | 14.8 | AAA |
| Ink-soft on Cream | 7.0 | AAA |
| Teal 500 on Cream | 4.5 | AA (text OK, barely: prefer Teal 700 for small text) |
| Teal 700 on Cream | 5.7 | AA |
| White on Teal 500 | 4.9 | AA |
| White on Teal 700 | 6.2 | AA |
| Paprika on Cream / White | 4.8 / 5.2 | AA |
| White on Paprika | 5.2 | AA |
| White on Beet | 9.1 | AAA |
| **White on Saffron** | **2.8** | **FAIL → use Ink on Saffron (5.7)** |
| **Saffron 500 on Cream** | **2.6** | **FAIL → never text; use Saffron 700 (5.2, AA)** |
| Saffron 700 on Saffron 100 | 4.49 | just under AA → warning chips use Ink text instead |
| Paprika 700 on Paprika 50 | 6.9 | AA (danger chips) |
| Ink on Sand | 13.2 | AAA |

Rules that follow:
1. Buttons: primary = Paprika fill + white text; secondary = Teal 500 fill + white; Saffron fills take Ink text.
2. Saffron 500 is decorative (illustrations, chart fills, badges with Ink text), never text on a light background.
3. Small teal text on Cream uses Teal 700.

## Things I noticed in the deck that the identity fixes
- The deck mixes 4 families (Lovelo, Montserrat, Lato, Glacial Indifference) and 3 teals. We keep one teal
  as primary (`#007F7C`) and fold `#00776F`/`#017A72` into the scale.
- The logo's "V" is the heart and the **"O" has a stray teal leaf-fragment** inside it (visible in
  `image2.png`). I'll drop that artifact in the redrawn logo, in step 2.
- `#CC5500` (burnt orange in the deck) is dropped in favor of the logo's real wordmark color `#CC3300`.
