package ir.hanzodev1375.filetreelib.icons;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import ir.hanzodev1375.filetreelib.core.TreeNode;
import ir.hanzodev1375.filetreelib.model.FilePayload;

public abstract class BaseIconProvider implements IconProvider {

  protected boolean isRasterImageFile(@NonNull TreeNode node) {
    String name = node.getName().toLowerCase();
    return name.endsWith(".png")
        || name.endsWith(".jpg")
        || name.endsWith(".jpeg")
        || name.endsWith(".webp")
        || name.endsWith(".gif")
        || name.endsWith(".bmp");
  }

  protected boolean isSvgFile(@NonNull TreeNode node) {
    return node.getName().toLowerCase().endsWith(".svg");
  }

  protected boolean isVectorXmlFile(@NonNull TreeNode node) {
    return node.getName().toLowerCase().endsWith(".xml");
  }

  protected boolean isApkFile(@NonNull TreeNode node) {
    return node.getName().toLowerCase().endsWith(".apk");
  }

  protected boolean isAudioFile(@NonNull TreeNode node) {
    return node.getName().toLowerCase().endsWith(".mp3");
  }

  @Nullable
  protected String getFilePath(@NonNull TreeNode node) {
    FilePayload payload = node.getPayload(FilePayload.class);
    return payload != null ? payload.getAbsolutePath() : null;
  }

  @NonNull
  protected String getExtension(@NonNull TreeNode node) {
    String name = node.getName();
    int dot = name.lastIndexOf('.');

    if (dot < 0 || dot == name.length() - 1) {
      return "";
    }

    return name.substring(dot + 1).toLowerCase();
  }

  @Override
  public void loadIcon(
      @NonNull Context context, @NonNull TreeNode node, @NonNull ImageView target) {
    target.setImageDrawable(getIcon(context, node));
  }

  @Nullable
  @Override
  public abstract Drawable getIcon(@NonNull Context context, @NonNull TreeNode node);

  @Nullable
  @Override
  public Drawable getBadgeIcon(@NonNull Context context, @NonNull TreeNode node) {
    return null;
  }
}
