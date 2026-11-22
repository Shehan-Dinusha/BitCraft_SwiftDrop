import { useEffect, useState, type RefObject } from 'react';

type Box = Pick<DOMRect, 'top' | 'bottom' | 'left' | 'right'>;

const GAP = 8;
/** Put on a row of page buttons that the floating assistant button must never cover. */
const AVOID = '[data-assistant-avoid]';

const overlaps = (a: Box, b: Box) =>
  a.left < b.right + GAP &&
  a.right > b.left - GAP &&
  a.top < b.bottom + GAP &&
  a.bottom > b.top - GAP;

/**
 * True when the floating button would sit on something the person needs: a row marked
 * `data-assistant-avoid`, or the end of the page, where scrolling can no longer move content out from
 * under it.
 */
export function shouldDock(button: Box, avoid: Box[], pageBottom: number | undefined): boolean {
  if (avoid.some((box) => box.bottom > box.top && overlaps(button, box))) return true;
  if (pageBottom === undefined) return false;
  return pageBottom > button.top && pageBottom <= button.bottom + 3 * GAP;
}

/**
 * Whether the assistant button belongs in the header instead of the bottom-right corner. `button` must
 * keep its place while hidden (visibility, not display), so its box never depends on the answer.
 */
export function useAssistantDocked(
  button: RefObject<HTMLElement | null>,
  paused: boolean
): boolean {
  const [docked, setDocked] = useState(false);

  useEffect(() => {
    if (paused) return undefined;
    let frame = 0;
    const measure = () => {
      frame = 0;
      const element = button.current;
      if (!element) return;
      const content = document.querySelector('main')?.lastElementChild?.getBoundingClientRect();
      const avoid = [...document.querySelectorAll(AVOID)].map((row) => row.getBoundingClientRect());
      setDocked(shouldDock(element.getBoundingClientRect(), avoid, content?.bottom));
    };
    const schedule = () => {
      if (!frame) frame = window.requestAnimationFrame(measure);
    };

    schedule();
    window.addEventListener('scroll', schedule, { passive: true });
    window.addEventListener('resize', schedule);
    // A new page or booking step changes what is under the button without any scrolling.
    const main = document.querySelector('main');
    const changes = new MutationObserver(schedule);
    if (main) changes.observe(main, { childList: true, subtree: true });
    const size = typeof ResizeObserver === 'undefined' ? null : new ResizeObserver(schedule);
    if (main) size?.observe(main);
    return () => {
      window.cancelAnimationFrame(frame);
      window.removeEventListener('scroll', schedule);
      window.removeEventListener('resize', schedule);
      changes.disconnect();
      size?.disconnect();
    };
  }, [button, paused]);

  return docked;
}
