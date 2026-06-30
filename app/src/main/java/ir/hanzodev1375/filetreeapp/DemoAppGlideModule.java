package ir.hanzodev1375.filetreeapp;

import com.bumptech.glide.annotation.GlideModule;
import com.bumptech.glide.module.AppGlideModule;

/**
 * Required so Glide's annotation processor can generate the merged
 * GeneratedAppGlideModule. Without an AppGlideModule somewhere in the
 * final app, the library's LibraryGlideModule (AppGlide.java in
 * filetreelib) will NOT be picked up, and Glide.with(context) won't
 * know about SVG, APK icon, or MP3 cover loading at all.
 *
 * This class can stay empty — its only job is to mark this module
 * as the "app" for Glide's module system.
 */
@GlideModule
public class DemoAppGlideModule extends AppGlideModule {}
