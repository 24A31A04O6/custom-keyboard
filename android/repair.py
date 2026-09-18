import re

path = 'C:/Users/Asus/AndroidStudioProjects/keyboard new version/app/src/main/java/com/example/keyboard2/MyKeyboardService.java'
with open(path, 'r', encoding='utf-8', errors='ignore') as f:
    lines = f.readlines()

def replace_corrupted(lines):
    for i, line in enumerate(lines):
        if 'enabled ? "[CORRUPTED]" : "[CORRUPTED]"' in line:
            lines[i] = line.replace('enabled ? "[CORRUPTED]" : "[CORRUPTED]"', 'enabled ? "Disable Photo BG" : "Enable Photo BG"')
        elif 'SOUND_MECHANICAL: return "[CORRUPTED]"' in line:
            lines[i] = line.replace('SOUND_MECHANICAL: return "[CORRUPTED]"', 'SOUND_MECHANICAL: return "Mechanical"')
        elif 'SOUND_TYPEWRITER: return "[CORRUPTED]"' in line:
            lines[i] = line.replace('SOUND_TYPEWRITER: return "[CORRUPTED]"', 'SOUND_TYPEWRITER: return "Typewriter"')
        elif 'SOUND_BUBBLE:     return "[CORRUPTED]"' in line:
            lines[i] = line.replace('SOUND_BUBBLE:     return "[CORRUPTED]"', 'SOUND_BUBBLE:     return "Bubble"')
        elif 'SOUND_GAMING:     return "[CORRUPTED]"' in line:
            lines[i] = line.replace('SOUND_GAMING:     return "[CORRUPTED]"', 'SOUND_GAMING:     return "Gaming"')
        elif 'SOUND_SOFT:       return "[CORRUPTED]"' in line:
            lines[i] = line.replace('SOUND_SOFT:       return "[CORRUPTED]"', 'SOUND_SOFT:       return "Soft"')
        elif 'SOUND_CRYSTAL:    return "[CORRUPTED]"' in line:
            lines[i] = line.replace('SOUND_CRYSTAL:    return "[CORRUPTED]"', 'SOUND_CRYSTAL:    return "Crystal"')
        elif 'default:               return "[CORRUPTED]"' in line:
            lines[i] = line.replace('default:               return "[CORRUPTED]"', 'default:               return "Off"')
        elif 'detectedOtp + "[CORRUPTED]"' in line:
            lines[i] = line.replace('detectedOtp + "[CORRUPTED]"', 'detectedOtp + " (Tap)"')
        elif 'suggestion = "[CORRUPTED]" +' in line:
            lines[i] = line.replace('suggestion = "[CORRUPTED]" +', 'suggestion = "" +')
        elif 'addStatRow("[CORRUPTED]", true);' in line:
            lines[i] = line.replace('addStatRow("[CORRUPTED]", true);', 'addStatRow("Keyboard Stats", true);')
        elif 'e.getKey() + "[CORRUPTED]"' in line:
            lines[i] = line.replace('e.getKey() + "[CORRUPTED]"', 'e.getKey() + ": "')
        elif '(isPinned ? "[CORRUPTED]" : "")' in line:
            lines[i] = line.replace('(isPinned ? "[CORRUPTED]" : "")', '(isPinned ? "P: " : "")')
        elif '+ "[CORRUPTED]" : text' in line:
            lines[i] = line.replace('+ "[CORRUPTED]" : text', '+ "..." : text')
        elif 'del.setText("[CORRUPTED]")' in line:
            lines[i] = line.replace('del.setText("[CORRUPTED]")', 'del.setText("X")')
        elif 'textShortcuts.put("gm",   "[CORRUPTED]")' in line:
            lines[i] = line.replace('textShortcuts.put("gm",   "[CORRUPTED]")', 'textShortcuts.put("gm",   "Good morning")')
        elif 'textShortcuts.put("gn",   "[CORRUPTED]")' in line:
            lines[i] = line.replace('textShortcuts.put("gn",   "[CORRUPTED]")', 'textShortcuts.put("gn",   "Good night")')
        elif 'textShortcuts.put("ty",   "[CORRUPTED]")' in line:
            lines[i] = line.replace('textShortcuts.put("ty",   "[CORRUPTED]")', 'textShortcuts.put("ty",   "Thank you")')
        elif 'textShortcuts.put("omw",  "[CORRUPTED]")' in line:
            lines[i] = line.replace('textShortcuts.put("omw",  "[CORRUPTED]")', 'textShortcuts.put("omw",  "On my way")')
        elif 'textShortcuts.put("np",   "[CORRUPTED]")' in line:
            lines[i] = line.replace('textShortcuts.put("np",   "[CORRUPTED]")', 'textShortcuts.put("np",   "No problem")')
        elif 'isListening ? "[CORRUPTED]" : "[CORRUPTED]"' in line:
            lines[i] = line.replace('isListening ? "[CORRUPTED]" : "[CORRUPTED]"', 'isListening ? "[ REC ]" : "[ MIC ]"')
        elif 'message = "[CORRUPTED]"' in line:
            lines[i] = line.replace('message = "[CORRUPTED]"', 'message = "Success"')
        elif 'replies.add("[CORRUPTED]");' in line:
            lines[i] = line.replace('"[CORRUPTED]"', '"Okay"')
        elif 'Log.w(TAG, dictName + "[CORRUPTED]");' in line:
            lines[i] = line.replace('"[CORRUPTED]"', '" not found"')
        elif 'Log.e(TAG, dictName + "[CORRUPTED]", e);' in line:
            lines[i] = line.replace('"[CORRUPTED]"', '" error"')
        elif '"Failed to read " + dictName + "[CORRUPTED]"' in line:
            lines[i] = line.replace('"[CORRUPTED]"', '""')
        elif 'Log.w(TAG, "[CORRUPTED]");' in line:
            lines[i] = line.replace('"[CORRUPTED]"', '"Error"')
        elif 'Log.e(TAG, "[CORRUPTED]", e);' in line:
            lines[i] = line.replace('"[CORRUPTED]"', '"Error"')
        elif 'quickPhrases.add("[CORRUPTED]");' in line:
            lines[i] = line.replace('"[CORRUPTED]"', '"Okay"')
        # A catch-all for remaining [CORRUPTED]
        lines[i] = lines[i].replace('"[CORRUPTED]"', '"..."')
    return lines

new_lines = replace_corrupted(lines)
with open(path, 'w', encoding='utf-8') as f:
    f.writelines(new_lines)
print("Repaired corrupted constants!")
