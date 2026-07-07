package ir.hanzodev1375.filetreeapp;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.util.Log;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.PopupWindow;
import android.widget.Toast;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.sidesheet.SideSheetDialog;
import ir.hanzodev1375.filetreeapp.databinding.ActivityMainBinding;
import ir.hanzodev1375.filetreelib.core.TreeNode;
import ir.hanzodev1375.filetreelib.model.FilePayload;
import ir.hanzodev1375.filetreelibglide.FileIconGlide;
import ir.hanzodev1375.filetreelib.widget.FileTreeView;
import java.io.File;
import java.util.Arrays;

public class MainActivity extends AppCompatActivity {
  private static final String TAG = "FileTreeDemo";

  private static final String NORMAL_VIEW_ROOT = "/storage/emulated/0/";

  private ActivityMainBinding binding;
  private FileTreeView drawerFileTreeView;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    binding = ActivityMainBinding.inflate(getLayoutInflater());
    setContentView(binding.getRoot());

    setupDrawerFileTree();

    // Two clearly separate buttons for the two ways to browse: a plain file tree, and Android
    // Studio's flattened "Android" project view. Both reuse the same FileTreeView instance —
    // this doubles as a demo that setAndroidMod()/setNodePath() can be switched at runtime.
    binding.btnNormalView.setOnClickListener(
        v -> {
          String path = binding.androidmod.getText().toString().trim();
          if (path.isEmpty()) {
            path = NORMAL_VIEW_ROOT;
            binding.androidmod.setText(path);
          }
          
          File dir = new File(path);
          if (!dir.exists() || !dir.isDirectory()) {
            Toast.makeText(MainActivity.this, 
                "Path does not exist: " + path, 
                Toast.LENGTH_SHORT).show();
            return;
          }
          
          drawerFileTreeView.setAndroidMod(false);
          drawerFileTreeView.setNodePath(path);
          drawerFileTreeView.loadTree();
          binding.drawerLayout.openDrawer(GravityCompat.START);
        });
        
    binding.btnAndroidView.setOnClickListener(
        v -> {
          String path = binding.androidmod.getText().toString().trim();
          if (path.isEmpty()) {
            path = NORMAL_VIEW_ROOT;
            binding.androidmod.setText(path);
          }
          
          File dir = new File(path);
          if (!dir.exists() || !dir.isDirectory()) {
            Toast.makeText(MainActivity.this, 
                "Path does not exist: " + path, 
                Toast.LENGTH_SHORT).show();
            return;
          }
          
          drawerFileTreeView.setAndroidMod(true);
          drawerFileTreeView.setNodePath(path);
          drawerFileTreeView.loadTree();
          binding.drawerLayout.openDrawer(GravityCompat.START);
        });
        
    //    binding.btnPopupWindow.setOnClickListener(v -> buildinPopWindows());
    binding.launchSampleExplorer.setOnClickListener(
        v -> startActivity(new Intent(getApplicationContext(), SampleExplorerActivity.class)));
    
