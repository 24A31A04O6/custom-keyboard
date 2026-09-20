import React from 'react';
import { ALL_TOOLBAR_DEFS } from './Toolbar';
import { ThemeId } from '../types';
import { Check, X, RotateCcw, ArrowUp, ArrowDown } from 'lucide-react';

interface ToolbarCustomizerProps {
  themeId: ThemeId;
  enabledButtons: string[];
  onUpdateToolbar: (newButtons: string[]) => void;
  onClose: () => void;
}

const DEFAULT_BUTTONS = ['clipboard', 'translate', 'autocorrect', 'emoji', 'voice', 'undo', 'phrases', 'stats', 'settings'];

export const ToolbarCustomizer: React.FC<ToolbarCustomizerProps> = ({
  themeId,
  enabledButtons,
  onUpdateToolbar,
  onClose,
}) => {
  const isLight = themeId === ThemeId.LIGHT;

  const handleToggle = (id: string) => {
    if (enabledButtons.includes(id)) {
      if (enabledButtons.length <= 1) return; // Keep at least one
      onUpdateToolbar(enabledButtons.filter((b) => b !== id));
    } else {
      onUpdateToolbar([...enabledButtons, id]);
    }
  };

  const handleMove = (index: number, direction: 'up' | 'down') => {
    const newArr = [...enabledButtons];
    const targetIdx = direction === 'up' ? index - 1 : index + 1;
    if (targetIdx < 0 || targetIdx >= newArr.length) return;
    const temp = newArr[index];
    newArr[index] = newArr[targetIdx];
    newArr[targetIdx] = temp;
    onUpdateToolbar(newArr);
  };

  const handleReset = () => {
    onUpdateToolbar(DEFAULT_BUTTONS);
  };

  return (
    <div
      id="toolbar_customizer_view"
      className={`w-full flex flex-col h-[280px] select-none transition-colors border-t p-3 ${
        isLight
          ? 'bg-neutral-100 text-neutral-900 border-neutral-300'
          : 'bg-[#191F26] text-neutral-100 border-neutral-700'
      }`}
    >
      <div className="flex items-center justify-between pb-2 border-b border-neutral-500/20">
        <div className="flex items-center space-x-2">
          <span className="font-semibold text-xs uppercase tracking-wider text-blue-500">
            Customize Quick Toolbar
          </span>
        </div>

        <div className="flex items-center space-x-2">
          <button
            type="button"
            onClick={handleReset}
            title="Reset to default"
            className="p-1 rounded-md text-xs hover:bg-neutral-500/20 text-neutral-400 hover:text-neutral-200 flex items-center space-x-1"
          >
            <RotateCcw size={13} />
            <span>Reset</span>
          </button>
          <button
            type="button"
            onClick={onClose}
            className="p-1 rounded-md hover:bg-neutral-500/20 opacity-70 hover:opacity-100"
          >
            <X size={15} />
          </button>
        </div>
      </div>

      <div className="flex-1 overflow-y-auto py-2 space-y-1.5">
        {ALL_TOOLBAR_DEFS.map((btn) => {
          const Icon = btn.icon;
          const isEnabled = enabledButtons.includes(btn.id);
          const activeIndex = enabledButtons.indexOf(btn.id);

          return (
            <div
              key={btn.id}
              className={`flex items-center justify-between p-2 rounded-lg border text-xs transition-colors ${
                isEnabled
                  ? isLight
                    ? 'bg-white border-blue-400/50'
                    : 'bg-neutral-800/80 border-blue-500/40'
                  : isLight
                  ? 'bg-neutral-200/50 border-neutral-300 opacity-60'
                  : 'bg-neutral-900/40 border-neutral-800 opacity-50'
              }`}
            >
              <div
                onClick={() => handleToggle(btn.id)}
                className="flex items-center space-x-2.5 flex-1 cursor-pointer"
              >
                <div
                  className={`w-4 h-4 rounded-sm flex items-center justify-center border ${
                    isEnabled
                      ? 'bg-blue-600 border-blue-600 text-white'
                      : 'border-neutral-400 bg-transparent'
                  }`}
                >
                  {isEnabled && <Check size={12} strokeWidth={3} />}
                </div>
                <Icon size={16} />
                <span className="font-medium">{btn.label}</span>
              </div>

              {isEnabled && (
                <div className="flex items-center space-x-1">
                  <button
                    type="button"
                    disabled={activeIndex === 0}
                    onClick={() => handleMove(activeIndex, 'up')}
                    className="p-1 rounded-md hover:bg-neutral-500/20 disabled:opacity-20"
                  >
                    <ArrowUp size={13} />
                  </button>
                  <button
                    type="button"
                    disabled={activeIndex === enabledButtons.length - 1}
                    onClick={() => handleMove(activeIndex, 'down')}
                    className="p-1 rounded-md hover:bg-neutral-500/20 disabled:opacity-20"
                  >
                    <ArrowDown size={13} />
                  </button>
                </div>
              )}
            </div>
          );
        })}
      </div>
    </div>
  );
};
