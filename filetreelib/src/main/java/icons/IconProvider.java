package ir.hanzodev1375.filetreelib.icons;

import android.content.Context;
import android.graphics.drawable.Drawable;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ir.hanzodev1375.filetreelib.core.TreeNode;

/** Returns the icon Drawable for a given TreeNode. */
public interface IconProvider {
  @Nullable
  Drawable getIcon(@NonNull Context context, @NonNull TreeNode node);

  /** Optional: returns an overlay badge drawable (git/error/bookmark). */
  @Nullable
  default Drawable getBadgeIcon(@NonNull Context context, @NonNull TreeNode node) {
    return null;
  }
}
