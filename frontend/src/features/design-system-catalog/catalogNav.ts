export const FIGMA_FILE =
  'https://www.figma.com/design/XX3u7zxTDat0l2MbG27niR/BitCraft-SwiftDrop-Web-Design--Copy-';

/** Link to a node in the Velocity Figma file. `node` looks like `2029:1849`. */
export const figmaLink = (node: string) => `${FIGMA_FILE}?node-id=${node.replace(':', '-')}`;

export interface CatalogPageDef {
  slug: string;
  title: string;
  summary: string;
  /** Section ids from `catalogFamilies`, in display order. */
  familyIds: string[];
  /** Figma nodes this page mirrors. */
  figma: { label: string; node: string }[];
  /** Extra sections that are not in `catalogFamilies`. */
  kind?: 'foundations' | 'shells';
  /** Extra search words. */
  keywords?: string;
}

/** The catalog is grouped like the Figma component library. */
export const catalogPages: CatalogPageDef[] = [
  {
    slug: 'foundations',
    title: 'Foundations',
    summary:
      'Colour tokens, status and role colours, type scale, spacing, radius, elevation and density.',
    familyIds: ['typography'],
    figma: [{ label: 'Foundations v2', node: '2010:4129' }],
    kind: 'foundations',
    keywords: 'colour color token palette spacing radius shadow density text'
  },
  {
    slug: 'actions',
    title: 'Actions',
    summary: 'Buttons in five types, six states and three densities, plus the square icon button.',
    familyIds: ['buttons'],
    figma: [
      { label: 'Button', node: '51:36359' },
      { label: 'Inputs & selection (IconButton)', node: '2029:1849' }
    ],
    keywords: 'button icon primary secondary destructive'
  },
  {
    slug: 'inputs',
    title: 'Inputs and selection',
    summary:
      'Text inputs, fields, selects and menus, checkboxes, radios, toggles, textarea, chips and the booking fields.',
    familyIds: ['form-controls', 'selection', 'menus'],
    figma: [
      { label: 'TextInput', node: '51:37379' },
      { label: 'SelectDropdown', node: '51:38705' },
      { label: 'Inputs & selection', node: '2029:1849' }
    ],
    keywords: 'input field select dropdown checkbox radio toggle textarea chip form'
  },
  {
    slug: 'data-display',
    title: 'Data display',
    summary:
      'Status badges, tags, tracking numbers and money, tabs, page header, filters, tables and pagination.',
    familyIds: ['status', 'identifiers', 'page-structure', 'data'],
    figma: [
      { label: 'FSMStatusBadge', node: '51:38080' },
      { label: 'Domain tags', node: '2017:1783' },
      { label: 'Identifiers & money', node: '2017:1952' },
      { label: 'Page structure', node: '2019:1818' },
      { label: 'Data tables', node: '2032:1859' }
    ],
    keywords: 'badge status tag tracking money table pagination filter tabs header'
  },
  {
    slug: 'feedback',
    title: 'Feedback and progress',
    summary:
      'Alerts, toasts, empty states, skeletons, timelines, progress and the live connection indicator.',
    familyIds: ['alerts', 'timeline', 'progress', 'feedback'],
    figma: [
      { label: 'Feedback & system states', node: '2018:1875' },
      { label: 'Status timeline', node: '2018:1778' }
    ],
    keywords: 'alert toast empty skeleton timeline progress live loading'
  },
  {
    slug: 'patterns',
    title: 'Cards and patterns',
    summary:
      'Surfaces, dialogs and drawers, price breakdown, order cards, bottom sheet, photo upload, slot picker and notifications.',
    familyIds: ['surfaces', 'dialogs', 'order-patterns'],
    figma: [
      { label: 'SurfaceCard', node: '51:39900' },
      { label: 'ModalDrawerShell', node: '51:40629' },
      { label: 'Order & merchant patterns', node: '2019:2947' },
      { label: 'Cards, sheets & special controls', node: '2039:2674' }
    ],
    keywords: 'card surface dialog drawer modal price order sheet photo slot notification'
  },
  {
    slug: 'courier',
    title: 'Courier and assistant',
    summary: 'The slide-to-confirm control and the assistant launcher and panel.',
    familyIds: ['courier-assistant'],
    figma: [{ label: 'Cards, sheets & special controls', node: '2039:2674' }],
    keywords: 'slide confirm assistant ai chat'
  },
  {
    slug: 'shells',
    title: 'Role shells',
    summary:
      'The side navigation, top bar and mobile tabs for every role, shown as full pages at three widths.',
    familyIds: ['shell'],
    figma: [
      { label: 'App navigation & icons', node: '2038:2225' },
      { label: 'Navigation spec', node: '2074:3404' }
    ],
    kind: 'shells',
    keywords:
      'sidebar side nav top bar header bottom navigation role customer merchant courier dispatcher admin public'
  }
];

export const findCatalogPage = (slug: string | undefined) =>
  catalogPages.find((page) => page.slug === slug);
