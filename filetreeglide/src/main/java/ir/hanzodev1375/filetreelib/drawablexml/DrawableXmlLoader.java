package ir.hanzodev1375.filetreelib.drawablexml;

import android.content.Context;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.itsvks.layouteditor.vectormaster.VectorMasterDrawable;

import java.io.File;

/**
 * Tries VectorMasterDrawable first (for {@code <vector>} XML),
 * then falls back to XmlShapeParser (shape/selector/layer-list/ripple/inset).
 */
public final class DrawableXmlLoader {

    private DrawableXmlLoader() {}

    @Nullable
    public static Drawable load(@NonNull Context context, @NonNull File file) {
        try {
            VectorMasterDrawable vd = new VectorMasterDrawable(context, file);
            if (vd.isVector()) {
                return vd;
            }
        } catch (Exception e) {
            // not a vector drawable, fall through to shape parser
        }

        try {
            Drawable shapeDrawable = XmlShapeParser.parse(context, file);
            if (shapeDrawable != null) {
                return shapeDrawable;
            }
        } catch (Exception e) {
            // ignore
        }

        return null;
    }
}