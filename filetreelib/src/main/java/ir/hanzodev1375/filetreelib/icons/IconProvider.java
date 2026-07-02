package ir.hanzodev1375.filetreelib.icons;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import ir.hanzodev1375.filetreelib.core.TreeNode;

public interface IconProvider {

  void loadIcon(
      @NonNull Context context,
      @NonNull TreeNode node,
      @NonNull ImageView target
  );

  @Nullable
  Drawable getIcon(
      @NonNull Context context,
      @NonNull TreeNode node
  );

  @Nullable
  default Drawable getBadgeIcon(
      @NonNull Context context,
      @NonNull TreeNode node
  ) {
    return null;
  }
}