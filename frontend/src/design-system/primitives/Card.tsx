import { SurfaceCard } from '@/design-system/components/SurfaceCard/SurfaceCard';

/**
 * Backwards-compatible alias for the base `SurfaceCard` role. New code may use
 * either name; `SurfaceCard` additionally exposes `elevated`, `interactive`, and
 * `selected` roles plus header/actions/footer slots.
 */
export const Card = SurfaceCard;

export type {
  SurfaceCardProps,
  SurfaceCardState,
  SurfaceCardVariant
} from '@/design-system/components/SurfaceCard/SurfaceCard';
