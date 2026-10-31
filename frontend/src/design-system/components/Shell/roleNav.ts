import type { NavIconName } from './navIcons';

export type ShellRole = 'customer' | 'merchant' | 'courier' | 'dispatcher' | 'system-admin';

export interface NavEntry {
  id: string;
  label: string;
  icon: NavIconName;
  /** Live count badge, e.g. open orders. */
  count?: number;
  /** Route the item links to. Entries without one are shown only in the catalog until the page exists. */
  to?: string;
}

export interface NavGroup {
  label?: string;
  items: NavEntry[];
}

export interface RoleShellSpec {
  label: string;
  /** Workspace line under "SwiftDrop" in the side nav. */
  workspace: string;
  searchPlaceholder: string;
  /** Primary call-to-action button at the top of the side nav. */
  cta?: { label: string };
  groups: NavGroup[];
  /** Mobile tab bar (customer and courier only). */
  tabs?: NavEntry[];
}

/** Menus per role from the Figma Navigation spec (page 07). Counts are sample values. */
export const roleShellSpec: Record<ShellRole, RoleShellSpec> = {
  customer: {
    label: 'Customer',
    workspace: 'Personal account',
    searchPlaceholder: 'Track a parcel (SD-…)',
    cta: { label: 'Send a parcel' },
    groups: [
      {
        label: 'Send & track',
        items: [
          { id: 'book', label: 'Book a parcel', icon: 'plus' },
          { id: 'parcels', label: 'My parcels', icon: 'package' },
          { id: 'track', label: 'Track a parcel', icon: 'route' }
        ]
      },
      {
        label: 'Account',
        items: [
          { id: 'notifications', label: 'Notifications', icon: 'bell', count: 3 },
          { id: 'addresses', label: 'Addresses', icon: 'mapPin' },
          { id: 'profile', label: 'Profile', icon: 'user' }
        ]
      },
      { items: [{ id: 'help', label: 'Help & assistant', icon: 'sparkles' }] }
    ],
    tabs: [
      { id: 'book', label: 'Book', icon: 'plus' },
      { id: 'parcels', label: 'Parcels', icon: 'list' },
      { id: 'track', label: 'Track', icon: 'route' },
      { id: 'profile', label: 'Profile', icon: 'user' }
    ]
  },
  merchant: {
    label: 'Merchant',
    workspace: 'Lanka Crafts (Pvt) Ltd',
    searchPlaceholder: 'Search orders, receivers…',
    cta: { label: 'New order' },
    groups: [
      {
        label: 'Shipping',
        items: [
          { id: 'dashboard', label: 'Dashboard', icon: 'grid' },
          { id: 'orders', label: 'Orders', icon: 'list', count: 248 },
          { id: 'bulk', label: 'Bulk import', icon: 'upload' },
          { id: 'labels', label: 'Labels', icon: 'tag' }
        ]
      },
      {
        label: 'Developers',
        items: [
          { id: 'keys', label: 'API keys', icon: 'key' },
          { id: 'webhooks', label: 'Webhook logs', icon: 'webhook', count: 2 }
        ]
      },
      {
        label: 'Insights',
        items: [{ id: 'reports', label: 'Reports', icon: 'chart' }]
      },
      { items: [{ id: 'help', label: 'Help & assistant', icon: 'sparkles' }] }
    ]
  },
  courier: {
    label: 'Courier',
    workspace: 'Courier app',
    searchPlaceholder: 'Search jobs…',
    groups: [
      {
        items: [
          { id: 'today', label: 'Today', icon: 'home' },
          { id: 'scan', label: 'Scan', icon: 'scan' },
          { id: 'cash', label: 'Cash', icon: 'wallet' },
          { id: 'profile', label: 'Profile', icon: 'user' }
        ]
      }
    ],
    tabs: [
      { id: 'today', label: 'Today', icon: 'home' },
      { id: 'scan', label: 'Scan', icon: 'scan' },
      { id: 'cash', label: 'Cash', icon: 'wallet' },
      { id: 'profile', label: 'Profile', icon: 'user' }
    ]
  },
  dispatcher: {
    label: 'Dispatcher',
    workspace: 'Colombo district',
    searchPlaceholder: 'Search parcels, couriers…',
    cta: { label: 'Assign parcels' },
    groups: [
      {
        label: 'Operations',
        items: [
          { id: 'queues', label: 'Queues', icon: 'inbox', count: 14 },
          { id: 'map', label: 'Live map', icon: 'map' },
          { id: 'handoffs', label: 'Hand-offs', icon: 'swap', count: 5 }
        ]
      },
      {
        label: 'Team & cash',
        items: [
          { id: 'couriers', label: 'Couriers', icon: 'truck' },
          { id: 'cash', label: 'Cash', icon: 'wallet' }
        ]
      },
      { label: 'Insights', items: [{ id: 'reports', label: 'Reports', icon: 'chart' }] },
      { items: [{ id: 'help', label: 'Help & assistant', icon: 'sparkles' }] }
    ]
  },
  'system-admin': {
    label: 'System Admin',
    workspace: 'System administration',
    searchPlaceholder: 'Search everything…',
    groups: [
      {
        label: 'Platform',
        items: [
          { id: 'overview', label: 'Overview', icon: 'grid' },
          { id: 'approvals', label: 'Merchant approvals', icon: 'store', count: 4 },
          { id: 'users', label: 'Users & roles', icon: 'users' },
          { id: 'districts', label: 'Districts & dispatchers', icon: 'mapPin' }
        ]
      },
      {
        label: 'Configuration',
        items: [
          { id: 'pricing', label: 'Pricing rules', icon: 'calc' },
          { id: 'settings', label: 'Settings', icon: 'settings' }
        ]
      },
      { label: 'Insights', items: [{ id: 'reports', label: 'Reports', icon: 'chart' }] },
      { items: [{ id: 'help', label: 'Help & assistant', icon: 'sparkles' }] }
    ]
  }
};
