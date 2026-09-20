/**
 * textUtils.ts — Robust UTF-16 & Grapheme Cluster Aware Text Manipulation
 * Emulates Android IME InputConnection behaviors:
 * - deleteSurroundingTextInCodePoints(1, 0)
 * - Grapheme cluster boundary preservation (Intl.Segmenter / BreakIterator parity)
 * - Complex emoji sequences (ZWJ \u200D, skin tone modifiers, flag pairs)
 * - Indic / Telugu conjuncts (Virama / Halant consonant clusters & Matras)
 * - Surrogate pair integrity (prevents orphaned \uD800-\uDBFF or \uDC00-\uDFFF)
 * - Safe word-level backspace
 * - Grapheme-aware cursor stepping
 */

export interface DeletionResult {
  newText: string;
  newPos: number;
  deletedText: string;
}

interface IntlSegmentItem {
  segment: string;
  index: number;
}

interface IntlSegmenterInstance {
  segment: (input: string) => Iterable<IntlSegmentItem>;
}

type IntlSegmenterConstructor = new (
  locales?: string | string[],
  options?: { granularity: 'grapheme' | 'word' | 'sentence' }
) => IntlSegmenterInstance;

/**
 * Safely delete previous character or compound emoji at cursorPos without corrupting UTF-16 surrogate pairs.
 * Parallels Gboard InputConnection.deleteSurroundingText(1, 0) / deleteSurroundingTextInCodePoints(1, 0).
 */
