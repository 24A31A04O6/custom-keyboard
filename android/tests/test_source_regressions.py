"""Static source/resource checks; not a substitute for Gradle or device tests."""
import re
import unittest
from pathlib import Path
from xml.etree import ElementTree

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "app/src/main"
JAVA = MAIN / "java/com/babeltech/babelkey"
ANDROID = "{http://schemas.android.com/apk/res/android}"


class SourceRegressionTests(unittest.TestCase):
    def test_all_resource_xml_is_well_formed(self):
        for path in (MAIN / "res").rglob("*.xml"):
            with self.subTest(path=path.name):
                ElementTree.parse(path)

    def test_keyboard_rows_fit_available_width(self):
        for path in (MAIN / "res/xml").glob("keyboard_*.xml"):
            keyboard = ElementTree.parse(path).getroot()
            default_width = keyboard.get(ANDROID + "keyWidth")
            for index, row in enumerate(keyboard.findall("Row")):
                width = 0.0
                for key in row.findall("Key"):
                    width += float(key.get(ANDROID + "keyWidth", default_width).removesuffix("%p"))
                    gap = key.get(ANDROID + "horizontalGap", "0dp")
                    if gap.endswith("%p"):
                        width += float(gap.removesuffix("%p"))
                    else:
                        self.assertEqual(gap, "0dp")
                with self.subTest(layout=path.name, row=index):
                    self.assertLessEqual(width, 100.001)

    def test_numpad_has_alphabet_exit(self):
        keyboard = ElementTree.parse(MAIN / "res/xml/keyboard_numpad.xml").getroot()
        codes = {key.get(ANDROID + "codes") for key in keyboard.iter("Key")}
        self.assertIn("-3", codes)

    def test_emoji_catalog_contains_valid_nonempty_categories(self):
        source = (JAVA / "emoji/EmojiKeyboardView.java").read_text()
        self.assertNotIn("\ufffd", source)
        catalog = source.split("CATEGORY_EMOJIS = {", 1)[1].split("};", 1)[0]
        categories = re.findall(r"codePoints\((.*?)\)", catalog, re.S)
        self.assertEqual(len(categories), 8)
        for category in categories:
            points = [int(value, 16) for value in re.findall(r"0x[0-9A-F]+", category)]
            self.assertGreaterEqual(len(points), 20)
            self.assertEqual(len(points), len(set(points)))
            for point in points:
                self.assertNotEqual(point, 0xFFFD)
                self.assertFalse(0xD800 <= point <= 0xDFFF)
                chr(point).encode("utf-8")

    def test_overlay_container_is_returned(self):
        source = (JAVA / "core/MyKeyboardService.java").read_text()
        create = source.split("public View onCreateInputView()", 1)[1].split("@Override public void onStartInput", 1)[0]
        self.assertIn("return root;", create)
        self.assertNotIn("removeView(keyboardRoot)", create)
        qwerty = source.split("private void switchToQwerty()", 1)[1].split("private void switchToSymbols", 1)[0]
        self.assertIn("flipTo(FLIPPER_KB)", qwerty)

    def test_visible_toolbar_actions_are_labeled(self):
        root = ElementTree.parse(MAIN / "res/layout/keyboard_view.xml").getroot()
        strip = next(view for view in root.iter() if view.get(ANDROID + "id") == "@+id/toolbar_strip")
        self.assertEqual(strip.get(ANDROID + "layout_height"), "48dp")
        for button in strip:
            self.assertTrue(button.get(ANDROID + "contentDescription"))
            self.assertEqual(button.get(ANDROID + "layout_height"), "48dp")
        tools = next(view for view in root.iter() if view.get(ANDROID + "id") == "@+id/toolbar_scroll_legacy")
        self.assertNotEqual(tools.get(ANDROID + "layout_height"), "0dp")


if __name__ == "__main__":
    unittest.main()
