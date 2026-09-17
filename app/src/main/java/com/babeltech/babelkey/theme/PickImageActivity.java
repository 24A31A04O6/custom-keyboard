package com.babeltech.babelkey.theme;

import com.babeltech.babelkey.R;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.FileOutputStream;
import java.io.InputStream;

/**
 * PickImageActivity - image picker + crop/pan/zoom editor.
 *
 * Flow:
 *   1. Opens system image picker.
 *   2. Shows the selected image full-screen with a keyboard-sized crop rect overlay.
 *   3. User can pinch-to-zoom and drag to position which part appears behind the keyboard.
 *   4. Tapping "Next" crops that region and saves it to internal storage.
 */
public class PickImageActivity extends Activity {

    private static final int REQUEST_PICK_IMAGE = 1001;
    private static final String PREFS_NAME      = "MyBoardPrefs";
    private static final String PREF_BG_URI     = "customBackgroundUri";
    private static final String PREF_BG_ENABLED = "customBgEnabled";

    // Crop editor views
    private FrameLayout cropRoot;
    private ImageView   cropImageView;
    private View        cropOverlay;   // dark overlay with transparent rect
    private TextView    hintText;
    private TextView    btnNext;

    // The full bitmap loaded from the picker
    private Bitmap sourceBitmap;

    // Touch handling for pan & pinch-zoom
    private final Matrix imageMatrix = new Matrix();
    private float lastTouchX, lastTouchY;
    private float lastSpan = 0f;
    private int   pointerCount = 0;

