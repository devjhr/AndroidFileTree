package ir.hanzodev1375.filetreelib.widget;

import android.app.Activity;
import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.util.AttributeSet;
import android.widget.PopupMenu;
import androidx.annotation.NonNull;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import ir.hanzodev1375.filetreelib.R;
import ir.hanzodev1375.filetreelib.core.TreeController;
import ir.hanzodev1375.filetreelib.adapter.TreeAdapter;
import ir.hanzodev1375.filetreelib.icons.IconProvider;
import ir.hanzodev1375.filetreelib.model.FilePayload;
import ir.hanzodev1375.filetreelib.core.TreeModel;
import ir.hanzodev1375.filetreelib.provider.FileTreeProvider;
import ir.hanzodev1375.filetreelib.cache.TreeCache;
import ir.hanzodev1375.filetreelib.drag.DragManager;
import android.widget.Toast;
import ir.hanzodev1375.filetreelib.theme.ThemeManager;
import ir.hanzodev1375.filetreelib.clipboard.ClipboardManager;
import ir.hanzodev1375.filetreelib.filesystem.FileWatcher;
import ir.hanzodev1375.filetreelib.search.TreeSearchEngine;
import ir.hanzodev1375.filetreelib.search.TreeFilter;
import java.util.ArrayList;
import java.util.List;
import ir.hanzodev1375.filetreelib.model.SearchResult;
import ir.hanzodev1375.filetreelib.core.TreeNode;
import java.io.File;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FileTreeView extends LinearLayout {
  private String nodePath;
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
  private List<TreeNode> fullVisibleSnapshot = null;
  private TreeNode lastOpenedFolder = null;

  public FileTreeView(Context context) {
    super(context);
    init();
  }

  public FileTreeView(Context context, AttributeSet attributeset) {
    super(context, attributeset);
    init();
  }

  public FileTreeView(Context context, AttributeSet attributeset, int defStyle) {
    super(context, attributeset, defStyle);
    init();
  }

  void init() {
    View v = LayoutInflater.from(getContext()).inflate(R.layout.layout_nodeview, null, false);
    removeAllViews();
    if (v != null) {
      addView(v);
    }
    treeView = v.findViewById(R.id.tree_view);
    selectionPanel = v.findViewById(R.id.selectionPanel);
    EditText etSearch = v.findViewById(R.id.et_search);

    theme = new ThemeManager(getContext());
    clipboard = new ClipboardManager();
    fileWatcher = new FileWatcher();
    searchEngine = new TreeSearchEngine();
    treeFilter = new TreeFilter();
    searchExecutor = Executors.newSingleThreadScheduledExecutor();
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
  }

  public String getNodePath() {
    return this.nodePath;
  }

  public void setNodePath(String nodePath) {
    this.nodePath = nodePath;
  }

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
          Set<String> matchingIds = new HashSet<>();
          for (SearchResult r : results) matchingIds.add(r.getNodeId());
          List<TreeNode> filteredList =
              treeFilter.filter(controller.getModel().getRoot(), matchingIds);
          ((Activity) getContext())
              .runOnUiThread(
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

  public void loadTree() {
    File rootDir = new File(getNodePath());
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

      adapter.setOnNodeClickListener(
          (node, view) -> {
            if (node.isFolder()) {
              lastOpenedFolder = node;
              controller.toggleNode(node);
            } else {
              Toast.makeText(getContext(), "Open: " + node.getName(), Toast.LENGTH_SHORT).show();
            }
          });
    }

    selectionPanel.attach(controller, clipboard);
    selectionPanel.setActionListener(
        new SelectionActionPanel.ActionListener() {

          @Override
          public void onCopy(@NonNull List<TreeNode> nodes) {
            Toast.makeText(
                    getContext(),
                    nodes.size() + " item(s) copied — open a folder then tap paste",
                    Toast.LENGTH_SHORT)
                .show();
          }

          @Override
          public void onCut(@NonNull List<TreeNode> nodes) {
            Toast.makeText(
                    getContext(),
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
                            getContext(),
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
                            getContext(), "Paste failed: " + error.getMessage(), Toast.LENGTH_SHORT)
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
            new MaterialAlertDialogBuilder(getContext())
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
                                Toast.makeText(getContext(), "Deleted", Toast.LENGTH_SHORT).show();
                              }

                              @Override
                              public void onDeleteFailed(
                                  @NonNull List<TreeNode> n, @NonNull Exception e) {
                                Toast.makeText(
                                        getContext(),
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
            PopupMenu popup = new PopupMenu(getContext(), anchor);
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
    EditText et = new EditText(getContext());
    et.setText(node.getName());
    new MaterialAlertDialogBuilder(getContext())
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
                        Toast.makeText(getContext(), "Renamed to " + nw, Toast.LENGTH_SHORT).show();
                      }

                      @Override
                      public void onRenameFailed(@NonNull TreeNode n, @NonNull Exception e) {
                        Toast.makeText(getContext(), "Rename failed", Toast.LENGTH_SHORT).show();
                      }
                    });
              }
            })
        .setNegativeButton("Cancel", null)
        .show();
  }

  private void showCreateDialog(@NonNull TreeNode parent, int type) {
    EditText et = new EditText(getContext());
    et.setHint(type == TreeNode.TYPE_FOLDER ? "Folder name" : "File name");
    new MaterialAlertDialogBuilder(getContext())
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
                        Toast.makeText(getContext(), "Created", Toast.LENGTH_SHORT).show();
                      }

                      @Override
                      public void onCreateFailed(@NonNull String nm, @NonNull Exception e) {
                        Toast.makeText(
                                getContext(), "Failed: " + e.getMessage(), Toast.LENGTH_SHORT)
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

  public void setIconProvider(IconProvider ic) {
    adapter.setIconProvider(ic);
  }

  /**
   * Sets the git status of a file/folder — the status of one of FilePayload.GIT_* or OR multiple
   * (e.g. FilePayload.GIT_MODIFIED | FilePayload.GIT_STAGED). Determining which badge
   * (ic_badge_git_modified / staged / conflict) to show is automatic, in
   * DefaultIconProvider.getBadgeIcon() is done from this status — here we just set the status and
   * refresh the row. It should be called on the Main thread.
   *
   * @param absolutePath Absolute path of the file/folder (same id as the node in the tree)
   * @param gitStatus Bitmask from FilePayload.GIT_*
   */
  public void setGitStatus(@NonNull String absolutePath, int gitStatus) {
    if (controller == null) return;
    TreeNode node = controller.getModel().findNodeById(absolutePath);
    if (node == null) return;
    FilePayload payload = node.getPayload(FilePayload.class);
    if (payload == null) return;
    payload.setGitStatus(gitStatus);
    controller.getModel().notifyNodeChanged(node);
    if (adapter != null) adapter.refreshNode(node.getId());
  }

  public void putDestroy() {

    if (selectionPanel != null) selectionPanel.detach();
    fileWatcher.unwatchAll();
    searchExecutor.shutdownNow();
    if (controller != null) controller.destroy();
  }

  public SelectionActionPanel getSelectionPanel() {
    return this.selectionPanel;
  }

  public void setSelectionPanel(SelectionActionPanel selectionPanel) {
    this.selectionPanel = selectionPanel;
  }

  public TreeAdapter getAdapter() {
    return this.adapter;
  }

  public void setAdapter(TreeAdapter adapter) {
    this.adapter = adapter;
  }

  public ThemeManager getTheme() {
    return this.theme;
  }

  public void setTheme(ThemeManager theme) {
    this.theme = theme;
  }
}
