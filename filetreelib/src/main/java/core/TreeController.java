package ir.hanzodev1375.filetreelib.core;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import ir.hanzodev1375.filetreelib.cache.TreeCache;
import ir.hanzodev1375.filetreelib.model.TreeNodeState;
import ir.hanzodev1375.filetreelib.provider.TreeDataProvider;
import ir.hanzodev1375.filetreelib.selection.SelectionManager;
import ir.hanzodev1375.filetreelib.utils.VisibleNodeList;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The single public façade for all tree operations.
 *
 * <p>{@code TreeController} coordinates between:
 * <ul>
 *   <li>{@link TreeModel}         — structural data owner
 *   <li>{@link ExpandManager}     — expand/collapse and visible-list maintenance
 *   <li>{@link SelectionManager}  — selection state
 *   <li>{@link TreeDataProvider}  — async data loading
 *   <li>{@link TreeCache}         — subtree caching
 * </ul>
 *
 * <p>Callers should hold a reference only to {@code TreeController} and never
 * manipulate the model or managers directly.  This keeps the code compatible with
 * MVC, MVVM, and MVP patterns.
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * TreeController controller = new TreeController.Builder(context)
 *     .model(treeModel)
 *     .provider(fileTreeProvider)
 *     .build();
 *
 * controller.expandNode(node);
 * controller.selectNode(node, SelectionManager.MODE_SINGLE);
 * controller.renameNode(node, "NewName");
 * controller.saveState();   // returns TreeState Parcelable
 * }</pre>
 */
public final class TreeController {

    // -------------------------------------------------------------------------
    // Callback interfaces
    // -------------------------------------------------------------------------

    /**
     * Notified after a rename completes (or fails).
     */
    public interface RenameCallback {
        /** Called on the main thread when the rename succeeded. */
        @MainThread
        void onRenamed(@NonNull TreeNode node, @NonNull String oldName, @NonNull String newName);

        /** Called on the main thread when the rename failed. */
        @MainThread
        void onRenameFailed(@NonNull TreeNode node, @NonNull Exception error);
    }

    /**
     * Notified after a delete completes (or fails).
     */
    public interface DeleteCallback {
        /** Called on the main thread when deletion succeeded. */
        @MainThread
        void onDeleted(@NonNull List<TreeNode> deletedNodes);

        /** Called on the main thread when deletion failed. */
        @MainThread
        void onDeleteFailed(@NonNull List<TreeNode> nodes, @NonNull Exception error);
    }

    /**
     * Notified after a create-folder or create-file operation completes.
     */
    public interface CreateCallback {
        /** Called on the main thread when the node was created and inserted. */
        @MainThread
        void onCreated(@NonNull TreeNode newNode);

        /** Called on the main thread when creation failed. */
        @MainThread
        void onCreateFailed(@NonNull String name, @NonNull Exception error);
    }

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    @NonNull private final TreeModel          model;
    @NonNull private final ExpandManager      expandManager;
    @NonNull private final SelectionManager   selectionManager;
    @NonNull private final TreeDataProvider   dataProvider;
    @NonNull private final TreeCache          cache;
    @NonNull private final VisibleNodeList    visibleList;
    @NonNull private final ExecutorService    backgroundExecutor;
    @NonNull private final Handler            mainHandler;

    // -------------------------------------------------------------------------
    // Constructor (via Builder)
    // -------------------------------------------------------------------------

