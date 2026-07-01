package ir.hanzodev1375.filetreeapp;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import ir.hanzodev1375.filetreeapp.databinding.ActivityMainBinding;
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
    binding.textView.setOnLongClickListener(
        v -> {
          showFileTreeBottomSheet();
          return true;
        });
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
    view.setZoomScale(50,300);
    view.setNodePath("/storage/emulated/0/");
    view.loadTree();
    view.setIconProvider(new FileIconGlide());

    if (view != null) {
      dialog.setContentView(view);
    }
    dialog.show();
  }
}
