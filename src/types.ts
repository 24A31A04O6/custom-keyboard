export type KeyboardMode = 'qwerty' | 'symbols' | 'symbols_shift' | 'numpad' | 'emoji';

export type ImeAction =
  | 'actionDone'
  | 'actionSearch'
  | 'actionSend'
  | 'actionGo'
  | 'actionNext'
  | 'actionNone'
  | 'actionUnspecified'
  | 'IME_ACTION_SEARCH'
  | 'IME_ACTION_SEND'
  | 'IME_ACTION_GO'
  | 'IME_ACTION_NEXT'
  | 'IME_ACTION_DONE'
  | 'IME_ACTION_NONE'
  | 'IME_ACTION_UNSPECIFIED';

export type EditorInputType = 'text' | 'number' | 'phone' | 'email' | 'password' | 'visible_password' | 'search';

export enum ShiftState {
  OFF = 0,
  ON = 1,
  CAPS = 2,
}

export enum ThemeId {
  LIGHT = 0,
  DARK = 1,
  BLACK = 2,
  AMOLED = 3,
  BLUE = 4,
  GREEN = 5,
  CUSTOM = 6,
}

export enum SoundPackId {
  OFF = 0,
  MECHANICAL = 1,
  TYPEWRITER = 2,
  BUBBLE = 3,
  GAMING = 4,
  SOFT = 5,
  CRYSTAL = 6,
}

export interface ToolbarItem {
  id: string;
  label: string;
  iconName: string;
}

export interface ClipItem {
  id: string;
  text: string;
  pinned: boolean;
  createdAt: number;
}

export interface AppSettings {
  autoCorrectEnabled: boolean;
  autoCapsEnabled: boolean;
  spellCheckEnabled: boolean;
  grammarCheckEnabled: boolean;
  smartComposeEnabled: boolean;
  aiRepliesEnabled: boolean;
  blockOffensiveEnabled: boolean;
  swipeTypingEnabled: boolean;
  voiceTypingEnabled: boolean;
  soundPack: SoundPackId;
  soundVolume: number; // 0 to 100
  hapticEnabled: boolean;
  selectedTheme: ThemeId;
  customBackgroundUrl: string | null;
  customAccentColor?: string | null;
  keyboardHeightDp: number; // 240 to 380 px
  keyFontSize?: number; // 12 to 24 px (default: 16)
  toolbarButtons: string[]; // IDs of enabled toolbar buttons in order
  personalDictionary: string[];
  shortcuts: Record<string, string>; // shortcut -> replacement
  quickPhrases: string[];
}

export interface TypingActivityEvent {
  timestamp: number;
  chars: number;
  words: number;
}

export interface KeyboardStats {
  totalWords: number;
  totalCharacters: number;
  sessionStartTime: number;
  lastActiveTime?: number;
  activeTypingSeconds?: number;
  recentActivity?: TypingActivityEvent[];
  correctionCounts: Record<string, number>;
  dailyWords: Record<string, number>;
}
