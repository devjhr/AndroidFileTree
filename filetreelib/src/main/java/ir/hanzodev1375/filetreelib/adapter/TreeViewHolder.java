package ir.hanzodev1375.filetreelib.adapter;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.text.SpannableString;
import android.text.style.BackgroundColorSpan;
import android.view.View;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.ViewFlipper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.widget.ImageViewCompat;
import androidx.recyclerview.widget.RecyclerView;
import ir.hanzodev1375.filetreelib.R;
import ir.hanzodev1375.filetreelib.core.FileIconHelper;
import ir.hanzodev1375.filetreelib.core.TreeNode;
import ir.hanzodev1375.filetreelib.icons.IconProvider;
import ir.hanzodev1375.filetreelib.model.FilePayload;
import ir.hanzodev1375.filetreelib.model.SearchResult;
import ir.hanzodev1375.filetreelib.theme.FTThemeManager;
import android.graphics.drawable.Drawable;
import android.view.ViewGroup;
import ir.hanzodev1375.filetreelib.utils.TreeUtils;

public final class TreeViewHolder extends RecyclerView.ViewHolder {

  private static final int CHILD_CHECKBOX = 0;
  private static final int CHILD_ARROW = 1;
  private static final int CHILD_LOADING = 2;

  final View itemRoot;
  final ViewFlipper arrowSwitcher;
  final ImageView ivArrow;
  final ImageView ivIcon;
  final ImageView ivIconBadge;
  final TextView tvName;
  final TextView tvDescription;
  final TextView tvBadge;
  final ImageView ivBadgeIcon;
  final View indentSpacer;
  final CheckBox checkbox;
  boolean showItem = false;
  final ImageView creatorFile, creatorFolder;
  private int iconArrow;

  public TreeViewHolder(@NonNull View itemView) {
    super(itemView);
    itemRoot = itemView;
    arrowSwitcher = itemView.findViewById(R.id.tv_arrow_switcher);
    ivArrow = itemView.findViewById(R.id.tv_arrow);
    ivIcon = itemView.findViewById(R.id.tv_icon);
    ivIconBadge = itemView.findViewById(R.id.tv_icon_badge);
    tvName = itemView.findViewById(R.id.tv_name);
    tvDescription = itemView.findViewById(R.id.tv_description);
    tvBadge = itemView.findViewById(R.id.tv_badge);
    ivBadgeIcon = itemView.findViewById(R.id.tv_badge_icon);
    indentSpacer = itemView.findViewById(R.id.tv_indent);
    checkbox = itemView.findViewById(R.id.tv_checkbox);
    creatorFile = itemView.findViewById(R.id.filecreator);
    creatorFolder = itemView.findViewById(R.id.foldercreator);
  }

