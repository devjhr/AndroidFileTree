package ir.hanzodev1375.filetreelib.filetreelibglide.glide.xml;

import android.content.Context;
import androidx.annotation.NonNull;
import com.bumptech.glide.Priority;
import android.graphics.drawable.Drawable;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.data.DataFetcher;
import ir.hanzodev1375.filetreelib.vectormaster.VectorMasterDrawable;
import ir.hanzodev1375.filetreelibglide.drawablexml.XmlShapeParser;

public class VectorModelFetcher implements DataFetcher<Drawable> {

  private final VectorModel model;
  private final Context context;

  public VectorModelFetcher(VectorModel model, Context context) {
    this.model = model;
    this.context = context;
  }

  @Override
  public void loadData(
      @NonNull Priority priority, @NonNull DataCallback<? super Drawable> callback) {
    try {
      var drawable = new VectorMasterDrawable(context, model.getFile());
      if (drawable.isVector()) {
        callback.onDataReady(drawable);
      } else {
        callback.onDataReady(XmlShapeParser.parse(context, model.getFile()));
      }
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