    private TreeController(@NonNull Builder builder) {
        this.model             = builder.model;
        this.visibleList       = builder.visibleList;
        this.expandManager     = builder.expandManager;
        this.selectionManager  = builder.selectionManager;
        this.dataProvider      = builder.dataProvider;
        this.cache             = builder.cache;
        this.backgroundExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "TreeController-bg");
            t.setDaemon(true);
            return t;
        });
        this.mainHandler       = new Handler(Looper.getMainLooper());
    }

    // -------------------------------------------------------------------------
    // Expand / Collapse
    // -------------------------------------------------------------------------

    /**
     * Expands {@code node}.  If the node has not yet loaded its children,
     * triggers a lazy load via the {@link TreeDataProvider}.
     *
     * @param node the node to expand
     */
    @MainThread
    public void expandNode(@NonNull TreeNode node) {
        if (node.isExpanded()) return;

        if (node.hasChildren() && node.getChildCount() == 0) {
            // Children declared but not yet loaded → trigger lazy load.
            node.setLazyLoadPending(true);
            expandManager.expand(node);
            lazyLoadChildren(node);
        } else {
            expandManager.expand(node);
        }
    }

    /**
     * Collapses {@code node}.
     *
     * @param node the node to collapse
     */
    @MainThread
    public void collapseNode(@NonNull TreeNode node) {
        expandManager.collapse(node);
    }

    /**
     * Toggles the expand/collapse state of {@code node}.
     *
     * @param node the node to toggle
     */
    @MainThread
    public void toggleNode(@NonNull TreeNode node) {
        if (node.isExpanded()) {
            collapseNode(node);
        } else {
            expandNode(node);
        }
    }

    /**
     * Expands all ancestors of {@code node} so it becomes visible, then scrolls
     * the RecyclerView to it.
     *
     * @param node the node to reveal
     */
    @MainThread
    public void revealNode(@NonNull TreeNode node) {
        expandManager.expandToNode(node);
    }

    /**
     * Fully expands the entire subtree rooted at {@code node}.
     *
     * @param node the subtree root
     */
    @MainThread
    public void expandAll(@NonNull TreeNode node) {
        expandManager.expandAll(node);
    }

    /**
     * Fully collapses the entire subtree rooted at {@code node}.
     *
     * @param node the subtree root
     */
    @MainThread
    public void collapseAll(@NonNull TreeNode node) {
        expandManager.collapseAll(node);
    }

    // -------------------------------------------------------------------------
    // Selection
    // -------------------------------------------------------------------------

    /**
     * Selects {@code node} using the given mode.
     *
     * @param node the node to select
     * @param mode one of {@link SelectionManager#MODE_SINGLE},
     *             {@link SelectionManager#MODE_MULTI},
     *             {@link SelectionManager#MODE_RANGE}
     */
    @MainThread
    public void selectNode(@NonNull TreeNode node, int mode) {
        selectionManager.select(node, mode);
    }

    /**
     * Deselects {@code node}.
     *
     * @param node the node to deselect
     */
    @MainThread
    public void deselectNode(@NonNull TreeNode node) {
        selectionManager.deselect(node);
    }

    /** Clears all selected nodes. */
    @MainThread
    public void clearSelection() {
        selectionManager.clearSelection();
    }

    /**
     * Returns the current selection as an unmodifiable list.
     */
    @NonNull
    @MainThread
    public List<TreeNode> getSelectedNodes() {
        return selectionManager.getSelectedNodes();
    }

    // -------------------------------------------------------------------------
    // Rename
    // -------------------------------------------------------------------------

    /**
     * Renames {@code node} to {@code newName} asynchronously.
     * The rename is first committed to the filesystem (via {@link TreeDataProvider}),
     * then the tree model is updated, then {@code callback} is invoked on the
     * main thread.
     *
     * @param node     the node to rename
     * @param newName  the new display name (must not be empty)
     * @param callback result callback (may be null)
     */
    @MainThread
    public void renameNode(
            @NonNull TreeNode node,
            @NonNull String newName,
            @Nullable RenameCallback callback) {

        if (newName.isEmpty()) {
            if (callback != null) {
                callback.onRenameFailed(node,
                        new IllegalArgumentException("New name must not be empty"));
            }
            return;
        }

        final String oldName = node.getName();

        backgroundExecutor.submit(() -> {
            try {
                dataProvider.renameNode(node, newName);
                mainHandler.post(() -> {
                    node.setName(newName);
                    model.notifyNodeChanged(node);
                    cache.invalidate(node.getId());
                    if (callback != null) {
                        callback.onRenamed(node, oldName, newName);
                    }
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    if (callback != null) {
                        callback.onRenameFailed(node, e);
                    }
                });
            }
        });
    }

    // -------------------------------------------------------------------------
    // Delete
    // -------------------------------------------------------------------------

    /**
     * Deletes all currently selected nodes asynchronously.  Falls back to
     * deleting only {@code node} if nothing is selected.
     *
     * @param node     the primary node to delete (used if no selection)
     * @param callback result callback (may be null)
     */
    @MainThread
    public void deleteNode(
            @NonNull TreeNode node,
            @Nullable DeleteCallback callback) {

        List<TreeNode> toDelete = new ArrayList<>(selectionManager.getSelectedNodes());
        if (toDelete.isEmpty()) toDelete.add(node);

        final List<TreeNode> snapshot = new ArrayList<>(toDelete);

        backgroundExecutor.submit(() -> {
            try {
                dataProvider.deleteNodes(snapshot);
                mainHandler.post(() -> {
                    for (TreeNode n : snapshot) {
                        TreeNode parent = n.getParent();
                        if (parent != null) {
                            // Collapse first so that the visible list does not
                            // contain stale child rows.
                            expandManager.collapse(n);
                            model.removeNode(n);
                            cache.invalidate(n.getId());
                        }
                    }
                    selectionManager.clearSelection();
                    if (callback != null) {
                        callback.onDeleted(snapshot);
                    }
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    if (callback != null) {
                        callback.onDeleteFailed(snapshot, e);
                    }
                });
            }
        });
    }

    // -------------------------------------------------------------------------
    // Create
    // -------------------------------------------------------------------------

    /**
     * Creates a new folder named {@code name} as a child of {@code parent}.
     *
     * @param parent   the parent node under which the folder is created
     * @param name     the folder name (must not be empty)
     * @param callback result callback (may be null)
     */
    @MainThread
    public void createFolder(
            @NonNull TreeNode parent,
            @NonNull String name,
            @Nullable CreateCallback callback) {
        createEntry(parent, name, TreeNode.TYPE_FOLDER, callback);
    }

    /**
     * Creates a new file named {@code name} as a child of {@code parent}.
     *
     * @param parent   the parent node under which the file is created
     * @param name     the file name (must not be empty)
     * @param callback result callback (may be null)
     */
    @MainThread
    public void createFile(
            @NonNull TreeNode parent,
            @NonNull String name,
            @Nullable CreateCallback callback) {
        createEntry(parent, name, TreeNode.TYPE_FILE, callback);
    }

    @MainThread
    private void createEntry(
            @NonNull TreeNode parent,
            @NonNull String name,
            int type,
            @Nullable CreateCallback callback) {

        if (name.isEmpty()) {
            if (callback != null) {
                callback.onCreateFailed(name,
                        new IllegalArgumentException("Name must not be empty"));
            }
            return;
        }

        backgroundExecutor.submit(() -> {
            try {
                TreeNode newNode = dataProvider.createNode(parent, name, type);
                mainHandler.post(() -> {
                    // Insert alphabetically among siblings.
                    int insertIdx = findAlphabeticalInsertIndex(parent, newNode, type);
                    model.insertNodeAt(parent, insertIdx, newNode);
                    // Expand the parent if not already so the new node is visible.
                    if (!parent.isExpanded()) {
                        expandManager.expand(parent);
                    }
                    cache.invalidate(parent.getId());
                    if (callback != null) {
                        callback.onCreated(newNode);
                    }
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    if (callback != null) {
                        callback.onCreateFailed(name, e);
                    }
                });
            }
        });
    }

    /**
     * Finds the correct alphabetical insertion index for {@code newNode} within
     * {@code parent}'s children.  Folders are grouped before files (VS Code style).
     *
     * @param parent  the parent whose children list to search
     * @param newNode the node to be inserted
     * @param type    {@link TreeNode#TYPE_FOLDER} or {@link TreeNode#TYPE_FILE}
     * @return the insertion index
     */
    private int findAlphabeticalInsertIndex(
            @NonNull TreeNode parent,
            @NonNull TreeNode newNode,
            int type) {

        List<TreeNode> children = new ArrayList<>(parent.getChildren());
        String newName = newNode.getName().toLowerCase();
        int index = 0;

        for (TreeNode child : children) {
            // Folders come before files.
            if (type == TreeNode.TYPE_FOLDER && child.isFile()) break;
            if (type == TreeNode.TYPE_FILE   && child.isFolder()) {
                index++;
                continue;
            }
            if (newName.compareTo(child.getName().toLowerCase()) <= 0) break;
            index++;
        }
        return index;
    }

    // -------------------------------------------------------------------------
    // Lazy loading
    // -------------------------------------------------------------------------

    /**
     * Triggers a background load of {@code parent}'s children via the
     * {@link TreeDataProvider}.
     *
     * @param parent the node whose children to load
     */
    @MainThread
    private void lazyLoadChildren(@NonNull TreeNode parent) {
        // Check cache first.
        List<TreeNode> cached = cache.getChildren(parent.getId());
        if (cached != null) {
            onChildrenLoaded(parent, cached);
            return;
        }

        backgroundExecutor.submit(() -> {
            try {
                List<TreeNode> children = dataProvider.loadChildren(parent);
                mainHandler.post(() -> onChildrenLoaded(parent, children));
            } catch (Exception e) {
                mainHandler.post(() -> expandManager.onLazyLoadFailed(parent));
            }
        });
    }

    @MainThread
    private void onChildrenLoaded(@NonNull TreeNode parent, @NonNull List<TreeNode> children) {
        // Attach children to the model — this fires structural events.
        for (TreeNode child : children) {
            parent.addChild(child);
        }
        cache.putChildren(parent.getId(), children);
        expandManager.onLazyLoadCompleted(parent, children);
    }

    // -------------------------------------------------------------------------
    // State save / restore
    // -------------------------------------------------------------------------

    /**
     * Captures the current UI state into a {@link TreeState} that can be
     * persisted across configuration changes.
     *
     * @return a snapshot of the current state
     */
    @MainThread
    @NonNull
    public TreeState saveState() {
        TreeState.Builder builder = new TreeState.Builder();

        // Collect expanded IDs from the visible list.
        for (TreeNode node : visibleList.getItems()) {
            if (node.isExpanded()) builder.addExpandedId(node.getId());
        }

        // Collect selected IDs.
        for (TreeNode node : selectionManager.getSelectedNodes()) {
            builder.addSelectedId(node.getId());
        }

        // Collect bookmarked IDs.
        collectBookmarkedIds(model.getRoot(), builder);

        // Scroll position is injected by the adapter layer; leave -1 for now.
        return builder.build();
    }

    private void collectBookmarkedIds(@NonNull TreeNode node, @NonNull TreeState.Builder builder) {
        if (node.isBookmarked()) builder.addBookmarkedId(node.getId());
        for (TreeNode child : node.getChildren()) {
            collectBookmarkedIds(child, builder);
        }
    }

    /**
     * Restores the tree's UI state from a previously saved {@link TreeState}.
     * Expanded nodes are re-expanded, selected nodes are re-selected, and
     * bookmarked nodes are re-bookmarked.
     *
     * @param state the state to restore; if {@code null} or empty, nothing happens
     */
    @MainThread
    public void restoreState(@Nullable TreeState state) {
        if (state == null || state.isEmpty()) return;

        Set<String> expandedIds   = state.getExpandedIds();
        Set<String> selectedIds   = state.getSelectedIds();
        Set<String> bookmarkedIds = state.getBookmarkedIds();
        Map<String, TreeNodeState> nodeStates = state.getNodeStates();

        // Apply per-node states.
        for (Map.Entry<String, TreeNodeState> entry : nodeStates.entrySet()) {
            TreeNode node = model.findNodeById(entry.getKey());
            if (node != null) {
                TreeNodeState ns = entry.getValue();
                node.setBookmarked(ns.isBookmarked());
            }
        }

        // Restore bookmarks.
        for (String id : bookmarkedIds) {
            TreeNode node = model.findNodeById(id);
            if (node != null) node.setBookmarked(true);
        }

        // Restore selection.
        for (String id : selectedIds) {
            TreeNode node = model.findNodeById(id);
            if (node != null) selectionManager.select(node, SelectionManager.MODE_MULTI);
        }

        // Restore expand state — rebuild the visible list from scratch.
        applyExpandedState(model.getRoot(), expandedIds);
        expandManager.rebuildVisibleList(model.getRoot());
    }

    private void applyExpandedState(
            @NonNull TreeNode node,
            @NonNull Set<String> expandedIds) {
        node.setExpanded(expandedIds.contains(node.getId()));
        for (TreeNode child : node.getChildren()) {
            applyExpandedState(child, expandedIds);
        }
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    /** Returns the {@link TreeModel} owned by this controller. */
    @NonNull
    public TreeModel getModel() {
        return model;
    }

    /** Returns the {@link ExpandManager} owned by this controller. */
    @NonNull
    public ExpandManager getExpandManager() {
        return expandManager;
    }

    /** Returns the {@link SelectionManager} owned by this controller. */
    @NonNull
    public SelectionManager getSelectionManager() {
        return selectionManager;
    }

    /** Returns the {@link VisibleNodeList} maintained by this controller. */
    @NonNull
    public VisibleNodeList getVisibleList() {
        return visibleList;
    }

    /** Returns the {@link TreeDataProvider} used by this controller. */
    @NonNull
    public TreeDataProvider getDataProvider() {
        return dataProvider;
    }

    /**
     * Shuts down the background executor.  Call from {@code onDestroy()}.
     */
    public void destroy() {
        backgroundExecutor.shutdownNow();
    }

    // -------------------------------------------------------------------------
    // Builder
    // -------------------------------------------------------------------------

    /** Fluent builder for {@link TreeController}. */
    public static final class Builder {

        @NonNull private TreeModel         model            = new TreeModel();
        @NonNull private VisibleNodeList   visibleList      = new VisibleNodeList();
        @NonNull private ExpandManager     expandManager    = new ExpandManager(visibleList);
        @NonNull private SelectionManager  selectionManager = new SelectionManager();
        @NonNull private TreeDataProvider  dataProvider;
        @NonNull private TreeCache         cache            = new TreeCache();

        /**
         * Creates a builder.  A {@link TreeDataProvider} is mandatory.
         *
         * @param dataProvider the provider that backs lazy loading and mutations
         */
        public Builder(@NonNull TreeDataProvider dataProvider) {
            this.dataProvider = dataProvider;
        }

        public Builder model(@NonNull TreeModel model) {
            this.model = model;
            // Re-wire ExpandManager to the existing visible list.
            this.visibleList   = new VisibleNodeList();
            this.expandManager = new ExpandManager(this.visibleList);
            return this;
        }

        public Builder selectionManager(@NonNull SelectionManager selectionManager) {
            this.selectionManager = selectionManager;
            return this;
        }

        public Builder cache(@NonNull TreeCache cache) {
            this.cache = cache;
            return this;
        }

        /** Builds and returns the {@link TreeController}. */
        @NonNull
        public TreeController build() {
            return new TreeController(this);
        }
    }
}
