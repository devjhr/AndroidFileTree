package ir.hanzodev1375.filetreelibglide.drawablexml;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.StateListDrawable;
import android.content.res.ColorStateList;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import ir.hanzodev1375.filetreelib.vectormaster.utilities.Utils;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public final class XmlShapeParser {

  private static final String TAG = "XmlShapeParser";
  private static final String NS = "http://schemas.android.com/apk/res/android";
  private static final String MASK_ID = "@android:id/mask";

  // Reused across calls instead of recreating per parse
  private static volatile XmlPullParserFactory FACTORY;

  private XmlShapeParser() {}

  @Nullable
  public static Drawable parse(@NonNull Context context, @NonNull File file) {
    if (!file.exists() || !file.canRead()) {
      Log.w(TAG, "File not accessible: " + file.getAbsolutePath());
      return null;
    }

    try (InputStream is = new FileInputStream(file)) {
      XmlPullParser xpp = getFactory().newPullParser();
      xpp.setInput(is, "utf-8");

      int event = xpp.getEventType();
      while (event != XmlPullParser.END_DOCUMENT) {
        if (event == XmlPullParser.START_TAG) {
          Drawable result = parseElement(context, xpp);
          if (result != null) return result;
        }
        event = xpp.next();
      }
    } catch (Exception e) {
      Log.e(TAG, "Error parsing XML: " + file.getName(), e);
    }
    return null;
  }

  private static XmlPullParserFactory getFactory() throws Exception {
    XmlPullParserFactory f = FACTORY;
    if (f == null) {
      synchronized (XmlShapeParser.class) {
        f = FACTORY;
        if (f == null) {
          f = XmlPullParserFactory.newInstance();
          f.setNamespaceAware(true);
          FACTORY = f;
        }
      }
    }
    return f;
  }

  // Supported root elements: shape, selector, layer-list, ripple, inset, clip/scale/rotate (basic)
  @Nullable
  private static Drawable parseElement(Context context, XmlPullParser xpp) {
    try {
      String tag = xpp.getName();
      switch (tag) {
        case "shape":
          return parseShape(xpp);
        case "selector":
          return parseSelector(context, xpp);
        case "layer-list":
          return parseLayerList(context, xpp);
        case "ripple":
          return parseRipple(context, xpp);
        case "inset":
          return parseInset(context, xpp);
        case "clip":
        case "scale":
        case "rotate":
          return parseBasicTransformDrawable(context, xpp, tag);
        default:
          skipSubtree(xpp);
          return null;
      }
    } catch (Exception e) {
      Log.e(TAG, "Error parsing element", e);
      return null;
    }
  }

  private static Drawable parseInset(Context context, XmlPullParser xpp) throws Exception {
    int insetLeft = parseDimensionPx(attr(xpp, "insetLeft"), 0);
    int insetTop = parseDimensionPx(attr(xpp, "insetTop"), 0);
    int insetRight = parseDimensionPx(attr(xpp, "insetRight"), 0);
    int insetBottom = parseDimensionPx(attr(xpp, "insetBottom"), 0);
    int inset = parseDimensionPx(attr(xpp, "inset"), -1);

    if (inset >= 0) {
      insetLeft = insetTop = insetRight = insetBottom = inset;
    }

    Drawable child = parseNestedItemDrawable(context, xpp);
    if (child == null) return null;

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      return new InsetDrawable(child, insetLeft, insetTop, insetRight, insetBottom);
    }
    return child;
  }

  // Transform properties (clip/scale/rotate) are not applied; only the child drawable is returned
  private static Drawable parseBasicTransformDrawable(
      Context context, XmlPullParser xpp, String type) throws Exception {
    Drawable child = parseNestedItemDrawable(context, xpp);
    if (child == null) return null;
    Log.w(TAG, "Transform properties ignored for: " + type);
    return child;
  }

  private static Drawable parseShape(XmlPullParser xpp) throws Exception {
    GradientDrawable gd = new GradientDrawable();
    gd.setShape(shapeTypeFromString(attr(xpp, "shape")));

    int startDepth = xpp.getDepth();
    int event = xpp.next();
    while (!(event == XmlPullParser.END_TAG && xpp.getDepth() == startDepth)) {
      if (event == XmlPullParser.START_TAG) {
        switch (xpp.getName()) {
          case "solid":
            gd.setColor(parseSolidColor(xpp));
            break;
          case "corners":
            {
              String radius = attr(xpp, "radius");
              if (radius != null) {
                gd.setCornerRadius(parseDimensionPx(radius, 0));
              } else {
                float tl = parseDimensionPx(attr(xpp, "topLeftRadius"), 0);
                float tr = parseDimensionPx(attr(xpp, "topRightRadius"), 0);
                float br = parseDimensionPx(attr(xpp, "bottomRightRadius"), 0);
                float bl = parseDimensionPx(attr(xpp, "bottomLeftRadius"), 0);
                gd.setCornerRadii(new float[] {tl, tl, tr, tr, br, br, bl, bl});
              }
              break;
            }
          case "gradient":
            parseGradient(gd, xpp);
            break;
          case "stroke":
            parseStroke(gd, xpp);
            break;
          case "size":
            {
              int w = parseDimensionPx(attr(xpp, "width"), -1);
              int h = parseDimensionPx(attr(xpp, "height"), -1);
              gd.setSize(w, h);
              break;
            }
          default:
            break;
        }
      }
      event = xpp.next();
    }
    return gd;
  }

  private static int parseSolidColor(XmlPullParser xpp) {
    int color = parseColor(attr(xpp, "color"), Color.BLACK);
    String alphaStr = attr(xpp, "alpha");
    if (alphaStr != null) {
      try {
        float alpha = Float.parseFloat(alphaStr);
        int a = Math.round(alpha * 255f);
        color = (color & 0x00FFFFFF) | (a << 24);
      } catch (Exception ignored) {
      }
    }
    return color;
  }

  private static void parseGradient(GradientDrawable gd, XmlPullParser xpp) {
    try {
      String startColor = attr(xpp, "startColor");
      String centerColor = attr(xpp, "centerColor");
      String endColor = attr(xpp, "endColor");
      List<Integer> colors = new ArrayList<>();
      if (startColor != null) colors.add(parseColor(startColor, Color.BLACK));
      if (centerColor != null) colors.add(parseColor(centerColor, Color.BLACK));
      if (endColor != null) colors.add(parseColor(endColor, Color.BLACK));

      if (colors.size() >= 2) {
        int[] arr = new int[colors.size()];
        for (int i = 0; i < arr.length; i++) arr[i] = colors.get(i);
        gd.setColors(arr);
      }

      String angleStr = attr(xpp, "angle");
      if (angleStr != null) {
        try {
          int angle = ((int) Float.parseFloat(angleStr)) % 360;
          if (angle < 0) angle += 360;
          gd.setOrientation(orientationFromAngle(angle));
        } catch (Exception ignored) {
        }
      }

      String typeStr = attr(xpp, "type");
      if ("radial".equals(typeStr)) {
        gd.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        gd.setGradientRadius(parseDimensionPx(attr(xpp, "gradientRadius"), 50));
      } else if ("sweep".equals(typeStr)) {
        gd.setGradientType(GradientDrawable.SWEEP_GRADIENT);
      } else {
        gd.setGradientType(GradientDrawable.LINEAR_GRADIENT);
      }

      String centerX = attr(xpp, "centerX");
      String centerY = attr(xpp, "centerY");
      if (centerX != null && centerY != null) {
        try {
          gd.setGradientCenter(Float.parseFloat(centerX), Float.parseFloat(centerY));
        } catch (Exception ignored) {
        }
      }
    } catch (Exception e) {
      Log.w(TAG, "Error parsing gradient", e);
    }
  }

  private static void parseStroke(GradientDrawable gd, XmlPullParser xpp) {
    try {
      int width = parseDimensionPx(attr(xpp, "width"), 0);
      int color = parseColor(attr(xpp, "color"), Color.BLACK);
      String dashWidth = attr(xpp, "dashWidth");
      String dashGap = attr(xpp, "dashGap");
      if (dashWidth != null && dashGap != null) {
        gd.setStroke(width, color, parseDimensionPx(dashWidth, 0), parseDimensionPx(dashGap, 0));
      } else {
        gd.setStroke(width, color);
      }
    } catch (Exception e) {
      Log.w(TAG, "Error parsing stroke", e);
    }
  }

  private static Drawable parseSelector(Context context, XmlPullParser xpp) throws Exception {
    StateListDrawable sld = new StateListDrawable();
    int startDepth = xpp.getDepth();
    int event = xpp.next();

    while (!(event == XmlPullParser.END_TAG && xpp.getDepth() == startDepth)) {
      if (event == XmlPullParser.START_TAG && "item".equals(xpp.getName())) {
        List<Integer> states = new ArrayList<>();
        for (int i = 0; i < xpp.getAttributeCount(); i++) {
          String name = xpp.getAttributeName(i);
          if (name != null && name.startsWith("state_")) {
            int stateAttr = stateNameToAttr(name);
            if (stateAttr != 0) {
              boolean value = Boolean.parseBoolean(xpp.getAttributeValue(i));
              states.add(value ? stateAttr : -stateAttr);
            }
          }
        }

        String drawableRef = attr(xpp, "drawable");
        String colorRef = attr(xpp, "color");
        Drawable itemDrawable = parseNestedItemDrawable(context, xpp);
        if (itemDrawable == null && drawableRef != null) {
          itemDrawable = new ColorDrawable(parseColor(drawableRef, Color.TRANSPARENT));
        }
        if (itemDrawable == null && colorRef != null) {
          itemDrawable = new ColorDrawable(parseColor(colorRef, Color.TRANSPARENT));
        }
        if (itemDrawable == null) {
          itemDrawable = new ColorDrawable(Color.TRANSPARENT);
        }

        sld.addState(toIntArray(states), itemDrawable);
      }
      event = xpp.next();
    }
    return sld;
  }

  private static Drawable parseLayerList(Context context, XmlPullParser xpp) throws Exception {
    List<Drawable> layers = new ArrayList<>();
    int startDepth = xpp.getDepth();
    int event = xpp.next();

    while (!(event == XmlPullParser.END_TAG && xpp.getDepth() == startDepth)) {
      if (event == XmlPullParser.START_TAG && "item".equals(xpp.getName())) {
        Drawable itemDrawable = parseNestedItemDrawable(context, xpp);
        if (itemDrawable != null) layers.add(itemDrawable);
      }
      event = xpp.next();
    }

    if (layers.isEmpty()) return null;
    return new LayerDrawable(layers.toArray(new Drawable[0]));
  }

  private static Drawable parseRipple(Context context, XmlPullParser xpp) throws Exception {
    int rippleColor = parseColor(attr(xpp, "color"), Color.GRAY);
    Drawable content = null;
    Drawable mask = null;

    int startDepth = xpp.getDepth();
    int event = xpp.next();
    while (!(event == XmlPullParser.END_TAG && xpp.getDepth() == startDepth)) {
      if (event == XmlPullParser.START_TAG && "item".equals(xpp.getName())) {
        String idRef = attr(xpp, "id");
        boolean isMask = MASK_ID.equals(idRef);
        Drawable itemDrawable = parseNestedItemDrawable(context, xpp);
        if (isMask) {
          mask = itemDrawable;
        } else if (itemDrawable != null) {
          content = itemDrawable;
        }
      }
      event = xpp.next();
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      return new RippleDrawable(ColorStateList.valueOf(rippleColor), content, mask);
    }
    return content != null ? content : new ColorDrawable(rippleColor);
  }

  @Nullable
  private static Drawable parseNestedItemDrawable(Context context, XmlPullParser xpp)
      throws Exception {
    int itemDepth = xpp.getDepth();
    Drawable result = null;
    int event = xpp.next();
    while (!(event == XmlPullParser.END_TAG && xpp.getDepth() == itemDepth)) {
      if (event == XmlPullParser.START_TAG) {
        Drawable d = parseElement(context, xpp);
        if (result == null) result = d;
      }
      event = xpp.next();
    }
    return result;
  }

  private static void skipSubtree(XmlPullParser xpp) throws Exception {
    int depth = xpp.getDepth();
    int event = xpp.next();
    while (!(event == XmlPullParser.END_TAG && xpp.getDepth() == depth)) {
      event = xpp.next();
    }
  }

  @Nullable
  private static String attr(XmlPullParser xpp, String name) {
    String v = xpp.getAttributeValue(NS, name);
    if (v == null) v = xpp.getAttributeValue(null, name);
    return v;
  }

  private static int parseColor(@Nullable String value, int fallback) {
    if (value == null) return fallback;
    try {
      return Utils.getColorFromString(value, true);
    } catch (Exception e) {
      return fallback;
    }
  }

  private static int parseDimensionPx(@Nullable String value, int fallback) {
    if (value == null) return fallback;
    try {
      if (value.endsWith("dip")) {
        return Utils.dpToPx((int) Float.parseFloat(value.substring(0, value.length() - 3)));
      } else if (value.endsWith("dp") || value.endsWith("sp")) {
        return Utils.dpToPx((int) Float.parseFloat(value.substring(0, value.length() - 2)));
      } else if (value.endsWith("px")) {
        return (int) Float.parseFloat(value.substring(0, value.length() - 2));
      } else {
        return (int) Float.parseFloat(value);
      }
    } catch (Exception e) {
      return fallback;
    }
  }

  private static int shapeTypeFromString(@Nullable String value) {
    if (value == null) return GradientDrawable.RECTANGLE;
    switch (value) {
      case "oval":
        return GradientDrawable.OVAL;
      case "line":
        return GradientDrawable.LINE;
      case "ring":
        return GradientDrawable.RING;
      default:
        return GradientDrawable.RECTANGLE;
    }
  }

  // Rounds to the nearest of the 8 supported orientations instead of only matching exact values
  private static GradientDrawable.Orientation orientationFromAngle(int angle) {
    int rounded = Math.round(angle / 45f) * 45 % 360;
    switch (rounded) {
      case 45:
        return GradientDrawable.Orientation.BL_TR;
      case 90:
        return GradientDrawable.Orientation.BOTTOM_TOP;
      case 135:
        return GradientDrawable.Orientation.BR_TL;
      case 180:
        return GradientDrawable.Orientation.RIGHT_LEFT;
      case 225:
        return GradientDrawable.Orientation.TR_BL;
      case 270:
        return GradientDrawable.Orientation.TOP_BOTTOM;
      case 315:
        return GradientDrawable.Orientation.TL_BR;
      default:
        return GradientDrawable.Orientation.LEFT_RIGHT;
    }
  }

  private static int stateNameToAttr(String name) {
    if (name.startsWith("android:")) {
      name = name.substring(8);
    }
    switch (name) {
      case "state_pressed":
        return android.R.attr.state_pressed;
      case "state_enabled":
        return android.R.attr.state_enabled;
      case "state_selected":
        return android.R.attr.state_selected;
      case "state_checked":
        return android.R.attr.state_checked;
      case "state_checkable":
        return android.R.attr.state_checkable;
      case "state_focused":
        return android.R.attr.state_focused;
      case "state_activated":
        return android.R.attr.state_activated;
      case "state_hovered":
        return android.R.attr.state_hovered;
      case "state_window_focused":
        return android.R.attr.state_window_focused;
      default:
        return 0;
    }
  }

  private static int[] toIntArray(List<Integer> list) {
    int[] arr = new int[list.size()];
    for (int i = 0; i < arr.length; i++) arr[i] = list.get(i);
    return arr;
  }
}
