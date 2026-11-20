import { useEffect, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { fetchCurrentUser, fetchMyAvatar } from '@/services/api/authApi';
import { useAuthStore } from '@/store/useAuthStore';
import { cn } from '@/utils/cn';

/** Side of the stored picture in pixels. Shown at most at 64px, so 256 stays sharp on high-DPI screens. */
export const AVATAR_SIZE = 256;
/** Photos straight from a phone can be large; anything bigger than this is refused before decoding. */
export const MAX_SOURCE_BYTES = 15 * 1024 * 1024;
export const ACCEPTED_TYPES = ['image/png', 'image/jpeg', 'image/webp'];

/** Why a chosen file cannot be used, or undefined when it is fine to process. */
export function sourceProblem(file: Pick<File, 'type' | 'size'>): string | undefined {
  if (!ACCEPTED_TYPES.includes(file.type)) return 'Choose a PNG, JPEG or WebP image.';
  if (file.size > MAX_SOURCE_BYTES) return 'Choose an image smaller than 15 MB.';
  return undefined;
}

/** The centred square of a width x height image: the largest one that fits. */
export function centreSquare(
  width: number,
  height: number
): { sx: number; sy: number; side: number } {
  const side = Math.min(width, height);
  return { sx: Math.round((width - side) / 2), sy: Math.round((height - side) / 2), side };
}

/**
 * Crops the picture to its centred square and shrinks it to {@link AVATAR_SIZE}, as a JPEG.
 * Done in the browser, so a 5 MB phone photo uploads as about 20-40 KB.
 */
export async function toAvatarImage(file: File): Promise<Blob> {
  const bitmap = await createImageBitmap(file);
  try {
    const { sx, sy, side } = centreSquare(bitmap.width, bitmap.height);
    const canvas = document.createElement('canvas');
    canvas.width = AVATAR_SIZE;
    canvas.height = AVATAR_SIZE;
    const context = canvas.getContext('2d');
    if (!context) throw new Error('This browser cannot process images.');
    context.fillStyle = '#ffffff'; // transparent PNGs get a white background in JPEG
    context.fillRect(0, 0, AVATAR_SIZE, AVATAR_SIZE);
    context.imageSmoothingQuality = 'high';
    context.drawImage(bitmap, sx, sy, side, side, 0, 0, AVATAR_SIZE, AVATAR_SIZE);
    return await new Promise<Blob>((resolve, reject) =>
      canvas.toBlob(
        (blob) => (blob ? resolve(blob) : reject(new Error('The image could not be processed.'))),
        'image/jpeg',
        0.88
      )
    );
  } finally {
    bitmap.close();
  }
}

/**
 * Loads the full profile once per session. The sign-in response only carries the basics, so without
 * this the sidebar would not know about the picture (or a name changed on another device).
 */
export function useSyncCurrentUser(): void {
  const userId = useAuthStore((s) => s.user?.id);
  const isDevSession = useAuthStore((s) => s.isDevSession);
  const setUser = useAuthStore((s) => s.setUser);
  const { data } = useQuery({
    queryKey: ['profile', 'me'],
    queryFn: fetchCurrentUser,
    enabled: !!userId && !isDevSession,
    staleTime: 5 * 60_000,
    retry: false
  });
  useEffect(() => {
    if (data?.id && data.id === useAuthStore.getState().user?.id) setUser(data);
  }, [data, setUser]);
}

/** Object URL of the signed-in user's picture (null when there is none). Refetched when it changes. */
export function useMyAvatarUrl(): string | null {
  const userId = useAuthStore((s) => s.user?.id);
  const updatedAt = useAuthStore((s) => s.user?.avatarUpdatedAt);
  const isDevSession = useAuthStore((s) => s.isDevSession);
  const { data } = useQuery({
    queryKey: ['profile', 'avatar', userId, updatedAt],
    queryFn: fetchMyAvatar,
    enabled: !!userId && !!updatedAt && !isDevSession,
    staleTime: Infinity,
    retry: false
  });
  const [url, setUrl] = useState<string | null>(null);
  useEffect(() => {
    if (!data || !updatedAt) {
      setUrl(null);
      return;
    }
    const next = URL.createObjectURL(data);
    setUrl(next);
    return () => URL.revokeObjectURL(next);
  }, [data, updatedAt]);
  return url;
}

export function initials(name: string): string {
  const parts = name.trim().split(/\s+/).filter(Boolean);
  return (
    ((parts[0]?.[0] ?? '') + (parts.length > 1 ? parts[parts.length - 1][0] : '')).toUpperCase() ||
    '?'
  );
}

/** Round profile picture, or the person's initials when there is none. */
export function UserAvatar({
  name,
  src,
  className
}: {
  name: string;
  src?: string | null;
  className?: string;
}) {
  return src ? (
    <img src={src} alt="" className={cn('shrink-0 rounded-full object-cover', className)} />
  ) : (
    <span
      aria-hidden="true"
      className={cn(
        'flex shrink-0 items-center justify-center rounded-full bg-brand-primary font-semibold text-brand-on',
        className
      )}
    >
      {initials(name)}
    </span>
  );
}
