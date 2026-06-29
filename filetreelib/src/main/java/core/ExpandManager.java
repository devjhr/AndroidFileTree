package ir.hanzodev1375.filetreelib.core;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import ir.hanzodev1375.filetreelib.utils.VisibleNodeList;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Manages the expand/collapse state of tree nodes and keeps the
 * {@link VisibleNodeList} in sync.
 *
 * <h3>Virtual Tree Algorithm (VS Code-style)</h3>
 * <p>Only <em>expanded</em> subtrees contribute rows to the RecyclerView dataset.
 * When a node is collapsed, its entire descendant subtree is removed from the
 * visible list in one operation.  When a node is expanded, its direct children are
 * inserted at the correct position.
 *
 * <p>The flat index of a node within the visible list is computed by the
 * {@link com.treeview.utils.NodeIndexer}, which is updated after every
 * expand/collapse.
 *
 * <h3>Threading</h3>
 * <p>All public methods must be called on the main thread.  The DiffUtil
 * computation is posted to a background thread by the adapter layer.
 */
public final class ExpandManager {

    // -------------------------------------------------------------------------
    // Listener
    // -------------------------------------------------------------------------

    /**
     * Callback interface notified when the visible node list changes as a
     * result of an expand or collapse operation.
     */
    public interface ExpandListener {

        /**
         * Called after a set of nodes was inserted into the visible list.
         *
         * @param parentNode     the node that was expanded
         * @param insertedNodes  the nodes that became visible
         * @param insertPosition the position in the visible list where the first
         *                       inserted node appears
         */
        void onNodesExpanded(
                @NonNull TreeNode parentNode,
                @NonNull List<TreeNode> insertedNodes,
                int insertPosition);

        /**
         * Called after a set of nodes was removed from the visible list.
         *
         * @param parentNode    the node that was collapsed
         * @param removedNodes  the nodes that became invisible
         * @param removePosition the position in the visible list where the first
         *                       removed node was located before removal
         */
        void onNodesCollapsed(
                @NonNull TreeNode parentNode,
                @NonNull List<TreeNode> removedNodes,
                int removePosition);
    }

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    /** The flat list of currently visible nodes — the adapter's data source. */
    @NonNull
    private final VisibleNodeList visibleList;

    /** Registered listeners (typically just the adapter). */
    @NonNull
    private final List<ExpandListener> listeners = new ArrayList<>();

    /**
     * IDs of nodes whose children are being lazily loaded.  These nodes are
     * considered "expanded with pending load" and show a loading placeholder.
     */
    @NonNull
    private final Set<String> lazyLoadingIds = new HashSet<>();

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    /**
     * Creates an {@code ExpandManager} bound to the given visible list.
     *
     * @param visibleList the flat visible node list maintained by the adapter
     */
    public ExpandManager(@NonNull VisibleNodeList visibleList) {
        this.visibleList = visibleList;
    }

    // -------------------------------------------------------------------------
    // Listener management
    // -------------------------------------------------------------------------

