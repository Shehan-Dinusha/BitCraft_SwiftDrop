import type { ReactNode } from 'react';
import { ButtonsSection } from './sections/ButtonsSection';
import { FormControlsSection } from './sections/FormControlsSection';
import { MenusSection } from './sections/MenusSection';
import { SurfacesSection } from './sections/SurfacesSection';
import { DialogsSection } from './sections/DialogsSection';
import { StatusSection } from './sections/StatusSection';
import { ProgressSection } from './sections/ProgressSection';
import { FeedbackSection } from './sections/FeedbackSection';
import { DataSection } from './sections/DataSection';
import { TypographySection } from './sections/TypographySection';
import { SelectionSection } from './sections/SelectionSection';
import { IdentifiersSection } from './sections/IdentifiersSection';
import { TimelineSection } from './sections/TimelineSection';
import { AlertsSection } from './sections/AlertsSection';
import { PageStructureSection } from './sections/PageStructureSection';
import { OrderPatternsSection } from './sections/OrderPatternsSection';
import { CourierAssistantSection } from './sections/CourierAssistantSection';
import { ShellSection } from './sections/ShellSection';

export interface CatalogEntry {
  id: string;
  title: string;
  description: string;
  content: ReactNode;
}

export const catalogFamilies: CatalogEntry[] = [
  {
    id: 'typography',
    title: 'Typography',
    description:
      'Twelve Velocity text styles (Display to Mono L), exposed as Tailwind text-* utilities.',
    content: <TypographySection />
  },
  {
    id: 'buttons',
    title: 'Buttons',
    description:
      'Five variants across six forced states and three densities. Density decides height, padding, icon size, and label size.',
    content: <ButtonsSection />
  },
  {
    id: 'form-controls',
    title: 'Inputs and fields',
    description:
      'Five input archetypes and seven visual states, plus the labelled Field wrapper that wires hints and errors to `aria-describedby`.',
    content: <FormControlsSection />
  },
  {
    id: 'selection',
    title: 'Selection controls',
    description:
      'Checkbox, radio, toggle, textarea with counter, radio cards, choice chips and the booking special fields.',
    content: <SelectionSection />
  },
  {
    id: 'identifiers',
    title: 'Identifiers and tags',
    description:
      'Tracking number, money amount, speed and role tags, dispatcher queue chips, and cash state.',
    content: <IdentifiersSection />
  },
  {
    id: 'timeline',
    title: 'Timeline and progress',
    description:
      'Timeline step and tracking history card, the five-step status progress bar, and the live connection indicator.',
    content: <TimelineSection />
  },
  {
    id: 'alerts',
    title: 'Alerts, toasts and empty states',
    description: 'Inline alerts, the toast queue (5 s, max 3) and the three empty-state types.',
    content: <AlertsSection />
  },
  {
    id: 'page-structure',
    title: 'Page structure and tables',
    description:
      'Page header, tabs, filter bar, the orders table with sortable header cells and selectable rows, and pagination.',
    content: <PageStructureSection />
  },
  {
    id: 'order-patterns',
    title: 'Order patterns and cards',
    description:
      'Price breakdown, order cards, bottom sheet, photo upload, pickup slot picker and notifications.',
    content: <OrderPatternsSection />
  },
  {
    id: 'courier-assistant',
    title: 'Slide to confirm and assistant',
    description:
      'The courier slide control (idle, dragging, holding, done, locked) and the assistant launcher and panel. SlideToTransitionBar and AiAssistantDock stay available for existing pages.',
    content: <CourierAssistantSection />
  },
  {
    id: 'shell',
    title: 'Role app shells',
    description:
      'Small navigation parts: the nav item, bottom tabs and the public header. The full role shells are in the gallery above.',
    content: <ShellSection />
  },
  {
    id: 'menus',
    title: 'Selects and menus',
    description:
      'Combobox and listbox families share rows, status dots, and density through the menu provider.',
    content: <MenusSection />
  },
  {
    id: 'surfaces',
    title: 'Cards and surfaces',
    description:
      'Four surface roles and four forced states. Interactive roles are keyboard operable and only become buttons when given an onClick.',
    content: <SurfacesSection />
  },
  {
    id: 'dialogs',
    title: 'Dialogs and drawers',
    description:
      'One shell for modals and right-side sheets. Rendered inline here so the catalog stays scrollable.',
    content: <DialogsSection />
  },
  {
    id: 'status',
    title: 'Status and feedback',
    description:
      'FSM badges are the only source of delivery-state color. Banners use the status token triads, so both themes stay readable.',
    content: <StatusSection />
  },
  {
    id: 'progress',
    title: 'Progress and transitions',
    description:
      'The customer stepper and the courier slide-to-confirm control, including every forced state.',
    content: <ProgressSection />
  },
  {
    id: 'feedback',
    title: 'Loading and assistance',
    description:
      'Skeletons are decorative placeholders; the AI dock keeps its own conversation state and cites the data it used.',
    content: <FeedbackSection />
  },
  {
    id: 'data',
    title: 'Dense data and actions',
    description:
      "Operational grids and the courier action bar, capped by Hick's Law for each density.",
    content: <DataSection />
  }
];
