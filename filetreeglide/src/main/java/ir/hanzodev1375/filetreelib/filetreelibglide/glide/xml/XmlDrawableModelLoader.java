package ir.hanzodev1375.filetreelibglide.glide.xml;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;

import androidx.annotation.NonNull;

import com.bumptech.glide.load.Options;
import com.bumptech.glide.load.data.DataFetcher;
import com.bumptech.glide.load.model.ModelLoader;
import com.bumptech.glide.load.model.ModelLoaderFactory;
import com.bumptech.glide.load.model.MultiModelLoaderFactory;

import com.bumptech.glide.signature.ObjectKey;
import java.io.File;

import ir.hanzodev1375.filetreelibglide.drawablexml.AlphaPatternDrawable;
import ir.hanzodev1375.filetreelibglide.drawablexml.DrawableXmlLoader;

/**
 * ModelLoader برای بارگذاری فایل‌های XML Drawable (vector, shape, selector, layer-list, ...) و
 * ترکیب آن‌ها با AlphaPatternDrawable به‌عنوان پس‌زمینه‌ی شطرنجی.
 */
public class XmlDrawableModelLoader implements ModelLoader<File, Drawable> {

  private Context context;

  private XmlDrawableModelLoader(Context context) {
    this.context = context.getApplicationContext();
  }

  @Override
  public LoadData<Drawable> buildLoadData(
      @NonNull File file, int width, int height, @NonNull Options options) {
    return new LoadData<>(new ObjectKey(file), new XmlDrawableFetcher(context, file));
  }

  @Override
  public boolean handles(@NonNull File file) {
    // فقط فایل‌های با پسوند .xml را بررسی می‌کنیم (اختیاری)
    return file.exists() && file.isFile() && file.getName().toLowerCase().endsWith(".xml");
  }

  public static class Factory implements ModelLoaderFactory<File, Drawable> {
    private Context contexts;
    
    public Factory(Context contexts){
      this.contexts= contexts;
    }
    @Override
    public ModelLoader<File, Drawable> build(@NonNull MultiModelLoaderFactory multiFactory) {
      return new XmlDrawableModelLoader(contexts);
    }

    @Override
    public void teardown() {
      // هیچ کاری
    }
  }
}
