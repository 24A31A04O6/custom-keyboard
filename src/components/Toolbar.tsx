import React, { useContext } from 'react';
import {
  Clipboard,
  Globe,
  Sparkles,
  Smile,
  Palette,
  Settings as SettingsIcon,
  Mic,
  RotateCcw,
  MessageSquareQuote,
  BarChart2,
  SlidersHorizontal,
  Trash2
} from 'lucide-react';
import { ThemeId } from '../types';
import { AccentColorContext } from '../context/AccentColorContext';
import { hexToRgba, getContrastTextColor } from '../utils/color';

interface ToolbarProps {
  themeId: ThemeId;
  enabledButtons: string[];
  activePanel: string | null;
  onButtonClick: (id: string) => void;
  onToggleCustomizer: () => void;
  canUndo: boolean;
  historyCount?: number;
  onClearHistory?: () => void;
  customAccentColor?: string | null;
}

export const ALL_TOOLBAR_DEFS = [
  { id: 'clipboard', label: 'Clipboard', icon: Clipboard },
  { id: 'translate', label: 'Translate', icon: Globe },
  { id: 'autocorrect', label: 'Auto Correct', icon: Sparkles },
  { id: 'emoji', label: 'Emoji', icon: Smile },
  { id: 'theme', label: 'Theme & Settings', icon: Palette },
  { id: 'voice', label: 'Voice Typing', icon: Mic },
  { id: 'undo', label: 'Undo & Clear History', icon: RotateCcw },
  { id: 'phrases', label: 'Phrases', icon: MessageSquareQuote },
  { id: 'stats', label: 'Stats', icon: BarChart2 },
  { id: 'settings', label: 'Settings', icon: SettingsIcon },
];

