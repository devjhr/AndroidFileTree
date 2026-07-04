package ir.hanzodev1375.filetreeapp;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.PopupWindow;
import android.widget.Toast;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.sidesheet.SideSheetBehavior;
import com.google.android.material.sidesheet.SideSheetDialog;
import ir.hanzodev1375.filetreeapp.databinding.ActivityMainBinding;
import ir.hanzodev1375.filetreelib.model.FilePayload;
import ir.hanzodev1375.filetreelibglide.FileIconGlide;
import ir.hanzodev1375.filetreelib.widget.FileTreeView;

public class MainActivity extends AppCompatActivity {
  private ActivityMainBinding binding;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    binding = ActivityMainBinding.inflate(getLayoutInflater());
    setContentView(binding.getRoot());
    binding.textView.setText("Hello, Basic Activity!");
    binding.textView.setOnClickListener(
        v -> {
          startActivity(new Intent(getApplicationContext(), SampleExplorerActivity.class));
        });
    binding.sidesheet.setOnClickListener(
        v -> {
          buildinPopWindows();
        });

    binding.textView.setOnLongClickListener(
        v -> {
          showFileTreeBottomSheet();
          return true;
        });

    FileTreeView view = new FileTreeView(this);
    view.setPadding(10, 10, 10, 10);
    view.setZoomMod(true);
    view.setZoomScale(50, 300);
    view.setIconArrow(R.drawable.ic_badge_error);
    view.setNodePath("/storage/emulated/0/");
    view.setGitStatus(
        "/storage/emulated/0/AndroidIDEProjects/Ghostide33", FilePayload.GIT_MODIFIED);
    view.loadTree();
    int[] colors = {
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
    view.setRainbowIndentGuideColors(colors);
    view.setRainbowIndentGuides(true);

    view.setClickNode(
        (node, views) -> {
          Toast.makeText(getApplication(), node.getAbsolutePath(), Toast.LENGTH_LONG).show();
        });

    binding.drawer.addView(view);

    ActionBarDrawerToggle _toggle =
        new ActionBarDrawerToggle(
            MainActivity.this, binding.drawerLayout, null, R.string.app_name, R.string.app_name);
    binding.drawerLayout.addDrawerListener(_toggle);
    _toggle.syncState();
    if (binding.drawerLayout.isDrawerOpen(GravityCompat.END)) {
      binding.drawerLayout.setDrawerLockMode(
          DrawerLayout.LOCK_MODE_LOCKED_CLOSED, GravityCompat.END);
    }
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    binding = null;
  }

  public void showFileTreeBottomSheet() {
    var dialog = new BottomSheetDialog(this);
    var view = new FileTreeView(this);
    var theme = view.getTheme();
    // theme.setTextColor(Color.CYAN);
    // theme.setTreeLineColor(Color.parseColor("#fff888"));
    //  theme.setSelectedBg(Color.parseColor("#ff4107"));
    view.setZoomMod(true);
    view.setZoomScale(50, 300);
    view.setNodePath("/storage/emulated/0/");
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

  void buildinPopWindows() {
    var windows = new PopupWindow(binding.sidesheet);
    windows.setWidth(ViewGroup.LayoutParams.WRAP_CONTENT);
    windows.setHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
    var view = new FileTreeView(this);
    view.setZoomMod(true);
    view.setZoomScale(50, 300);
    view.setNodePath("/storage/emulated/0/");
    view.loadTree();
    view.setIconProvider(new FileIconGlide());
    windows.setContentView(view);
    windows.setBackgroundDrawable(new ColorDrawable(Color.CYAN));
    windows.showAsDropDown(binding.sidesheet);
  }
}
