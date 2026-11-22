import { describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import { NotificationTray, type TrayItem } from './NotificationTray';
import { resolveNotificationType } from './notificationTypes';

const now = new Date().toISOString();

const items: TrayItem[] = [
  {
    id: 1,
    type: 'PARCEL_CREATED',
    title: 'Order confirmed',
    message: 'Your parcel SD-WA9Q-25CC has been scheduled for pickup.',
    createdAt: now,
    unread: true
  },
  {
    id: 2,
    type: 'PARCEL_DELIVERED',
    title: 'Delivered',
    message: 'Your parcel SD-AAAA-BBBB was delivered.',
    createdAt: '2026-01-02T10:00:00Z',
    unread: false
  }
];

function renderTray(overrides: Partial<Parameters<typeof NotificationTray>[0]> = {}) {
  const props = {
    items,
    unreadCount: 1,
    onItemClick: vi.fn(),
    onMarkAllRead: vi.fn(),
    ...overrides
  };
  render(<NotificationTray {...props} />);
  return props;
}

describe('NotificationTray', () => {
  it('shows each notification with its own label and the tracking number only once', () => {
    renderTray();

    expect(screen.getByText('Order confirmed')).toBeInTheDocument();
    expect(screen.getByText('Delivered')).toBeInTheDocument();
    expect(screen.getAllByText(/SD-WA9Q-25CC/)).toHaveLength(1);
    expect(screen.getByRole('tab', { name: 'Unread (1)' })).toBeInTheDocument();
  });

  it('groups items into Today and Earlier', () => {
    renderTray();

    expect(screen.getByText('Today')).toBeInTheDocument();
    expect(screen.getByText('Earlier')).toBeInTheDocument();
  });

  it('marks all as read', () => {
    const props = renderTray();

    fireEvent.click(screen.getByRole('button', { name: 'Mark all as read' }));
    expect(props.onMarkAllRead).toHaveBeenCalledTimes(1);
  });

  it('hides Mark all as read when nothing is unread', () => {
    renderTray({ unreadCount: 0 });

    expect(screen.queryByRole('button', { name: 'Mark all as read' })).not.toBeInTheDocument();
  });

  it('filters to unread notifications', () => {
    renderTray();

    fireEvent.click(screen.getByRole('tab', { name: /Unread/ }));
    expect(screen.getByText('Order confirmed')).toBeInTheDocument();
    expect(screen.queryByText('Delivered')).not.toBeInTheDocument();
  });

  it('passes the clicked item to the handler', () => {
    const props = renderTray();

    fireEvent.click(screen.getByText('Order confirmed'));
    expect(props.onItemClick).toHaveBeenCalledWith(items[0]);
  });
});

describe('resolveNotificationType', () => {
  it('uses the stored type, then falls back to the title for older notifications', () => {
    expect(resolveNotificationType('PARCEL_FAILED', 'anything')).toBe('PARCEL_FAILED');
    expect(resolveNotificationType(null, 'SwiftDrop Out for Delivery')).toBe('PARCEL_OUT_FOR_DELIVERY');
    expect(resolveNotificationType(undefined, 'SwiftDrop Delivery Exception')).toBe('PARCEL_FAILED');
    expect(resolveNotificationType(null, 'Something else')).toBeNull();
  });
});
