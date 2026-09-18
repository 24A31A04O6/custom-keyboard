import re

path = 'C:/Users/Asus/AndroidStudioProjects/keyboard new version/app/src/main/java/com/example/keyboard2/MyKeyboardService.java'
with open(path, 'r', encoding='utf-8', errors='ignore') as f:
    content = f.read()

lines = content.split('\n')
new_lines = []
for line in lines:
    if len(line) > 500:
        line = re.sub(r'\"[^\"]{100,}\"', '\"[CORRUPTED]\"', line)
        line = re.sub(r'//.*', '// [CORRUPTED COMMENT]', line)
        
        if len(line) > 500:
            line = line[:100] + '... [TRUNCATED]'
    new_lines.append(line)

with open(path, 'w', encoding='utf-8') as f:
    f.write('\n'.join(new_lines))

print('Cleaned up massive lines!')
