package ir.hanzodev1375.filetreelib.icons;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import ir.hanzodev1375.filetreelib.R;
import ir.hanzodev1375.filetreelib.core.FileIconHelper;
import ir.hanzodev1375.filetreelib.core.TreeNode;
import ir.hanzodev1375.filetreelib.model.FilePayload;
import ir.hanzodev1375.filetreelib.utils.TreeUtils;

public class DefaultIconProvider extends BaseIconProvider {

  @Nullable
  @Override
  public Drawable getIcon(@NonNull Context context, @NonNull TreeNode node) {

    if (node.isLoadingPlaceholder()) {
      return null;
    }

    if (node.isVirtual()) {
      return ContextCompat.getDrawable(context, resolveVirtualIconRes(node));
    }

    FilePayload payloads = node.getPayload(FilePayload.class);
    String filePath = payloads != null ? payloads.getAbsolutePath() : node.getName();
    if (filePath == null || filePath.isEmpty()) filePath = node.getName();

    FileIconHelper helper = new FileIconHelper(filePath);
    helper.setDynamicFolderEnabled(true);
    helper.setEnvironmentEnabled(true);

    return ContextCompat.getDrawable(context, helper.getFileIcon());
  }

  @Override
  public void loadIcon(
      @NonNull Context context, @NonNull TreeNode node, @NonNull ImageView target) {

    if (node.isLoadingPlaceholder()) {
      target.setImageDrawable(null);
      return;
    }

    if (node.isVirtual()) {
      target.setImageResource(resolveVirtualIconRes(node));
      return;
    }

    FilePayload payload = node.getPayload(FilePayload.class);

    String filePath = payload != null ? payload.getAbsolutePath() : node.getName();

    if (filePath == null || filePath.isEmpty()) {
      filePath = node.getName();
    }

    FileIconHelper helper = new FileIconHelper(filePath);
    helper.setDynamicFolderEnabled(true);
    helper.setEnvironmentEnabled(true);
    target.setImageResource(helper.getFileIcon());
  }

  /**
   * Icon resource for a {@link TreeNode#TYPE_VIRTUAL} node — these are synthetic groups (e.g. a
   * "Gradle Scripts" node gathering files from several real directories), so unlike real files/
   * folders they have no filesystem path for {@link FileIconHelper} to resolve an icon from.
   *
   * <p>Callers set the icon via {@link TreeNode#setTag(Object)} with a {@code @DrawableRes int}
   * (see {@code FileTreeView#addVirtualGroup}); falls back to a plain folder icon if no tag was
   * set.
   */
  private int resolveVirtualIconRes(@NonNull TreeNode node) {
    Object tag = node.getTag();
    return tag instanceof Integer ? (Integer) tag : R.drawable.ic_filetree_folder;
  }

  @Nullable
  @Override
  public Drawable getBadgeIcon(@NonNull Context context, @NonNull TreeNode node) {

    FilePayload p = node.getPayload(FilePayload.class);

    if (p == null) {
      return null;
    }

    if (p.getErrorCount() > 0) {
      return ContextCompat.getDrawable(context, R.drawable.ic_filetree_badge_error);
    }

    if (p.isBookmarked()) {
      return ContextCompat.getDrawable(context, R.drawable.ic_filetree_badge_bookmark);
    }

    if (p.isGitModified()) {
      return ContextCompat.getDrawable(context, R.drawable.ic_filetree_badge_git_modified);
    }

    if (p.isGitStaged()) {
      return ContextCompat.getDrawable(context, R.drawable.ic_filetree_badge_git_staged);
    }

    if (p.isGitConflicted()) {
      return ContextCompat.getDrawable(context, R.drawable.ic_filetree_badge_git_conflict);
    }

    return null;
  }
}
