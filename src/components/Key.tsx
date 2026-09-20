import React, { useState, useRef, useContext } from 'react';
import { ThemeId, ShiftState } from '../types';
import { getSymbolAlternatives } from '../data/keySymbolMap';
import { KeyFontSizeContext } from '../context/KeyFontSizeContext';
import { AccentColorContext } from '../context/AccentColorContext';
import { hexToRgba, getContrastTextColor } from '../utils/color';

interface KeyProps {
  code?: number;
  label: string;
  icon?: React.ReactNode;
  width?: string;
  heightClass?: string;
  marginClass?: string;
  isSpecial?: boolean;
  themeId: ThemeId;
  shiftState: ShiftState;
  onKeyPress: (char: string) => void;
  onLongPressSymbol?: (symbols: string[], anchorX: number, anchorY: number) => void;
  isExternallyPressed?: boolean;
  fontSize?: number;
  className?: string;
  customAccentColor?: string | null;
}

export const Key: React.FC<KeyProps> = ({
  label,
  icon,
  width = 'w-[9.5%]',
  heightClass = 'h-11',
  marginClass,
  isSpecial = false,
  themeId,
  shiftState,
  onKeyPress,
  onLongPressSymbol,
  isExternallyPressed = false,
  fontSize,
  className,
  customAccentColor,
}) => {
  const contextFontSize = useContext(KeyFontSizeContext);
  const activeFontSize = fontSize ?? contextFontSize ?? 16;
  const contextAccent = useContext(AccentColorContext);
  const effectiveAccent = customAccentColor !== undefined ? customAccentColor : contextAccent;
  const [isPressed, setIsPressed] = useState(false);
  const activePointersRef = useRef<Set<number>>(new Set());
  const timerRef = useRef<number | null>(null);
  const isLongPressedRef = useRef<boolean>(false);
  const keyRef = useRef<HTMLButtonElement>(null);
  const lastTouchEmitMsRef = useRef<number>(0);

  const isLight = themeId === ThemeId.LIGHT;
  const isKeyActive = isPressed || isExternallyPressed;

  // Determine display character
  let displayChar = label;
  if (/^[a-z]$/i.test(label)) {
    displayChar = shiftState !== ShiftState.OFF ? label.toUpperCase() : label.toLowerCase();
  }

  // Check if character has symbol alternatives for subtle corner hint
  const alternatives = getSymbolAlternatives(label);
  const hintSymbol = alternatives && alternatives.length > 1 ? alternatives[1] : null;

  const handlePointerDown = (e: React.PointerEvent) => {
    activePointersRef.current.add(e.pointerId);
    setIsPressed(true);
    isLongPressedRef.current = false;
    if (onLongPressSymbol && alternatives && alternatives.length > 1) {
      timerRef.current = window.setTimeout(() => {
        isLongPressedRef.current = true;
        if (keyRef.current) {
          const rect = keyRef.current.getBoundingClientRect();
          onLongPressSymbol(alternatives, rect.left + rect.width / 2, rect.top);
        }
      }, 350);
    }
  };

  const handlePointerUp = (e: React.PointerEvent) => {
    if (timerRef.current) {
      clearTimeout(timerRef.current);
      timerRef.current = null;
    }
    const wasActive = activePointersRef.current.has(e.pointerId);
    activePointersRef.current.delete(e.pointerId);
    if (activePointersRef.current.size === 0) {
      setIsPressed(false);
    }
    if (wasActive && !isLongPressedRef.current) {
      // For touch and pen pointers, simultaneous multi-touch taps often suppress standard browser
      // click events for secondary touches. Emitting directly on pointerup ensures simultaneous touches
      // reliably register without losing either key.
      if (e.pointerType === 'touch' || e.pointerType === 'pen') {
        lastTouchEmitMsRef.current = Date.now();
        onKeyPress(displayChar);
      }
    }
  };

  const handlePointerLeaveOrCancel = (e: React.PointerEvent) => {
    activePointersRef.current.delete(e.pointerId);
    if (activePointersRef.current.size === 0) {
      if (timerRef.current) {
        clearTimeout(timerRef.current);
        timerRef.current = null;
      }
      setIsPressed(false);
    }
  };

  const handleClick = () => {
    // If this click is a synthetic event following touch pointerup that already emitted, ignore it
    if (Date.now() - lastTouchEmitMsRef.current < 450) {
      return;
    }
    if (isLongPressedRef.current) {
      isLongPressedRef.current = false;
      return;
    }
    onKeyPress(displayChar);
  };

  // Theme-specific styles with dynamic active transitions
  const getKeyStyle = () => {
    if (isSpecial) {
      if (isKeyActive) {
        if (effectiveAccent) return 'border-b-2 shadow-inner';
        if (isLight) return 'bg-blue-200/90 text-blue-900 border-b-2 border-blue-400 shadow-inner';
        if (themeId === ThemeId.BLUE) return 'bg-[#1b4b7f] text-blue-50 border-b-2 border-blue-300 shadow-inner';
        if (themeId === ThemeId.GREEN) return 'bg-[#1d5b24] text-green-50 border-b-2 border-emerald-400 shadow-inner';
        return 'bg-neutral-800 text-blue-200 border-b-2 border-blue-500 shadow-inner';
      }
      if (isLight) return 'bg-neutral-300/80 hover:bg-neutral-300 text-neutral-800 active:bg-blue-200';
      if (themeId === ThemeId.BLUE) return 'bg-[#143a63] hover:bg-[#1b4b7f] text-blue-100 active:bg-[#1b4b7f]';
      if (themeId === ThemeId.GREEN) return 'bg-[#15461b] hover:bg-[#1d5b24] text-green-100 active:bg-[#1d5b24]';
      if (themeId === ThemeId.AMOLED || themeId === ThemeId.BLACK) return 'bg-neutral-900 hover:bg-neutral-800 text-neutral-300 active:bg-neutral-800';
      return 'bg-[#2E353D] hover:bg-[#38404a] text-neutral-200 active:bg-[#38404a]';
    }

    // Normal key
    if (isKeyActive) {
      if (effectiveAccent) return 'border-b-2 shadow-inner';
      if (isLight) return 'bg-blue-100/95 text-blue-900 border-b-2 border-blue-400 shadow-inner';
      if (themeId === ThemeId.BLUE) return 'bg-blue-600 text-white border-b-2 border-blue-300 shadow-inner';
      if (themeId === ThemeId.GREEN) return 'bg-emerald-600 text-white border-b-2 border-emerald-300 shadow-inner';
      if (themeId === ThemeId.AMOLED) return 'bg-neutral-800 text-blue-200 border-b-2 border-blue-400 shadow-inner';
      if (themeId === ThemeId.BLACK) return 'bg-neutral-800 text-blue-200 border-b-2 border-blue-400 shadow-inner';
      return 'bg-blue-600/60 text-white border-b-2 border-blue-400 shadow-inner';
    }

    if (isLight) return 'bg-white hover:bg-neutral-50 text-neutral-900 shadow-xs active:bg-blue-100 border-b-2 border-neutral-300';
    if (themeId === ThemeId.BLUE) return 'bg-[#1a4473] hover:bg-[#20528a] text-white shadow-xs active:bg-blue-600 border-b-2 border-[#102d4f]';
    if (themeId === ThemeId.GREEN) return 'bg-[#1a5222] hover:bg-[#22632b] text-white shadow-xs active:bg-emerald-600 border-b-2 border-[#103816]';
    if (themeId === ThemeId.AMOLED) return 'bg-[#111111] hover:bg-[#191919] text-white shadow-xs active:bg-neutral-800 border-b-2 border-neutral-900';
    if (themeId === ThemeId.BLACK) return 'bg-[#1a1a1a] hover:bg-[#222222] text-white shadow-xs active:bg-neutral-800 border-b-2 border-[#0d0d0d]';
    return 'bg-[#3A434C] hover:bg-[#434d57] text-white shadow-xs active:bg-blue-600/50 border-b-2 border-[#262c33]';
  };

  const getDynamicActiveStyle = (): React.CSSProperties | undefined => {
    if (!isKeyActive || !effectiveAccent) return undefined;
    if (isSpecial) {
      return {
        backgroundColor: hexToRgba(effectiveAccent, isLight ? 0.35 : 0.45),
        color: isLight ? '#0f172a' : '#ffffff',
        borderBottomColor: effectiveAccent,
        boxShadow: `0 0 12px ${hexToRgba(effectiveAccent, 0.35)} inset`,
      };
    }
    return {
      backgroundColor: effectiveAccent,
      color: getContrastTextColor(effectiveAccent),
      borderBottomColor: effectiveAccent,
      boxShadow: `0 0 14px ${hexToRgba(effectiveAccent, 0.4)} inset`,
    };
  };

  // Check if character is number or special symbol
  const isNumber = /^[0-9]$/.test(label);
  const isLetter = /^[a-zA-Z]$/i.test(label);
  const isSymbol = !isNumber && !isLetter && !icon && label.trim().length > 0;
  const isNumberOrSymbol = isNumber || isSymbol;

  // Enlarge size for numbers and special symbols as requested by user
  const effectiveFontSize = isNumber
    ? Math.round(activeFontSize * 1.35)
    : isSymbol
    ? Math.round(activeFontSize * 1.28)
    : activeFontSize;

  return (
    <button
      ref={keyRef}
      id={`key_${label.toLowerCase()}`}
      data-key-id={label.toLowerCase()}
      type="button"
      onPointerDown={handlePointerDown}
      onPointerUp={handlePointerUp}
      onPointerLeave={handlePointerLeaveOrCancel}
      onPointerCancel={handlePointerLeaveOrCancel}
      onClick={handleClick}
      style={{
        fontSize: `${effectiveFontSize}px`,
        ...getDynamicActiveStyle(),
      }}
      className={`relative ${heightClass} ${width} ${marginClass ?? 'm-[2px]'} ${
        isNumberOrSymbol ? 'rounded-xl font-semibold' : 'rounded-xl font-medium'
      } flex items-center justify-center font-sans select-none cursor-pointer transform will-change-transform transition-[transform,background-color,border-color,box-shadow,filter,color] duration-100 ease-out active:scale-90 active:duration-75 active:ease-out ${
        isKeyActive ? 'scale-90 brightness-105' : 'hover:-translate-y-[1px]'
      } ${getKeyStyle()} ${className ?? ''}`}
    >
      {/* Secondary symbol hint in corner (like Gboard/BabelKey) */}
      {!isSpecial && !isNumberOrSymbol && hintSymbol && (
        <span
          className="absolute top-1 right-1.5 opacity-40 font-mono pointer-events-none"
          style={{ fontSize: `${Math.max(8, Math.round(activeFontSize * 0.55))}px` }}
        >
          {hintSymbol}
        </span>
      )}

      {/* Main icon or label */}
      <div className={`flex items-center justify-center pointer-events-none ${isNumberOrSymbol ? 'scale-105' : ''}`}>
        {icon ? icon : displayChar}
      </div>
    </button>
  );
};
