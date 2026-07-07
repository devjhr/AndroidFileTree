package ir.hanzodev1375.filetreelibglide;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.PictureDrawable;
import android.view.View;
import android.widget.ImageView;

import androidx.annotation.NonNull;

import com.bumptech.glide.Glide;
import ir.hanzodev1375.filetreelib.R;
import ir.hanzodev1375.filetreelib.filetreelibglide.glide.xml.VectorModel;
import ir.hanzodev1375.filetreelib.icons.BaseIconProvider;
import ir.hanzodev1375.filetreelib.icons.DefaultIconProvider;
import ir.hanzodev1375.filetreelib.core.TreeNode;
import java.io.File;
import ir.hanzodev1375.filetreelibglide.drawablexml.DrawableXmlLoader;
import ir.hanzodev1375.filetreelibglide.drawablexml.AlphaPatternDrawable;

public class FileIconGlide extends BaseIconProvider {

  private final DefaultIconProvider defaultProvider = new DefaultIconProvider();

  @Override
  public void loadIcon(
      @NonNull Context context, @NonNull TreeNode node, @NonNull ImageView target) {
    target.setBackground(null);
    String path = getFilePath(node);

    if (isRasterImageFile(node) && path != null) {
      Glide.with(context).load(path).into(target);
      return;
    }

    if (isSvgFile(node) && path != null) {
      target.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
      Glide.with(context).as(PictureDrawable.class).load(new File(path)).into(target);
      return;
    }

    if (isVectorXmlFile(node) && path != null) {
      Glide.with(context)
          .as(Drawable.class)
          .load(new VectorModel(new File(path), context))
          .placeholder(R.drawable.ic_filetree_xml)
          .into(target);
    }
    if (node.getName().toLowerCase().endsWith(".pdf")) {
      if (path != null) {
        Glide.with(context)
            .asBitmap()
            .load(path)
            .placeholder(R.drawable.ic_filetree_pdf)
            .error(R.drawable.ic_filetree_pdf)
            .into(target);
      }
    }

    if (isApkFile(node) && path != null) {
      Glide.with(context)
          .as(Drawable.class)
          .load(path)
          .error(defaultProvider.getIcon(context, node))
          .into(target);
      return;
    }

    if (isAudioFile(node) && path != null) {
      Glide.with(context)
          .asBitmap()
          .load(path)
          .error(defaultProvider.getIcon(context, node))
          .into(target);
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
