import { useEffect, useState } from 'react';

/**
 * True while the page is being scrolled down, so a floating control can get out of the way of
 * the content. It comes back as soon as the person scrolls up, or is near the top of the page.
 *
 * @param threshold pixels of movement to ignore, so tiny scroll jitters do not flicker the control
 * @param topOffset the control is always shown within this many pixels of the top
 */
export function useHideOnScrollDown(threshold = 8, topOffset = 80): boolean {
  const [hidden, setHidden] = useState(false);

  useEffect(() => {
    let lastY = window.scrollY;

    const onScroll = () => {
      const y = window.scrollY;
      if (y <= topOffset) {
        setHidden(false);
      } else if (Math.abs(y - lastY) >= threshold) {
        setHidden(y > lastY);
      }
      lastY = y;
    };

    window.addEventListener('scroll', onScroll, { passive: true });
    return () => window.removeEventListener('scroll', onScroll);
  }, [threshold, topOffset]);

  return hidden;
}

export default useHideOnScrollDown;
