# ─── Debugging: preserve line numbers in release stack traces ─────────────────
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ─── Input Method Service ─────────────────────────────────────────────────────
# The Android OS instantiates the IME service by name; it must never be renamed.
-keep public class com.babeltech.babelkey.MyKeyboardService { *; }

# ─── All app Activities & helper classes ──────────────────────────────────────
-keep public class com.babeltech.babelkey.** { *; }

# ─── Deprecated framework Keyboard / KeyboardView ─────────────────────────────
# These classes are used at runtime; R8 must not strip or rename them.
-keep class android.inputmethodservice.Keyboard { *; }
-keep class android.inputmethodservice.Keyboard$Key { *; }
-keep class android.inputmethodservice.KeyboardView { *; }
-keep interface android.inputmethodservice.KeyboardView$OnKeyboardActionListener { *; }

# ─── Android Speech Recognition (OS calls these via reflection) ───────────────
-keep class android.speech.** { *; }
-keep interface android.speech.RecognitionListener { *; }

# ─── JSON (used by translation / AI replies) ──────────────────────────────────
-keep class org.json.** { *; }

# ─── AppCompat / Material (transitive; already handled by consumer rules, ──────
#     but kept here defensively) ────────────────────────────────────────────────
-keep class androidx.appcompat.** { *; }
-keep class com.google.android.material.** { *; }

# ─── Enum: prevent R8 from removing synthetic $values / valueOf methods ────────
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ─── Parcelable (standard boilerplate) ────────────────────────────────────────
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# ─── Serializable (standard boilerplate) ──────────────────────────────────────
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# ─── View constructors (used by XML inflation) ────────────────────────────────
-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

# ─── Keep all onClick handlers referenced from XML ────────────────────────────
-keepclassmembers class * extends android.app.Activity {
    public void *(android.view.View);
}

# ─── Google ML Kit Smart Reply ────────────────────────────────────────────────
# ML Kit uses reflection to load native TFLite models and result parsers.
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_smart_reply.** { *; }
-dontwarn com.google.mlkit.**
# TensorFlow Lite (bundled inside ML Kit)
-keep class org.tensorflow.** { *; }
-dontwarn org.tensorflow.**