/**
 * Color utility helpers for dynamic accent colors and keyboard highlights.
 */

export function isValidHex(hex: string): boolean {
  return /^#([0-9A-Fa-f]{3}){1,2}$/.test(hex);
}

export function normalizeHex(hex: string): string {
  if (!hex) return '#3B82F6';
  let clean = hex.trim();
  if (!clean.startsWith('#')) {
    clean = '#' + clean;
  }
  if (clean.length === 4) {
    clean = '#' + clean[1] + clean[1] + clean[2] + clean[2] + clean[3] + clean[3];
  }
  return isValidHex(clean) ? clean.toUpperCase() : '#3B82F6';
}

export function hexToRgb(hexColor: string): { r: number; g: number; b: number } {
  const norm = normalizeHex(hexColor).replace('#', '');
  const r = parseInt(norm.substring(0, 2), 16) || 0;
  const g = parseInt(norm.substring(2, 4), 16) || 0;
  const b = parseInt(norm.substring(4, 6), 16) || 0;
  return { r, g, b };
}

export function hexToRgba(hexColor: string, alpha: number): string {
  const { r, g, b } = hexToRgb(hexColor);
  return `rgba(${r}, ${g}, ${b}, ${alpha})`;
}

/**
 * Calculates optimal text color (#0f172a or #ffffff) based on perceived brightness (YIQ standard).
 */
export function getContrastTextColor(hexColor: string): string {
  const { r, g, b } = hexToRgb(hexColor);
  const yiq = (r * 299 + g * 587 + b * 114) / 1000;
  return yiq >= 148 ? '#0f172a' : '#ffffff';
}

/**
 * Curated preset accent swatches for one-click selection
 */
export const ACCENT_COLOR_PRESETS = [
  { name: 'Google Blue', hex: '#3B82F6' },
  { name: 'Electric Cyan', hex: '#06B6D4' },
  { name: 'Vibrant Teal', hex: '#14B8A6' },
  { name: 'Emerald', hex: '#10B981' },
  { name: 'Neon Lime', hex: '#84CC16' },
  { name: 'Amber Flame', hex: '#F59E0B' },
  { name: 'Warm Coral', hex: '#FF6B6B' },
  { name: 'Ruby Rose', hex: '#F43F5E' },
  { name: 'Cyber Pink', hex: '#EC4899' },
  { name: 'Neon Purple', hex: '#8B5CF6' },
  { name: 'Royal Indigo', hex: '#6366F1' },
  { name: 'Sunset Tangerine', hex: '#FB923C' },
];
