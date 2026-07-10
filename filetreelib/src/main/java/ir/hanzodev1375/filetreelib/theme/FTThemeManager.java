package ir.hanzodev1375.filetreelib.theme;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;
import com.google.android.material.color.MaterialColors;
import ir.hanzodev1375.filetreelib.R;

/** Resolves theme attributes for the TreeView. */
public class FTThemeManager {

  @ColorInt private int textColor;
  @ColorInt private int selectedBg;
  @ColorInt private int hoveredBg;
  @ColorInt private int treeLineColor;
  @ColorInt private int searchHighlightColor;
  @ColorInt private int gitModifiedColor;
  @ColorInt private int gitAddedColor;
  @ColorInt private int gitDeletedColor;
  @ColorInt private int errorColor;
  @ColorInt private int panelBackgroundColor;
  @ColorInt private int panelColorFilterColor;
  @ColorInt private int panelTextColor;
  @ColorInt private int panelDividerColors;
  @ColorInt private int revealHighlightColor;
  @NonNull private Context context;
  private int indentWidthPx;
  private int iconSizePx;
  private float treeLineWidthPx;

  public FTThemeManager(@NonNull Context context) {
    this.context = context;
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
              R.attr.panelBackgrounds,
              R.attr.panelColorFilter,
              R.attr.panelTextColor,
              R.attr.panelDividerColor
            });

    textColor = a.getColor(0, get(R.attr.colorOnSurface));
    selectedBg = a.getColor(1, get(R.attr.colorSecondaryContainer));
    hoveredBg = a.getColor(2, get(getAlphaColor(R.attr.colorSurfaceContainerHigh)));
    treeLineColor = a.getColor(3, get(R.attr.colorOutlineVariant));
    searchHighlightColor = a.getColor(4, get(R.attr.colorTertiaryContainer));
    gitModifiedColor = a.getColor(5, get(R.attr.colorTertiary));
    gitAddedColor = a.getColor(6, get(R.attr.colorPrimary));
    gitDeletedColor = a.getColor(7, get(R.attr.colorError));
    errorColor = a.getColor(8, get(R.attr.colorError));
    panelBackgroundColor = a.getColor(9, get(R.attr.colorSurface));
    panelColorFilterColor = a.getColor(10, get(R.attr.colorPrimary));
    panelTextColor = a.getColor(11, get(R.attr.colorPrimary));
    panelDividerColors = a.getColor(12, get(R.attr.colorOnSurface));
    a.recycle();

    // Not backed by a custom XML attr (unlike the colors above) — a translucent tint derived from
    // the theme's tertiary color, matching how indentWidthPx/iconSizePx are computed rather than
    // declared. Used for the temporary "reveal" flash after expandToPath() finds a row; override
    // with setRevealHighlightColor() if a specific color is wanted instead.
    revealHighlightColor = ColorUtils.setAlphaComponent(get(R.attr.colorTertiary), 180);

    float density = context.getResources().getDisplayMetrics().density;
    indentWidthPx = (int) (20 * density);
    iconSizePx = (int) (20 * density);
    treeLineWidthPx = 1 * density;
  }

  int get(int id) {
    return MaterialColors.getColor(context, id, 0);
  }

  int getAlphaColor(int color) {
    return ColorUtils.setAlphaComponent(color, 128);
  }

  @ColorInt
  public int getTextColor() {
    return textColor;
  }

  /**
   * A dimmer variant of {@link #getTextColor()} for secondary text — e.g. the description shown
   * after a node's name (see {@link
   * ir.hanzodev1375.filetreelib.model.FilePayload#getDescription()}). Always derived from the
   * current text color, so it stays in sync if the theme changes.
   */
  @ColorInt
  public int getSecondaryTextColor() {
    return ColorUtils.setAlphaComponent(textColor, 128);
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

  public void setTextColor(int textColor) {
    this.textColor = textColor;
  }

  public void setSelectedBg(int selectedBg) {
    this.selectedBg = selectedBg;
  }

  public void setHoveredBg(int hoveredBg) {
    this.hoveredBg = hoveredBg;
  }

  public void setTreeLineColor(int treeLineColor) {
    this.treeLineColor = treeLineColor;
  }

  public void setSearchHighlightColor(int searchHighlightColor) {
    this.searchHighlightColor = searchHighlightColor;
  }

  public void setGitModifiedColor(int gitModifiedColor) {
    this.gitModifiedColor = gitModifiedColor;
  }

  public void setGitAddedColor(int gitAddedColor) {
    this.gitAddedColor = gitAddedColor;
  }

  public void setGitDeletedColor(int gitDeletedColor) {
    this.gitDeletedColor = gitDeletedColor;
  }

  public void setErrorColor(int errorColor) {
    this.errorColor = errorColor;
  }

  public int getPanelBackgroundColor() {
    return this.panelBackgroundColor;
  }

  public void setPanelBackgroundColor(int panelBackgroundColor) {
    this.panelBackgroundColor = panelBackgroundColor;
  }

  public int getPanelColorFilterColor() {
    return this.panelColorFilterColor;
  }

  public void setPanelColorFilterColor(int panelColorFilterColor) {
    this.panelColorFilterColor = panelColorFilterColor;
  }

  public int getPanelTextColor() {
    return this.panelTextColor;
  }

  public void setPanelTextColor(int panelTextColor) {
    this.panelTextColor = panelTextColor;
  }

  public int getPanelDividerColors() {
    return this.panelDividerColors;
  }

  public void setPanelDividerColors(int panelDividerColors) {
    this.panelDividerColors = panelDividerColors;
  }

  /**
   * @return the tint used for the temporary "reveal" highlight flash (see {@link
   *     ir.hanzodev1375.filetreelib.widget.FileTreeView#highlightNode}).
   */
  @ColorInt
  public int getRevealHighlightColor() {
    return revealHighlightColor;
  }

  public void setRevealHighlightColor(int revealHighlightColor) {
    this.revealHighlightColor = revealHighlightColor;
  }
}
