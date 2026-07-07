package ir.hanzodev1375.filetreelib.filetreelibglide.glide.pdf;

import android.content.Context;
import android.graphics.Bitmap;

import androidx.annotation.NonNull;

import com.bumptech.glide.load.model.ModelLoader;
import com.bumptech.glide.load.model.ModelLoaderFactory;
import com.bumptech.glide.load.model.MultiModelLoaderFactory;

public class ThumbnailBuilderFactory implements ModelLoaderFactory<String, Bitmap> {
  private Context mContext;

  public ThumbnailBuilderFactory(@NonNull Context mContext) {
    this.mContext = mContext;
  }

  @NonNull
  @Override
  public ModelLoader<String, Bitmap> build(@NonNull MultiModelLoaderFactory multiFactory) {
    return new ThumbnailBuilder(mContext);
  }

  @Override
  public void teardown() {
    // empty

  }
}
