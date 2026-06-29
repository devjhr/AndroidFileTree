package ir.hanzodev1375.filetreelib.theme;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import ir.hanzodev1375.filetreelib.R;

/** Resolves theme attributes for the TreeView. */
public final class ThemeManager {

  @ColorInt private final int textColor;
  @ColorInt private final int selectedBg;
  @ColorInt private final int hoveredBg;
  @ColorInt private final int treeLineColor;
  @ColorInt private final int searchHighlightColor;
  @ColorInt private final int gitModifiedColor;
  @ColorInt private final int gitAddedColor;
  @ColorInt private final int gitDeletedColor;
  @ColorInt private final int errorColor;

  private final int indentWidthPx;
  private final int iconSizePx;
  private final float treeLineWidthPx;

  public ThemeManager(@NonNull Context context) {
    TypedArray a =
        context.obtainStyledAttributes(
            new int[] {
              R.attr.tv_textColor,
              R.attr.tv_selectedBackground,
              R.attr.tv_hoveredBackground,
              R.attr.tv_treeLineColor,
              R.attr.tv_searchHighlightColor,
              R.attr.tv_gitModifiedColor,
              R.attr.tv_gitAddedColor,
              R.attr.tv_gitDeletedColor,
              R.attr.tv_errorColor,
            });

    textColor = a.getColor(0, Color.parseColor("#CCCCCC"));
    selectedBg = a.getColor(1, Color.parseColor("#094771"));
    hoveredBg = a.getColor(2, Color.parseColor("#2A2D2E"));
    treeLineColor = a.getColor(3, Color.parseColor("#404040"));
    searchHighlightColor = a.getColor(4, Color.parseColor("#613315"));
    gitModifiedColor = a.getColor(5, Color.parseColor("#E2C08D"));
    gitAddedColor = a.getColor(6, Color.parseColor("#81B88B"));
    gitDeletedColor = a.getColor(7, Color.parseColor("#C74E39"));
    errorColor = a.getColor(8, Color.parseColor("#F44747"));
    a.recycle();

    float density = context.getResources().getDisplayMetrics().density;
    indentWidthPx = (int) (20 * density);
    iconSizePx = (int) (20 * density);
    treeLineWidthPx = 1 * density;
  }

  @ColorInt
  public int getTextColor() {
    return textColor;
  }

  @ColorInt
  public int getSelectedBg() {
    return selectedBg;
  }

  @ColorInt
  public int getHoveredBg() {
    return hoveredBg;
  }

  @ColorInt
  public int getTreeLineColor() {
    return treeLineColor;
  }

  @ColorInt
  public int getSearchHighlightColor() {
    return searchHighlightColor;
  }

  @ColorInt
  public int getGitModifiedColor() {
    return gitModifiedColor;
  }

  @ColorInt
  public int getGitAddedColor() {
    return gitAddedColor;
  }

  @ColorInt
  public int getGitDeletedColor() {
    return gitDeletedColor;
  }

  @ColorInt
  public int getErrorColor() {
    return errorColor;
  }

  public int getIndentWidthPx() {
    return indentWidthPx;
  }

  public int getIconSizePx() {
    return iconSizePx;
  }

  public float getTreeLineWidthPx() {
    return treeLineWidthPx;
  }
}