export const Toolbar: React.FC<ToolbarProps> = ({
  themeId,
  enabledButtons,
  activePanel,
  onButtonClick,
  onToggleCustomizer,
  canUndo,
  historyCount = 0,
  onClearHistory,
  customAccentColor: propAccentColor,
}) => {
  const contextAccent = useContext(AccentColorContext);
  const effectiveAccent = propAccentColor ?? contextAccent;
  const isLight = themeId === ThemeId.LIGHT;

  // Filter and order buttons based on enabled list
  const visibleButtons = enabledButtons
    .map((id) => ALL_TOOLBAR_DEFS.find((def) => def.id === id))
    .filter((def): def is (typeof ALL_TOOLBAR_DEFS)[0] => !!def);

  return (
    <div
      id="toolbar_strip"
      className={`w-full flex items-center justify-between px-3 py-2 min-h-[50px] border-b transition-colors ${
        isLight
          ? 'bg-neutral-200/90 border-neutral-300 text-neutral-800'
          : themeId === ThemeId.BLUE
          ? 'bg-[#0A1F38]/90 border-[#102E50] text-blue-100'
          : themeId === ThemeId.GREEN
          ? 'bg-[#0A2410]/90 border-[#103818] text-green-100'
          : themeId === ThemeId.AMOLED || themeId === ThemeId.BLACK
          ? 'bg-black border-neutral-900 text-neutral-300'
          : 'bg-[#1B2127]/90 border-[#2b333d] text-neutral-200'
      }`}
    >
      <div className="flex items-center space-x-1.5 overflow-x-auto no-scrollbar py-0.5 scroll-smooth">
        {visibleButtons.map((btn) => {
          const Icon = btn.icon;
          const isActive = activePanel === btn.id;
          const isDisabled = btn.id === 'undo' && !canUndo;

          if (btn.id === 'undo') {
            return (
              <div
                key={btn.id}
                id="undo_control_group"
                className={`flex items-center rounded-xl p-1 shrink-0 transition-all border ${
                  canUndo
                    ? isLight
                      ? 'bg-neutral-300/60 border-neutral-300'
                      : 'bg-white/10 border-white/10 shadow-xs'
                    : 'bg-transparent border-transparent'
                }`}
              >
                <button
                  id="tb_btn_undo"
                  onClick={() => onButtonClick('undo')}
                  disabled={isDisabled}
                  title={canUndo ? `Undo (${historyCount} saved states)` : 'Undo (no history)'}
                  className={`h-9 px-2.5 rounded-lg text-xs flex items-center space-x-1.5 transition-all cursor-pointer ${
                    isDisabled
                      ? 'opacity-30 cursor-not-allowed'
                      : isLight
                      ? 'hover:bg-neutral-300 active:bg-neutral-400/40 text-neutral-800'
                      : 'hover:bg-white/15 active:bg-white/20 text-neutral-100'
                  }`}
                >
                  <Icon size={19} />
                  {historyCount > 0 && (
                    <span
                      style={
                        effectiveAccent
                          ? {
                              backgroundColor: hexToRgba(effectiveAccent, 0.2),
                              color: effectiveAccent,
                            }
                          : undefined
                      }
                      className="text-[11px] font-mono font-bold px-1.5 py-0.5 rounded-full bg-blue-500/20 text-blue-600 dark:text-blue-400"
                    >
                      {historyCount}
                    </span>
                  )}
                </button>

                <button
                  id="btn_clear_history"
                  onClick={(e) => {
                    e.stopPropagation();
                    onClearHistory?.();
                  }}
                  disabled={!canUndo}
                  title={
                    canUndo
                      ? `Clear History (${historyCount} snapshots) - Free memory immediately`
                      : 'Clear History (history is empty)'
                  }
                  className={`h-9 px-2 rounded-lg text-xs font-semibold flex items-center space-x-1 transition-all cursor-pointer ${
                    !canUndo
                      ? 'opacity-25 cursor-not-allowed text-neutral-400'
                      : isLight
                      ? 'text-red-600 hover:bg-red-500/15 active:bg-red-500/25'
                      : 'text-red-400 hover:bg-red-500/20 active:bg-red-500/30'
                  }`}
                >
                  <Trash2 size={15} />
                  <span className="text-[11px] tracking-tight whitespace-nowrap">Clear History</span>
                </button>
              </div>
            );
          }

          return (
            <button
              key={btn.id}
              id={`tb_btn_${btn.id}`}
              onClick={() => onButtonClick(btn.id)}
              disabled={isDisabled}
              title={btn.label}
              style={
                isActive && effectiveAccent
                  ? {
                      backgroundColor: effectiveAccent,
                      color: getContrastTextColor(effectiveAccent),
                      boxShadow: `0 2px 8px ${hexToRgba(effectiveAccent, 0.4)}`,
                    }
                  : undefined
              }
              className={`w-10 h-10 rounded-xl flex items-center justify-center transition-all shrink-0 cursor-pointer ${
                isDisabled
                  ? 'opacity-30 cursor-not-allowed'
                  : isActive
                  ? effectiveAccent
                    ? ''
                    : isLight
                    ? 'bg-blue-500 text-white shadow-sm'
                    : 'bg-blue-600 text-white shadow-sm'
                  : isLight
                  ? 'hover:bg-neutral-300/80 active:bg-neutral-300'
                  : 'hover:bg-white/10 active:bg-white/15'
              }`}
            >
              <Icon size={21} />
            </button>
          );
        })}
      </div>

      <div className="flex items-center pl-2 border-l border-neutral-500/20 shrink-0">
        <button
          id="btn_customize_toolbar"
          onClick={onToggleCustomizer}
          title="Customize Toolbar"
          style={
            activePanel === 'customizer' && effectiveAccent
              ? {
                  backgroundColor: effectiveAccent,
                  color: getContrastTextColor(effectiveAccent),
                  boxShadow: `0 2px 8px ${hexToRgba(effectiveAccent, 0.4)}`,
                }
              : undefined
          }
          className={`w-10 h-10 rounded-xl flex items-center justify-center transition-colors cursor-pointer ${
            activePanel === 'customizer'
              ? effectiveAccent
                ? ''
                : 'bg-blue-600 text-white'
              : isLight
              ? 'hover:bg-neutral-300 text-neutral-600'
              : 'hover:bg-white/10 text-neutral-400'
          }`}
        >
          <SlidersHorizontal size={20} />
        </button>
      </div>
    </div>
  );
};
