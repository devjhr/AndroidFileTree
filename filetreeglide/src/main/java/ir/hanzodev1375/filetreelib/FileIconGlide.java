package ir.hanzodev1375.filetreelib;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.widget.ImageView;

import androidx.annotation.NonNull;

import com.bumptech.glide.Glide;

import ir.hanzodev1375.filetreelib.core.TreeNode;
import ir.hanzodev1375.filetreelib.icons.BaseIconProvider;
import ir.hanzodev1375.filetreelib.icons.DefaultIconProvider;

public class FileIconGlide extends BaseIconProvider {

  private final DefaultIconProvider defaultProvider = new DefaultIconProvider();

  @Override
  public void loadIcon(
      @NonNull Context context, @NonNull TreeNode node, @NonNull ImageView target) {

    String path = getFilePath(node);

    if (isImageFile(node) && path != null) {
      Glide.with(context).load(path).into(target);
      return;
    }

    defaultProvider.loadIcon(context, node, target);
  }

  @Override
  public Drawable getIcon(@NonNull Context context, @NonNull TreeNode node) {
    return defaultProvider.getIcon(context, node);
  }

  @Override
  public Drawable getBadgeIcon(@NonNull Context context, @NonNull TreeNode node) {
    return defaultProvider.getBadgeIcon(context, node);
  }
}
