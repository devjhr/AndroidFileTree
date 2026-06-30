package ir.hanzodev1375.filetreelib.glide;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import androidx.annotation.NonNull;
import com.bumptech.glide.Glide;
import com.bumptech.glide.Registry;
import com.bumptech.glide.annotation.GlideModule;
import com.bumptech.glide.module.LibraryGlideModule;
import com.caverock.androidsvg.SVG;
import android.graphics.drawable.PictureDrawable;
import ir.hanzodev1375.filetreelib.glide.apkicon.ApkIconModelLoader;
import ir.hanzodev1375.filetreelib.glide.svg.SvgDrawableTranscoder;
import ir.hanzodev1375.filetreelib.glide.music.Mp3CoverLoaderFactory;
import java.io.InputStream;
import ir.hanzodev1375.filetreelib.glide.svg.SvgDecoder;

@GlideModule
public class AppGlideCompat extends LibraryGlideModule {
  @Override
  public void registerComponents(
      @NonNull Context context, @NonNull Glide glide, @NonNull Registry registry) {
    registry.append(String.class, Bitmap.class, new Mp3CoverLoaderFactory());
    registry
        .register(SVG.class, PictureDrawable.class, new SvgDrawableTranscoder())
        .append(InputStream.class, SVG.class, new SvgDecoder());
    registry.append(String.class, Drawable.class, new ApkIconModelLoader.Factory(context));
  }
}
