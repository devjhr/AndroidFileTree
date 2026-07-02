package ir.hanzodev1375.filetreelib.zoom;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Holds the pinch-to-zoom state for {@link ir.hanzodev1375.filetreelib.widget.TwoDScrollView}.
 *
 * <p>Responsible for maintaining the zoom state (on/off, allowed range, and current percentage). It
 * doesn't touch any Views itself — it just holds the state and notifies the outside via {@link
 * OnZoomChangeListener} when the zoom percentage changes.
 */
public final class ZoomManager {

  /** Notified whenever the current zoom level changes (including clamping). */
  public interface OnZoomChangeListener {
    void onZoomChanged(float oldFactor, float newFactor, float focusX, float focusY);
  }

  public static final int DEFAULT_MIN_ZOOM_PERCENT = 50;
  public static final int DEFAULT_MAX_ZOOM_PERCENT = 300;

  private boolean zoomMod = false;
  private int minZoomPercent = DEFAULT_MIN_ZOOM_PERCENT;
  private int maxZoomPercent = DEFAULT_MAX_ZOOM_PERCENT;
  private float currentZoomFactor = 1f;

  @Nullable private OnZoomChangeListener listener;

  /**
   * Enables or disables pinch-to-zoom gesture handling.
   *
   * <p>Disabling turns off pinch-to-zoom gesture handling but does not change the current zoom
   * level — use {@link #resetZoom()} to return to 100%.
   *
   * @param zoomMod true to allow the user to pinch-zoom the tree
   */
  public void setZoomMod(boolean zoomMod) {
    this.zoomMod = zoomMod;
  }

  /**
   * @return whether pinch-to-zoom is currently enabled.
   */
  public boolean isZoomMod() {
    return zoomMod;
  }

  /**
   * Sets the allowed zoom range, as percentages (100 = original size).
   *
   * @param minPercent minimum zoom, e.g. 50 for 50%
   * @param maxPercent maximum zoom, e.g. 300 for 300%
   * @throws IllegalArgumentException if the range is invalid
   */
  public void setZoomScale(int minPercent, int maxPercent) {
    if (minPercent <= 0 || maxPercent <= 0 || minPercent > maxPercent) {
      throw new IllegalArgumentException(
          "Invalid zoom scale range: min=" + minPercent + ", max=" + maxPercent);
    }
    this.minZoomPercent = minPercent;
    this.maxZoomPercent = maxPercent;
    // Re-clamp the current factor in case it now falls outside the new range.
    setCurrentZoomFactor(currentZoomFactor, -1f, -1f);
  }

  /**
   * @return {@code [minPercent, maxPercent]}
   */
  @NonNull
  public int[] getZoomScale() {
    return new int[] {minZoomPercent, maxZoomPercent};
  }

  public int getMinZoomScale() {
    return minZoomPercent;
  }

  public int getMaxZoomScale() {
    return maxZoomPercent;
  }

  public float getMinZoomFactor() {
    return minZoomPercent / 100f;
  }

  public float getMaxZoomFactor() {
    return maxZoomPercent / 100f;
  }

  /**
   * @return current zoom as a factor, where 1f == 100%.
   */
  public float getCurrentZoomFactor() {
    return currentZoomFactor;
  }

  /**
   * @return current zoom rounded to the nearest percent, where 100 == original size.
   */
  public int getCurrentZoomScale() {
    return Math.round(currentZoomFactor * 100f);
  }

  /** Programmatically sets the zoom level, clamped to [minPercent, maxPercent]. */
  public void setCurrentZoomScale(int percent) {
    setCurrentZoomFactor(percent / 100f, -1f, -1f);
  }

  /** Resets zoom back to 100%. */
  public void resetZoom() {
    setCurrentZoomFactor(1f, -1f, -1f);
  }

  /**
   * Internal setter used by the touch-handling code, also carries the pinch focus point so
   * listeners (e.g. TwoDScrollView) know which point on screen to keep fixed while re-scaling.
   * focusX/focusY of -1 mean "no particular focus point" (e.g. a programmatic call).
   */
  public void setCurrentZoomFactor(float factor, float focusX, float focusY) {
    float clamped = clamp(factor, getMinZoomFactor(), getMaxZoomFactor());
    float old = currentZoomFactor;
    currentZoomFactor = clamped;
    if (listener != null && Float.compare(old, clamped) != 0) {
      listener.onZoomChanged(old, clamped, focusX, focusY);
    }
  }

  public void setOnZoomChangeListener(@Nullable OnZoomChangeListener listener) {
    this.listener = listener;
  }

  private static float clamp(float v, float min, float max) {
    return Math.max(min, Math.min(max, v));
  }
}
