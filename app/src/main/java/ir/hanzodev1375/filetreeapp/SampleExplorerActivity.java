package ir.hanzodev1375.filetreeapp;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;
import ir.hanzodev1375.filetreelibglide.FileIconGlide; 
import ir.hanzodev1375.filetreelib.drag.DragManager;
import ir.hanzodev1375.filetreelib.widget.TreeView;
import ir.hanzodev1375.filetreelib.widget.SelectionActionPanel;
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
  private SelectionActionPanel selectionPanel;
  private TreeController controller;
  private TreeAdapter adapter;
  private ThemeManager theme;
  private ClipboardManager clipboard;
  private FileWatcher fileWatcher;
  private TreeSearchEngine searchEngine;
  private TreeFilter treeFilter;
  private ExecutorService searchExecutor;

  private TreeNode lastOpenedFolder = null;
  private Bundle pendingSavedState = null;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_sample_explorer);

    treeView = findViewById(R.id.tree_view);
    selectionPanel = findViewById(R.id.selectionPanel);
    EditText etSearch = findViewById(R.id.et_search);

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

    pendingSavedState = savedInstanceState;
    checkPermissionsAndLoad();
  }

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

    DragManager dragManager = new DragManager(controller);
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
              if (node.getAbsolutePath() != null && node.getAbsolutePath().endsWith(".png")) {
                ImageView img = new ImageView(this);
                img.setLayoutParams(
                    new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1000));
                img.setBackgroundColor(Color.RED);
                new AlertDialog.Builder(this).setTitle("rjejek").setView(img).show();
                Glide.with(this).load(new File(node.getAbsolutePath())).into(img);
              }
            }
          });
    }

    selectionPanel.attach(controller, clipboard);
    selectionPanel.setActionListener(
        new SelectionActionPanel.ActionListener() {

          @Override
          public void onCopy(@NonNull List<TreeNode> nodes) {
            Toast.makeText(
                    SampleExplorerActivity.this,
                    nodes.size() + " item(s) copied — open a folder then tap paste",
                    Toast.LENGTH_SHORT)
                .show();
          }

          @Override
          public void onCut(@NonNull List<TreeNode> nodes) {
            Toast.makeText(
                    SampleExplorerActivity.this,
                    nodes.size() + " item(s) cut — open a folder then tap paste",
                    Toast.LENGTH_SHORT)
                .show();
          }

          @Override
          public void onPaste(TreeNode targetFolder, @NonNull ClipboardManager cb) {
            TreeNode dest =
                targetFolder != null
                    ? targetFolder
                    : (lastOpenedFolder != null
                        ? lastOpenedFolder
                        : controller.getModel().getRoot());

            List<TreeNode> nodes = new ArrayList<>(cb.getClipboard());
            boolean isCut = cb.isCut();

            // Use controller.pasteNodes() so model events fire and UI updates
            controller.pasteNodes(
                dest,
                nodes,
                isCut,
                new TreeController.PasteCallback() {
                  @Override
                  public void onPasted(@NonNull List<TreeNode> pastedNodes) {
                    cb.clear(); // cut এবং copy উভয় ক্ষেত্রেই clipboard clear
                    controller
                        .clearSelection(); // এটা selection listener trigger করবে → panel hide হবে
                    Toast.makeText(
                            SampleExplorerActivity.this,
                            (isCut ? "Moved " : "Copied ")
                                + nodes.size()
                                + " item(s) to "
                                + dest.getName(),
                            Toast.LENGTH_SHORT)
                        .show();
                  }

                  @Override
                  public void onPasteFailed(@NonNull Exception error) {
                    Toast.makeText(
                            SampleExplorerActivity.this,
                            "Paste failed: " + error.getMessage(),
                            Toast.LENGTH_SHORT)
                        .show();
                  }
                });
          }

          @Override
          public void onRename(@NonNull TreeNode node) {
            showRenameDialog(node);
          }

          @Override
          public void onDelete(@NonNull List<TreeNode> nodes) {
            new AlertDialog.Builder(SampleExplorerActivity.this)
                .setTitle("Delete")
                .setMessage("Delete " + nodes.size() + " item(s)?")
                .setPositiveButton(
                    "Delete",
                    (d, w) ->
                        // Pass first node; deleteNode() internally uses all selected nodes
                        controller.deleteNode(
                            nodes.get(0),
                            new TreeController.DeleteCallback() {
                              @Override
                              public void onDeleted(@NonNull List<TreeNode> deleted) {
                                Toast.makeText(
                                        SampleExplorerActivity.this, "Deleted", Toast.LENGTH_SHORT)
                                    .show();
                              }

                              @Override
                              public void onDeleteFailed(
                                  @NonNull List<TreeNode> n, @NonNull Exception e) {
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

          @Override
          public void onMore(@NonNull List<TreeNode> nodes, @NonNull View anchor) {
            PopupMenu popup = new PopupMenu(SampleExplorerActivity.this, anchor);
            popup.getMenu().add(0, 1, 0, "Rename");
            popup.getMenu().add(0, 2, 0, "New Folder");
            popup.getMenu().add(0, 3, 0, "New File");
            popup.setOnMenuItemClickListener(
                item -> {
                  switch (item.getItemId()) {
                    case 1:
                      if (nodes.size() == 1) showRenameDialog(nodes.get(0));
                      return true;
                    case 2:
                      TreeNode p2 =
                          nodes.size() == 1 && nodes.get(0).isFolder()
                              ? nodes.get(0)
                              : (lastOpenedFolder != null
                                  ? lastOpenedFolder
                                  : controller.getModel().getRoot());
                      showCreateDialog(p2, TreeNode.TYPE_FOLDER);
                      return true;
                    case 3:
                      TreeNode p3 =
                          nodes.size() == 1 && nodes.get(0).isFolder()
                              ? nodes.get(0)
                              : (lastOpenedFolder != null
                                  ? lastOpenedFolder
                                  : controller.getModel().getRoot());
                      showCreateDialog(p3, TreeNode.TYPE_FILE);
                      return true;
                  }
                  return false;
                });
            popup.show();
          }

          @Override
          public void onSelectionCleared() {}
        });

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

  private List<TreeNode> fullVisibleSnapshot = null;

  private void performSearch(@NonNull String query) {
    if (query.isEmpty()) {
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
    if (fullVisibleSnapshot == null) {
      fullVisibleSnapshot = new ArrayList<>(controller.getVisibleList().snapshot());
    }
    searchExecutor.submit(
        () -> {
          List<SearchResult> results = searchEngine.search(controller.getModel().getRoot(), query);
          java.util.Set<String> matchingIds = new java.util.HashSet<>();
          for (SearchResult r : results) matchingIds.add(r.getNodeId());
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

  @Override
  public void onBackPressed() {
    if (selectionPanel != null && selectionPanel.isSelectionActive()) {
      if (clipboard != null) clipboard.clear();
      controller.clearSelection();
    } else {
      super.onBackPressed();
    }
  }

  @Override
  protected void onSaveInstanceState(@NonNull Bundle out) {
    super.onSaveInstanceState(out);
    if (controller != null) out.putParcelable(KEY_TREE_STATE, controller.saveState());
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    if (selectionPanel != null) selectionPanel.detach();
    fileWatcher.unwatchAll();
    searchExecutor.shutdownNow();
    if (controller != null) controller.destroy();
  }
}
