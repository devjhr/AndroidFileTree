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
import androidx.annotation.Nullable;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputLayout;
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
import ir.hanzodev1375.filetreelib.core.AndroidModTreeBuilder;
import ir.hanzodev1375.filetreelib.core.ExpandManager;
import ir.hanzodev1375.filetreelib.model.SearchResult;
import ir.hanzodev1375.filetreelib.core.TreeNode;
import java.io.File;
import java.util.Set;
import java.util.HashSet;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import ir.hanzodev1375.filetreelib.callbacks.OnNodeCallBack;

public class FileTreeView extends LinearLayout {
  private String nodePath;
  private TreeView treeView;
  private TwoDScrollView scrollContainer;
  private SelectionActionPanel selectionPanel;
  private TreeController controller;
  private TreeAdapter adapter;
  private ThemeManager theme;
  private ClipboardManager clipboard;
  private FileWatcher fileWatcher;
  private boolean showSearchBar = false;
  private TreeSearchEngine searchEngine;
  private TreeFilter treeFilter;
  private ExecutorService searchExecutor;
  private List<TreeNode> fullVisibleSnapshot = null;
  private TreeNode lastOpenedFolder = null;
  private OnNodeCallBack click;
  private int pendingIconArrowRes = 0;
  @Nullable private IconProvider pendingIconProvider = null;
  private boolean pendingSelectionModeEnabled = true;
  private boolean pendingDragEnabled = true;
  private TreeAdapter.OnNodeLongClickListener pendingLongClickListener = null;
  private BreadcrumbBar breadcrumbbar;
  private TreeNode rootTreeNode;
  private boolean androidMod = false;
  private TreeNode androidModGroup = null;
  private DragManager dragManager;
  private boolean autoExpandSingleChildChains = false;
  private ExecutorService androidModExecutor;
  private int androidModApplyToken = 0;
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
    scrollContainer = v.findViewById(R.id.two_d_scroll_view);
    selectionPanel = v.findViewById(R.id.selectionPanel);
    breadcrumbbar = v.findViewById(R.id.breadcrumb_bar);
    
    EditText etSearch = v.findViewById(R.id.et_search);
    TextInputLayout nodesearch = v.findViewById(R.id.nodesearch);
    if (!showSearchBar) {
      nodesearch.setVisibility(View.GONE);
    } else nodesearch.setVisibility(View.VISIBLE);
    theme = new ThemeManager(getContext());
    clipboard = new ClipboardManager();
    fileWatcher = new FileWatcher();
    searchEngine = new TreeSearchEngine();
    treeFilter = new TreeFilter();
    searchExecutor = Executors.newSingleThreadScheduledExecutor();
    androidModExecutor = Executors.newSingleThreadExecutor();
    breadcrumbbar.setTheme(theme);
    // NOTE: root path is set from loadTree() once nodePath/rootDir are known — calling it here
    // would pass null (setNodePath() hasn't run yet) and blow up in BreadcrumbBar.splitPath().
    breadcrumbbar.setOnSegmentClickListener(
        new BreadcrumbBar.OnSegmentClickListener() {
          @Override
          public void onRootSegmentClick(@NonNull String path) {
            // Tapped a root-path segment -> re-root the whole tree there.
            setNodePath(path);
            loadTree();
          }

          @Override
          public void onNodeSegmentClick(@NonNull TreeNode node) {
            // Tapped a node segment -> just reveal it in the tree that's already loaded.
            if (controller == null) return;
            controller.revealNode(node);
            breadcrumbbar.setSelectedNode(node, rootTreeNode);
          }
        });

    // Registered once here (not in loadTree()) so re-rooting the tree via the breadcrumb doesn't
    // pile up duplicate listeners that would each fire on every filesystem change.
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
    // loadTree() can now be called more than once per view (the breadcrumb re-roots the tree by
    // calling it again) — tear down what the previous call created so we don't leak the old
    // background executor or keep watching a directory we no longer show.
    if (controller != null) controller.destroy();
    if (fileWatcher != null) fileWatcher.unwatchAll();
    androidModGroup = null;

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
    rootTreeNode = storageNode;

