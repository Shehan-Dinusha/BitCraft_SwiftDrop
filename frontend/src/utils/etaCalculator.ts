export const calculateEta = (distanceKm: number, speedKmh = 28) =>
  Math.max(1, Math.ceil((distanceKm / Math.max(speedKmh, 1)) * 60));
