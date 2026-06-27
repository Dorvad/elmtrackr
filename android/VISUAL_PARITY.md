# Visual Parity: Android ↔ ElmTrackr Web

> This audit was written against the web snapshot at the Android branch point.
> The current default web branch contains later UX changes. Treat this document
> as a token/component baseline, not a claim of complete current parity.

Audit of differences between the native Android app and the original
ElmTrackr web/PWA. Changes applied in commit "Align native Android UI
with original ElmTrackr web design".

## Design Tokens

| Token | Web (`--au-*`) | Android | Status |
|---|---|---|---|
| Background | `#ECEEFA` | `AuroraLavender Color(0xFFECEEFA)` | ✓ |
| Primary text | `#181530` | `AuroraNavy Color(0xFF181530)` | ✓ |
| Secondary text | `#615C8A` | `AuroraInk2 Color(0xFF615C8A)` | ✓ |
| Placeholder | `#A7A2C8` | `AuroraFaint Color(0xFFA7A2C8)` | ✓ |
| Dividers | `rgba(91,77,242,0.10)` | `AuroraHair Color(0x1A5B4DF2)` | ✓ |
| Card surface | `#FFFFFF` | `AuroraSurface Color(0xFFFFFFFF)` | ✓ |
| Surface sub | `#F6F6FD` | `AuroraSurfaceSub Color(0xFFF6F6FD)` | ✓ |
| Indigo (primary) | `#5B4DF2` | `AuroraIndigo Color(0xFF5B4DF2)` | ✓ |
| Plum | `#8B5CF6` | `AuroraPlum Color(0xFF8B5CF6)` | ✓ |
| Aqua | `#16C8D6` | `AuroraAqua Color(0xFF16C8D6)` | ✓ |
| Peach (overtime) | `#FF9E7D` | `AuroraPeach Color(0xFFFF9E7D)` | ✓ |
| Overtime bg | `#FFF0E9` | `AuroraOvertimeBg Color(0xFFFFF0E9)` | ✓ |
| Weekend bg | `#F2EDFE` | `AuroraWeekendBg Color(0xFFF2EDFE)` | ✓ |
| Gradient | `118deg #5B4DF2→#7C5CF6→#16C8D6` | `auroraGradient` linearGradient | ✓ |

## Typography

| Element | Web | Android | Status |
|---|---|---|---|
| Display font | Bricolage Grotesque | `res/font/bricolage_grotesque.ttf` | ✓ |
| Body font | Hanken Grotesk | `res/font/hanken_grotesk.ttf` | ✓ |
| Greeting | `text-xs uppercase tracking-widest AuroraFaint` | `labelSmall Bold AuroraFaint` | ✓ |
| App title | `text-3xl font-bold tracking-tight` | `headlineMedium Bold` | ✓ |
| Section header | `text-xs font-bold uppercase tracking-widest AuroraInk2` | `labelSmall SemiBold AuroraInk2` | ✓ |

## Components

### Cards
| Property | Web | Android | Status |
|---|---|---|---|
| Corner radius | `rounded-3xl` = 24dp | 24dp default | ✓ |
| Background | `bg-white` | `AuroraSurface` | ✓ |
| Border | `border border-white/80` | none | skip |
| Shadow | `0 16px 40px -26px rgba(80,64,210,0.34)` | `shadow()` indigo spotColor | ✓ |

### Buttons
| Property | Web | Android | Status |
|---|---|---|---|
| Primary | `background: var(--au-grad)` gradient | `ElmGradientButton` | ✓ |
| Border radius | `rounded-2xl` = 18dp | 18dp (`CornerRadius.Button`) | ✓ |
| Shadow | `shadow-[0_14px_26px_-10px_rgba(91,77,242,0.7)]` | `shadow()` indigo spotColor | ✓ |

### Bottom Nav
| Property | Web | Android | Status |
|---|---|---|---|
| Active pill | gradient `var(--au-grad)` | custom gradient pill | ✓ |
| Background | `rgba(236,238,250,0.85)` + blur | `AuroraLavender` 92% + upward shadow | partial |
| Active icon | white on gradient | white on gradient pill | ✓ |
| Inactive | `AuroraFaint` icon + label | `AuroraFaint` | ✓ |
| Top divider | `border-t var(--au-hair)` | `HorizontalDivider AuroraHair` | ✓ |

### Shift Row
| Property | Web | Android | Status |
|---|---|---|---|
| Left stripe | 4px colored per type | 4dp colored stripe | ✓ |
| Date block | day number + abbreviated weekday | day number + weekday | ✓ |
| Time range | `HH:mm — HH:mm` | `HH:mm → HH:mm` | ✓ |
| Active badge | green `Live` pill | `● Active` indigo pill | ✓ |

### Dashboard Header
| Property | Web | Android | Status |
|---|---|---|---|
| Greeting | small-caps `AuroraFaint` above title | `labelSmall Bold AuroraFaint` | ✓ |
| Bolt icon | 22×22 gradient box + SVG bolt | 22dp `Box` with gradient + `Icons.Filled.Bolt` | ✓ |
| App name | "elmtrackr" `headlineMedium Bold` | "elmtrackr" `headlineMedium Bold` | ✓ |
| Sign-out | circle button in header | in Settings screen | skip |

### App Icon
| Property | Web | Android | Status |
|---|---|---|---|
| Design | gradient bg + horseshoe arc + bolt | vector gradient bg + horseshoe + bolt | ✓ |
| Adaptive | — | mipmap-anydpi-v26 + gradient bg drawable | ✓ |

## Known Gaps (not blocking)
- Card border `border-white/80` (subtle; hairline outline used instead)
- ~~Aurora mesh animated background on dashboard~~ ✅ added (`AuroraMeshBackground`)
- ~~True backdrop blur on bottom nav~~ ✅ frosted mesh blur on API 31+ (`ElmBottomNav`)
