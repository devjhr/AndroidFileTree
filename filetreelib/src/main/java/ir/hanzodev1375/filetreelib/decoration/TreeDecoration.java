package ir.hanzodev1375.filetreelib.decoration;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.RecyclerView;
import ir.hanzodev1375.filetreelib.adapter.TreeAdapter;
import ir.hanzodev1375.filetreelib.core.TreeNode;
import ir.hanzodev1375.filetreelib.theme.ThemeManager;

/** Draws the vertical indentation guide lines (like VS Code's tree lines). */
public final class TreeDecoration extends RecyclerView.ItemDecoration {

  /**
   * Default cycling palette for rainbow indent guides, used until {@link #setRainbowColors(int[])}
   * overrides it. Depth 1 uses index 0, depth 2 uses index 1, and so on, wrapping back to index 0
   * once the palette is exhausted.
   */
  private static final int[] DEFAULT_RAINBOW_COLORS = {
    0xFF29C5D6, // cyan
    0xFFE05FEA, // magenta / orchid
    0xFFFFC940, // gold
    0xFF63D471, // green
    0xFFFF8A50, // orange
    0xFF7C83FD, // violet
  };

  private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final ThemeManager theme;
  private final int indentPx;
  private boolean showLines = true;
  private boolean rainbowLines = false;
  private int[] rainbowColors = DEFAULT_RAINBOW_COLORS;

  public TreeDecoration(@NonNull ThemeManager theme) {
    this.theme = theme;
    this.indentPx = theme.getIndentWidthPx();
    linePaint.setColor(theme.getTreeLineColor());
    linePaint.setStrokeWidth(theme.getTreeLineWidthPx());
    linePaint.setStyle(Paint.Style.STROKE);
  }

  public void setShowLines(boolean show) {
    this.showLines = show;
  }

  /**
   * Enables or disables rainbow indent guides. When enabled, each indentation level is drawn in a
   * different color cycled from the palette (see {@link #setRainbowColors(int[])}), similar to
   * bracket-pair colorization in VS Code. When disabled (the default), every guide line uses the
   * theme's single {@link ThemeManager#getTreeLineColor()}.
   */
  public void setRainbowIndentGuides(boolean enabled) {
    this.rainbowLines = enabled;
  }

  /**
   * @return whether rainbow indent guides are currently enabled. Off by default.
   */
  public boolean isRainbowIndentGuides() {
    return rainbowLines;
  }

  /**
   * Sets the palette rainbow indent guides cycle through. Depth 1 uses {@code colors[0]}, depth 2
   * uses {@code colors[1]}, wrapping back to {@code colors[0]} once the array is exhausted. Has no
   * visible effect unless {@link #setRainbowIndentGuides(boolean)} is enabled.
   *
   * @param colors non-empty array of ARGB colors
   */
  public void setRainbowColors(@NonNull int[] colors) {
    if (colors.length == 0) throw new IllegalArgumentException("colors must not be empty");
    this.rainbowColors = colors;
  }

  /**
   * @return the color palette currently used for rainbow indent guides.
   */
  @NonNull
  public int[] getRainbowColors() {
    return rainbowColors;
  }

  /** Resolves the paint color for the guide line at the given 1-based indent depth. */
  private int colorForDepth(int depth) {
    if (!rainbowLines) return theme.getTreeLineColor();
    return rainbowColors[(depth - 1) % rainbowColors.length];
  }

  @Override
  public void onDraw(
      @NonNull Canvas c, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
    if (!showLines) return;
    RecyclerView.Adapter<?> adapter = parent.getAdapter();
    if (!(adapter instanceof TreeAdapter)) return;
    TreeAdapter treeAdapter = (TreeAdapter) adapter;

    boolean isRtl = ViewCompat.getLayoutDirection(parent) == ViewCompat.LAYOUT_DIRECTION_RTL;
    int parentWidth = parent.getWidth();

    int childCount = parent.getChildCount();
    for (int i = 0; i < childCount; i++) {
      View child = parent.getChildAt(i);
      int position = parent.getChildAdapterPosition(child);
      if (position == RecyclerView.NO_ID) continue;

      TreeNode node = treeAdapter.getNode(position);
      if (node == null) continue;

      int depth = node.getDepth();
      if (depth <= 0) continue;

      int centerY = (child.getTop() + child.getBottom()) / 2;
      int top = child.getTop();
      int bottom = child.getBottom();

      for (int d = 1; d <= depth; d++) {
        float x =
            isRtl ? parentWidth - (d * indentPx + indentPx / 2f) : d * indentPx + indentPx / 2f;
        linePaint.setColor(colorForDepth(d));
        c.drawLine(x, top, x, bottom, linePaint);
      }

      // خط افقی connector — همرنگ عمیق ترین سطح (رنگ خودِ آیتم)
      float vx =
          isRtl
              ? parentWidth - (depth * indentPx + indentPx / 2f)
              : depth * indentPx + indentPx / 2f;
      float hEnd = isRtl ? parentWidth - (depth + 1) * indentPx : (depth + 1) * indentPx;
      linePaint.setColor(colorForDepth(depth));
      c.drawLine(vx, centerY, hEnd, centerY, linePaint);
    }
  }

  @Override
  public void getItemOffsets(
      @NonNull Rect outRect,
      @NonNull View view,
      @NonNull RecyclerView parent,
      @NonNull RecyclerView.State state) {
    outRect.set(0, 0, 0, 0);
  }
}