export function deletePreviousGrapheme(text: string, cursorPos: number): DeletionResult {
  if (cursorPos <= 0 || text.length === 0) {
    return { newText: text, newPos: 0, deletedText: '' };
  }

  const before = text.substring(0, cursorPos);
  const after = text.substring(cursorPos);

  // 0. Explicit surrogate pair check: if cursor splits a high surrogate and low surrogate,
  // remove both code units so no orphaned surrogate is left in the string.
  if (
    before.length > 0 &&
    after.length > 0 &&
    before.charCodeAt(before.length - 1) >= 0xd800 &&
    before.charCodeAt(before.length - 1) <= 0xdbff &&
    after.charCodeAt(0) >= 0xdc00 &&
    after.charCodeAt(0) <= 0xdfff
  ) {
    const newBefore = before.substring(0, before.length - 1);
    const newAfter = after.substring(1);
    return {
      newText: newBefore + newAfter,
      newPos: newBefore.length,
      deletedText: before.slice(-1) + after.slice(0, 1),
    };
  }

  // 1. Modern browser standard: Intl.Segmenter with 'grapheme' granularity on the full string
  if (typeof Intl !== 'undefined' && 'Segmenter' in Intl) {
    try {
      const SegmenterConstructor = (Intl as unknown as { Segmenter: IntlSegmenterConstructor }).Segmenter;
      if (SegmenterConstructor) {
        // Use active or fallback locales
        const segmenter = new SegmenterConstructor(undefined, { granularity: 'grapheme' });
        const segments = Array.from(segmenter.segment(text));

        // Find the segment that contains or terminates at cursorPos
        for (let i = segments.length - 1; i >= 0; i--) {
          const s = segments[i];
          const segEnd = s.index + s.segment.length;
          if (cursorPos > s.index && cursorPos <= segEnd) {
            const newText = text.substring(0, s.index) + text.substring(segEnd);
            return {
              newText,
              newPos: s.index,
              deletedText: s.segment,
            };
          }
        }
      }
    } catch {
      // Fallback if segmenter throws
    }
  }

  // 2. Comprehensive Unicode fallback regex
  // Handles:
  // - Regional indicator pairs (Flags: 🇺🇸, 🇮🇳, etc.)
  // - Keycap sequences: 1️⃣, #️⃣, etc.
  // - Extended Pictographic + Skin Tone modifiers + ZWJ compound emojis (👨‍👩‍👧‍👦, 👍🏽, etc.)
  // - Indic / Telugu / Devanagari conjuncts with Virama (క్ష, క్రి, etc.) & Matras
  // - High surrogate (\uD800-\uDBFF) + Low surrogate (\uDC00-\uDFFF)
  // - Any single fallback character
  const graphemeRegex = /[\u{1F1E6}-\u{1F1FF}]{2}|(?:[0-9#*]\uFE0F?\u20E3)|(?:\p{Extended_Pictographic}(?:\uFE0F|[\u{1F3FB}-\u{1F3FF}])?(?:\u200D\p{Extended_Pictographic}(?:\uFE0F|[\u{1F3FB}-\u{1F3FF}])?)*)|(?:[\u0900-\u097F\u0C00-\u0C7F](?:[\u094D\u0C4D][\u0900-\u097F\u0C00-\u0C7F])*[\u0901-\u0903\u093A-\u094F\u0951-\u0957\u0962-\u0963\u0C01-\u0C03\u0C3E-\u0C4C\u0C55-\u0C56\u0C62-\u0C63]?)|[\uD800-\uDBFF][\uDC00-\uDFFF]|[\s\S]/gu;

  const matches = [...text.matchAll(graphemeRegex)];
  for (let i = matches.length - 1; i >= 0; i--) {
    const match = matches[i];
    const matchIndex = match.index ?? 0;
    const matchEnd = matchIndex + match[0].length;
    if (cursorPos > matchIndex && cursorPos <= matchEnd) {
      const newText = text.substring(0, matchIndex) + text.substring(matchEnd);
      return {
        newText,
        newPos: matchIndex,
        deletedText: match[0],
      };
    }
  }

  // Absolute fallback: 1 code unit
  const newBefore = before.substring(0, cursorPos - 1);
  return {
    newText: newBefore + after,
    newPos: newBefore.length,
    deletedText: before.substring(cursorPos - 1),
  };
}

/**
 * Safely delete previous word (used by backspace swipe-to-delete and accelerated repeat).
 */
export function deletePreviousWord(text: string, cursorPos: number): DeletionResult {
  if (cursorPos <= 0 || text.length === 0) {
    return { newText: text, newPos: 0, deletedText: '' };
  }

  const before = text.substring(0, cursorPos);
  const after = text.substring(cursorPos);

  // Match whitespace + word characters (including Telugu \u0C00-\u0C7F and general words)
  const match = before.match(/(\s*[\w\u0900-\u097F\u0C00-\u0C7F'-]+|\s+[^\w\s]+|\s+)$/);
  if (match && match[0].length > 0) {
    const deleteLen = match[0].length;
    const newBefore = before.substring(0, cursorPos - deleteLen);
    return {
      newText: newBefore + after,
      newPos: newBefore.length,
      deletedText: match[0],
    };
  }

  return deletePreviousGrapheme(text, cursorPos);
}

/**
 * Grapheme-aware cursor navigation (used by spacebar trackpad glide and cursor steps).
 * Prevents dropping the cursor inside surrogate pairs or compound grapheme clusters.
 */
export function getAdjacentGraphemePos(text: string, cursorPos: number, offset: number): number {
  if (!text || text.length === 0) return 0;
  if (offset === 0) return Math.max(0, Math.min(text.length, cursorPos));

  if (typeof Intl !== 'undefined' && 'Segmenter' in Intl) {
    try {
      const SegmenterConstructor = (Intl as unknown as { Segmenter: IntlSegmenterConstructor }).Segmenter;
      if (SegmenterConstructor) {
        const segmenter = new SegmenterConstructor(undefined, { granularity: 'grapheme' });
        const segments = Array.from(segmenter.segment(text));
        const boundaries = [0, ...segments.map((s) => s.index + s.segment.length)];

        if (offset < 0) {
          // Move left: Find the largest boundary strictly less than cursorPos
          for (let i = boundaries.length - 1; i >= 0; i--) {
            if (boundaries[i] < cursorPos) return boundaries[i];
          }
          return 0;
        } else {
          // Move right: Find the smallest boundary strictly greater than cursorPos
          for (let i = 0; i < boundaries.length; i++) {
            if (boundaries[i] > cursorPos) return boundaries[i];
          }
          return text.length;
        }
      }
    } catch {
      // Fallback
    }
  }

  // Fallback stepping with surrogate pair protection
  if (offset < 0) {
    let target = Math.max(0, cursorPos - 1);
    // If target lands on a low surrogate, step back 1 more to avoid splitting
    if (target > 0 && text.charCodeAt(target) >= 0xdc00 && text.charCodeAt(target) <= 0xdfff) {
      target = Math.max(0, target - 1);
    }
    return target;
  } else {
    let target = Math.min(text.length, cursorPos + 1);
    // If target lands on a low surrogate, step forward 1 more
    if (target < text.length && text.charCodeAt(target) >= 0xdc00 && text.charCodeAt(target) <= 0xdfff) {
      target = Math.min(text.length, target + 1);
    }
    return target;
  }
}

