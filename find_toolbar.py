path = 'C:/Users/Asus/AndroidStudioProjects/keyboard new version/app/src/main/res/layout/keyboard_view.xml'
with open(path, 'r', encoding='utf-8', errors='ignore') as f:
    lines = f.readlines()
for i, l in enumerate(lines):
    if 'toolbar_strip' in l:
        print(f'{i+1}: {l.rstrip()}')
