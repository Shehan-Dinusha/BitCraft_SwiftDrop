import { useState, useEffect, useCallback, useMemo } from 'react';
import { deliveryApi, type DeliverySessionResponse } from '@/services/api/deliveryApi';
import { useAuthStore } from '@/store/useAuthStore';

export interface CourierTodayStats {
  deliveries: DeliverySessionResponse[];
  deliveredDeliveries: DeliverySessionResponse[];
  totalCount: number;
  deliveredCount: number;
  activeCount: number;
  failedCount: number;
  totalEarnings: number;
  potentialEarnings: number;
  averageEarnings: number;
  loading: boolean;
  refresh: () => Promise<void>;
}

/**
 * Calculates earnings for a completed delivery item based on SwiftDrop tariff tiers:
 * - Intra-district (same district, e.g. Colombo -> Colombo): Rs. 350.00
 * - Inter-district (cross-district, e.g. Colombo -> Kandy / Gampaha): Rs. 550.00
 */
export function calculateDeliveryEarning(item: DeliverySessionResponse): number {
  if (item.currentState !== 'DELIVERED') return 0;
  if (!item.pickupDistrict || !item.deliveryDistrict) return 350;
  return item.pickupDistrict === item.deliveryDistrict ? 350 : 550;
}

/**
 * Checks if a delivery was inter-district (cross-district).
 */
export function isInterDistrict(item: DeliverySessionResponse): boolean {
  if (!item.pickupDistrict || !item.deliveryDistrict) return false;
  return item.pickupDistrict !== item.deliveryDistrict;
}

/**
 * Formats a numeric rupee amount into standard Sri Lankan Rupee representation.
 */
export function formatLKR(amount: number): string {
  return `Rs. ${amount.toLocaleString('en-LK', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
}

export function useCourierTodayStats(): CourierTodayStats {
  const role = useAuthStore((state) => state.role);
  const [deliveries, setDeliveries] = useState<DeliverySessionResponse[]>([]);
  const [loading, setLoading] = useState(false);

  const fetchStats = useCallback(async () => {
    if (role !== 'COURIER') return;
    try {
      setLoading(true);
      const data = await deliveryApi.getMyDeliveries('today');
      setDeliveries(data || []);
    } catch {
      // In case of transient network error, keep current state
    } finally {
      setLoading(false);
    }
  }, [role]);

  useEffect(() => {
    void fetchStats();
  }, [fetchStats]);

  const deliveredDeliveries = useMemo(
    () => deliveries.filter((d) => d.currentState === 'DELIVERED'),
    [deliveries]
  );

  const failedDeliveries = useMemo(
    () => deliveries.filter((d) => d.currentState === 'FAILED' || d.currentState === 'CANCELLED'),
    [deliveries]
  );

  const activeDeliveries = useMemo(
    () =>
      deliveries.filter(
        (d) =>
          d.currentState !== 'DELIVERED' &&
          d.currentState !== 'FAILED' &&
          d.currentState !== 'CANCELLED'
      ),
    [deliveries]
  );

  const totalEarnings = useMemo(
    () => deliveredDeliveries.reduce((sum, item) => sum + calculateDeliveryEarning(item), 0),
    [deliveredDeliveries]
  );

  const potentialEarnings = useMemo(
    () =>
      activeDeliveries.reduce((sum, item) => {
        const rate =
          !item.pickupDistrict || !item.deliveryDistrict || item.pickupDistrict === item.deliveryDistrict
            ? 350
            : 550;
        return sum + rate;
      }, 0),
    [activeDeliveries]
  );

  const averageEarnings = useMemo(
    () => (deliveredDeliveries.length > 0 ? totalEarnings / deliveredDeliveries.length : 0),
    [deliveredDeliveries, totalEarnings]
  );

  return {
    deliveries,
    deliveredDeliveries,
    totalCount: deliveries.length,
    deliveredCount: deliveredDeliveries.length,
    activeCount: activeDeliveries.length,
    failedCount: failedDeliveries.length,
    totalEarnings,
    potentialEarnings,
    averageEarnings,
    loading,
    refresh: fetchStats
  };
}
