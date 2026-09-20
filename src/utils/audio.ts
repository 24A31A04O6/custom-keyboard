import { SoundPackId } from '../types';

class SoundController {
  private ctx: AudioContext | null = null;
  private audioElements: Map<SoundPackId, HTMLAudioElement> = new Map();

  private getAudioContext(): AudioContext | null {
    if (typeof window === 'undefined') return null;
    if (!this.ctx) {
      const AudioCtx = window.AudioContext || (window as unknown as { webkitAudioContext: typeof AudioContext }).webkitAudioContext;
      if (AudioCtx) {
        this.ctx = new AudioCtx();
      }
    }
    if (this.ctx && this.ctx.state === 'suspended') {
      this.ctx.resume().catch(() => {});
    }
    return this.ctx;
  }

  constructor() {
    if (typeof window !== 'undefined') {
      const soundFiles: [SoundPackId, string][] = [
        [SoundPackId.MECHANICAL, '/sounds/sound_mechanical.wav'],
        [SoundPackId.TYPEWRITER, '/sounds/sound_typewriter.wav'],
        [SoundPackId.BUBBLE, '/sounds/sound_bubble.wav'],
        [SoundPackId.GAMING, '/sounds/sound_gaming.wav'],
        [SoundPackId.SOFT, '/sounds/sound_soft.wav'],
        [SoundPackId.CRYSTAL, '/sounds/sound_crystal.wav'],
      ];

      soundFiles.forEach(([id, url]) => {
        try {
          const audio = new Audio(url);
          audio.preload = 'auto';
          this.audioElements.set(id, audio);
        } catch {
          // Fallback to Web Audio synthesis
        }
      });
    }
  }

  public playKeySound(pack: SoundPackId, volume: number) {
    if (pack === SoundPackId.OFF || volume <= 0) return;

    const vol = Math.max(0.01, Math.min(1, volume / 100));

    // Try playing HTMLAudio clone
    const audio = this.audioElements.get(pack);
    if (audio) {
      try {
        const clone = audio.cloneNode() as HTMLAudioElement;
        clone.volume = vol;
        const playPromise = clone.play();
        if (playPromise) {
          playPromise.catch(() => {
            this.synthesizeSound(pack, vol);
          });
          return;
        }
      } catch {
        // Fallback to Web Audio synthesis
      }
    }

    this.synthesizeSound(pack, vol);
  }

  private synthesizeSound(pack: SoundPackId, vol: number) {
    const ctx = this.getAudioContext();
    if (!ctx) return;

    try {
      const now = ctx.currentTime;
      const osc = ctx.createOscillator();
      const gain = ctx.createGain();

      switch (pack) {
        case SoundPackId.MECHANICAL: {
          // Sharp click + thud
          osc.type = 'triangle';
          osc.frequency.setValueAtTime(320, now);
          osc.frequency.exponentialRampToValueAtTime(80, now + 0.04);
          gain.gain.setValueAtTime(vol * 0.9, now);
          gain.gain.exponentialRampToValueAtTime(0.001, now + 0.05);
          osc.connect(gain);
          gain.connect(ctx.destination);
          osc.start(now);
          osc.stop(now + 0.05);
          break;
        }

        case SoundPackId.TYPEWRITER: {
          // Metallic clack
          osc.type = 'square';
          osc.frequency.setValueAtTime(800, now);
          osc.frequency.exponentialRampToValueAtTime(140, now + 0.035);
          gain.gain.setValueAtTime(vol * 0.7, now);
          gain.gain.exponentialRampToValueAtTime(0.001, now + 0.04);
          osc.connect(gain);
          gain.connect(ctx.destination);
          osc.start(now);
          osc.stop(now + 0.04);
          break;
        }

        case SoundPackId.BUBBLE: {
          // Rising pop
          osc.type = 'sine';
          osc.frequency.setValueAtTime(380, now);
          osc.frequency.exponentialRampToValueAtTime(820, now + 0.05);
          gain.gain.setValueAtTime(vol * 0.8, now);
          gain.gain.exponentialRampToValueAtTime(0.001, now + 0.06);
          osc.connect(gain);
          gain.connect(ctx.destination);
          osc.start(now);
          osc.stop(now + 0.06);
          break;
        }

        case SoundPackId.GAMING: {
          // Crisp high-tech tick
          osc.type = 'sawtooth';
          osc.frequency.setValueAtTime(600, now);
          osc.frequency.exponentialRampToValueAtTime(220, now + 0.03);
          gain.gain.setValueAtTime(vol * 0.6, now);
          gain.gain.exponentialRampToValueAtTime(0.001, now + 0.035);
          osc.connect(gain);
          gain.connect(ctx.destination);
          osc.start(now);
          osc.stop(now + 0.035);
          break;
        }

        case SoundPackId.SOFT: {
          // Low gentle thud
          osc.type = 'sine';
          osc.frequency.setValueAtTime(160, now);
          osc.frequency.exponentialRampToValueAtTime(60, now + 0.04);
          gain.gain.setValueAtTime(vol * 0.5, now);
          gain.gain.exponentialRampToValueAtTime(0.001, now + 0.05);
          osc.connect(gain);
          gain.connect(ctx.destination);
          osc.start(now);
          osc.stop(now + 0.05);
          break;
        }

        case SoundPackId.CRYSTAL: {
          // Shimmering chime
          osc.type = 'sine';
          osc.frequency.setValueAtTime(1200, now);
          osc.frequency.exponentialRampToValueAtTime(750, now + 0.08);
          gain.gain.setValueAtTime(vol * 0.6, now);
          gain.gain.exponentialRampToValueAtTime(0.001, now + 0.09);
          osc.connect(gain);
          gain.connect(ctx.destination);
          osc.start(now);
          osc.stop(now + 0.09);
          break;
        }

        default:
          break;
      }
    } catch {
      // Audio context error ignored
    }
  }

  public triggerHaptic(enabled: boolean, durationMs = 12) {
    if (!enabled) return;
    try {
      if (typeof window !== 'undefined' && 'vibrate' in navigator) {
        navigator.vibrate(durationMs);
      }
    } catch {
      // Vibrate not permitted
    }
  }
}

export const soundController = new SoundController();
