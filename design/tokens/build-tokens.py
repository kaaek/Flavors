#!/usr/bin/env python3
"""Generates tokens.css and tokens.json from one table, so they can't drift apart.

    python3 design/tokens/build-tokens.py

Edit values HERE, not in the generated files. Colors mirror design/foundations/palette.md.
Two layers: primitives (raw scales, named by what they are) and semantic tokens (named by
what they are FOR). Components should only use the semantic layer: that is what lets us
re-theme (e.g. dark mode) later by remapping a few names instead of touching components.
"""
import json
import pathlib

HERE = pathlib.Path(__file__).parent

PRIMITIVES = {
    "color": {
        "teal-50": "#E6F5F3", "teal-100": "#C2E7E2", "teal-300": "#4DB8AC", "teal-400": "#009383",
        "teal-500": "#007F7C", "teal-600": "#00776F", "teal-700": "#006D6B", "teal-800": "#00504F",
        "teal-900": "#003836",
        "paprika-50": "#FDEBE5", "paprika-300": "#F0907A", "paprika-500": "#CC3300", "paprika-700": "#992600",
        "saffron-100": "#FBE3CF", "saffron-500": "#E58039", "saffron-700": "#A94E0C",
        "beet-500": "#8E1341",
        "cream": "#FBF6EE", "sand": "#F1E9DB", "sand-dark": "#E4D8C4", "stone-500": "#74807E",
        "ink": "#1B2422", "ink-soft": "#4A5755", "white": "#FFFFFF",
    },
}

# What each color is FOR. Values reference primitives by name.
SEMANTIC = {
    "bg": "cream",                  # page background
    "surface": "white",             # cards, inputs, modals
    "surface-muted": "sand",        # table stripes, sidebars, code blocks
    "border": "sand-dark",          # decorative dividers and card edges only (1.4:1)
    "border-strong": "stone-500",   # form controls and anything that must be seen: 3:1+ (WCAG 1.4.11)
    "text": "ink",
    "text-muted": "ink-soft",
    "text-on-primary": "white",     # Paprika fill takes white (5.2:1)
    "text-on-secondary": "white",   # Teal fill takes white (4.9:1)
    "text-on-accent": "ink",        # Saffron fill takes ink: white fails (2.8:1)
    "primary": "paprika-500",       # main call to action
    "primary-hover": "paprika-700",
    "secondary": "teal-500",        # navigation, selected, secondary buttons
    "secondary-hover": "teal-700",
    "accent": "saffron-500",        # decorative only, never text on light
    "link": "teal-700",             # teal-500 on cream is only 4.5:1, so links use 700
    "focus-ring": "teal-500",
    "success": "teal-600", "success-bg": "teal-50",
    "warning": "saffron-700", "warning-bg": "saffron-100",   # chip text uses --color-text, see palette.md
    "danger": "paprika-700", "danger-bg": "paprika-50",
    "info": "teal-700", "info-bg": "teal-50",   # teal-500 on teal-50 is only 4.3:1
    "nav-bg": "teal-700",
    "nav-text": "cream",
}

OTHER = {
    "font": {
        # Display only for big headings (h1-h3, hero, numbers). Marcellus has one weight (400).
        "display": "'Marcellus', 'Cormorant Garamond', Georgia, serif",
        "body": "'Lato', system-ui, -apple-system, 'Segoe UI', Roboto, sans-serif",
        "mono": "ui-monospace, 'SF Mono', Menlo, Consolas, monospace",
    },
    "font-size": {  # rem; 1rem = 16px
        "xs": "0.75rem", "sm": "0.875rem", "md": "1rem", "lg": "1.125rem", "xl": "1.25rem",
        "2xl": "1.5rem", "3xl": "1.875rem", "4xl": "2.375rem", "5xl": "3rem", "6xl": "3.75rem",
    },
    "font-weight": {"regular": "400", "bold": "700", "black": "900"},
    "line-height": {"tight": "1.15", "snug": "1.3", "normal": "1.5", "relaxed": "1.65"},
    "letter-spacing": {"display": "0.02em", "caps": "0.08em", "normal": "0"},
    "space": {  # 4px base
        "0": "0", "1": "0.25rem", "2": "0.5rem", "3": "0.75rem", "4": "1rem", "5": "1.25rem",
        "6": "1.5rem", "8": "2rem", "10": "2.5rem", "12": "3rem", "16": "4rem", "20": "5rem",
    },
    "radius": {"sm": "6px", "md": "10px", "lg": "16px", "xl": "24px", "pill": "999px"},
    # Shadows are tinted with Ink, not black, so they sit warmly on cream.
    "shadow": {
        "sm": "0 1px 2px rgba(27, 36, 34, 0.08)",
        "md": "0 4px 12px rgba(27, 36, 34, 0.10)",
        "lg": "0 12px 32px rgba(27, 36, 34, 0.14)",
        "focus": "0 0 0 3px rgba(0, 127, 124, 0.35)",
    },
    "gradient": {  # from the logo
        "heart": "linear-gradient(160deg, #E58039 0%, #B5433D 55%, #8E1341 100%)",
        "leaf": "linear-gradient(135deg, #009383 0%, #006D6B 100%)",
        "hero": "linear-gradient(135deg, #00776F 0%, #003836 100%)",
    },
    "overlay": {"scrim": "rgba(27, 36, 34, 0.55)"},   # behind modals
    "motion": {
        "duration-fast": "120ms", "duration-base": "200ms", "duration-slow": "320ms",
        "ease-out": "cubic-bezier(0.22, 1, 0.36, 1)", "ease-in-out": "cubic-bezier(0.65, 0, 0.35, 1)",
    },
    "z": {"nav": "100", "dropdown": "200", "modal": "300", "toast": "400"},
    "layout": {"content-max": "72rem", "prose-max": "42rem", "touch-target": "44px"},
}
# CSS custom properties can't be used inside @media queries, so these live in JSON only.
BREAKPOINTS = {"sm": "640px", "md": "768px", "lg": "1024px", "xl": "1280px"}


def css():
    out = ["/* GENERATED by build-tokens.py: edit that file, not this one. */",
           "/* Fonts: add to <head>: */",
           "/* <link rel=\"stylesheet\" href=\"https://fonts.googleapis.com/css2?family=Lato:wght@400;700;900&family=Marcellus&display=swap\"> */",
           ":root {", "  /* Primitives: raw palette. Don't use directly in components. */"]
    for k, v in PRIMITIVES["color"].items():
        out.append(f"  --palette-{k}: {v};")
    out.append("\n  /* Semantic colors: what components use. */")
    for k, v in SEMANTIC.items():
        out.append(f"  --color-{k}: var(--palette-{v});")
    for group, items in OTHER.items():
        out.append(f"\n  /* {group} */")
        for k, v in items.items():
            out.append(f"  --{group}-{k}: {v};")
    out.append("}")
    return "\n".join(out) + "\n"


def js():
    pal = PRIMITIVES["color"]
    return json.dumps({
        "palette": pal,
        "color": {k: pal[v] for k, v in SEMANTIC.items()},
        **OTHER,
        "breakpoint": BREAKPOINTS,
    }, indent=2) + "\n"


if __name__ == "__main__":
    for k, v in SEMANTIC.items():
        assert v in PRIMITIVES["color"], f"semantic '{k}' points at unknown palette color '{v}'"
    (HERE / "tokens.css").write_text(css())
    (HERE / "tokens.json").write_text(js())
    print("wrote tokens.css, tokens.json")
