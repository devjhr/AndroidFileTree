package ir.hanzodev1375.filetreeapp;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import ir.hanzodev1375.filetreelib.adapter.TreeAdapter;
import ir.hanzodev1375.filetreelib.core.TreeNode;
import ir.hanzodev1375.filetreelib.icons.DefaultIconProvider;
import ir.hanzodev1375.filetreelib.model.FilePayload;
import ir.hanzodev1375.filetreelib.theme.ThemeManager;
import ir.hanzodev1375.filetreelib.widget.FileTreeView;
import ir.hanzodev1375.filetreelibglide.FileIconGlide;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A single scrollable bottom sheet that puts a live control next to every runtime-toggleable
 * {@link FileTreeView} API, all acting on the one shared instance passed to {@link #show}. Meant
 * as a one-stop way to exercise/verify each feature by hand rather than hunting through {@link
 * MainActivity}'s one-shot setup code.
 *
 * <p>Not covered here (deliberately): {@link FileTreeView#setClickNode} and {@link
 * FileTreeView#putDestroy()} are one-shot/lifecycle wiring already demonstrated once in {@link
 * MainActivity#setupDrawerFileTree()} and aren't meaningful to toggle at runtime; {@link
 * FileTreeView#setAdapter} / {@link FileTreeView#setSelectionPanel} are extension points that need
 * an actual custom subclass to demo (their current values are still shown in the state dump).
 */
public final class FeatureControlSheet {

  private static final String TAG = "FeatureControlSheet";

  // Kept as fields so a second call to show() (re-opening the sheet) doesn't create duplicate
  // virtual nodes / stack redundant long-click listeners on top of what a previous open already
  // set up.
  private static boolean virtualGroupAdded = false;
  private static TreeAdapter.OnNodeLongClickListener customLongClickListener;

  private FeatureControlSheet() {}

  public static void show(Activity activity, FileTreeView view, DrawerLayout drawerLayout) {
    BottomSheetDialog dialog = new BottomSheetDialog(activity);

    ScrollView scroll = new ScrollView(activity);
    LinearLayout root = new LinearLayout(activity);
    root.setOrientation(LinearLayout.VERTICAL);
    int pad = dp(activity, 16);
    root.setPadding(pad, pad, pad, pad);
    scroll.addView(root);
    dialog.setContentView(scroll);

    // Opening any control here is only useful if the drawer holding the FileTreeView is visible.
    if (!drawerLayout.isDrawerOpen(GravityCompat.START)) {
      drawerLayout.openDrawer(GravityCompat.START);
    }

    addSectionHeader(activity, root, "Behavior toggles");
    addBehaviorToggles(activity, view, root);

    addSectionHeader(activity, root, "Zoom (setZoomMod / setZoomScale / setCurrentZoomScale)");
    addZoomControls(activity, view, root);

    addSectionHeader(activity, root, "Theme (ThemeManager via getTheme/setTheme)");
    addThemeControls(activity, view, root);

    addSectionHeader(activity, root, "Icon provider (setIconProvider / setIconArrow)");
    addIconControls(activity, view, root);

    addSectionHeader(activity, root, "Git status (setGitStatus)");
    addGitStatusControls(activity, view, root);

    addSectionHeader(activity, root, "Virtual group (addVirtualGroup)");
    addVirtualGroupControls(activity, view, root);

    addSectionHeader(activity, root, "Navigate (expandToPath)");
    addExpandToPathControls(activity, view, root);

    addSectionHeader(activity, root, "Current state (all getters)");
    addStateDumpButton(activity, view, root);

    dialog.show();
  }

  // ---------------------------------------------------------------------------------------
  // Behavior toggles: setZoomMod, setRainbowIndentGuides, setShowSearchBar, setDragEnabled,
  // setAutoExpandSingleChildChains, setAndroidMod, and the paired
  // setSelectionModeEnabled/setOnNodeLongClickListener.
  // ---------------------------------------------------------------------------------------

  private static void addBehaviorToggles(Activity activity, FileTreeView view, LinearLayout root) {
    addSwitchRow(
        activity,
        root,
        "Zoom mode (pinch-to-zoom)",
        view.isZoomMod(),
        (btn, checked) -> view.setZoomMod(checked));

    addSwitchRow(
        activity,
        root,
        "Rainbow indent guides",
        view.isRainbowIndentGuides(),
        (btn, checked) -> view.setRainbowIndentGuides(checked));

    addSwitchRow(
        activity,
        root,
        "Show search bar",
        view.getShowSearchBar(),
        (btn, checked) -> view.setShowSearchBar(checked));

    addSwitchRow(
        activity,
        root,
        "Show breadcrumb bar",
        view.getShowBreadcrumbBar(),
        (btn, checked) -> view.setShowBreadcrumbBar(checked));

    addSwitchRow(
        activity,
        root,
        "Drag-to-move enabled",
        view.isDragEnabled(),
        (btn, checked) -> view.setDragEnabled(checked));

    addSwitchRow(
        activity,
        root,
        "Auto-expand single-child chains",
        view.isAutoExpandSingleChildChains(),
        (btn, checked) -> view.setAutoExpandSingleChildChains(checked));

    addSwitchRow(
        activity,
        root,
        "Android view (project structure)",
        view.isAndroidMod(),
        (btn, checked) -> {
          view.setAndroidMod(checked);
          Toast.makeText(
                  activity,
                  checked ? "Android view applied" : "Android view removed",
                  Toast.LENGTH_SHORT)
              .show();
        });

    if (customLongClickListener == null) {
      customLongClickListener =
          (node, nodeView) -> {
            new android.app.AlertDialog.Builder(activity)
                .setTitle(node.getName())
                .setMessage(node.getAbsolutePath())
                .setPositiveButton("Close", null)
                .show();
            return true;
          };
    }
    addSwitchRow(
        activity,
        root,
        "Custom long-press dialog (off = built-in selection mode)",
        !view.isSelectionModeEnabled(),
        (btn, checked) -> {
          view.setSelectionModeEnabled(!checked);
          view.setOnNodeLongClickListener(checked ? customLongClickListener : null);
        });
  }

  // ---------------------------------------------------------------------------------------
  // Zoom: min/max range + jump-to-percent + reset.
  // ---------------------------------------------------------------------------------------

  private static void addZoomControls(Activity activity, FileTreeView view, LinearLayout root) {
    int[] range = view.getZoomScale();

    LinearLayout row = new LinearLayout(activity);
    row.setOrientation(LinearLayout.HORIZONTAL);
    EditText minInput = new EditText(activity);
    minInput.setHint("min %");
    minInput.setText(String.valueOf(range[0]));
    minInput.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
    EditText maxInput = new EditText(activity);
    maxInput.setHint("max %");
    maxInput.setText(String.valueOf(range[1]));
    maxInput.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
    row.addView(minInput);
    row.addView(maxInput);
    root.addView(row);

    addButtonRow(
        activity,
        root,
        "Apply zoom range",
        v -> {
          try {
            int min = Integer.parseInt(minInput.getText().toString().trim());
            int max = Integer.parseInt(maxInput.getText().toString().trim());
            view.setZoomScale(min, max);
            Toast.makeText(activity, "zoomScale=" + min + ".." + max, Toast.LENGTH_SHORT).show();
          } catch (NumberFormatException e) {
            Toast.makeText(activity, "Enter valid numbers", Toast.LENGTH_SHORT).show();
          }
        });

    TextView currentZoomLabel = new TextView(activity);
    currentZoomLabel.setText(
        String.format(Locale.US, "Current zoom: %d%%", view.getCurrentZoomScale()));
    root.addView(currentZoomLabel);

    SeekBar seekBar = new SeekBar(activity);
    seekBar.setMax(Math.max(1, range[1] - range[0]));
    seekBar.setProgress(Math.max(0, view.getCurrentZoomScale() - range[0]));
    seekBar.setOnSeekBarChangeListener(
        new SeekBar.OnSeekBarChangeListener() {
          @Override
          public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            if (fromUser) {
              currentZoomLabel.setText(
                  String.format(Locale.US, "Current zoom: %d%%", range[0] + progress));
            }
          }

          @Override
          public void onStartTrackingTouch(SeekBar seekBar) {}

          @Override
          public void onStopTrackingTouch(SeekBar seekBar) {
            view.setCurrentZoomScale(range[0] + seekBar.getProgress());
          }
        });
    root.addView(seekBar);

    addButtonRow(
        activity,
        root,
        "Reset zoom to 100%",
        v -> {
          view.resetZoom();
          currentZoomLabel.setText(
              String.format(Locale.US, "Current zoom: %d%%", view.getCurrentZoomScale()));
          seekBar.setProgress(Math.max(0, view.getCurrentZoomScale() - range[0]));
        });
  }

  // ---------------------------------------------------------------------------------------
  // Theme: a few preset swaps built from ThemeManager's setters, applied on the live instance
  // returned by getTheme() and pushed back via setTheme().
  // ---------------------------------------------------------------------------------------

  private static void addThemeControls(Activity activity, FileTreeView view, LinearLayout root) {
    addButtonRow(
        activity,
        root,
        "Default theme",
        v -> {
          ThemeManager theme = new ThemeManager(activity);
          view.setTheme(theme);
        });

    addButtonRow(
        activity,
        root,
        "Neon theme",
        v -> {
          ThemeManager theme = view.getTheme();
          theme.setTextColor(Color.parseColor("#00FFE5"));
          theme.setTreeLineColor(Color.parseColor("#FF00FF"));
          theme.setSelectedBg(Color.parseColor("#332A00FF"));
          view.setTheme(theme);
        });

    addButtonRow(
        activity,
        root,
        "Warm theme",
        v -> {
          ThemeManager theme = view.getTheme();
          theme.setTextColor(Color.parseColor("#FFD9A0"));
          theme.setTreeLineColor(Color.parseColor("#FF8C42"));
          theme.setSelectedBg(Color.parseColor("#33FF8C42"));
          view.setTheme(theme);
        });
  }

  // ---------------------------------------------------------------------------------------
  // Icon provider: swap between the Glide-backed provider and the library's DefaultIconProvider,
  // plus setIconArrow.
  // ---------------------------------------------------------------------------------------

  private static void addIconControls(Activity activity, FileTreeView view, LinearLayout root) {
    addButtonRow(
        activity,
        root,
        "Use Glide icon provider (thumbnails/APK icons)",
        v -> view.setIconProvider(new FileIconGlide()));

    addButtonRow(
        activity,
        root,
        "Use DefaultIconProvider (static icons)",
        v -> view.setIconProvider(new DefaultIconProvider()));

    addButtonRow(
        activity,
        root,
        "Error badge arrow icon",
        v -> view.setIconArrow(R.drawable.ic_filetree_badge_error));

    addButtonRow(
        activity,
        root,
        "Default arrow icon",
        v -> view.setIconArrow(R.drawable.ic_filetree_arrow_right));
  }

  // ---------------------------------------------------------------------------------------
  // Git status: type an absolute path, tap a GIT_* constant to apply it.
  // ---------------------------------------------------------------------------------------

  private static void addGitStatusControls(Activity activity, FileTreeView view, LinearLayout root) {
    EditText pathInput = new EditText(activity);
    pathInput.setHint("Absolute file/folder path");
    String current = view.getNodePath();
    pathInput.setText(current != null ? current : "");
    root.addView(pathInput);

    LinearLayout row1 = new LinearLayout(activity);
    row1.setOrientation(LinearLayout.HORIZONTAL);
    row1.addView(gitStatusButton(activity, view, pathInput, "Modified", FilePayload.GIT_MODIFIED));
    row1.addView(gitStatusButton(activity, view, pathInput, "Staged", FilePayload.GIT_STAGED));
    row1.addView(gitStatusButton(activity, view, pathInput, "Added", FilePayload.GIT_ADDED));
    root.addView(row1);

    LinearLayout row2 = new LinearLayout(activity);
    row2.setOrientation(LinearLayout.HORIZONTAL);
    row2.addView(gitStatusButton(activity, view, pathInput, "Deleted", FilePayload.GIT_DELETED));
    row2.addView(
        gitStatusButton(activity, view, pathInput, "Conflicted", FilePayload.GIT_CONFLICTED));
    row2.addView(gitStatusButton(activity, view, pathInput, "Ignored", FilePayload.GIT_IGNORED));
    root.addView(row2);

    LinearLayout row3 = new LinearLayout(activity);
    row3.setOrientation(LinearLayout.HORIZONTAL);
    row3.addView(gitStatusButton(activity, view, pathInput, "Clean", FilePayload.GIT_CLEAN));
    row3.addView(
        gitStatusButton(activity, view, pathInput, "Untracked", FilePayload.GIT_UNTRACKED));
    root.addView(row3);
  }

  private static Button gitStatusButton(
      Activity activity, FileTreeView view, EditText pathInput, String label, int gitStatus) {
    Button button = new Button(activity);
    button.setText(label);
    button.setLayoutParams(
        new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
    button.setOnClickListener(
        v -> {
          String path = pathInput.getText().toString().trim();
          if (TextUtils.isEmpty(path)) {
            Toast.makeText(activity, "Enter a path first", Toast.LENGTH_SHORT).show();
            return;
          }
          view.setGitStatus(path, gitStatus);
          Toast.makeText(activity, label + " applied to " + path, Toast.LENGTH_SHORT).show();
        });
    return button;
  }

  // ---------------------------------------------------------------------------------------
  // Virtual group: reproduces the Gradle Scripts grouping example, guarded so re-opening the
  // sheet can't insert duplicate (and therefore stable-id-colliding) virtual nodes.
  // ---------------------------------------------------------------------------------------

  private static void addVirtualGroupControls(Activity activity, FileTreeView view, LinearLayout root) {
    Button button = new Button(activity);
    button.setText(
        virtualGroupAdded ? "Gradle Scripts group already added" : "Add \"Gradle Scripts\" group");
    button.setEnabled(!virtualGroupAdded);
    button.setOnClickListener(
        v -> {
          String rootPath = view.getNodePath();
          if (TextUtils.isEmpty(rootPath)) {
            Toast.makeText(activity, "Load a tree first", Toast.LENGTH_SHORT).show();
            return;
          }
          List<TreeNode> children = new ArrayList<>();
          children.add(
              new TreeNode.Builder("settings.gradle")
                  .setId(rootPath + "settings.gradle::demo")
                  .setType(TreeNode.TYPE_FILE)
                  .setPayload(
                      new FilePayload.Builder(rootPath + "settings.gradle", false)
                          .description("(Project: Settings)")
                          .build())
                  .build());
          children.add(
              new TreeNode.Builder("build.gradle")
                  .setId(rootPath + "build.gradle::demo")
                  .setType(TreeNode.TYPE_FILE)
                  .setPayload(
                      new FilePayload.Builder(rootPath + "build.gradle", false)
                          .description("(Project: Build)")
                          .build())
                  .build());
          TreeNode group =
              view.addVirtualGroup(
                  "Gradle Scripts", R.drawable.ic_filetree_folder_gradle, children);
          if (group != null) {
            virtualGroupAdded = true;
            button.setEnabled(false);
            button.setText("Gradle Scripts group already added");
            Toast.makeText(activity, "Virtual group added", Toast.LENGTH_SHORT).show();
          } else {
            Toast.makeText(activity, "loadTree() hasn't run yet", Toast.LENGTH_SHORT).show();
          }
        });
    root.addView(button);
  }

  // ---------------------------------------------------------------------------------------
  // Navigate: expandToPath.
  // ---------------------------------------------------------------------------------------

  private static void addExpandToPathControls(
      Activity activity, FileTreeView view, LinearLayout root) {
    EditText pathInput = new EditText(activity);
    pathInput.setHint("Absolute path to reveal");
    root.addView(pathInput);

    addButtonRow(
        activity,
        root,
        "Expand to path",
        v -> {
          String path = pathInput.getText().toString().trim();
          if (TextUtils.isEmpty(path)) {
            Toast.makeText(activity, "Enter a path first", Toast.LENGTH_SHORT).show();
            return;
          }
          boolean found = view.expandToPath(path);
          Toast.makeText(
                  activity, found ? "Revealed: " + path : "Not found: " + path, Toast.LENGTH_SHORT)
              .show();
        });
  }

  // ---------------------------------------------------------------------------------------
  // State dump: every getter in one place, for a quick "is this actually applied?" sanity check.
  // ---------------------------------------------------------------------------------------

  private static void addStateDumpButton(Activity activity, FileTreeView view, LinearLayout root) {
    addButtonRow(
        activity,
        root,
        "Log current state",
        v -> {
          int[] zoomRange = view.getZoomScale();
          String summary =
              "nodePath="
                  + view.getNodePath()
                  + "\nandroidMod="
                  + view.isAndroidMod()
                  + "\nzoomMod="
                  + view.isZoomMod()
                  + "\nzoomScale=["
                  + zoomRange[0]
                  + ","
                  + zoomRange[1]
                  + "]"
                  + "\ncurrentZoomScale="
                  + view.getCurrentZoomScale()
                  + "\nrainbowIndentGuides="
                  + view.isRainbowIndentGuides()
                  + "\nshowSearchBar="
                  + view.getShowSearchBar()
                  + "\nshowBreadcrumbBar="
                  + view.getShowBreadcrumbBar()
                  + "\nselectionModeEnabled="
                  + view.isSelectionModeEnabled()
                  + "\ndragEnabled="
                  + view.isDragEnabled()
                  + "\nautoExpandSingleChildChains="
                  + view.isAutoExpandSingleChildChains()
                  + "\ncontroller="
                  + (view.getController() != null
                      ? view.getController().getClass().getSimpleName()
                      : "null")
                  + "\nadapter="
                  + (view.getAdapter() != null ? view.getAdapter().getClass().getSimpleName() : "null")
                  + "\nselectionPanel="
                  + (view.getSelectionPanel() != null
                      ? view.getSelectionPanel().getClass().getSimpleName()
                      : "null");
          Log.d(TAG, summary);
          new android.app.AlertDialog.Builder(activity)
              .setTitle("Current FileTreeView state")
              .setMessage(summary)
              .setPositiveButton("Close", null)
              .show();
        });
  }

  // ---------------------------------------------------------------------------------------
  // Small UI-building helpers.
  // ---------------------------------------------------------------------------------------

  private interface OnCheckedChanged {
    void onChanged(CompoundButton button, boolean checked);
  }

  private static void addSwitchRow(
      Context context,
      LinearLayout root,
      String label,
      boolean initialChecked,
      OnCheckedChanged listener) {
    LinearLayout row = new LinearLayout(context);
    row.setOrientation(LinearLayout.HORIZONTAL);
    row.setGravity(Gravity.CENTER_VERTICAL);
    row.setPadding(0, dp(context, 4), 0, dp(context, 4));

    TextView text = new TextView(context);
    text.setText(label);
    text.setLayoutParams(
        new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
    row.addView(text);

    SwitchCompat switchCompat = new SwitchCompat(context);
    switchCompat.setChecked(initialChecked);
    switchCompat.setOnCheckedChangeListener(listener::onChanged);
    row.addView(switchCompat);

    root.addView(row);
  }

  private static void addButtonRow(
      Context context, LinearLayout root, String label, View.OnClickListener onClick) {
    Button button = new Button(context);
    button.setText(label);
    button.setOnClickListener(onClick);
    root.addView(button);
  }

  private static void addSectionHeader(Context context, LinearLayout root, String title) {
    TextView header = new TextView(context);
    header.setText(title);
    header.setTypeface(header.getTypeface(), android.graphics.Typeface.BOLD);
    header.setPadding(0, dp(context, 20), 0, dp(context, 6));
    root.addView(header);
  }

  private static int dp(Context context, int value) {
    return (int) (value * context.getResources().getDisplayMetrics().density);
  }
}
