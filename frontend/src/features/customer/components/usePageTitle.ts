import { useEffect } from 'react';

/**
 * Sets the browser tab title for the current page (WCAG 2.4.2) and moves focus to the page's h1
 * when the route changes, so screen readers announce where the customer landed.
 */
export function usePageTitle(title: string | undefined, { focusHeading = true } = {}) {
  useEffect(() => {
    if (!title) return;
    const previous = document.title;
    document.title = `${title} · SwiftDrop`;
    return () => {
      document.title = previous;
    };
  }, [title]);

  useEffect(() => {
    if (!focusHeading || !title) return;
    const heading = document.querySelector<HTMLElement>('h1');
    if (heading && !heading.contains(document.activeElement)) {
      heading.tabIndex = -1;
      // Focus is for screen readers; a heading is not a control, so no visible ring.
      heading.classList.add('outline-none');
      heading.focus({ preventScroll: true });
    }
    // Only on first render of a page: later title changes are not navigations.
  }, [Boolean(title)]);
}
