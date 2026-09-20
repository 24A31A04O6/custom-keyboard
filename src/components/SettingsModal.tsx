import React, { useState } from 'react';
import { AppSettings, ThemeId, SoundPackId } from '../types';
import { soundController } from '../utils/audio';
import {
  Palette,
  Volume2,
  Sparkles,
  Sliders,
  BookOpen,
  Zap,
  X,
  Plus,
  Trash2,
  Check,
  Upload,
  Play,
  Type,
  RotateCcw
} from 'lucide-react';

interface SettingsModalProps {
  settings: AppSettings;
  onUpdateSettings: (newSettings: Partial<AppSettings>) => void;
  onClose: () => void;
  initialTab?: TabType;
}

type TabType = 'theme' | 'sound' | 'corrections' | 'dictionary' | 'shortcuts';

export const SettingsModal: React.FC<SettingsModalProps> = ({
  settings,
  onUpdateSettings,
  onClose,
  initialTab = 'theme',
}) => {
  const [activeTab, setActiveTab] = useState<TabType>(initialTab);

  // Directly persist settings to localStorage immediately, and notify parent
  const updateAndPersist = (newSettings: Partial<AppSettings>) => {
    try {
      const saved = localStorage.getItem('babelkey_settings');
      const current = saved ? JSON.parse(saved) : settings;
      const merged = { ...current, ...newSettings };
      try {
        localStorage.setItem('babelkey_settings', JSON.stringify(merged));
      } catch (quotaError) {
        // Handle QuotaExceededError (e.g., if custom background is too large)
        const fallback = { ...merged, customBackgroundUrl: null };
        localStorage.setItem('babelkey_settings', JSON.stringify(fallback));
      }
    } catch (e) {
      console.error('Failed to persist settings in localStorage:', e);
    }
    onUpdateSettings(newSettings);
  };

  // Key Font Size helper
  const currentFontSize = settings.keyFontSize ?? 16;
  const getFontSizeTier = (size: number) => {
    if (size >= 22) {
      return {
        label: 'Extra Large',
        badgeClass: 'text-purple-600 dark:text-purple-400 bg-purple-500/15 border-purple-500/30',
      };
    }
    if (size >= 19) {
      return {
        label: 'Large',
        badgeClass: 'text-blue-600 dark:text-blue-400 bg-blue-500/15 border-blue-500/30',
      };
    }
    if (size >= 15) {
      return {
        label: 'Standard',
        badgeClass: 'text-emerald-600 dark:text-emerald-400 bg-emerald-500/15 border-emerald-500/30',
      };
    }
    return {
      label: 'Compact',
      badgeClass: 'text-amber-600 dark:text-amber-400 bg-amber-500/15 border-amber-500/30',
    };
  };
  const fontSizeTier = getFontSizeTier(currentFontSize);

  // Personal dictionary inputs
  const [newWord, setNewWord] = useState('');

  // Shortcuts inputs
  const [shortcutKey, setShortcutKey] = useState('');
  const [shortcutVal, setShortcutVal] = useState('');

  // Theme options
  const themes = [
    { id: ThemeId.LIGHT, name: 'Clean Light', color: 'bg-neutral-100 border-neutral-300 text-neutral-900' },
    { id: ThemeId.DARK, name: 'Graphite Dark', color: 'bg-[#2E353D] border-neutral-600 text-white' },
    { id: ThemeId.BLACK, name: 'Pitch Black', color: 'bg-black border-neutral-700 text-white' },
    { id: ThemeId.AMOLED, name: 'AMOLED Pure', color: 'bg-black border-neutral-900 text-neutral-300' },
    { id: ThemeId.BLUE, name: 'Deep Blue', color: 'bg-[#0E294A] border-blue-800 text-blue-100' },
    { id: ThemeId.GREEN, name: 'Forest Green', color: 'bg-[#0E3516] border-green-800 text-green-100' },
  ];

  // Sound pack options
  const soundPacks = [
    { id: SoundPackId.OFF, name: 'Mute (Silent)' },
    { id: SoundPackId.MECHANICAL, name: 'Mechanical Switch' },
    { id: SoundPackId.TYPEWRITER, name: 'Classic Typewriter' },
    { id: SoundPackId.BUBBLE, name: 'Pop Bubble' },
    { id: SoundPackId.GAMING, name: 'Tactile Gaming' },
    { id: SoundPackId.SOFT, name: 'Soft Thud' },
    { id: SoundPackId.CRYSTAL, name: 'Crystal Chime' },
  ];

  const handleImageUpload = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (file) {
      const reader = new FileReader();
      reader.onload = (event) => {
        const rawData = event.target?.result as string;
        if (rawData) {
          // Downscale to prevent exceeding localStorage quota on Android / mobile
          const img = new Image();
          img.onload = () => {
            const canvas = document.createElement('canvas');
            const maxW = 800;
            const maxH = 480;
            let width = img.width;
            let height = img.height;
            if (width > maxW || height > maxH) {
              const ratio = Math.min(maxW / width, maxH / height);
              width = Math.round(width * ratio);
              height = Math.round(height * ratio);
            }
            canvas.width = width;
            canvas.height = height;
            const ctx = canvas.getContext('2d');
            if (ctx) {
              ctx.drawImage(img, 0, 0, width, height);
              const compressedUrl = canvas.toDataURL('image/jpeg', 0.82);
              updateAndPersist({
                selectedTheme: ThemeId.CUSTOM,
                customBackgroundUrl: compressedUrl,
              });
            } else {
              updateAndPersist({
                selectedTheme: ThemeId.CUSTOM,
                customBackgroundUrl: rawData,
              });
            }
          };
          img.onerror = () => {
            updateAndPersist({
              selectedTheme: ThemeId.CUSTOM,
              customBackgroundUrl: rawData,
            });
          };
          img.src = rawData;
        }
      };
      reader.readAsDataURL(file);
    }
  };

  const handleAddWord = (e: React.FormEvent) => {
    e.preventDefault();
    if (!newWord.trim()) return;
    if (!settings.personalDictionary.includes(newWord.trim())) {
      updateAndPersist({
        personalDictionary: [...settings.personalDictionary, newWord.trim()],
      });
    }
    setNewWord('');
  };

  const handleDeleteWord = (word: string) => {
    updateAndPersist({
      personalDictionary: settings.personalDictionary.filter((w) => w !== word),
    });
  };

  const handleAddShortcut = (e: React.FormEvent) => {
    e.preventDefault();
    if (!shortcutKey.trim() || !shortcutVal.trim()) return;
    updateAndPersist({
      shortcuts: {
        ...settings.shortcuts,
        [shortcutKey.trim().toLowerCase()]: shortcutVal.trim(),
      },
    });
    setShortcutKey('');
    setShortcutVal('');
  };

  const handleDeleteShortcut = (key: string) => {
    const updated = { ...settings.shortcuts };
    delete updated[key];
    updateAndPersist({ shortcuts: updated });
  };

  const testSound = (pack: SoundPackId) => {
    soundController.playKeySound(pack, settings.soundVolume);
  };

  return (
    <div
      id="settings_modal_backdrop"
      className="fixed inset-0 z-50 flex items-center justify-center p-3 bg-black/60 backdrop-blur-xs animate-in fade-in duration-150"
    >
      <div
        id="settings_card"
        className="w-full max-w-xl max-h-[90vh] flex flex-col bg-white dark:bg-[#1C2127] text-neutral-900 dark:text-neutral-100 rounded-2xl shadow-2xl border border-neutral-200 dark:border-neutral-800 overflow-hidden"
      >
        {/* Header */}
        <div className="flex items-center justify-between px-5 py-3.5 border-b border-neutral-200 dark:border-neutral-800">
          <div className="flex items-center space-x-2">
            <Sliders size={18} className="text-blue-500" />
            <h2 className="font-bold text-sm tracking-wide">BabelKey Settings</h2>
            <span className="text-[10px] text-emerald-600 dark:text-emerald-400 bg-emerald-500/10 border border-emerald-500/20 px-2 py-0.5 rounded-full font-medium flex items-center gap-1">
              <Check size={10} /> Saved to storage
            </span>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="p-1 rounded-lg hover:bg-neutral-100 dark:hover:bg-neutral-800 text-neutral-500 cursor-pointer"
          >
            <X size={18} />
          </button>
        </div>

        {/* Tab Navigation */}
        <div className="flex items-center px-4 pt-2 border-b border-neutral-200 dark:border-neutral-800 overflow-x-auto no-scrollbar space-x-2">
          {[
            { id: 'theme', label: 'Theme', icon: Palette },
            { id: 'sound', label: 'Sound & Haptics', icon: Volume2 },
            { id: 'corrections', label: 'Text Corrections', icon: Sparkles },
            { id: 'dictionary', label: 'Personal Dictionary', icon: BookOpen },
            { id: 'shortcuts', label: 'Text Shortcuts', icon: Zap },
          ].map((tab) => {
            const Icon = tab.icon;
            const isActive = activeTab === tab.id;
            return (
              <button
                key={tab.id}
                type="button"
                onClick={() => setActiveTab(tab.id as TabType)}
                className={`flex items-center space-x-1.5 px-3 py-2 text-xs font-semibold border-b-2 transition-all shrink-0 ${
                  isActive
                    ? 'border-blue-500 text-blue-600 dark:text-blue-400'
                    : 'border-transparent text-neutral-500 hover:text-neutral-900 dark:hover:text-neutral-200'
                }`}
              >
                <Icon size={14} />
                <span>{tab.label}</span>
              </button>
            );
          })}
        </div>

        {/* Tab Content */}
        <div className="flex-1 overflow-y-auto p-5 space-y-5">
          {/* THEME TAB */}
          {activeTab === 'theme' && (
            <div className="space-y-4">
              <div>
                <h3 className="text-xs font-bold uppercase tracking-wider text-neutral-400 mb-2">
                  Color Themes
                </h3>
                <div className="grid grid-cols-2 sm:grid-cols-3 gap-2.5">
                  {themes.map((t) => {
                    const isSelected = settings.selectedTheme === t.id;
                    return (
                      <button
                        key={t.id}
                        type="button"
                        onClick={() => updateAndPersist({ selectedTheme: t.id })}
                        className={`h-16 p-3 rounded-xl border flex flex-col justify-between transition-all text-left ${
                          t.color
                        } ${isSelected ? 'ring-2 ring-blue-500 scale-[1.02]' : 'opacity-85 hover:opacity-100'}`}
                      >
                        <span className="font-semibold text-xs">{t.name}</span>
                        {isSelected && (
                          <div className="self-end p-0.5 rounded-full bg-blue-500 text-white">
                            <Check size={12} />
                          </div>
                        )}
                      </button>
                    );
                  })}
                </div>
              </div>

              {/* Custom Image Upload */}
              <div className="p-3.5 rounded-xl border border-neutral-200 dark:border-neutral-800 space-y-2">
                <div className="flex items-center justify-between">
                  <div>
                    <h4 className="text-xs font-bold">Custom Photo Background</h4>
                    <p className="text-[11px] text-neutral-400">
                      Upload an image wallpaper for your keyboard
                    </p>
                  </div>
                  <label className="cursor-pointer px-3 py-1.5 rounded-lg bg-blue-600 hover:bg-blue-500 text-white text-xs font-semibold flex items-center space-x-1.5">
                    <Upload size={13} />
                    <span>Choose Image</span>
                    <input
                      type="file"
                      accept="image/*"
                      onChange={handleImageUpload}
                      className="hidden"
                    />
                  </label>
                </div>
                {settings.customBackgroundUrl && (
                  <div className="flex items-center space-x-3 pt-2">
                    <img
                      src={settings.customBackgroundUrl}
                      alt="Custom preview"
                      className="w-16 h-10 object-cover rounded-md border"
                    />
                    <button
                      type="button"
                      onClick={() => updateAndPersist({ customBackgroundUrl: null, selectedTheme: ThemeId.DARK })}
                      className="text-xs text-red-400 hover:underline cursor-pointer"
                    >
                      Remove Wallpaper
                    </button>
                  </div>
                )}
              </div>

              {/* Key Font Size (Accessibility) Slider */}
              <div
                id="slider_key_font_size_card"
                className="p-3.5 rounded-xl border border-neutral-200 dark:border-neutral-800 space-y-3 bg-neutral-50/50 dark:bg-neutral-900/30"
              >
                <div className="flex items-center justify-between">
                  <div className="flex items-center space-x-2">
                    <div className="p-1.5 rounded-lg bg-blue-500/10 text-blue-600 dark:text-blue-400">
                      <Type size={16} />
                    </div>
                    <div>
                      <div className="flex items-center space-x-2">
                        <h4 className="text-xs font-bold">Key Text Size (Accessibility)</h4>
                        <span className={`text-[10px] font-semibold px-2 py-0.5 rounded-full border ${fontSizeTier.badgeClass}`}>
                          {fontSizeTier.label}
                        </span>
                      </div>
                      <p className="text-[11px] text-neutral-500 dark:text-neutral-400">
                        Scale keyboard key lettering for enhanced legibility
                      </p>
                    </div>
                  </div>

                  <div className="flex items-center space-x-2">
                    <span id="text_font_size_val" className="font-mono text-sm font-bold text-blue-600 dark:text-blue-400">
                      {currentFontSize}px
                    </span>
                    {currentFontSize !== 16 && (
                      <button
                        type="button"
                        id="btn_reset_font_size"
                        onClick={() => updateAndPersist({ keyFontSize: 16 })}
                        title="Reset to default 16px"
                        className="p-1 text-xs text-neutral-400 hover:text-neutral-600 dark:hover:text-neutral-200 hover:bg-neutral-200/50 dark:hover:bg-neutral-800 rounded-md transition-colors cursor-pointer"
                      >
                        <RotateCcw size={12} />
                      </button>
                    )}
                  </div>
                </div>

                {/* Slider Input */}
                <div className="space-y-1">
                  <input
                    id="slider_key_font_size"
                    type="range"
                    min="12"
                    max="24"
                    step="1"
                    value={currentFontSize}
                    onChange={(e) => updateAndPersist({ keyFontSize: Number(e.target.value) })}
                    className="w-full accent-blue-600 cursor-pointer h-2 bg-neutral-200 dark:bg-neutral-700 rounded-lg appearance-none"
                    aria-label="Keyboard key text size"
                  />
                  <div className="flex justify-between items-center text-[10px] text-neutral-400 font-mono">
                    <span>12px (Compact)</span>
                    <span>16px (Default)</span>
                    <span>20px (Large)</span>
                    <span>24px (Max)</span>
                  </div>
                </div>

                {/* Preset Buttons */}
                <div className="flex items-center gap-1.5 pt-0.5">
                  {[
                    { label: 'Compact', size: 13, id: 'btn_font_size_compact' },
                    { label: 'Default', size: 16, id: 'btn_font_size_default' },
                    { label: 'Large', size: 20, id: 'btn_font_size_large' },
                    { label: 'XL', size: 24, id: 'btn_font_size_xlarge' },
                  ].map((preset) => {
                    const isSelected = currentFontSize === preset.size;
                    return (
                      <button
                        key={preset.size}
                        id={preset.id}
                        type="button"
                        onClick={() => updateAndPersist({ keyFontSize: preset.size })}
                        className={`flex-1 py-1 rounded-lg text-xs font-medium border transition-all cursor-pointer ${
                          isSelected
                            ? 'bg-blue-600 text-white border-blue-600 shadow-xs'
                            : 'border-neutral-200 dark:border-neutral-700 hover:bg-neutral-100 dark:hover:bg-neutral-800 text-neutral-700 dark:text-neutral-300'
                        }`}
                      >
                        {preset.label}
                      </button>
                    );
                  })}
                </div>

                {/* Live Preview Strip */}
                <div className="pt-2 border-t border-neutral-200 dark:border-neutral-800 flex items-center justify-between">
                  <span className="text-[11px] text-neutral-400 font-medium">Live Key Preview:</span>
                  <div className="flex items-center space-x-1.5">
                    {['A', 'g', '5', '₹'].map((char) => (
                      <div
                        key={char}
                        style={{ fontSize: `${currentFontSize}px` }}
                        className="w-8 h-9 rounded-md bg-white dark:bg-neutral-800 border border-neutral-300 dark:border-neutral-700 text-neutral-900 dark:text-neutral-100 flex items-center justify-center font-sans font-medium shadow-xs select-none"
                      >
                        {char}
                      </div>
                    ))}
                    <div
                      style={{ fontSize: `${Math.max(10, Math.round(currentFontSize * 0.75))}px` }}
                      className="px-2 h-9 rounded-md bg-neutral-200 dark:bg-neutral-700/60 border border-neutral-300 dark:border-neutral-700 text-neutral-700 dark:text-neutral-200 flex items-center justify-center font-semibold shadow-xs select-none"
                    >
                      ?123
                    </div>
                  </div>
                </div>
              </div>

              {/* Keyboard Height Slider */}
              <div className="p-3.5 rounded-xl border border-neutral-200 dark:border-neutral-800 space-y-2">
                <div className="flex items-center justify-between text-xs">
                  <span className="font-bold">Keyboard Height</span>
                  <span className="font-mono opacity-70">{settings.keyboardHeightDp}px</span>
                </div>
                <input
                  type="range"
                  min="240"
                  max="360"
                  step="10"
                  value={settings.keyboardHeightDp}
                  onChange={(e) => updateAndPersist({ keyboardHeightDp: Number(e.target.value) })}
                  className="w-full accent-blue-600 cursor-pointer"
                />
              </div>
            </div>
          )}

          {/* SOUND & HAPTICS TAB */}
          {activeTab === 'sound' && (
            <div className="space-y-4">
              <div>
                <h3 className="text-xs font-bold uppercase tracking-wider text-neutral-400 mb-2">
                  Keypress Sound Pack
                </h3>
                <div className="space-y-1.5">
                  {soundPacks.map((pack) => {
                    const isSelected = settings.soundPack === pack.id;
                    return (
                      <div
                        key={pack.id}
                        onClick={() => updateAndPersist({ soundPack: pack.id })}
                        className={`flex items-center justify-between p-2.5 rounded-xl border cursor-pointer text-xs transition-colors ${
                          isSelected
                            ? 'border-blue-500 bg-blue-500/10'
                            : 'border-neutral-200 dark:border-neutral-800 hover:bg-neutral-50 dark:hover:bg-neutral-800/40'
                        }`}
                      >
                        <div className="flex items-center space-x-2">
                          <div
                            className={`w-4 h-4 rounded-full border flex items-center justify-center ${
                              isSelected ? 'border-blue-500 bg-blue-500' : 'border-neutral-400'
                            }`}
                          >
                            {isSelected && <div className="w-1.5 h-1.5 rounded-full bg-white" />}
                          </div>
                          <span className="font-medium">{pack.name}</span>
                        </div>

                        {pack.id !== SoundPackId.OFF && (
                          <button
                            type="button"
                            onClick={(e) => {
                              e.stopPropagation();
                              testSound(pack.id);
                            }}
                            title="Preview sound"
                            className="p-1 rounded-md hover:bg-blue-500/20 text-blue-500 cursor-pointer"
                          >
                            <Play size={13} />
                          </button>
                        )}
                      </div>
                    );
                  })}
                </div>
              </div>

              {/* Volume Slider */}
              <div className="p-3.5 rounded-xl border border-neutral-200 dark:border-neutral-800 space-y-2">
                <div className="flex items-center justify-between text-xs">
                  <span className="font-bold">Sound Volume</span>
                  <span className="font-mono opacity-70">{settings.soundVolume}%</span>
                </div>
                <input
                  type="range"
                  min="0"
                  max="100"
                  value={settings.soundVolume}
                  onChange={(e) => updateAndPersist({ soundVolume: Number(e.target.value) })}
                  className="w-full accent-blue-600 cursor-pointer"
                />
              </div>

              {/* Haptic Toggle */}
              <div className="flex items-center justify-between p-3.5 rounded-xl border border-neutral-200 dark:border-neutral-800">
                <div>
                  <h4 className="text-xs font-bold">Vibration Haptic Feedback</h4>
                  <p className="text-[11px] text-neutral-400">Vibrate subtly on keypress</p>
                </div>
                <input
                  type="checkbox"
                  checked={settings.hapticEnabled}
                  onChange={(e) => {
                    updateAndPersist({ hapticEnabled: e.target.checked });
                    if (e.target.checked) soundController.triggerHaptic(true);
                  }}
                  className="w-4 h-4 accent-blue-600 rounded cursor-pointer"
                />
              </div>
            </div>
          )}

          {/* CORRECTIONS TAB */}
          {activeTab === 'corrections' && (
            <div className="space-y-3">
              {[
                { key: 'autoCorrectEnabled', label: 'Auto-Correction', desc: 'Fix typos instantly when spacebar is pressed' },
                { key: 'autoCapsEnabled', label: 'Auto-Capitalization', desc: 'Capitalize the first word of each sentence' },
                { key: 'spellCheckEnabled', label: 'Spell Checker', desc: 'Highlight and suggest fixes for misspelled words' },
                { key: 'grammarCheckEnabled', label: 'Telugu Grammar Correction', desc: 'Detect and suggest fixes for Telugu grammatical mistakes' },
                { key: 'smartComposeEnabled', label: 'Predictive Suggestions', desc: 'Show Telugu and English completions in suggestion bar' },
                { key: 'aiRepliesEnabled', label: 'AI Smart Replies', desc: 'Suggest context-aware quick replies for messages' },
                { key: 'blockOffensiveEnabled', label: 'Block Offensive Words', desc: 'Do not suggest potentially offensive vocabulary' },
                { key: 'swipeTypingEnabled', label: 'Glide / Swipe Typing', desc: 'Input words by swiping finger across keys' },
              ].map((item) => (
                <div
                  key={item.key}
                  className="flex items-center justify-between p-3 rounded-xl border border-neutral-200 dark:border-neutral-800"
                >
                  <div>
                    <h4 className="text-xs font-bold">{item.label}</h4>
                    <p className="text-[11px] text-neutral-400">{item.desc}</p>
                  </div>
                  <input
                    type="checkbox"
                    checked={(settings as any)[item.key]}
                    onChange={(e) => updateAndPersist({ [item.key]: e.target.checked })}
                    className="w-4 h-4 accent-blue-600 rounded cursor-pointer"
                  />
                </div>
              ))}
            </div>
          )}

          {/* PERSONAL DICTIONARY TAB */}
          {activeTab === 'dictionary' && (
            <div className="space-y-3">
              <p className="text-xs text-neutral-400">
                Words added here will not be marked as typos and will appear in suggestions.
              </p>

              <form onSubmit={handleAddWord} className="flex items-center space-x-2">
                <input
                  type="text"
                  placeholder="Enter word to add..."
                  value={newWord}
                  onChange={(e) => setNewWord(e.target.value)}
                  className="flex-1 px-3 py-1.5 text-xs rounded-lg border border-neutral-300 dark:border-neutral-700 bg-transparent focus:outline-hidden"
                />
                <button
                  type="submit"
                  className="px-3 py-1.5 bg-blue-600 hover:bg-blue-500 text-white rounded-lg text-xs font-semibold flex items-center space-x-1"
                >
                  <Plus size={14} />
                  <span>Add</span>
                </button>
              </form>

              <div className="max-h-48 overflow-y-auto space-y-1 pt-2">
                {settings.personalDictionary.length === 0 ? (
                  <p className="text-xs opacity-50 text-center py-4">No custom words added yet.</p>
                ) : (
                  settings.personalDictionary.map((word) => (
                    <div
                      key={word}
                      className="flex items-center justify-between px-3 py-2 rounded-lg bg-neutral-100 dark:bg-neutral-800/60 text-xs"
                    >
                      <span>{word}</span>
                      <button
                        type="button"
                        onClick={() => handleDeleteWord(word)}
                        className="text-red-400 hover:text-red-500"
                      >
                        <Trash2 size={13} />
                      </button>
                    </div>
                  ))
                )}
              </div>
            </div>
          )}

          {/* SHORTCUTS TAB */}
          {activeTab === 'shortcuts' && (
            <div className="space-y-3">
              <p className="text-xs text-neutral-400">
                Type an abbreviation and it will automatically expand into full text!
              </p>

              <form onSubmit={handleAddShortcut} className="grid grid-cols-2 gap-2">
                <input
                  type="text"
                  placeholder="Shortcut (e.g. brb)"
                  value={shortcutKey}
                  onChange={(e) => setShortcutKey(e.target.value)}
                  className="px-3 py-1.5 text-xs rounded-lg border border-neutral-300 dark:border-neutral-700 bg-transparent focus:outline-hidden"
                />
                <input
                  type="text"
                  placeholder="Expands to (e.g. Be right back)"
                  value={shortcutVal}
                  onChange={(e) => setShortcutVal(e.target.value)}
                  className="px-3 py-1.5 text-xs rounded-lg border border-neutral-300 dark:border-neutral-700 bg-transparent focus:outline-hidden"
                />
                <button
                  type="submit"
                  className="col-span-2 py-1.5 bg-blue-600 hover:bg-blue-500 text-white rounded-lg text-xs font-semibold flex items-center justify-center space-x-1"
                >
                  <Plus size={14} />
                  <span>Add Shortcut</span>
                </button>
              </form>

              <div className="max-h-48 overflow-y-auto space-y-1.5 pt-2">
                {Object.keys(settings.shortcuts).length === 0 ? (
                  <p className="text-xs opacity-50 text-center py-4">No text shortcuts added yet.</p>
                ) : (
                  Object.entries(settings.shortcuts).map(([key, val]) => (
                    <div
                      key={key}
                      className="flex items-center justify-between px-3 py-2 rounded-lg bg-neutral-100 dark:bg-neutral-800/60 text-xs"
                    >
                      <div className="flex items-center space-x-2">
                        <span className="font-mono font-bold text-blue-500">{key}</span>
                        <span className="opacity-40">→</span>
                        <span>{val}</span>
                      </div>
                      <button
                        type="button"
                        onClick={() => handleDeleteShortcut(key)}
                        className="text-red-400 hover:text-red-500"
                      >
                        <Trash2 size={13} />
                      </button>
                    </div>
                  ))
                )}
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};