    /** Registers a listener. */
    public void addExpandListener(@NonNull ExpandListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    /** Unregisters a listener. */
    public void removeExpandListener(@NonNull ExpandListener listener) {
        listeners.remove(listener);
    }

    // -------------------------------------------------------------------------
    // Expand / Collapse
    // -------------------------------------------------------------------------

    /**
     * Expands {@code node}, making its direct children visible.
     *
     * <p>If the node is already expanded or has no children (and is not marked
     * as having unloaded children), this is a no-op.
     *
     * <p>If {@link TreeNode#isLazyLoadPending()} is {@code true}, a loading
     * placeholder row is inserted instead of real children; the caller is
     * responsible for triggering the actual load and then calling
     * {@link #onLazyLoadCompleted(TreeNode, List)}.
     *
     * @param node the node to expand
     */
    @MainThread
    public void expand(@NonNull TreeNode node) {
        if (node.isExpanded()) return;
        if (!node.hasChildren()) return;

        node.setExpanded(true);

        // Find insertion point: it is the position immediately after `node`
        // in the visible list.
        int nodePosition = visibleList.indexOf(node);
        if (nodePosition < 0) {
            // Node is not in the visible list (it is inside a collapsed parent).
            // Still mark it expanded so that when its parent is expanded the
            // correct children will be visible.
            return;
        }
        int insertPosition = nodePosition + 1;

        List<TreeNode> toInsert;

        if (node.isLazyLoadPending()) {
            // Insert a single TYPE_LOADING placeholder.
            TreeNode placeholder = new TreeNode.Builder("Loading…")
                    .setId("__loading_" + node.getId() + "__")
                    .setType(TreeNode.TYPE_LOADING)
                    .build();
            toInsert = Collections.singletonList(placeholder);
            lazyLoadingIds.add(node.getId());
        } else {
            // Collect the visible descendants of the newly expanded children.
            // A child contributes itself + any expanded sub-children.
            toInsert = collectExpandedSubtree(node);
        }

        visibleList.insertAll(insertPosition, toInsert);
        notifyExpanded(node, toInsert, insertPosition);
    }

    /**
     * Collapses {@code node}, hiding all its descendant rows.
     *
     * <p>If the node is already collapsed, this is a no-op.
     *
     * @param node the node to collapse
     */
    @MainThread
    public void collapse(@NonNull TreeNode node) {
        if (!node.isExpanded()) return;

        int nodePosition = visibleList.indexOf(node);

        // Collect all visible descendants before marking collapsed.
        List<TreeNode> toRemove = collectExpandedSubtree(node);

        // Mark collapsed AFTER collecting so the collection sees expanded state.
        node.setExpanded(false);
        lazyLoadingIds.remove(node.getId());

        if (nodePosition >= 0 && !toRemove.isEmpty()) {
            int removePosition = nodePosition + 1;
            visibleList.removeRange(removePosition, removePosition + toRemove.size());
            notifyCollapsed(node, toRemove, removePosition);
        }
    }

    /**
     * Toggles the expand/collapse state of {@code node}.
     *
     * @param node the node to toggle
     */
    @MainThread
    public void toggle(@NonNull TreeNode node) {
        if (node.isExpanded()) {
            collapse(node);
        } else {
            expand(node);
        }
    }

    /**
     * Expands all ancestors of {@code node} so that the node itself becomes
     * visible in the list.  Does not expand {@code node} itself.
     *
     * @param node the node to reveal
     */
    @MainThread
    public void expandToNode(@NonNull TreeNode node) {
        // Collect ancestors from root down to node's parent.
        List<TreeNode> ancestors = new ArrayList<>();
        TreeNode current = node.getParent();
        while (current != null && !current.getId().equals("__root__")) {
            ancestors.add(0, current);
            current = current.getParent();
        }
        // Expand from top (shallowest) to bottom to maintain correct positions.
        for (TreeNode ancestor : ancestors) {
            if (!ancestor.isExpanded()) {
                expand(ancestor);
            }
        }
    }

    /**
     * Expands {@code node} and recursively expands all of its descendants.
     * For very large trees this can produce a large visible list; prefer
     * {@link #expand(TreeNode)} for individual nodes.
     *
     * @param node the subtree root to fully expand
     */
    @MainThread
    public void expandAll(@NonNull TreeNode node) {
        // Iterative BFS to avoid stack overflow on deep trees.
        Deque<TreeNode> queue = new ArrayDeque<>();
        queue.add(node);
        while (!queue.isEmpty()) {
            TreeNode current = queue.pollFirst();
            if (!current.isExpanded() && current.hasChildren()) {
                expand(current);
            }
            for (TreeNode child : current.getChildren()) {
                if (child.hasChildren()) {
                    queue.add(child);
                }
            }
        }
    }

    /**
     * Collapses {@code node} and recursively collapses all of its descendants.
     *
     * @param node the subtree root to fully collapse
     */
    @MainThread
    public void collapseAll(@NonNull TreeNode node) {
        // Post-order: collapse deepest first to avoid multiple list rebuilds.
        collapseAllRecursive(node);
        if (node.isExpanded()) {
            collapse(node);
        }
    }

    private void collapseAllRecursive(@NonNull TreeNode node) {
        for (TreeNode child : node.getChildren()) {
            if (child.isExpanded()) {
                collapseAllRecursive(child);
                collapse(child);
            }
        }
    }

    /**
     * Expands all top-level nodes (direct children of root).
     *
     * @param root the invisible root node
     */
    @MainThread
    public void expandTopLevel(@NonNull TreeNode root) {
        for (TreeNode child : root.getChildren()) {
            if (!child.isExpanded() && child.hasChildren()) {
                expand(child);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Lazy load completion
    // -------------------------------------------------------------------------

    /**
     * Called by the data provider after a lazy load completes.  Removes the
     * loading placeholder row and inserts the real children.
     *
     * @param parent      the node whose children have now been loaded
     * @param newChildren the freshly loaded children (already attached to {@code parent})
     */
    @MainThread
    public void onLazyLoadCompleted(
            @NonNull TreeNode parent,
            @NonNull List<TreeNode> newChildren) {
        lazyLoadingIds.remove(parent.getId());
        parent.setLazyLoadPending(false);

        // Remove the loading placeholder.
        String placeholderId = "__loading_" + parent.getId() + "__";
        int placeholderPos   = visibleList.indexOfId(placeholderId);
        if (placeholderPos >= 0) {
            visibleList.removeAt(placeholderPos);
        }

        if (newChildren.isEmpty()) {
            // No children: mark as leaf so the arrow disappears.
            parent.setHasChildren(false);
            notifyCollapsed(parent, Collections.emptyList(), -1);
            return;
        }

        // Insert visible subtree of the newly loaded children.
        List<TreeNode> toInsert = collectExpandedSubtree(parent);
        int parentPos = visibleList.indexOf(parent);
        int insertPos = (parentPos >= 0) ? parentPos + 1 : visibleList.size();

        visibleList.insertAll(insertPos, toInsert);
        notifyExpanded(parent, toInsert, insertPos);
    }

    /**
     * Called when a lazy load fails.  Removes the loading placeholder and
     * collapses the node to prevent a broken open state.
     *
     * @param parent the node whose load failed
     */
    @MainThread
    public void onLazyLoadFailed(@NonNull TreeNode parent) {
        lazyLoadingIds.remove(parent.getId());
        parent.setLazyLoadPending(false);

        String placeholderId = "__loading_" + parent.getId() + "__";
        int placeholderPos   = visibleList.indexOfId(placeholderId);
        if (placeholderPos >= 0) {
            visibleList.removeAt(placeholderPos);
        }

        parent.setExpanded(false);
        // Notify collapsed with empty list — adapter will animate the arrow.
        notifyCollapsed(parent, Collections.emptyList(), -1);
    }

    // -------------------------------------------------------------------------
    // Subtree collection — virtual tree core algorithm
    // -------------------------------------------------------------------------

    /**
     * Recursively collects the visible flat list of nodes that should be inserted
     * when {@code parent} is expanded.
     *
     * <p>This is the core of the Virtual Tree algorithm:
     * <ol>
     *   <li>For each direct child of {@code parent}, add the child to the list.
     *   <li>If the child is itself expanded (it was expanded before this call),
     *       recursively collect its visible subtree too.
     * </ol>
     *
     * <p>Result is in display order (depth-first, pre-order), which maps exactly
     * to the RecyclerView row order.
     *
     * @param parent the node whose visible subtree to collect
     * @return ordered flat list of visible descendant nodes
     */
    @NonNull
    private List<TreeNode> collectExpandedSubtree(@NonNull TreeNode parent) {
        List<TreeNode> result = new ArrayList<>();
        collectExpandedSubtreeRecursive(parent, result);
        return result;
    }

    private void collectExpandedSubtreeRecursive(
            @NonNull TreeNode node,
            @NonNull List<TreeNode> result) {
        for (TreeNode child : node.getChildren()) {
            result.add(child);
            if (child.isExpanded() && child.hasChildren()) {
                collectExpandedSubtreeRecursive(child, result);
            }
        }
    }

    // -------------------------------------------------------------------------
    // State queries
    // -------------------------------------------------------------------------

    /**
     * Returns {@code true} if the given node's children are currently being loaded.
     *
     * @param nodeId the node ID to check
     */
    public boolean isLazyLoading(@NonNull String nodeId) {
        return lazyLoadingIds.contains(nodeId);
    }

    /**
     * Returns the set of IDs of all currently expanded nodes.
     * This is a snapshot — mutations after this call are not reflected.
     */
    @NonNull
    public Set<String> collectExpandedIds() {
        Set<String> expanded = new HashSet<>();
        collectExpandedIdsRecursive(visibleList.getItems(), expanded);
        return expanded;
    }

    private void collectExpandedIdsRecursive(
            @NonNull List<TreeNode> nodes,
            @NonNull Set<String> result) {
        for (TreeNode node : nodes) {
            if (node.isExpanded()) {
                result.add(node.getId());
            }
        }
    }

    // -------------------------------------------------------------------------
    // Rebuilding the visible list from scratch
    // -------------------------------------------------------------------------

    /**
     * Rebuilds the entire visible list from the model, respecting each node's
     * current expanded state.  Used after state restoration or a full tree reload.
     *
     * @param root the invisible root node
     */
    @MainThread
    public void rebuildVisibleList(@NonNull TreeNode root) {
        List<TreeNode> newVisible = new ArrayList<>();
        buildVisibleListRecursive(root, newVisible);
        visibleList.reset(newVisible);
    }

    private void buildVisibleListRecursive(
            @NonNull TreeNode node,
            @NonNull List<TreeNode> result) {
        for (TreeNode child : node.getChildren()) {
            result.add(child);
            if (child.isExpanded()) {
                buildVisibleListRecursive(child, result);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Private notification helpers
    // -------------------------------------------------------------------------

    private void notifyExpanded(
            @NonNull TreeNode parent,
            @NonNull List<TreeNode> insertedNodes,
            int insertPosition) {
        for (ExpandListener listener : listeners) {
            listener.onNodesExpanded(parent, insertedNodes, insertPosition);
        }
    }

    private void notifyCollapsed(
            @NonNull TreeNode parent,
            @NonNull List<TreeNode> removedNodes,
            int removePosition) {
        for (ExpandListener listener : listeners) {
            listener.onNodesCollapsed(parent, removedNodes, removePosition);
        }
    }
}
