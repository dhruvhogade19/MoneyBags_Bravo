# MoneyBags OJET UI Theme Specification

## Theme direction: Warm Editorial Banking

This is the approved MoneyBags visual direction. It adapts the supplied reference into an original banking interface: warm ivory canvas, deep espresso navigation, layered cocoa financial cards, oversized rounded headings, soft translucent panels, minimal line icons, and generous whitespace.

The reference supplies the visual language, not the information architecture. MoneyBags keeps its real customer and operations navigation, financial statuses, accessibility requirements, and Oracle JET component behavior.

## 1. Design character

The interface should feel:

- Warm and personal rather than corporate blue.
- Premium without looking like a luxury shopping product.
- Friendly in customer journeys and precise in operations journeys.
- Spacious, with a few large surfaces rather than many small metric cards.
- Soft in shape but firm in financial confirmations and error handling.

Avoid:

- Teal or blue as the dominant brand color.
- Generic white SaaS dashboards with small KPI cards.
- Glass effects that reduce text contrast.
- Excessive gradients, shadows, animated blobs, or decorative charts.
- Copying the reference brand name, logo, wording, or unsupported budgeting features.

## 2. Core palette

All feature code must use semantic tokens. Hex values belong only in the theme layer.

| Semantic token | Value | Purpose |
|---|---:|---|
| Canvas | `#F6F0EA` | Main application background |
| Canvas highlight | `#FBF8F4` | Subtle radial highlight behind content |
| Surface | `#FFFCF8` | Activity panels, dialogs, forms |
| Surface soft | `#EFE4DA` | Secondary panels and icon backgrounds |
| Surface warm | `#E7D7C8` | Progress/bill/selected supporting surface |
| Text primary | `#24170F` | Headings, financial values, body text |
| Text secondary | `#695A50` | Supporting copy and metadata |
| Border | `#E3D7CD` | Dividers, inputs, quiet panel outlines |
| Espresso 950 | `#28170E` | Navigation start, darkest actions |
| Espresso 900 | `#382215` | Primary action and strong brand surface |
| Espresso 800 | `#4A2E1C` | Hover/active action |
| Cocoa 700 | `#765139` | Main balance card |
| Cocoa 500 | `#A17B60` | Decorative tonal layer only |
| Cream on dark | `#FFF9F2` | Text/icons on espresso and cocoa |
| Focus | `#2F6EB3` | Accessible keyboard focus ring |
| Information | `#315F86` | Information/processing status |
| Success | `#3E6C4E` | Approved, active, verified, settled |
| Warning | `#8A5A19` | Pending, review, blocked, due soon |
| Danger | `#A43C32` | Failed, rejected, overdue, destructive |

### Tonal layer recipe

The balance card may contain two or three large abstract shapes using Cocoa 500 or Cream on dark at 8–14% opacity. Shapes are decorative, remain behind content, do not animate continuously, and never reduce text contrast. Other cards remain mostly flat.

## 3. Typography

- Preferred family: `Manrope`, then `Oracle Sans`, `Segoe UI`, system sans-serif.
- Use a rounded geometric sans; do not use a serif or handwritten face.
- Page greeting: 40–52px desktop, 30–36px tablet, 28–32px mobile; weight 700.
- Large financial balance: 48–58px desktop, 38–44px compact; weight 700.
- Section title: 20–24px; weight 700.
- Navigation and action label: 15–16px; weight 600.
- Body: 15–16px customer, 14px operations.
- Supporting text: 13–14px; never below 12px.
- Use tabular numerals for money, balances, limits, and aligned dates.
- Use sentence case except real identifiers such as `CC-101`, `INR`, and `KYC`.
- Break long greetings over two lines only when it improves the composition; do not force awkward wrapping on small screens.

## 4. Shape, spacing, and elevation

- Spacing grid: 4px with primary gaps of 8, 12, 16, 24, 32, and 48px.
- Outer application frame: 28–36px radius on wide screens, 20px on tablet, no decorative outer frame required on mobile.
- Sidebar: 28–36px radius where it meets outer corners.
- Hero/balance card: 28–34px radius.
- Standard content panel: 22–28px radius.
- Inputs and compact buttons: 14–18px radius.
- Square icon controls use soft “squircle” corners, not circles.
- Avatar and short status markers may be circular/pill shaped.
- Prefer faint 1px warm borders. Use shadows only for floating menus/dialogs and a very soft application-frame lift.
- Do not nest bordered panels. Use spacing and dividers within activity or detail panels.

## 5. Application shell

### Wide customer shell

- Sidebar width: 248–288px depending on viewport.
- Espresso-to-cocoa vertical gradient with a restrained radial highlight near the lower edge.
- White/cream MoneyBags mark and name at the top.
- Small uppercase group label such as “YOUR MONEY” with extra letter spacing.
- Each navigation item has a minimal outlined icon and visible label.
- Selected navigation uses a translucent cream/cocoa surface with a subtle inset border.
- Support text remains at the bottom, separated by a translucent divider.
- Main canvas uses a warm radial highlight and 32–48px content padding.

### Top actions

- Search is a labeled squircle control, not an unlabeled magnifier.
- The primary top action is a taller espresso surface such as “Transfer money”.
- Avatar is a compact espresso circle with initials.
- Notifications may be a small icon control; do not crowd the top row.

### Mobile shell

- Replace the sidebar with a 60px warm top bar and modal navigation drawer.
- Show logo, page context, notifications, and avatar.
- Stack all dashboard panels in priority order.
- Primary transaction action becomes full width below the greeting or uses a non-obscuring bottom action area.

