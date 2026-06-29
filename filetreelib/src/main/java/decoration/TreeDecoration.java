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

  private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final ThemeManager theme;
  private final int indentPx;
  private boolean showLines = true;

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
        c.drawLine(x, top, x, bottom, linePaint);
      }

      // خط افقی connector
      float vx =
          isRtl
              ? parentWidth - (depth * indentPx + indentPx / 2f)
              : depth * indentPx + indentPx / 2f;
      float hEnd = isRtl ? parentWidth - (depth + 1) * indentPx : (depth + 1) * indentPx;
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