    // Crop rectangle in screen coords (set after layout)
    private RectF cropRect = new RectF();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Launch picker on first create; crop editor shown in onActivityResult
        launchPicker();
    }

    private void launchPicker() {
        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("image/*");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                    | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            startActivityForResult(intent, REQUEST_PICK_IMAGE);
        } catch (Exception e) {
            try {
                Intent fallback = new Intent(Intent.ACTION_PICK,
                        android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                startActivityForResult(fallback, REQUEST_PICK_IMAGE);
            } catch (Exception e2) {
                Toast.makeText(this, "No gallery app found", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_PICK_IMAGE && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                // Persist permission
                try {
                    getContentResolver().takePersistableUriPermission(uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION);
                } catch (Exception ignored) {}

                // Load bitmap
                try {
                    InputStream is = getContentResolver().openInputStream(uri);
                    sourceBitmap = BitmapFactory.decodeStream(is);
                    if (is != null) is.close();
                } catch (Exception e) {
                    Toast.makeText(this, "Could not load image", Toast.LENGTH_SHORT).show();
                    finish();
                    return;
                }

                // Show crop editor
                showCropEditor();
                return;
            }
        }
        // Cancelled or failed
        if (resultCode == RESULT_CANCELED) {
            Toast.makeText(this, "No photo selected", Toast.LENGTH_SHORT).show();
        }
        finish();
    }

    // ─────────────────────────────────────────────────────────────────────
    //  CROP EDITOR
    // ────────────────────────────────────────────────────────────────────

    private void showCropEditor() {
        // Build UI programmatically so we don't need a layout file
        cropRoot = new FrameLayout(this);
        cropRoot.setBackgroundColor(0xFF000000);

        // Full-screen image underneath
        cropImageView = new ImageView(this);
        cropImageView.setScaleType(ImageView.ScaleType.MATRIX);
        cropRoot.addView(cropImageView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        // Semi-transparent dark overlay (crop window drawn in onDraw)
        cropOverlay = new CropOverlayView(this);
        cropRoot.addView(cropOverlay, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        // Hint text
        hintText = new TextView(this);
        hintText.setText("Pinch to scale, drag to move");
        hintText.setTextColor(0xFFFFFFFF);
        hintText.setTextSize(16f);
        hintText.setGravity(android.view.Gravity.CENTER);
        hintText.setBackgroundColor(0x88000000);
        hintText.setPadding(12, 8, 12, 8);
        FrameLayout.LayoutParams hintLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        hintLp.gravity = android.view.Gravity.CENTER_HORIZONTAL | android.view.Gravity.TOP;
        hintLp.topMargin = dpToPx(60);
        cropRoot.addView(hintText, hintLp);

        // "Next" button
        btnNext = new TextView(this);
        btnNext.setText("Next");
        btnNext.setTextColor(0xFF000000);
        btnNext.setTextSize(16f);
        btnNext.setGravity(android.view.Gravity.CENTER);
        btnNext.setBackgroundResource(android.R.drawable.btn_default);
        android.widget.LinearLayout.LayoutParams btnLayoutLp = new android.widget.LinearLayout.LayoutParams(
                dpToPx(120), dpToPx(40));
        FrameLayout.LayoutParams btnLp = new FrameLayout.LayoutParams(dpToPx(120), dpToPx(40));
        btnLp.gravity = android.view.Gravity.BOTTOM | android.view.Gravity.CENTER_HORIZONTAL;
        btnLp.bottomMargin = dpToPx(40);
        // Use a GradientDrawable for the button
        android.graphics.drawable.GradientDrawable btnBg = new android.graphics.drawable.GradientDrawable();
        btnBg.setColor(0xCCAABBCC);
        btnBg.setCornerRadius(dpToPx(20));
        btnNext.setBackground(btnBg);
        btnNext.setPadding(dpToPx(16), 0, dpToPx(16), 0);
        cropRoot.addView(btnNext, btnLp);

        btnNext.setOnClickListener(v -> saveCropAndFinish());

        setContentView(cropRoot);

        // Once layout is done, set up initial image position and crop rect
        cropRoot.getViewTreeObserver().addOnGlobalLayoutListener(
                new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                cropRoot.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                setupInitialImageMatrix();
            }
        });

        // Touch listener for pan and pinch zoom
        cropImageView.setOnTouchListener((v, event) -> {
            handleTouch(event);
            return true;
        });
        cropOverlay.setOnTouchListener((v, event) -> {
            handleTouch(event);
            return true;
        });
    }

    private void setupInitialImageMatrix() {
        if (sourceBitmap == null) return;

        int vw = cropRoot.getWidth();
        int vh = cropRoot.getHeight();

        // Crop rect = bottom 40% of screen (keyboard area)
        float cropH = vh * 0.40f;
        float cropTop = vh - cropH;
        cropRect.set(0, cropTop, vw, vh);

        // Scale bitmap to fill the crop window initially (like center-crop into crop rect)
        float bw = sourceBitmap.getWidth();
        float bh = sourceBitmap.getHeight();
        float scaleX = vw / bw;
        float scaleY = cropH / bh;
        float scale = Math.max(scaleX, scaleY);

        float scaledW = bw * scale;
        float scaledH = bh * scale;
        float dx = (vw - scaledW) / 2f;
        float dy = cropTop + (cropH - scaledH) / 2f;

        imageMatrix.reset();
        imageMatrix.setScale(scale, scale);
        imageMatrix.postTranslate(dx, dy);
        cropImageView.setImageBitmap(sourceBitmap);
        cropImageView.setImageMatrix(imageMatrix);

        ((CropOverlayView) cropOverlay).setCropRect(cropRect);
    }

    private void handleTouch(MotionEvent event) {
        int action = event.getActionMasked();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN:
                pointerCount = event.getPointerCount();
                lastTouchX = event.getX(0);
                lastTouchY = event.getY(0);
                if (pointerCount >= 2) {
                    lastSpan = getSpan(event);
                }
                break;

            case MotionEvent.ACTION_MOVE:
                float x0 = event.getX(0);
                float y0 = event.getY(0);
                if (event.getPointerCount() >= 2) {
                    // Pinch zoom
                    float span = getSpan(event);
                    if (lastSpan > 0) {
                        float scaleFactor = span / lastSpan;
                        float px = (event.getX(0) + event.getX(1)) / 2f;
                        float py = (event.getY(0) + event.getY(1)) / 2f;
                        imageMatrix.postScale(scaleFactor, scaleFactor, px, py);
                        cropImageView.setImageMatrix(imageMatrix);
                    }
                    lastSpan = span;
                    // Also pan
                    float mx = (event.getX(0) + event.getX(1)) / 2f;
                    float my = (event.getY(0) + event.getY(1)) / 2f;
                    imageMatrix.postTranslate(mx - lastTouchX, my - lastTouchY);
                    lastTouchX = mx;
                    lastTouchY = my;
                } else {
                    // Pan
                    float dx = x0 - lastTouchX;
                    float dy = y0 - lastTouchY;
                    imageMatrix.postTranslate(dx, dy);
                    lastTouchX = x0;
                    lastTouchY = y0;
                }
                cropImageView.setImageMatrix(imageMatrix);
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
                pointerCount = event.getPointerCount() - 1;
                lastSpan = 0f;
                break;
        }
    }

    private float getSpan(MotionEvent e) {
        if (e.getPointerCount() < 2) return 0f;
        float dx = e.getX(0) - e.getX(1);
        float dy = e.getY(0) - e.getY(1);
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    private void saveCropAndFinish() {
        if (sourceBitmap == null) { finish(); return; }

        // Map the crop rect back to bitmap coordinates using the inverse matrix
        Matrix inv = new Matrix();
        if (!imageMatrix.invert(inv)) {
            Toast.makeText(this, "Could not process image", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        float[] pts = {
                cropRect.left, cropRect.top,
                cropRect.right, cropRect.bottom
        };
        inv.mapPoints(pts);

        int left   = Math.max(0, (int) pts[0]);
        int top    = Math.max(0, (int) pts[1]);
        int right  = Math.min(sourceBitmap.getWidth(), (int) pts[2]);
        int bottom = Math.min(sourceBitmap.getHeight(), (int) pts[3]);

        if (right <= left || bottom <= top) {
            // Degenerate crop  just use full bitmap
            left = 0; top = 0;
            right = sourceBitmap.getWidth(); bottom = sourceBitmap.getHeight();
        }

        Bitmap cropped = Bitmap.createBitmap(sourceBitmap, left, top, right - left, bottom - top);

        // Save to internal storage
        try {
            java.io.File outFile = new java.io.File(getFilesDir(), "custom_bg.jpg");
            FileOutputStream fos = new FileOutputStream(outFile);
            cropped.compress(Bitmap.CompressFormat.JPEG, 90, fos);
            fos.close();
            cropped.recycle();
            sourceBitmap.recycle();
            sourceBitmap = null;
        } catch (Exception e) {
            Toast.makeText(this, "Failed to save image: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Enable the custom background flag
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        prefs.edit()
                .putBoolean(PREF_BG_ENABLED, true)
                .apply();

        Toast.makeText(this, "Background saved! Reopen keyboard to see it.", Toast.LENGTH_LONG).show();
        finish();
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Crop overlay: draws dark vignette + transparent crop window
    // ─────────────────────────────────────────────────────────────────────
    private static class CropOverlayView extends View {
        private final android.graphics.Paint dimPaint;
        private final android.graphics.Paint borderPaint;
        private RectF cropRect;

        CropOverlayView(android.content.Context ctx) {
            super(ctx);
            dimPaint = new android.graphics.Paint();
            dimPaint.setColor(0xAA000000);
            borderPaint = new android.graphics.Paint();
            borderPaint.setColor(0xFFFFFFFF);
            borderPaint.setStyle(android.graphics.Paint.Style.STROKE);
            borderPaint.setStrokeWidth(3f);
        }

        void setCropRect(RectF rect) {
            this.cropRect = rect;
            invalidate();
        }

        @Override
        protected void onDraw(android.graphics.Canvas canvas) {
            super.onDraw(canvas);
            if (cropRect == null) return;
            int w = getWidth(), h = getHeight();
            // Draw dim above crop rect
            canvas.drawRect(0, 0, w, cropRect.top, dimPaint);
            // Draw dim left / right at crop level (in case crop doesn't span full width)
            canvas.drawRect(0, cropRect.top, cropRect.left, cropRect.bottom, dimPaint);
            canvas.drawRect(cropRect.right, cropRect.top, w, cropRect.bottom, dimPaint);
            // Draw dim below crop rect
            canvas.drawRect(0, cropRect.bottom, w, h, dimPaint);
            // Draw crop border
            canvas.drawRect(cropRect, borderPaint);
        }
    }
}