## 6. Dashboard composition

The customer overview follows the visual rhythm of the reference while using MoneyBags data:

1. Large greeting, one-line financial context, search, transfer action, and avatar.
2. Main grid with a dominant total-available balance card and one narrow contextual card.
3. Activity panel below the balance card.
4. Narrow upcoming-bill or KYC panel beside activity on wide screens.
5. On mobile: greeting → main action → balance → contextual card → bill → activity.

Do not add unsupported budgeting or “insights” pages merely because they appear in the reference.

## 7. Component treatment

### Primary balance card

- Cocoa surface with cream text.
- Small contextual label, then one dominant amount.
- No more than two actions, rendered as translucent cream controls with sufficient contrast.
- Account count and freshness appear as secondary text.
- Abstract tonal shapes are clipped to the card and marked decorative.

### Supporting feature card

- Soft cream/warm surface with one espresso icon squircle.
- Short editorial heading such as “Your banking, clearly organised.”
- One actionable supporting fact, for example next maturity or KYC state.

### Activity list

- Surface card with section heading and “See all”.
- Rows use a small warm icon tile, primary transaction description, secondary account/date text, and right-aligned amount.
- Use warm dividers only; no vertical grid lines.
- Status appears as text where it adds value, not as a chip on every row.

### Upcoming bill panel

- Warm surface with due date, outstanding amount, minimum payment, and a simple progress indicator for available limit.
- The progress track uses Surface warm; the fill uses Espresso 900.
- “Pay bill” is the only dominant action in this panel.

### Buttons

- Primary: Espresso 900 with Cream on dark text.
- Secondary: translucent cream on cocoa or Surface with warm border on the canvas.
- Tertiary: text/icon only.
- Destructive: Danger text/border or solid Danger only in the final confirmation.
- Minimum target: 44×44px.
- Keep one primary action per action group.

### Forms

- Use Surface panels rather than placing fields directly on the patterned canvas.
- Labels remain visible above controls.
- Controls have 44px minimum height, warm border, Surface background, and 14–16px radius.
- Error state uses border, icon, and text; focus moves to the error summary.
- Financial review screens remove editable styling and present clear label/value rows.

### Statuses

| State family | Examples | Treatment |
|---|---|---|
| Neutral | Draft, inactive, cancelled | Warm gray text/surface |
| Information | Submitted, processing, opened | Information text/icon on pale information surface |
| Success | Active, approved, verified, settled | Success text/icon on pale success surface |
| Warning | Pending, due soon, under review, blocked | Warning text/icon on pale amber surface |
| Danger | Failed, rejected, overdue, mismatch | Danger text/icon on pale red surface |

Every status includes readable text and, where space permits, an icon. Unknown backend statuses render neutrally with their original value.

### Operations tables

Operations pages keep the same palette and typography but use lower density:

- Warm canvas with one Surface table panel.
- Compact filter row above the table.
- Subdued warm header and horizontal dividers.
- Right-aligned tabular financial columns.
- Selected row uses a very pale cocoa tint, never the dark sidebar color.
- Details open in a full route or wide side sheet; do not squeeze large reconciliation data into decorative cards.

## 8. Accessibility safeguards

The reference aesthetic must not override banking usability:

- Cream-on-cocoa and cream-on-espresso pairs must pass WCAG AA.
- Translucent surfaces receive an opaque fallback and minimum contrast check.
- Brown is brand identity, not status meaning; semantic success/warning/danger colors remain distinct.
- Focus ring uses blue because it is clearly distinguishable from the brown palette.
- Decorative shapes are hidden from assistive technologies.
- All icon actions have visible labels or accessible names.
- Support keyboard-only use, screen readers, high contrast, 200%/400% zoom, and reduced motion.

## 9. OJET theme implementation

Start with Oracle JET Redwood and map the theme through supported variables and application layout classes:

```text
src/styles/
  tokens/
    warm-editorial-colors.css
    typography.css
    spacing.css
    motion.css
  components/
    shell.css
    dashboard.css
    financial.css
    status.css
    operations-table.css
  moneybags-redwood.scss
```

Rules:

1. Map semantic tokens to supported JET/Redwood variables in one theme entry point.
2. Prefer Core Pack properties and CSS variables; never style shadow-DOM internals.
3. Prefix application layout classes with `mb-`.
4. Load Manrope locally in production or use the approved corporate font asset; do not depend on Google Fonts at runtime without approval.
5. Use `prefers-reduced-motion` and keep transitions between 120–180ms.
6. Keep decorative balance-card layers in an application-owned wrapper around the JET content, not inside JET component internals.
7. If a future dark theme is required, design and test it separately. Do not automatically invert this approved warm palette.

## 10. Acceptance checklist

- The shell is recognizably warm ivory and espresso, not the prior teal theme.
- Branding and content are MoneyBags-specific; no “Mira” text or copied logo remains.
- Customer navigation covers actual MoneyBags domains rather than unsupported budgeting features.
- Dashboard composition works at 320, 736, 1024, and 1440px.
- Sidebar becomes a functional drawer on compact screens.
- All content remains readable at 200% and 400% zoom.
- Buttons, fields, navigation, dialogs, status treatments, tables, and financial receipts share the same tokens.
- Abstract shapes never cover text or become the only way information is communicated.
- Financial amounts use tabular numerals and explicit currency.
- All status, focus, error, and disabled-state contrasts pass WCAG AA.
- OJET styling uses supported theme variables rather than brittle internal selectors.

