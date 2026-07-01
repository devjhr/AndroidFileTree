package ir.hanzodev1375.filetreelib.drawablexml;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;

/**
 * Draws a light/dark checker pattern behind icon previews so that transparent or
 * near-background-colored shapes remain visible instead of blending in and appearing as parsing
 * failures.
 */
public final class AlphaPatternDrawable extends Drawable {

  private static final int LIGHT = 0xFFE0E0E0;
  private static final int DARK = 0xFFC0C0C0;
  private static final int CELL_SIZE_PX = 8;

  private final Paint lightPaint = new Paint();
  private final Paint darkPaint = new Paint();

  public AlphaPatternDrawable() {
    lightPaint.setColor(LIGHT);
    darkPaint.setColor(DARK);
  }

  @Override
  public void draw(@NonNull Canvas canvas) {
    Rect bounds = getBounds();
    int cols = (bounds.width() / CELL_SIZE_PX) + 1;
    int rows = (bounds.height() / CELL_SIZE_PX) + 1;

    for (int row = 0; row < rows; row++) {
      for (int col = 0; col < cols; col++) {
        boolean isDark = (row + col) % 2 == 0;
        int left = bounds.left + col * CELL_SIZE_PX;
        int top = bounds.top + row * CELL_SIZE_PX;
        canvas.drawRect(
            left, top, left + CELL_SIZE_PX, top + CELL_SIZE_PX, isDark ? darkPaint : lightPaint);
      }
    }
  }

  @Override
  public void setAlpha(int alpha) {}

  @Override
  public void setColorFilter(ColorFilter colorFilter) {}

  @Override
  public int getOpacity() {
    return PixelFormat.OPAQUE;
  }
}