  public void bind(
      @NonNull TreeNode node,
      @NonNull FTThemeManager theme,
      @NonNull IconProvider iconProvider,
      @Nullable SearchResult searchResult,
      @NonNull View.OnClickListener clickListener,
      @NonNull View.OnLongClickListener longClickListener,
      @NonNull View.OnClickListener arrowClickListener,
      boolean isCut,
      boolean selectionMode) {

    int indentPx = theme.getIndentWidthPx() * node.getDepth();
    ViewGroup.LayoutParams lp = indentSpacer.getLayoutParams();
    lp.width = indentPx;
    indentSpacer.setLayoutParams(lp);
    if (selectionMode) {
      arrowSwitcher.setVisibility(View.VISIBLE);
      showChild(CHILD_CHECKBOX);
      checkbox.setChecked(node.isSelected());
    } else if (node.isFile()) {
      arrowSwitcher.setVisibility(View.INVISIBLE);
    } else {
      arrowSwitcher.setVisibility(node.hasChildren() ? View.VISIBLE : View.INVISIBLE);
      applyLoadingState(node);
      ivArrow.setRotation(node.isExpanded() ? 90f : 0f);
    }
    if (iconArrow == 0) {
      iconArrow = R.drawable.ic_filetree_arrow_right;
    }
    iconArrow = R.drawable.ic_filetree_arrow_right;
    if (iconArrow == 0) {
      iconArrow = R.drawable.ic_filetree_arrow_right;
    }
    ivArrow.setImageResource(iconArrow);
    ivArrow.setOnClickListener(selectionMode ? null : arrowClickListener);
    if (!showItem) {
      creatorFolder.setVisibility(View.GONE);
      creatorFile.setVisibility(View.GONE);
    } else {
      creatorFolder.setVisibility(node.isFolder() ? View.VISIBLE : View.GONE);
      creatorFile.setVisibility(node.isFolder() ? View.VISIBLE : View.GONE);
    }

    try {
      iconProvider.loadIcon(itemView.getContext(), node, ivIcon);
      ivIcon.setVisibility(View.VISIBLE);
    } catch (Exception e) {
      ivIcon.setImageResource(R.drawable.ic_filetree_document);
      ivIcon.setVisibility(View.VISIBLE);
    }

    if (searchResult != null && searchResult.getHighlightedName() != null) {
      tvName.setText(searchResult.getHighlightedName());
    } else if (searchResult != null && !searchResult.getMatchRanges().isEmpty()) {
      SpannableString spannable = new SpannableString(node.getName());
      for (SearchResult.MatchRange r : searchResult.getMatchRanges()) {
        if (r.end <= node.getName().length()) {
          spannable.setSpan(
              new BackgroundColorSpan(theme.getSearchHighlightColor()),
              r.start,
              r.end,
              SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
      }
      tvName.setText(spannable);
    } else {
      tvName.setText(node.getName());
    }

    FilePayload payload = node.getPayload(FilePayload.class);
    if (payload != null) {
      if (payload.getErrorCount() > 0) {
        tvName.setTextColor(theme.getErrorColor());
      } else if (payload.isGitModified()) {
        tvName.setTextColor(theme.getGitModifiedColor());
      } else if ((payload.getGitStatus() & FilePayload.GIT_ADDED) != 0) {
        tvName.setTextColor(theme.getGitAddedColor());
      } else {
        tvName.setTextColor(theme.getTextColor());
      }
    } else {
      tvName.setTextColor(theme.getTextColor());
    }

    if (payload != null && payload.getDescription() != null && !payload.getDescription().isEmpty()) {
      tvDescription.setText(payload.getDescription());
      tvDescription.setTextColor(theme.getSecondaryTextColor());
      tvDescription.setVisibility(View.VISIBLE);
    } else {
      tvDescription.setVisibility(View.GONE);
    }

    if (payload != null && payload.getBadgeColor() != 0) {
      ImageViewCompat.setImageTintList(ivIconBadge, ColorStateList.valueOf(payload.getBadgeColor()));
      ivIconBadge.setVisibility(View.VISIBLE);
    } else {
      ivIconBadge.setVisibility(View.GONE);
    }

    itemRoot.setAlpha(isCut ? 0.4f : 1f);
    itemRoot.setBackgroundColor(node.isSelected() ? theme.getSelectedBg() : Color.TRANSPARENT);

    if (payload != null && payload.getBadge() != null) {
      tvBadge.setText(payload.getBadge());
      tvBadge.setVisibility(View.VISIBLE);
    } else {
      tvBadge.setVisibility(View.GONE);
    }

    Drawable badgeIcon = iconProvider.getBadgeIcon(itemView.getContext(), node);
    if (badgeIcon != null) {
      ivBadgeIcon.setImageDrawable(badgeIcon);
      ivBadgeIcon.setVisibility(View.VISIBLE);
    } else {
      ivBadgeIcon.setVisibility(View.GONE);
    }

    itemRoot.setOnClickListener(clickListener);
    itemRoot.setOnLongClickListener(longClickListener);
  }

  public void updateSelection(boolean selected, int selectedBg, boolean selectionMode) {
    itemRoot.setBackgroundColor(selected ? selectedBg : android.graphics.Color.TRANSPARENT);
    if (selectionMode) {
      arrowSwitcher.setVisibility(View.VISIBLE);
      showChild(CHILD_CHECKBOX);
      checkbox.setChecked(selected);
    }
  }

  /**
   * Applies (or clears) the temporary "reveal" highlight — a brief flash used to draw the user's
   * eye to a row right after it's been found and scrolled to programmatically (see {@link
   * ir.hanzodev1375.filetreelib.widget.FileTreeView#highlightNode}). Distinct from selection: once
   * the highlight clears, the row falls back to its selected/transparent background as normal.
   */
  public void setRevealHighlighted(
      boolean highlighted, int highlightColor, boolean selected, int selectedBg) {
    itemRoot.setBackgroundColor(
        highlighted ? highlightColor : (selected ? selectedBg : Color.TRANSPARENT));
  }

  public void updateArrow(@NonNull TreeNode node, boolean selectionMode) {
    if (selectionMode) return;
    if (node.isFile()) {
      arrowSwitcher.setVisibility(View.INVISIBLE);
      return;
    }
    arrowSwitcher.setVisibility(node.hasChildren() ? View.VISIBLE : View.INVISIBLE);
    applyLoadingState(node);
    ivArrow.setRotation(node.isExpanded() ? 90f : 0f);
  }

  private void applyLoadingState(@NonNull TreeNode node) {
    showChild(node.isLazyLoadPending() ? CHILD_LOADING : CHILD_ARROW);
    ivArrow.setClickable(!node.isLazyLoadPending());
  }

  private void showChild(int index) {
    if (arrowSwitcher.getDisplayedChild() != index) {
      arrowSwitcher.setDisplayedChild(index);
    }
  }

  public void updateIcon(
      @NonNull android.content.Context context,
      @NonNull TreeNode node,
      @NonNull IconProvider iconProvider) {
    try {
      iconProvider.loadIcon(context, node, ivIcon);
    } catch (Exception e) {
      ivIcon.setImageResource(ir.hanzodev1375.filetreelib.R.drawable.ic_filetree_document);
    }
  }

  public void setShowIconFolderAndFile(boolean show) {
    this.showItem = show;
  }

  public int getIconArrow() {
    return this.iconArrow;
  }

  public void setIconArrow(int iconArrow) {
    this.iconArrow = iconArrow;
  }

  public View getItemRoot() {
    return itemRoot;
  }

  public ViewFlipper getArrowSwitcher() {
    return arrowSwitcher;
  }

  public ImageView getIvArrow() {
    return ivArrow;
  }

  public ImageView getIvIcon() {
    return ivIcon;
  }

  public TextView getTvName() {
    return tvName;
  }

  public TextView getTvDescription() {
    return tvDescription;
  }

  public ImageView getIvIconBadge() {
    return ivIconBadge;
  }

  public TextView getTvBadge() {
    return tvBadge;
  }

  public ImageView getIvBadgeIcon() {
    return ivBadgeIcon;
  }

  public View getIndentSpacer() {
    return indentSpacer;
  }

  public CheckBox getCheckbox() {
    return checkbox;
  }

  public ImageView getCreatorFile() {
    return creatorFile;
  }

  public ImageView getCreatorFolder() {
    return creatorFolder;
  }

  public boolean isShowItem() {
    return showItem;
  }

  public boolean isSelected() {
    return checkbox != null && checkbox.isChecked();
  }

  public boolean isLoading() {
    return arrowSwitcher != null && arrowSwitcher.getDisplayedChild() == CHILD_LOADING;
  }

  public boolean isArrowVisible() {
    return arrowSwitcher != null
        && arrowSwitcher.getVisibility() == View.VISIBLE
        && arrowSwitcher.getDisplayedChild() == CHILD_ARROW;
  }
}