    TreeModel model = new TreeModel(rootNode);
    FileTreeProvider provider = new FileTreeProvider();

    controller =
        new TreeController.Builder(provider).model(model).cache(new TreeCache(512)).build();

    // Auto-descend a chain of single-child folders — e.g. Android Studio's "compact middle
    // packages": expanding a folder that turns out to contain exactly one item keeps expanding
    // that item too, and so on, stopping the moment a folder has 2+ items (or the chain ends at a
    // file). Works the same whether the children just arrived from a real lazy disk load or were
    // already sitting there from android-mod's synthetic tree — both paths fire the same
    // onNodesExpanded callback once a node's children become visible, so one listener covers both
    // modes without needing to know which one is active.
    controller
        .getExpandManager()
        .addExpandListener(
            new ExpandManager.ExpandListener() {
              @Override
              public void onNodesExpanded(
                  @NonNull TreeNode parent, @NonNull List<TreeNode> inserted, int insertPos) {
                if (!autoExpandSingleChildChains) return;
                if (parent.getChildCount() != 1) return;
                TreeNode onlyChild = parent.getChildren().get(0);
                if (onlyChild.isFolder() || onlyChild.isVirtual()) {
                  controller.expandNode(onlyChild);
                }
              }

              @Override
              public void onNodesCollapsed(
                  @NonNull TreeNode parent, @NonNull List<TreeNode> removed, int removePos) {}
            });

    treeView.setup(controller, theme);
    adapter = treeView.getTreeAdapter();

    // Fresh tree -> breadcrumb shows just the root path segments (clears any previously
    // selected-node extension from the tree we just replaced).
    breadcrumbbar.setRootPath(rootDir.getAbsolutePath());

    dragManager = new DragManager(controller);
    dragManager.setDragLocked(!pendingDragEnabled);
    treeView.attachDragManager(dragManager);

