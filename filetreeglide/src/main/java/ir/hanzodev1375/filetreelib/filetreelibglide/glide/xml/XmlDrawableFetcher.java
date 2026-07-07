package ir.hanzodev1375.filetreelibglide.glide.xml;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.Priority;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.data.DataFetcher;

import java.io.File;

import ir.hanzodev1375.filetreelibglide.drawablexml.AlphaPatternDrawable;
import ir.hanzodev1375.filetreelibglide.drawablexml.DrawableXmlLoader;

class XmlDrawableFetcher implements DataFetcher<Drawable> {

  private final Context context;
  private final File file;

  XmlDrawableFetcher(Context context, File file) {
    this.context = context.getApplicationContext();
    this.file = file;
  }

  public Context getContext() {
    return context;
  }

  @Override
  public void loadData(
      @NonNull Priority priority, @NonNull DataCallback<? super Drawable> callback) {
    try {
      // بارگذاری Drawable اصلی
      Drawable original = DrawableXmlLoader.load(context, file);
      if (original == null) {
        callback.onLoadFailed(new Exception("Failed to load XML drawable from " + file));
        return;
      }

      // ایجاد لایه‌بندی: AlphaPatternDrawable در زیر، Drawable اصلی در بالا
      Drawable[] layers = {new AlphaPatternDrawable(), original};
      LayerDrawable layered = new LayerDrawable(layers);
      callback.onDataReady(layered);
    } catch (Exception e) {
      callback.onLoadFailed(e);
    }
  }

  @Override
  public void cleanup() {}

  @Override
  public void cancel() {}

  @NonNull
  @Override
  public Class<Drawable> getDataClass() {
    return Drawable.class;
  }

  @NonNull
  @Override
  public DataSource getDataSource() {
    return DataSource.LOCAL;
  }
}
