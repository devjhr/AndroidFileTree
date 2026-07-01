package ir.hanzodev1375.filetreelib.adapter;

import android.text.SpannableString;
import android.text.style.BackgroundColorSpan;
import android.view.View;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.ViewFlipper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;
import ir.hanzodev1375.filetreelib.R;
import ir.hanzodev1375.filetreelib.core.FileIconHelper;
import ir.hanzodev1375.filetreelib.core.TreeNode;
import ir.hanzodev1375.filetreelib.icons.IconProvider;
import ir.hanzodev1375.filetreelib.model.FilePayload;
import ir.hanzodev1375.filetreelib.model.SearchResult;
import ir.hanzodev1375.filetreelib.theme.ThemeManager;
import android.graphics.drawable.Drawable;
import android.view.ViewGroup;
import ir.hanzodev1375.filetreelib.utils.TreeUtils;

public final class TreeViewHolder extends RecyclerView.ViewHolder {

  final View itemRoot;
  final ViewFlipper arrowSwitcher;
  final ImageView ivArrow;
  final ImageView ivIcon;
  final TextView tvName;
  final TextView tvBadge;
  final ImageView ivBadgeIcon;
  final View indentSpacer;
  final CheckBox checkbox;
  boolean showItem = false;
  final ImageView creatorFile, creatorFolder;

  public TreeViewHolder(@NonNull View itemView) {
    super(itemView);
    itemRoot = itemView;
    arrowSwitcher = itemView.findViewById(R.id.tv_arrow_switcher);
    ivArrow = itemView.findViewById(R.id.tv_arrow);
    ivIcon = itemView.findViewById(R.id.tv_icon);
    tvName = itemView.findViewById(R.id.tv_name);
    tvBadge = itemView.findViewById(R.id.tv_badge);
    ivBadgeIcon = itemView.findViewById(R.id.tv_badge_icon);
    indentSpacer = itemView.findViewById(R.id.tv_indent);
    checkbox = itemView.findViewById(R.id.tv_checkbox);
    creatorFile = itemView.findViewById(R.id.filecreator);
    creatorFolder = itemView.findViewById(R.id.foldercreator);
  }

  public void bind(
      @NonNull TreeNode node,
      @NonNull ThemeManager theme,
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
      checkbox.setVisibility(View.VISIBLE);
      checkbox.setChecked(node.isSelected());
    } else {
      checkbox.setVisibility(View.GONE);
    }
    if (node.isFile()) {
      arrowSwitcher.setVisibility(View.INVISIBLE);
    } else {
      arrowSwitcher.setVisibility(node.hasChildren() ? View.VISIBLE : View.INVISIBLE);
      applyLoadingState(node);
      ivArrow.setRotation(node.isExpanded() ? 90f : 0f);
      ivArrow.setOnClickListener(selectionMode ? null : arrowClickListener);
    }
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

    // Name
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

    // Git/error color
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

    itemRoot.setAlpha(isCut ? 0.4f : 1f);
    itemRoot.setBackgroundColor(
        node.isSelected() ? theme.getSelectedBg() : android.graphics.Color.TRANSPARENT);

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
      checkbox.setVisibility(View.VISIBLE);
      checkbox.setChecked(selected);
    } else {
      checkbox.setVisibility(View.GONE);
    }
  }

  public void updateArrow(@NonNull TreeNode node) {
    if (node.isFile()) {
      arrowSwitcher.setVisibility(View.INVISIBLE);
      return;
    }
    arrowSwitcher.setVisibility(node.hasChildren() ? View.VISIBLE : View.INVISIBLE);
    applyLoadingState(node);
    ivArrow.setRotation(node.isExpanded() ? 90f : 0f);
  }

  /**
   * Flips {@link #arrowSwitcher} between the arrow icon and the inline loading spinner
   * depending on {@link TreeNode#isLazyLoadPending()}. Also disables the arrow's click
   * target while loading so the user can't re-trigger the load mid-flight.
   */
  private void applyLoadingState(@NonNull TreeNode node) {
    int wantedChild = node.isLazyLoadPending() ? 1 : 0;
    if (arrowSwitcher.getDisplayedChild() != wantedChild) {
      arrowSwitcher.setDisplayedChild(wantedChild);
    }
    ivArrow.setClickable(!node.isLazyLoadPending());
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
}
