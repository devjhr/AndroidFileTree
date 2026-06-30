package ir.hanzodev1375.filetreelib;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.PictureDrawable;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.FileInputStream;
import java.io.InputStream;

import ir.hanzodev1375.filetreelib.core.TreeNode;
import ir.hanzodev1375.filetreelib.icons.BaseIconProvider;
import ir.hanzodev1375.filetreelib.icons.DefaultIconProvider;
import com.itsvks.layouteditor.vectormaster.VectorMasterDrawable;

public class FileIconGlide extends BaseIconProvider {

    private final DefaultIconProvider defaultProvider = new DefaultIconProvider();

    @Override
    public void loadIcon(
            @NonNull Context context, @NonNull TreeNode node, @NonNull ImageView target) {

        String path = getFilePath(node);

        // রাস্টার ইমেজ → Glide
        if (isRasterImageFile(node) && path != null) {
            Glide.with(context).load(path).into(target);
            return;
        }

        // SVG → Glide pipeline (SvgDecoder + SvgDrawableTranscoder)
        if (isSvgFile(node) && path != null) {
            target.setLayerType(android.view.View.LAYER_TYPE_SOFTWARE, null);
            Glide.with(context)
                    .as(PictureDrawable.class)
                    .load(new java.io.File(path))
                    .into(target);
            return;
        }

        // XML Vector Drawable → parse করে render
        if (isVectorXmlFile(node) && path != null) {
            Drawable vd = loadVectorFromFile(context, path);
            if (vd != null) {
                target.setImageDrawable(vd);
                return;
            }
        }

        // APK ফাইল → ApkIconModelLoader pipeline (registered in AppGlide)
        if (isApkFile(node) && path != null) {
            Glide.with(context)
                    .as(Drawable.class)
                    .load(path)
                    .error(defaultProvider.getIcon(context, node))
                    .into(target);
            return;
        }

        // MP3 ফাইল → Mp3CoverLoaderFactory pipeline (registered in AppGlide)
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

    @Nullable
    private Drawable loadVectorFromFile(@NonNull Context context, @NonNull String path) {
        try {
            VectorMasterDrawable vd = new VectorMasterDrawable(context, new java.io.File(path));
            if (vd.isVector()) {
                return vd;
            }
        } catch (Exception e) {
            // ignore
        }
        return null;
    }
}