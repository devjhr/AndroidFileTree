package ir.hanzodev1375.filetreeapp;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import ir.hanzodev1375.filetreeapp.databinding.ActivityMainBinding;

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
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    binding = null;
  }
}
