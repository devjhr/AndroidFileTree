package ir.hanzodev1375.filetreelib.core;

import androidx.annotation.AnyThread;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The observable domain model that owns the tree's root node and fires
 * structured change events to registered {@link TreeModelListener}s.
 *
 * <p>{@code TreeModel} is the single source of truth for structural tree data.
 * It does NOT hold any UI state (expanded/selected flags live on
 * {@link TreeNode} directly and are managed by {@link ExpandManager} and
 * {@link com.treeview.selection.SelectionManager}).
 *
 * <p><strong>Threading:</strong> All mutations and listener notifications happen
 * on the main thread.  Read access to the tree is safe from any thread after
 * construction.
 *
 * <p><strong>Listener contract:</strong>
 * <ul>
 *   <li>{@link TreeModelListener#onNodesInserted} — children were added.
 *   <li>{@link TreeModelListener#onNodesRemoved}  — children were removed.
 *   <li>{@link TreeModelListener#onNodesChanged}  — node metadata changed (name,
 *       badge, Git status, etc.) but structure is unchanged.
 *   <li>{@link TreeModelListener#onStructureChanged} — the entire tree was
 *       replaced or a subtree was reorganized too drastically for incremental
 *       updates.
 * </ul>
 */
public final class TreeModel {

    // -------------------------------------------------------------------------
    // Listener interface
    // -------------------------------------------------------------------------

    /**
     * Callback interface for structural and data changes in the tree model.
     */
    public interface TreeModelListener {

        /**
         * Called when one or more nodes were inserted as children of {@code parent}.
         *
         * @param parent       the parent node whose children list changed
         * @param insertedNodes the newly inserted nodes, in insertion order
         * @param startIndex   the index within {@code parent}'s children where
         *                     the first inserted node was placed
         */
        @MainThread
        void onNodesInserted(
                @NonNull TreeNode parent,
                @NonNull List<TreeNode> insertedNodes,
                int startIndex);

        /**
         * Called when one or more children were removed from {@code parent}.
         *
         * @param parent       the parent node whose children list changed
         * @param removedNodes the removed nodes (detached from the tree)
         * @param startIndex   the former index of the first removed node within
         *                     {@code parent}'s children
         */
        @MainThread
        void onNodesRemoved(
                @NonNull TreeNode parent,
                @NonNull List<TreeNode> removedNodes,
                int startIndex);

        /**
         * Called when nodes had their display data changed (name, badge, Git
         * status, error count, etc.) but their position in the tree is unchanged.
         *
         * @param changedNodes the nodes whose data changed
         */
        @MainThread
        void onNodesChanged(@NonNull List<TreeNode> changedNodes);

        /**
         * Called when the tree structure changed in a way that cannot be expressed
         * as a series of insertions and removals — e.g. after a full tree reload.
         * Listeners should rebuild their full state from scratch.
         */
        @MainThread
        void onStructureChanged();
    }

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    /** The invisible root node.  Its children are the top-level visible nodes. */
    @NonNull
    private TreeNode root;

    /**
     * Thread-safe listener list.  Use {@link CopyOnWriteArrayList} so that
     * listeners can safely add/remove themselves inside a callback without
     * {@link java.util.ConcurrentModificationException}.
     */
    @NonNull
    private final CopyOnWriteArrayList<TreeModelListener> listeners =
            new CopyOnWriteArrayList<>();

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    /**
     * Creates a model with an empty root.
     */
    public TreeModel() {
        this.root = TreeNode.root();
    }

    /**
     * Creates a model pre-populated with the given root.
     *
     * @param root the invisible root node (created by {@link TreeNode#root()})
     */
    public TreeModel(@NonNull TreeNode root) {
        this.root = root;
    }

    // -------------------------------------------------------------------------
    // Listener management
    // -------------------------------------------------------------------------

    /**
     * Registers a listener.  Safe to call from any thread; the listener will be
     * notified on the main thread.
     *
     * @param listener the listener to add (must not be null)
     */
    @AnyThread
    public void addListener(@NonNull TreeModelListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    /**
     * Unregisters a listener.  Safe to call from any thread.
     *
     * @param listener the listener to remove
     */
    @AnyThread
    public void removeListener(@NonNull TreeModelListener listener) {
        listeners.remove(listener);
    }

    // -------------------------------------------------------------------------
    // Root access
    // -------------------------------------------------------------------------

    /**
     * Returns the invisible root node.  Do not render this node directly;
     * render {@link TreeNode#getChildren()} instead.
     */
    @NonNull
    public TreeNode getRoot() {
        return root;
    }

    /**
     * Returns an unmodifiable view of the top-level (depth-0) nodes.
     */
    @NonNull
    public List<TreeNode> getTopLevelNodes() {
        return root.getChildren();
    }

    // -------------------------------------------------------------------------
    // Structural mutations — fire events
    // -------------------------------------------------------------------------

    /**
     * Appends {@code child} as the last child of {@code parent} and notifies
     * all listeners.
     *
     * @param parent the parent to receive the child
     * @param child  the node to insert (must not already have a parent)
     */
    @MainThread
    public void insertNode(@NonNull TreeNode parent, @NonNull TreeNode child) {
        int insertIndex = parent.getChildCount();
        parent.addChild(child);
        notifyNodesInserted(parent, Collections.singletonList(child), insertIndex);
    }

    /**
     * Inserts {@code child} at {@code index} within {@code parent}'s children.
     *
     * @param parent the parent to receive the child
     * @param index  insertion index (0 = first)
     * @param child  the node to insert (must not already have a parent)
     */
    @MainThread
    public void insertNodeAt(@NonNull TreeNode parent, int index, @NonNull TreeNode child) {
        parent.addChildAt(index, child);
        notifyNodesInserted(parent, Collections.singletonList(child), index);
    }

    /**
     * Inserts a batch of children into {@code parent} starting at {@code startIndex}.
     * More efficient than calling {@link #insertNode} in a loop because it fires only
     * one change event.
     *
     * @param parent     the parent to receive the children
     * @param startIndex index of the first inserted child within parent
     * @param newNodes   the nodes to insert (must not already have parents)
     */
    @MainThread
    public void insertNodes(
            @NonNull TreeNode parent,
            int startIndex,
            @NonNull List<TreeNode> newNodes) {
        for (int i = 0; i < newNodes.size(); i++) {
            parent.addChildAt(startIndex + i, newNodes.get(i));
        }
        notifyNodesInserted(parent, newNodes, startIndex);
    }

    /**
     * Removes a single node from its parent and notifies listeners.
     *
     * @param node the node to remove; must have a parent
     */
    @MainThread
    public void removeNode(@NonNull TreeNode node) {
        TreeNode parent = node.getParent();
        if (parent == null) {
            throw new IllegalStateException(
                    "Cannot remove node '" + node.getName() + "': it has no parent.");
        }
        int removedIndex = parent.indexOfChild(node);
        parent.removeChild(node);
        notifyNodesRemoved(parent, Collections.singletonList(node), removedIndex);
    }

    /**
     * Removes all children of {@code parent} and notifies listeners.
     *
     * @param parent the parent whose children will be cleared
     */
    @MainThread
    public void removeAllChildren(@NonNull TreeNode parent) {
        List<TreeNode> removed = new ArrayList<>(parent.getChildren());
        if (removed.isEmpty()) return;
        parent.clearChildren();
        notifyNodesRemoved(parent, removed, 0);
    }

    /**
     * Moves a node from its current parent to {@code newParent} at the given index.
     * Fires a remove event then an insert event (two atomic DiffUtil-compatible ops).
     *
     * @param node      the node to move
     * @param newParent the destination parent
     * @param newIndex  the index within {@code newParent}'s children
     */
    @MainThread
    public void moveNode(
            @NonNull TreeNode node,
            @NonNull TreeNode newParent,
            int newIndex) {
        TreeNode oldParent = node.getParent();
        if (oldParent == null) {
            throw new IllegalStateException(
                    "Cannot move node '" + node.getName() + "': it has no parent.");
        }
        int oldIndex = oldParent.indexOfChild(node);
        oldParent.removeChild(node);
        notifyNodesRemoved(oldParent, Collections.singletonList(node), oldIndex);

        newParent.addChildAt(newIndex, node);
        notifyNodesInserted(newParent, Collections.singletonList(node), newIndex);
    }

    /**
     * Notifies listeners that the given nodes have updated display data.
     * Structural position is unchanged.
     *
     * @param changedNodes the nodes whose display data changed
     */
    @MainThread
    public void notifyNodesChanged(@NonNull List<TreeNode> changedNodes) {
        if (changedNodes.isEmpty()) return;
        for (TreeModelListener listener : listeners) {
            listener.onNodesChanged(changedNodes);
        }
    }

    /**
     * Notifies listeners that a single node's display data changed.
     *
     * @param node the node that changed
     */
    @MainThread
    public void notifyNodeChanged(@NonNull TreeNode node) {
        notifyNodesChanged(Collections.singletonList(node));
    }

    /**
     * Replaces the entire tree with a new root and notifies listeners of a full
     * structure change.  Use sparingly — prefer incremental mutations.
     *
     * @param newRoot the new invisible root node
     */
    @MainThread
    public void setRoot(@NonNull TreeNode newRoot) {
        this.root = newRoot;
        notifyStructureChanged();
    }

    // -------------------------------------------------------------------------
    // Private notification helpers
    // -------------------------------------------------------------------------

    private void notifyNodesInserted(
            @NonNull TreeNode parent,
            @NonNull List<TreeNode> insertedNodes,
            int startIndex) {
        for (TreeModelListener listener : listeners) {
            listener.onNodesInserted(parent, insertedNodes, startIndex);
        }
    }

    private void notifyNodesRemoved(
            @NonNull TreeNode parent,
            @NonNull List<TreeNode> removedNodes,
            int startIndex) {
        for (TreeModelListener listener : listeners) {
            listener.onNodesRemoved(parent, removedNodes, startIndex);
        }
    }

    private void notifyStructureChanged() {
        for (TreeModelListener listener : listeners) {
            listener.onStructureChanged();
        }
    }

    // -------------------------------------------------------------------------
    // Utilities
    // -------------------------------------------------------------------------

    /**
     * Searches for a node with the given ID using breadth-first traversal.
     * Returns {@code null} if not found.
     *
     * @param nodeId the ID to search for
     * @return the matching node, or {@code null}
     */
    @Nullable
    public TreeNode findNodeById(@NonNull String nodeId) {
        return findNodeByIdRecursive(root, nodeId);
    }

    @Nullable
    private TreeNode findNodeByIdRecursive(@NonNull TreeNode node, @NonNull String nodeId) {
        if (node.getId().equals(nodeId)) return node;
        for (TreeNode child : node.getChildren()) {
            TreeNode found = findNodeByIdRecursive(child, nodeId);
            if (found != null) return found;
        }
        return null;
    }

    /**
     * Returns the total number of nodes in the tree (excluding the invisible root).
     * Traverses the entire tree — O(n).
     */
    public int getTotalNodeCount() {
        return countRecursive(root) - 1; // subtract root itself
    }

    private int countRecursive(@NonNull TreeNode node) {
        int count = 1;
        for (TreeNode child : node.getChildren()) {
            count += countRecursive(child);
        }
        return count;
    }

    @NonNull
    @Override
    public String toString() {
        return "TreeModel{rootChildren=" + root.getChildCount()
                + ", totalNodes=" + getTotalNodeCount() + '}';
    }
}
