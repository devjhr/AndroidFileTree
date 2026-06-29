package ir.hanzodev1375.filetreelib.icons;

import android.content.Context;
import android.graphics.drawable.Drawable;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import ir.hanzodev1375.filetreelib.R;
import ir.hanzodev1375.filetreelib.core.FileIconHelper;
import ir.hanzodev1375.filetreelib.core.TreeNode;
import ir.hanzodev1375.filetreelib.model.FilePayload;
import ir.hanzodev1375.filetreelib.utils.TreeUtils;

/** Default icon provider. Maps node type and file extension to built-in drawables. */
public class DefaultIconProvider implements IconProvider {
  private FileIconHelper icon;

  @Nullable
  @Override
  public Drawable getIcon(@NonNull Context context, @NonNull TreeNode node) {
    if (node.isLoadingPlaceholder()) return null;
    if (node.isFolder()) {
      return ContextCompat.getDrawable(
          context, R.drawable.ic_filetree_folder);
    }
    // File: choose by extension
    FilePayload payload = node.getPayload(FilePayload.class);
    String ext = payload != null ? payload.getExtension() : TreeUtils.getExtension(node.getName());
    icon = new FileIconHelper(ext);
    icon.setDynamicFolderEnabled(true);
    icon.setEnvironmentEnabled(true);
    return ContextCompat.getDrawable(context, icon.getFileIcon());
  }

  @Nullable
  @Override
  public Drawable getBadgeIcon(@NonNull Context context, @NonNull TreeNode node) {
    FilePayload p = node.getPayload(FilePayload.class);
    if (p == null) return null;
    if (p.getErrorCount() > 0) return ContextCompat.getDrawable(context, R.drawable.ic_badge_error);
    if (p.isBookmarked()) return ContextCompat.getDrawable(context, R.drawable.ic_badge_bookmark);
    if (p.isGitModified())
      return ContextCompat.getDrawable(context, R.drawable.ic_badge_git_modified);
    if (p.isGitStaged()) return ContextCompat.getDrawable(context, R.drawable.ic_badge_git_staged);
    if (p.isGitConflicted())
      return ContextCompat.getDrawable(context, R.drawable.ic_badge_git_conflict);
    return null;
  }
}
