import { useEffect, useRef, useState } from 'react';

/**
 * Animates a numeric value counting up from its previous value to `target`.
 * Non-numeric targets (strings, null, undefined) pass through unchanged so
 * callers can share one prop for both numeric stats and plain text values.
 */
export function useCountUp(target, duration = 700) {
  const numeric = typeof target === 'number' ? target : Number(target);
  const isNumeric = target !== null && target !== undefined && target !== '' && !Number.isNaN(numeric);

  const [display, setDisplay] = useState(isNumeric ? numeric : target);
  const fromRef = useRef(isNumeric ? numeric : 0);
  const frameRef = useRef(null);

  useEffect(() => {
    if (!isNumeric) {
      setDisplay(target);
      return undefined;
    }
    const from = fromRef.current;
    const start = performance.now();

    const tick = (now) => {
      const progress = Math.min((now - start) / duration, 1);
      const eased = 1 - Math.pow(1 - progress, 3);
      setDisplay(Math.round(from + (numeric - from) * eased));
      if (progress < 1) {
        frameRef.current = requestAnimationFrame(tick);
      } else {
        fromRef.current = numeric;
      }
    };
    frameRef.current = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(frameRef.current);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [numeric, isNumeric, duration]);

  return display;
}