    if (adapter != null) {
      adapter.setClipboardManager(clipboard);
      if (pendingIconArrowRes != 0) {
        adapter.setIconArrow(pendingIconArrowRes);
      }
      if (pendingIconProvider != null) {
        adapter.setIconProvider(pendingIconProvider);
      }
      adapter.setSelectionModeEnabled(pendingSelectionModeEnabled);
      adapter.setOnNodeLongClickListener(pendingLongClickListener);
      adapter.setOnNodeClickListener(
          (node, view) -> {
            breadcrumbbar.setSelectedNode(node, rootTreeNode);
            if (node.isFolder() || node.isVirtual()) {
              lastOpenedFolder = node;
              controller.toggleNode(node);
            } else {
              if (click != null) {
                click.onClickNode(node, view);
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
            if (node.isVirtual()) {
              Toast.makeText(getContext(), "Virtual folders can't be renamed", Toast.LENGTH_SHORT)
                  .show();
              return;
            }
            showRenameDialog(node);
          }

          @Override
          public void onDelete(@NonNull List<TreeNode> nodes) {
            for (TreeNode n : nodes) {
              if (n.isVirtual()) {
                Toast.makeText(getContext(), "Virtual folders can't be deleted", Toast.LENGTH_SHORT)
                    .show();
                return;
              }
            }
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
            boolean singleRealNode = nodes.size() == 1 && !nodes.get(0).isVirtual();
            if (singleRealNode) popup.getMenu().add(0, 1, 0, "Rename");
            popup.getMenu().add(0, 2, 0, "New Folder");
            popup.getMenu().add(0, 3, 0, "New File");
            popup.setOnMenuItemClickListener(
                item -> {
                  switch (item.getItemId()) {
                    case 1:
                      if (singleRealNode) showRenameDialog(nodes.get(0));
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

    fileWatcher.watch(rootDir.getAbsolutePath());

    if (androidMod) applyAndroidMod();
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

  /**
   * Can be called before {@link #loadTree()} (e.g. right after constructing the view) — the
   * provider is remembered and applied once {@code loadTree()} creates the adapter, same as
   * {@link #setIconArrow}. Calling this before {@code loadTree()} used to throw a
   * NullPointerException since the adapter didn't exist yet.
   */
  public void setIconProvider(IconProvider ic) {
    this.pendingIconProvider = ic;
    if (adapter != null) {
      adapter.setIconProvider(ic);
    }
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

  /**
   * Adds a synthetic grouping folder at the top level of the tree — e.g. Android Studio's "Gradle
   * Scripts" node, which gathers files that actually live in several different real directories
   * under one heading. Call after {@link #loadTree()}.
   *
   * <p>Example:
   *
   * <pre>{@code
   * TreeNode settings = new TreeNode.Builder("settings.gradle.kts")
   *     .setId(settingsPath)
   *     .setPayload(new FilePayload.Builder(settingsPath, false)
   *         .description("(Project: Settings)")
   *         .build())
   *     .build();
   * fileTreeView.addVirtualGroup("Gradle Scripts", R.drawable.ic_filetree_folder_gradle,
   *     java.util.Arrays.asList(settings, buildFile, gradleProps));
   * }</pre>
   *
   * @param name the group's display name (e.g. "Gradle Scripts")
   * @param iconRes drawable resource used for the group's own folder icon
   * @param children fully-built nodes to show inside the group — typically real files elsewhere in
   *     the project, each with its own {@link FilePayload} (so tapping one opens the real file) and
   *     optionally a {@link FilePayload.Builder#description} hint like "(Module :app)"
   * @return the created virtual node, or {@code null} if {@link #loadTree()} hasn't run yet
   */
  @Nullable
  public TreeNode addVirtualGroup(
      @NonNull String name, int iconRes, @NonNull List<TreeNode> children) {
    if (controller == null || rootTreeNode == null) return null;
    TreeNode group =
        new TreeNode.Builder(name)
            .setId(rootTreeNode.getId() + "::virtual::" + name)
            .setType(TreeNode.TYPE_VIRTUAL)
            .setHasChildren(!children.isEmpty())
            .build();
    group.setTag(iconRes);
    for (TreeNode child : children) {
      group.addChild(child);
    }
    controller.insertVirtualGroup(rootTreeNode, group, -1);
    return group;
  }

  // -------------------------------------------------------------------------
  // Android Studio-style "Android" project view — one-call convenience built on top of
  // addVirtualGroup() / FilePayload's description+badgeColor.
  // -------------------------------------------------------------------------

  // -------------------------------------------------------------------------
  // Android Studio-style "Android" project view — one-call convenience built on top of
  // addVirtualGroup() / FilePayload's description+badgeColor. Discovery/tree-building itself
  // lives in AndroidModTreeBuilder; this class only owns the live-tree state (background thread,
  // loading spinner, swapping the result in).
  // -------------------------------------------------------------------------

  /**
   * Single toggle that makes the tree look and behave like Android Studio's "Android" project
   * view: root-level Gradle files (plus each module's own build.gradle) get gathered under a
   * "Gradle Scripts" group, and module folders get a colored dot.
   *
   * <p>Only kicks in when the current root actually looks like a Gradle project (contains {@code
   * settings.gradle}/{@code .kts}); otherwise it's a harmless no-op. Re-applies automatically every
   * time {@link #loadTree()} runs — including re-roots via the breadcrumb — so this only needs to
   * be called once, in any order relative to {@link #setNodePath} / {@link #loadTree()}.
   *
   * @param enabled turn the mode on or off
   */
  public void setAndroidMod(boolean enabled) {
    this.androidMod = enabled;
    if (enabled) {
      applyAndroidMod();
    } else {
      removeAndroidMod();
    }
  }

  public boolean isAndroidMod() {
    return androidMod;
  }

  private void applyAndroidMod() {
    if (controller == null || rootTreeNode == null) return;

    File projectRoot = new File(rootTreeNode.getId());
    File gradleRoot = AndroidModTreeBuilder.resolveGradleRoot(projectRoot);
    int myToken = ++androidModApplyToken;

    // Reuse the same inline loading spinner normally shown the first time a folder is expanded
    // (TreeViewHolder's arrow ViewFlipper — see ExpandManager) — discovery below can take a
    // moment on a project with a lot of modules/res files, so give the same "this is loading"
    // feedback on root's row instead of it just looking frozen. Every exit path from the
    // background task below (not a Gradle project, or discovery finished) clears this again.
    rootTreeNode.setLazyLoadPending(true);
    controller.getModel().notifyNodeChanged(rootTreeNode);

    // Everything discoverAndroidModTree/restructureModuleNode/buildResGroup do below is
    // java.io.File I/O plus building freestanding TreeNode objects that aren't attached to the
    // live tree yet — none of it touches a View or the tree model, so none of it needs the main
    // thread. On a project with a lot of modules/res files this walk can take a noticeable moment;
    // doing it inline on the main thread would jank the UI (worse on slower devices/storage). Only
    // the final swap-in at the bottom runs back on the main thread.
    androidModExecutor.execute(
        () -> {
          boolean isGradleProject =
              AndroidModTreeBuilder.firstExisting(gradleRoot, "settings.gradle.kts", "settings.gradle")
                  != null;
          if (!isGradleProject) {
            post(() -> clearAndroidModLoadingSpinner(myToken));
            return;
          }

          List<TreeNode> scripts = new ArrayList<>();
          String gradleRootPath = gradleRoot.getAbsolutePath();
          AndroidModTreeBuilder.addGradleFileIfExists(scripts, gradleRootPath, "settings.gradle.kts", "settings.gradle", "(Project: Settings)");
          AndroidModTreeBuilder.addGradleFileIfExists(scripts, gradleRootPath, "build.gradle.kts", "build.gradle", "(Project: Build)");
          AndroidModTreeBuilder.addGradleFileIfExists(scripts, gradleRootPath, "gradle.properties", null, "(Project: Properties)");
          AndroidModTreeBuilder.addGradleFileIfExists(scripts, gradleRootPath, "proguard-rules.pro", null, "(Project: proguard-rules)");
          AndroidModTreeBuilder.addGradleFileIfExists(scripts, gradleRootPath, "local.properties", null, "(SDK Location)");

          List<TreeNode> modules = new ArrayList<>();
          AndroidModTreeBuilder.discoverAndroidModTree(gradleRoot, modules, scripts, new HashMap<>());

          post(() -> applyAndroidModResult(myToken, modules, scripts));
        });
  }

  /**
   * Clears the loading spinner {@link #applyAndroidMod} turned on, for the path where the folder
   * turned out not to be a Gradle project after all (so {@link #applyAndroidModResult} never
   * runs). Guarded by {@code token} the same way {@link #applyAndroidModResult} is.
   */
  private void clearAndroidModLoadingSpinner(int token) {
    if (token != androidModApplyToken || controller == null || rootTreeNode == null) return;
    rootTreeNode.setLazyLoadPending(false);
    controller.getModel().notifyNodeChanged(rootTreeNode);
  }

  /**
   * Swaps the discovered modules + Gradle Scripts group into the live tree. Runs on the main
   * thread, called back from {@link #applyAndroidMod}'s background discovery.
   *
   * @param token must still match {@link #androidModApplyToken} — otherwise a newer {@link
   *     #applyAndroidMod}/{@link #removeAndroidMod} call (rapid re-toggle, or the user navigating
   *     to a different root before this one finished) has since superseded this result, and
   *     applying it now would either fight with that newer state or land on a tree that's already
   *     been torn down.
   */
  private void applyAndroidModResult(
      int token, @NonNull List<TreeNode> modules, @NonNull List<TreeNode> scripts) {
    if (token != androidModApplyToken || controller == null || rootTreeNode == null) return;

    rootTreeNode.setLazyLoadPending(false);

    // Build "Gradle Scripts" and fold it straight into the same children list as the modules,
    // instead of setting the modules first and inserting this as a *separate* structural change
    // afterward (that used to be addVirtualGroup()'s job here). Two back-to-back structural
    // mutations — bulk setChildren() for the modules, then a second incremental insert for this
    // group — was exactly what produced RecyclerView's "Inconsistency detected: invalid view
    // holder adapter position" crash on projects with enough modules for the position math
    // between the two steps to disagree. One mutation avoids that entirely.
    TreeNode scriptsGroup = AndroidModTreeBuilder.buildGradleScriptsGroup(rootTreeNode.getId(), scripts);
    if (scriptsGroup != null) {
      scriptsGroup.setTag(R.drawable.ic_filetree_folder_gradle);
      modules.add(0, scriptsGroup); // Android Studio shows "Gradle Scripts" above the modules
    }
    androidModGroup = scriptsGroup;

    // TreeNode.setChildren() is a raw structural mutation — safe only while the node isn't
    // currently expanded (no visible rows depend on its old children). Collapse first if needed,
    // swap the children, then re-expand through the controller so the adapter is notified properly
    // instead of going stale.
    boolean wasExpanded = rootTreeNode.isExpanded();
    if (wasExpanded) controller.collapseNode(rootTreeNode);
    rootTreeNode.setChildren(modules);
    controller.expandNode(rootTreeNode);
  }

  private void removeAndroidMod() {
    androidModApplyToken++;
    androidModGroup = null;
    if (controller != null && rootTreeNode != null) {
      // Everything under rootTreeNode is synthetic while android mod is on (see
      // applyAndroidMod/discoverAndroidModTree) — there's no per-node badge/description to
      // undo individually anymore. Just drop the whole subtree and mark root as needing a fresh
      // lazy load, so the real on-disk structure comes back instead of the flattened module view.
      boolean wasExpanded = rootTreeNode.isExpanded();
      if (wasExpanded) controller.collapseNode(rootTreeNode);
      rootTreeNode.clearChildren();
      rootTreeNode.setHasChildren(true);
      rootTreeNode.setLazyLoadPending(false);
      if (wasExpanded) controller.expandNode(rootTreeNode);
    }
  }

  public void putDestroy() {

    if (selectionPanel != null) selectionPanel.detach();
    fileWatcher.unwatchAll();
    searchExecutor.shutdownNow();
    androidModExecutor.shutdownNow();
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

  /**
   * Returns the {@link TreeController} driving this view (built fresh by every {@link
   * #loadTree()} call), or {@code null} before the first {@link #loadTree()}. Needed for direct
   * model access — e.g. {@code getController().getModel().findNodeById(path)} to look up a node
   * and tweak its {@link FilePayload} (badge color, description) after the tree is already loaded.
   */
  @Nullable
  public TreeController getController() {
    return this.controller;
  }

  public ThemeManager getTheme() {
    return this.theme;
  }

  /** setCustomTheme */
  public void setTheme(ThemeManager theme) {
    this.theme = theme;
  }

  /**
   * Enables or disables pinch-to-zoom on the tree, similar to zooming into a photo.
   *
   * <p>Disabling it only turns off the pinch gesture, it doesn't change the current zoom level — to
   * return it to 100% use {@link #resetZoom()}.
   *
   * @param zoomMod true to allow the user to pinch-zoom the tree with two fingers
   */
  public void setZoomMod(boolean zoomMod) {
    if (scrollContainer != null) scrollContainer.setZoomMod(zoomMod);
  }

  /**
   * @return whether pinch-to-zoom is currently enabled.
   */
  public boolean isZoomMod() {
    return scrollContainer != null && scrollContainer.isZoomMod();
  }

  /**
   * Sets the allowed zoom range, as percentages of the original size (100 = original size).
   *
   * @param minScale minimum zoom, e.g. 50 for 50%
   * @param maxScale maximum zoom, e.g. 300 for 300%
   */
  public void setZoomScale(int minScale, int maxScale) {
    if (scrollContainer != null) scrollContainer.setZoomScale(minScale, maxScale);
  }

  /**
   * @return {@code [minPercent, maxPercent]} currently allowed for zoom.
   */
  @NonNull
  public int[] getZoomScale() {
    return scrollContainer != null ? scrollContainer.getZoomScale() : new int[] {100, 100};
  }

  /**
   * @return the current zoom level as a percentage (100 = original size).
   */
  public int getCurrentZoomScale() {
    return scrollContainer != null ? scrollContainer.getCurrentZoomScale() : 100;
  }

  /** Programmatically sets the zoom level (percentage), clamped to the configured range. */
  public void setCurrentZoomScale(int percent) {
    if (scrollContainer != null) scrollContainer.setCurrentZoomScale(percent);
  }

  /** Resets the zoom level back to 100%. */
  public void resetZoom() {
    if (scrollContainer != null) scrollContainer.resetZoom();
  }

  /**
   * Enables or disables rainbow-colored indent guide lines, similar to bracket-pair colorization in
   * VS Code — each indentation level is drawn in a different color from a cycling palette instead
   * of one flat line color. <b>Off by default.</b>
   *
   * @param enabled true to color indent guides by depth, false for a single flat color
   */
  public void setRainbowIndentGuides(boolean enabled) {
    if (treeView != null) treeView.setRainbowIndentGuides(enabled);
  }

  /**
   * @return whether rainbow indent guides are currently enabled.
   */
  public boolean isRainbowIndentGuides() {
    return treeView != null && treeView.isRainbowIndentGuides();
  }

  /**
   * Customizes the color palette used when rainbow indent guides are enabled. Depth 1 uses {@code
   * colors[0]}, depth 2 uses {@code colors[1]}, and so on, wrapping back to the start once the
   * array is exhausted. Can be called before or after {@link #loadTree()}.
   *
   * @param colors non-empty array of ARGB colors, e.g. from {@link
   *     android.graphics.Color#parseColor(String)}
   */
  public void setRainbowIndentGuideColors(@NonNull int[] colors) {
    if (treeView != null) treeView.setRainbowIndentGuideColors(colors);
  }

  /** SetOnClick Item see#OnNodeCallBack(#TreeNode,#View) */
  public void setClickNode(OnNodeCallBack click) {
    this.click = click;
  }

  /** setIconArrow(#int) setCustomItemArrow adding new api */
  public void setIconArrow(int icon) {
    this.pendingIconArrowRes = icon;
    if (adapter != null) {
      adapter.setIconArrow(icon);
    }
  }

  /**
   * Enables or disables the built-in "long-press enters selection mode, shows the selection
   * action panel" behavior. Default {@code true}. Set to {@code false} to handle
   * selection/multi-select entirely yourself — combine with {@link #setOnNodeLongClickListener}
   * to show your own dialog/UI on long-press instead.
   */
  public void setSelectionModeEnabled(boolean enabled) {
    this.pendingSelectionModeEnabled = enabled;
    if (adapter != null) {
      adapter.setSelectionModeEnabled(enabled);
    }
  }

  public boolean isSelectionModeEnabled() {
    return adapter != null ? adapter.isSelectionModeEnabled() : pendingSelectionModeEnabled;
  }

  /**
   * Consulted on every long-press before the built-in selection-mode behavior runs. Return {@code
   * true} to say you've fully handled the long-press (your own dialog, custom action, etc.) — the
   * built-in selection mode is skipped for that press. Return {@code false}, or don't set a
   * listener at all, to fall through to the built-in behavior (or to do nothing, if {@link
   * #setSelectionModeEnabled} is {@code false}).
   */
  public void setOnNodeLongClickListener(@Nullable TreeAdapter.OnNodeLongClickListener listener) {
    this.pendingLongClickListener = listener;
    if (adapter != null) {
      adapter.setOnNodeLongClickListener(listener);
    }
  }

  /**
   * Enables or disables drag-to-move. Default {@code true}. Virtual grouping nodes (e.g. "Gradle
   * Scripts", "res" in {@link #setAndroidMod}'s project view) can never be dragged regardless of
   * this setting, since they have no real file to move — this only affects real files/folders.
   */
  public void setDragEnabled(boolean enabled) {
    this.pendingDragEnabled = enabled;
    if (dragManager != null) {
      dragManager.setDragLocked(!enabled);
    }
  }

  public boolean isDragEnabled() {
    return dragManager == null || !dragManager.isDragLocked();
  }

  /**
   * When {@code enabled}, expanding a folder that turns out to contain exactly one item
   * auto-expands that item too, and so on down the chain — stopping the moment a folder has 2+
   * items, or the chain ends at a file. The same "Android Studio compact middle packages" feel,
   * for any folder shape, not just Java/Kotlin packages. Default {@code false}. Applies equally
   * whether {@link #setAndroidMod} is on or off — both real lazy-loaded folders and android-mod's
   * synthetic tree go through the same expand path this hooks into.
   */
  public void setAutoExpandSingleChildChains(boolean enabled) {
    this.autoExpandSingleChildChains = enabled;
  }

  public boolean isAutoExpandSingleChildChains() {
    return autoExpandSingleChildChains;
  }

  /**
   * Expands every ancestor folder of {@code targetPath} (loading each one from disk first if
   * needed) and reveals/scrolls to it once found — e.g. jumping straight to a file deep in the
   * tree without the user manually tapping through each folder in between.
   *
   * @param targetPath absolute path of the file/folder to reveal
   * @return {@code false} immediately, without touching the tree, if {@code targetPath} doesn't
   *     exist on disk or isn't under the tree's current root ({@link #getNodePath()}); {@code
   *     true} otherwise, meaning an expansion attempt was made. A {@code true} return doesn't
   *     guarantee the target ends up fully revealed though — if a folder along the way turns out
   *     not to actually contain the next segment (e.g. it changed on disk after the initial
   *     existence check), this still expands as far as it successfully got and stops there rather
   *     than throwing.
   *     <p>Works the same in android-mod's flattened project view: a module's children were
   *     already built synthetically, so no disk load is needed for those — this just walks the
   *     already-present children instead of lazy-loading them.
   */
  public boolean expandToPath(@NonNull String targetPath) {
    if (controller == null || rootTreeNode == null) return false;

    File root = new File(rootTreeNode.getId());
    File target = new File(targetPath);
    if (!target.exists()) return false; // nothing there to expand to — don't bother lazy-loading

    List<String> segments = new ArrayList<>();
    File cur = target;
    while (cur != null && !cur.getAbsolutePath().equals(root.getAbsolutePath())) {
      segments.add(0, cur.getName());
      cur = cur.getParentFile();
    }
    if (cur == null) return false; // targetPath isn't under the current root at all

    expandToPathSegment(rootTreeNode, segments, 0);
    return true;
  }

  private void expandToPathSegment(
      @NonNull TreeNode current, @NonNull List<String> segments, int index) {
    if (index >= segments.size()) {
      controller.revealNode(current);
      return;
    }

    String nextName = segments.get(index);
    Runnable proceed =
        () -> {
          TreeNode match = null;
          for (TreeNode child : current.getChildren()) {
            if (child.getName().equals(nextName)) {
              match = child;
              break;
            }
          }
          if (match == null) {
            // Segment not found (path doesn't exist, or this folder's contents don't match what
            // was asked for) — reveal as far as we successfully got instead of doing nothing.
            controller.revealNode(current);
            return;
          }
          expandToPathSegment(match, segments, index + 1);
        };

    if (current.getChildCount() > 0 || !current.hasChildren()) {
      // Already loaded (real folder previously expanded, or android-mod's synthetic children),
      // or known to have nothing in it — no disk load needed, continue immediately.
      proceed.run();
    } else {
      // Real folder, not yet lazily loaded — expand it and continue once its children arrive.
      controller
          .getExpandManager()
          .addExpandListener(
              new ExpandManager.ExpandListener() {
                @Override
                public void onNodesExpanded(
                    @NonNull TreeNode parent, @NonNull List<TreeNode> inserted, int insertPos) {
                  if (parent != current) return;
                  controller.getExpandManager().removeExpandListener(this);
                  proceed.run();
                }

                @Override
                public void onNodesCollapsed(
                    @NonNull TreeNode parent, @NonNull List<TreeNode> removed, int removePos) {}
              });
      controller.expandNode(current);
    }
  }

  public boolean getShowSearchBar() {
    return this.showSearchBar;
  }

  public void setShowSearchBar(boolean showSearchBar) {
    this.showSearchBar = showSearchBar;
  }
}