    ActionBarDrawerToggle toggle =
        new ActionBarDrawerToggle(
            MainActivity.this, binding.drawerLayout, null, R.string.app_name, R.string.app_name);
    binding.drawerLayout.addDrawerListener(toggle);
    toggle.syncState();
  }

  /**
   * Left-drawer FileTreeView — the main demo surface. Set up once here with everyday API calls
   * (zoom, theming, click/long-click handling, the selection-mode/drag opt-outs, search bar);
   * {@link #NORMAL_VIEW_ROOT} and {@code setAndroidMod} are then switched at runtime by the
   * Normal View / Android View buttons (the latter reads its target path from {@code
   * binding.androidmod}'s EditText instead of a hardcoded constant).
   */
  private void setupDrawerFileTree() {
    FileTreeView view = new FileTreeView(this);
    drawerFileTreeView = view;
    view.setPadding(10, 10, 10, 10);

    // --- Theme customization (ThemeManager) ---
    var theme = view.getTheme();
    // theme.setTextColor(Color.CYAN);
    // theme.setTreeLineColor(Color.parseColor("#fff888"));
    // theme.setSelectedBg(Color.parseColor("#ff4107"));
    view.setTheme(theme);

    // --- Zoom ---
    view.setZoomMod(true);
    view.setZoomScale(50, 300);
    Log.d(TAG, "zoomScale=" + Arrays.toString(view.getZoomScale()));
    // view.setCurrentZoomScale(150); // jump straight to 150% instead of the 100% default
    // view.resetZoom();             // back to 100% without touching the pinch-enabled state

    // --- Icon provider (falls back to DefaultIconProvider if never called) ---
    view.setIconProvider(new FileIconGlide());

    // --- Row appearance ---
    view.setIconArrow(R.drawable.ic_filetree_badge_error);

    int[] rainbowColors = {
      Color.parseColor("#FFD9FF00"),
      Color.parseColor("#FF80FF00"),
      Color.parseColor("#FF00B7FF"),
      Color.parseColor("#FFFF000D"),
      Color.parseColor("#FFFFAFEB"),
      Color.parseColor("#FFFFE4AF"),
      Color.parseColor("#FFFFC7AF"),
      Color.parseColor("#FFAFD6FF"),
      Color.parseColor("#FFFFAFAF")
    };
    view.setRainbowIndentGuideColors(rainbowColors);
    view.setRainbowIndentGuides(true);
    Log.d(TAG, "rainbowIndentGuides=" + view.isRainbowIndentGuides());

    // --- Search bar (hidden by default) ---
    view.setShowSearchBar(true);
    Log.d(TAG, "showSearchBar=" + view.getShowSearchBar());

    // --- Git status badge on a node (normally driven by a GitViewModel/GitManager) ---
    view.setGitStatus(
        "/storage/emulated/0/AndroidIDEProjects/Ghostide33", FilePayload.GIT_MODIFIED);

    // --- Node path + load (Normal View is the initial state; the two buttons switch this) ---
    view.setNodePath(NORMAL_VIEW_ROOT);
    view.loadTree();
    
    view.setAutoExpandSingleChildChains(true);

    // --- Tap handling ---
    view.setClickNode(
        (node, views) ->
            Toast.makeText(getApplication(), node.getAbsolutePath(), Toast.LENGTH_LONG).show());

    // --- Opting out of the built-in long-press selection panel ---
    // setSelectionModeEnabled(false) turns off the automatic "long-press enters selection mode"
    // behavior entirely; setOnNodeLongClickListener lets this activity show its own UI instead
    // (an AlertDialog here, but it could just as easily be a bottom sheet, a custom menu, etc.).
    view.setSelectionModeEnabled(false);
    view.setOnNodeLongClickListener(
        (node, nodeView) -> {
          new AlertDialog.Builder(this)
              .setTitle(node.getName())
              .setMessage(node.getAbsolutePath())
              .setPositiveButton("Close", null)
              .show();
          return true; // handled — the (disabled) built-in selection mode would never have run
          // anyway
        });
    Log.d(TAG, "selectionModeEnabled=" + view.isSelectionModeEnabled());

    // Un-comment to go back to the built-in long-press-to-select behavior instead:
    // view.setSelectionModeEnabled(true);
    // view.setOnNodeLongClickListener(null);

    // --- Drag-to-move (on by default; shown here for completeness) ---
    view.setDragEnabled(true);
    Log.d(TAG, "dragEnabled=" + view.isDragEnabled());
    // view.setDragEnabled(false); // real files/folders become undraggable too (virtual nodes
    //                             // like android-mod's "Gradle Scripts"/"res" already always are)

    // --- Plumbing getters, not something a typical host needs day-to-day ---
    Log.d(TAG, "nodePath=" + view.getNodePath());
    Log.d(TAG, "controller=" + view.getController());
    Log.d(TAG, "adapter=" + view.getAdapter());
    // view.setAdapter(myCustomAdapter);             // swap in a custom TreeAdapter subclass
    // view.setSelectionPanel(myCustomSelectionPanel); // swap in a custom selection action panel

    binding.drawer.addView(view);
  }
  
  @Override
  protected void onDestroy() {
    super.onDestroy();
    // putDestroy() shuts down FileTreeView's internal background executors/file watchers. Needed
    // here since this instance was created in code (added to binding.drawer) rather than inflated
    // from XML, so it doesn't get its own View lifecycle callbacks tied to the Activity's.
    if (drawerFileTreeView != null) {
      drawerFileTreeView.putDestroy();
      drawerFileTreeView = null;
    }
    binding = null;
  }

  public void showFileTreeBottomSheet() {
    var dialog = new BottomSheetDialog(this);
    var view = new FileTreeView(this);
    var theme = view.getTheme();
    // theme.setTextColor(Color.CYAN);
    // theme.setTreeLineColor(Color.parseColor("#fff888"));
    //  theme.setSelectedBg(Color.parseColor("#ff4107"));
    view.setAndroidMod(true);
    view.setZoomMod(true);
    view.setZoomScale(50, 300);
    view.setNodePath("/storage/emulated/0/AndroidIDEProjects/Ghostide33");
    view.loadTree();
    view.setRainbowIndentGuides(true);
    view.setIconProvider(new FileIconGlide());

    if (view != null) {
      dialog.setContentView(view);
    }
    dialog.show();
  }

  void buildinSideSheet() {
    var sideSheet = new SideSheetDialog(this);
    var view = new FileTreeView(this);
    view.setZoomMod(true);
    view.setZoomScale(50, 300);
    view.setNodePath("/storage/emulated/0/");
    view.loadTree();
    view.setIconProvider(new FileIconGlide());
    if (view != null) {
      sideSheet.setContentView(view);
    }
    sideSheet.setCancelable(false);
    sideSheet.setCanceledOnTouchOutside(false);
    sideSheet.show();
  }

  /**
   * Popup window — demonstrates {@link FileTreeView#addVirtualGroup}, grouping a handful of real
   * files from elsewhere on disk under one synthetic "Gradle Scripts"-style folder.
   */
  /*

  void buildinPopWindows() {
    var windows = new PopupWindow(binding.btnPopupWindow);
    windows.setWidth(ViewGroup.LayoutParams.WRAP_CONTENT);
    windows.setHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
    var view = new FileTreeView(this);
    view.setZoomMod(true);
    view.setZoomScale(50, 300);
    view.setIconProvider(new FileIconGlide());
    view.setNodePath(NORMAL_VIEW_ROOT);
    view.loadTree();

    String root = NORMAL_VIEW_ROOT;
    TreeNode settings =
        new TreeNode.Builder("settings.gradle")
            .setId(root + "settings.gradle")
            .setType(TreeNode.TYPE_FILE)
            .setPayload(
                new FilePayload.Builder(root + "settings.gradle", false)
                    .description("(Project: Settings)")
                    .build())
            .build();
    TreeNode buildFile =
        new TreeNode.Builder("build.gradle")
            .setId(root + "build.gradle")
            .setType(TreeNode.TYPE_FILE)
            .setPayload(
                new FilePayload.Builder(root + "build.gradle", false)
                    .description("(Project: Build)")
                    .build())
            .build();
    view.addVirtualGroup(
        "Gradle Scripts", R.drawable.ic_filetree_folder_gradle, Arrays.asList(settings, buildFile));

    windows.setContentView(view);
    windows.setBackgroundDrawable(new ColorDrawable(Color.CYAN));
    windows.showAsDropDown(binding.btnPopupWindow);
  }
  */
}