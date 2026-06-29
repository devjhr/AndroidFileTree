package ir.hanzodev1375.filetreeapp;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.ContextMenu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import ir.hanzodev1375.filetreelib.FileIconGlide;
import ir.hanzodev1375.filetreelib.drag.DragManager;
import ir.hanzodev1375.filetreelib.widget.TreeView;
import ir.hanzodev1375.filetreelib.core.TreeController;
import ir.hanzodev1375.filetreelib.adapter.TreeAdapter;
import ir.hanzodev1375.filetreelib.theme.ThemeManager;
import ir.hanzodev1375.filetreelib.clipboard.ClipboardManager;
import ir.hanzodev1375.filetreelib.filesystem.FileWatcher;
import ir.hanzodev1375.filetreelib.search.TreeSearchEngine;
import ir.hanzodev1375.filetreelib.search.TreeFilter;
import ir.hanzodev1375.filetreelib.core.TreeNode;
import ir.hanzodev1375.filetreelib.model.FilePayload;
import ir.hanzodev1375.filetreelib.core.TreeModel;
import ir.hanzodev1375.filetreelib.provider.FileTreeProvider;
import ir.hanzodev1375.filetreelib.cache.TreeCache;
import ir.hanzodev1375.filetreelib.core.TreeState;
import ir.hanzodev1375.filetreelib.model.SearchResult;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SampleExplorerActivity extends AppCompatActivity {

  private static final int PERM_REQUEST_LEGACY = 1001;
  private static final int PERM_REQUEST_ALL_FILES = 1002;
  private static final String KEY_TREE_STATE = "tree_state";

  private TreeView treeView;
  private TreeController controller;
  private TreeAdapter adapter;
  private ThemeManager theme;
  private ClipboardManager clipboard;
  private FileWatcher fileWatcher;
  private TreeSearchEngine searchEngine;
  private TreeFilter treeFilter;
  private ExecutorService searchExecutor;

  // Selection bar
  private View barSelection;
  private TextView tvSelectionCount;

  // Clipboard bar
  private View barClipboard;
  private TextView tvClipboardInfo;

  // پوشه‌ای که کاربر آخرین بار بازش کرده — هدف paste
  private TreeNode lastOpenedFolder = null;

  private Bundle pendingSavedState = null;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_sample_explorer);

    treeView = findViewById(R.id.tree_view);
    EditText etSearch = findViewById(R.id.et_search);

    barSelection = findViewById(R.id.bar_selection);
    tvSelectionCount = findViewById(R.id.tv_selection_count);
    barClipboard = findViewById(R.id.bar_clipboard);
    tvClipboardInfo = findViewById(R.id.tv_clipboard_info);

    theme = new ThemeManager(this);
    clipboard = new ClipboardManager();
    fileWatcher = new FileWatcher();
    searchEngine = new TreeSearchEngine();
    treeFilter = new TreeFilter();
    searchExecutor = Executors.newSingleThreadExecutor();

    etSearch.addTextChangedListener(
        new TextWatcher() {
          @Override
          public void beforeTextChanged(CharSequence s, int i, int c, int a) {}

          @Override
          public void onTextChanged(CharSequence s, int i, int b, int c) {
            performSearch(s.toString());
          }

          @Override
          public void afterTextChanged(Editable s) {}
        });

    // Selection bar buttons
    findViewById(R.id.btn_select_all).setOnClickListener(v -> onActionSelectAll());
    findViewById(R.id.btn_copy).setOnClickListener(v -> onActionCopy());
    findViewById(R.id.btn_cut).setOnClickListener(v -> onActionCut());
    findViewById(R.id.btn_delete).setOnClickListener(v -> onActionDelete());
    findViewById(R.id.btn_cancel_selection)
        .setOnClickListener(
            v -> {
              if (adapter != null) adapter.exitSelectionMode();
            });

    // Clipboard bar buttons
    findViewById(R.id.btn_paste).setOnClickListener(v -> onActionPaste());
    findViewById(R.id.btn_cancel_clipboard)
        .setOnClickListener(
            v -> {
              clipboard.clear();
              barClipboard.setVisibility(View.GONE);
            });

    pendingSavedState = savedInstanceState;
    checkPermissionsAndLoad();
  }

  // -------------------------------------------------------------------------
  // Permissions
  // -------------------------------------------------------------------------

  private void checkPermissionsAndLoad() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      if (Environment.isExternalStorageManager()) {
        loadTree(pendingSavedState);
      } else {
        new AlertDialog.Builder(this)
            .setTitle("Storage Permission")
            .setMessage("This app needs access to all files to browse the filesystem.")
            .setPositiveButton(
                "Grant",
                (d, w) -> {
                  Intent intent =
                      new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                  intent.setData(Uri.parse("package:" + getPackageName()));
                  startActivityForResult(intent, PERM_REQUEST_ALL_FILES);
                })
            .setNegativeButton("Cancel", null)
            .show();
      }
    } else {
      boolean read =
          ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
              == PackageManager.PERMISSION_GRANTED;
      boolean write =
          ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
              == PackageManager.PERMISSION_GRANTED;
      if (read && write) {
        loadTree(pendingSavedState);
      } else {
        ActivityCompat.requestPermissions(
            this,
            new String[] {
              Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE
            },
            PERM_REQUEST_LEGACY);
      }
    }
  }

  @Override
  public void onRequestPermissionsResult(
      int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    if (requestCode == PERM_REQUEST_LEGACY) {
      if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
        loadTree(pendingSavedState);
      } else {
        Toast.makeText(this, "Storage permission denied", Toast.LENGTH_SHORT).show();
      }
    }
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    if (requestCode == PERM_REQUEST_ALL_FILES) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
          && Environment.isExternalStorageManager()) {
        loadTree(pendingSavedState);
      } else {
        Toast.makeText(this, "Storage permission denied", Toast.LENGTH_SHORT).show();
      }
    }
  }

  // -------------------------------------------------------------------------
  // Tree setup
  // -------------------------------------------------------------------------

  private void loadTree(Bundle savedState) {
    File rootDir = new File("/storage/emulated/0");
    FilePayload rootPayload = new FilePayload.Builder(rootDir.getAbsolutePath(), true).build();
    TreeNode rootNode = TreeNode.root();

    TreeNode storageNode =
        new TreeNode.Builder(rootDir.getName())
            .setId(rootDir.getAbsolutePath())
            .setType(TreeNode.TYPE_FOLDER)
            .setHasChildren(true)
            .setPayload(rootPayload)
            .build();
    rootNode.addChild(storageNode);

    TreeModel model = new TreeModel(rootNode);
    FileTreeProvider provider = new FileTreeProvider();

    controller =
        new TreeController.Builder(provider).model(model).cache(new TreeCache(512)).build();

    treeView.setup(controller, theme);
    adapter = treeView.getTreeAdapter();
    DragManager dragManager = new DragManager(provider);
    treeView.attachDragManager(dragManager);
    if (adapter != null) {
      adapter.setClipboardManager(clipboard);
      adapter.setIconProvider(new FileIconGlide());

      adapter.setOnNodeClickListener(
          (node, view) -> {
            if (node.isFolder()) {
              lastOpenedFolder = node;
              controller.toggleNode(node);
            } else {
              Toast.makeText(this, "Open: " + node.getName(), Toast.LENGTH_SHORT).show();
              if (node.getAbsolutePath().endsWith(".png")) {
                Toast.makeText(this, node.getAbsolutePath(), Toast.LENGTH_LONG).show();
                ImageView img = new ImageView(this);

                img.setLayoutParams(
                    new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1000));

                img.setBackgroundColor(Color.RED);

                new AlertDialog.Builder(this).setTitle("rjejek").setView(img).show();

                Glide.with(this).load(new File(node.getAbsolutePath())).into(img);
              }
            }
          });

      adapter.setOnSelectionModeChangeListener(
          new TreeAdapter.OnSelectionModeChangeListener() {
            @Override
            public void onSelectionModeEntered() {
              barSelection.setVisibility(View.VISIBLE);
              tvSelectionCount.setText("0 selected");
            }

            @Override
            public void onSelectionModeExited() {
              barSelection.setVisibility(View.GONE);
            }

            @Override
            public void onSelectionCountChanged(int count) {
              tvSelectionCount.setText(count + " selected");
            }
          });
    }

    if (savedState != null) {
      TreeState state = savedState.getParcelable(KEY_TREE_STATE);
      if (state != null) controller.restoreState(state);
    }

    fileWatcher.addListener(
        new FileWatcher.FileChangeListener() {
          @Override
          public void onFileCreated(@NonNull String path) {
            refreshParent(path);
          }

          @Override
          public void onFileDeleted(@NonNull String path) {
            refreshParent(path);
          }

          @Override
          public void onFileModified(@NonNull String path) {}

          @Override
          public void onFileRenamed(@NonNull String o, @NonNull String n) {
            refreshParent(n);
          }
        });
    fileWatcher.watch(rootDir.getAbsolutePath());
  }

  // -------------------------------------------------------------------------
  // Selection bar actions
  // -------------------------------------------------------------------------

  private void onActionSelectAll() {
    controller.getSelectionManager().setVisibleNodes(adapter.getCurrentList());
    controller.getSelectionManager().selectAll();
  }

  private void onActionCopy() {
    List<TreeNode> selected = controller.getSelectedNodes();
    if (selected.isEmpty()) return;
    clipboard.copy(selected);
    // خروج از selection mode — checkboxها پنهان میشن
    adapter.exitSelectionMode();
    // clipboard bar نشون داده میشه
    showClipboardBar(selected.size(), false);
  }

  private void onActionCut() {
    List<TreeNode> selected = controller.getSelectedNodes();
    if (selected.isEmpty()) return;
    clipboard.cut(selected);
    adapter.exitSelectionMode();
    showClipboardBar(selected.size(), true);
  }

  private void onActionDelete() {
    List<TreeNode> selected = controller.getSelectedNodes();
    if (selected.isEmpty()) return;
    new AlertDialog.Builder(this)
        .setTitle("Delete")
        .setMessage("Delete " + selected.size() + " item(s)?")
        .setPositiveButton(
            "Delete",
            (d, w) ->
                controller.deleteNode(
                    selected.get(0),
                    new TreeController.DeleteCallback() {
                      @Override
                      public void onDeleted(@NonNull List<TreeNode> nodes) {
                        adapter.exitSelectionMode();
                        Toast.makeText(SampleExplorerActivity.this, "Deleted", Toast.LENGTH_SHORT)
                            .show();
                      }

                      @Override
                      public void onDeleteFailed(
                          @NonNull List<TreeNode> nodes, @NonNull Exception e) {
                        Toast.makeText(
                                SampleExplorerActivity.this,
                                "Delete failed: " + e.getMessage(),
                                Toast.LENGTH_SHORT)
                            .show();
                      }
                    }))
        .setNegativeButton("Cancel", null)
        .show();
  }

  // -------------------------------------------------------------------------
  // Clipboard bar
  // -------------------------------------------------------------------------

  private void showClipboardBar(int count, boolean isCut) {
    String op = isCut ? "Cut" : "Copied";
    tvClipboardInfo.setText(op + " " + count + " item(s) — navigate to destination");
    barClipboard.setVisibility(View.VISIBLE);
  }

  private void onActionPaste() {
    if (!clipboard.hasClipboard()) return;

    // هدف paste: آخرین پوشه‌ای که کاربر بازش کرده
    TreeNode destination = lastOpenedFolder;
    if (destination == null) {
      Toast.makeText(this, "Open a folder first to paste into it", Toast.LENGTH_SHORT).show();
      return;
    }

    List<TreeNode> nodes = new ArrayList<>(clipboard.getClipboard());
    boolean isCut = clipboard.isCut();

    controller.getDataProvider();
    // عملیات واقعی copy/move از طریق FileTreeProvider
    new Thread(
            () -> {
              try {
                if (isCut) {
                  controller.getDataProvider().moveNodes(nodes, destination);
                } else {
                  controller.getDataProvider().copyNodes(nodes, destination);
                }
                runOnUiThread(
                    () -> {
                      // invalidate cache پوشه مقصد تا دفعه بعد lazy load مجدد انجام بشه
                      controller.getVisibleList();
                      if (isCut) clipboard.clear();
                      barClipboard.setVisibility(View.GONE);
                      Toast.makeText(
                              this,
                              (isCut ? "Moved " : "Copied ")
                                  + nodes.size()
                                  + " item(s) to "
                                  + destination.getName(),
                              Toast.LENGTH_SHORT)
                          .show();
                    });
              } catch (Exception e) {
                runOnUiThread(
                    () ->
                        Toast.makeText(this, "Paste failed: " + e.getMessage(), Toast.LENGTH_SHORT)
                            .show());
              }
            })
        .start();
  }

  // -------------------------------------------------------------------------
  // Back button
  // -------------------------------------------------------------------------

  @Override
  public void onBackPressed() {
    if (adapter != null && adapter.isInSelectionMode()) {
      adapter.exitSelectionMode();
    } else if (barClipboard.getVisibility() == View.VISIBLE) {
      clipboard.clear();
      barClipboard.setVisibility(View.GONE);
    } else {
      super.onBackPressed();
    }
  }

  // -------------------------------------------------------------------------
  // Context menu
  // -------------------------------------------------------------------------

  @Override
  public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo info) {}

  // -------------------------------------------------------------------------
  // Dialogs
  // -------------------------------------------------------------------------

  private void showRenameDialog(@NonNull TreeNode node) {
    EditText et = new EditText(this);
    et.setText(node.getName());
    new AlertDialog.Builder(this)
        .setTitle("Rename")
        .setView(et)
        .setPositiveButton(
            "Rename",
            (d, w) -> {
              String newName = et.getText().toString().trim();
              if (!newName.isEmpty()) {
                controller.renameNode(
                    node,
                    newName,
                    new TreeController.RenameCallback() {
                      @Override
                      public void onRenamed(
                          @NonNull TreeNode n, @NonNull String o, @NonNull String nw) {
                        Toast.makeText(
                                SampleExplorerActivity.this, "Renamed to " + nw, Toast.LENGTH_SHORT)
                            .show();
                      }

                      @Override
                      public void onRenameFailed(@NonNull TreeNode n, @NonNull Exception e) {
                        Toast.makeText(
                                SampleExplorerActivity.this, "Rename failed", Toast.LENGTH_SHORT)
                            .show();
                      }
                    });
              }
            })
        .setNegativeButton("Cancel", null)
        .show();
  }

  private void showCreateDialog(@NonNull TreeNode parent, int type) {
    EditText et = new EditText(this);
    et.setHint(type == TreeNode.TYPE_FOLDER ? "Folder name" : "File name");
    new AlertDialog.Builder(this)
        .setTitle(type == TreeNode.TYPE_FOLDER ? "New Folder" : "New File")
        .setView(et)
        .setPositiveButton(
            "Create",
            (d, w) -> {
              String name = et.getText().toString().trim();
              if (!name.isEmpty()) {
                TreeController.CreateCallback cb =
                    new TreeController.CreateCallback() {
                      @Override
                      public void onCreated(@NonNull TreeNode n) {
                        Toast.makeText(SampleExplorerActivity.this, "Created", Toast.LENGTH_SHORT)
                            .show();
                      }

                      @Override
                      public void onCreateFailed(@NonNull String nm, @NonNull Exception e) {
                        Toast.makeText(
                                SampleExplorerActivity.this,
                                "Failed: " + e.getMessage(),
                                Toast.LENGTH_SHORT)
                            .show();
                      }
                    };
                if (type == TreeNode.TYPE_FOLDER) controller.createFolder(parent, name, cb);
                else controller.createFile(parent, name, cb);
              }
            })
        .setNegativeButton("Cancel", null)
        .show();
  }

  private List<TreeNode> fullVisibleSnapshot = null; // snapshot قبل از filter

  private void performSearch(@NonNull String query) {
    if (query.isEmpty()) {
      // برگشت به لیست کامل
      if (adapter != null) {
        adapter.clearSearch();
        if (fullVisibleSnapshot != null) {
          adapter.submitNewList(fullVisibleSnapshot);
          fullVisibleSnapshot = null;
        } else {
          adapter.submitNewList(controller.getVisibleList().snapshot());
        }
      }
      return;
    }

    // snapshot اولین بار ذخیره کن
    if (fullVisibleSnapshot == null) {
      fullVisibleSnapshot = new ArrayList<>(controller.getVisibleList().snapshot());
    }

    searchExecutor.submit(
        () -> {
          List<SearchResult> results = searchEngine.search(controller.getModel().getRoot(), query);

          // ID های match شده
          java.util.Set<String> matchingIds = new java.util.HashSet<>();
          for (SearchResult r : results) matchingIds.add(r.getNodeId());

          // فیلتر کن — ancestor ها هم نشون داده میشن
          List<TreeNode> filteredList =
              treeFilter.filter(controller.getModel().getRoot(), matchingIds);

          runOnUiThread(
              () -> {
                if (adapter != null) {
                  adapter.setSearchResults(results);
                  adapter.submitNewList(filteredList);
                }
              });
        });
  }

  private void refreshParent(@NonNull String changedPath) {
    String parent = new File(changedPath).getParent();
    if (parent != null) controller.getVisibleList();
  }

  // -------------------------------------------------------------------------
  // Lifecycle
  // -------------------------------------------------------------------------

  @Override
  protected void onSaveInstanceState(@NonNull Bundle out) {
    super.onSaveInstanceState(out);
    if (controller != null) out.putParcelable(KEY_TREE_STATE, controller.saveState());
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    fileWatcher.unwatchAll();
    searchExecutor.shutdownNow();
    if (controller != null) controller.destroy();
  }
}
