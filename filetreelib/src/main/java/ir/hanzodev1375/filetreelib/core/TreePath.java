package ir.hanzodev1375.filetreelib.core;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * An immutable sequence of {@link TreeNode} references from the root's first
 * child down to a target node.  Analogous to {@code javax.swing.tree.TreePath}.
 *
 * <p>Paths are used to:
 * <ul>
 *   <li>Uniquely identify a node independent of its adapter position (which changes
 *       as siblings are inserted/removed).
 *   <li>Serialize expanded/selected state: a path resolves to a node ID list that
 *       survives structural mutations.
 *   <li>Drive programmatic expand-to-node / scroll-to-node operations.
 * </ul>
 *
 * <p>Paths do <em>not</em> include the invisible root node.
 */
public final class TreePath {

    /** The ordered segments from the first visible level down to the target node. */
    @NonNull
    private final List<TreeNode> segments;

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    /**
     * Creates a path from an ordered list of nodes.  The first element is the
     * direct child of the root; the last element is the target node.
     *
     * @param segments must contain at least one node
     */
    public TreePath(@NonNull List<TreeNode> segments) {
        if (segments.isEmpty()) {
            throw new IllegalArgumentException("TreePath must have at least one segment");
        }
        this.segments = Collections.unmodifiableList(new ArrayList<>(segments));
    }

    /**
     * Creates a single-segment path pointing directly to {@code node}.
     * The path omits the root node; {@code node} is expected to be a direct
     * child of root.
     */
    public TreePath(@NonNull TreeNode node) {
        this.segments = Collections.singletonList(node);
    }

    // -------------------------------------------------------------------------
    // Factory
    // -------------------------------------------------------------------------

    /**
     * Builds a {@link TreePath} by walking from {@code node} up to the root.
     * The invisible root node is excluded from the resulting segments.
     *
     * @param node the target node; must be attached to a root
     * @return a path from the first visible level to {@code node}
     */
    @NonNull
    public static TreePath fromNode(@NonNull TreeNode node) {
        List<TreeNode> segments = new ArrayList<>();
        TreeNode current = node;
        while (current != null && !current.getId().equals("__root__")) {
            segments.add(0, current);
            current = current.getParent();
        }
        if (segments.isEmpty()) {
            throw new IllegalStateException(
                    "Cannot build TreePath from a node with no parent chain");
        }
        return new TreePath(segments);
    }

    /**
     * Builds a {@link TreePath} from a list of node IDs by traversing the tree
     * rooted at {@code root}.  Returns {@code null} if any segment cannot be found.
     *
     * @param root    the invisible root node
     * @param nodeIds ordered node IDs from the first level down to the target
     * @return resolved path, or {@code null} if the path is invalid
     */
    @Nullable
    public static TreePath fromIds(@NonNull TreeNode root, @NonNull List<String> nodeIds) {
        List<TreeNode> resolved = new ArrayList<>();
        List<TreeNode> currentLevel = root.getChildren();

        for (String id : nodeIds) {
            TreeNode found = null;
            for (TreeNode candidate : currentLevel) {
                if (candidate.getId().equals(id)) {
                    found = candidate;
                    break;
                }
            }
            if (found == null) return null;
            resolved.add(found);
            currentLevel = found.getChildren();
        }
        if (resolved.isEmpty()) return null;
        return new TreePath(resolved);
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    /**
     * Returns the ordered list of nodes from the first visible level down to the
     * target (last segment).
     */
    @NonNull
    public List<TreeNode> getSegments() {
        return segments;
    }

    /** Returns the number of segments (= depth + 1 of the target node). */
    public int getLength() {
        return segments.size();
    }

    /** Returns the first segment (the node at the first visible level). */
    @NonNull
    public TreeNode getFirstSegment() {
        return segments.get(0);
    }

    /**
     * Returns the last segment, which is the target node this path points to.
     */
    @NonNull
    public TreeNode getLastSegment() {
        return segments.get(segments.size() - 1);
    }

    /**
     * Returns the node at position {@code index} in the segment list.
     *
     * @param index 0-based index
     */
    @NonNull
    public TreeNode getSegment(int index) {
        return segments.get(index);
    }

    /**
     * Returns the parent path (all segments except the last), or {@code null} if
     * this path has only one segment.
     */
    @Nullable
    public TreePath getParentPath() {
        if (segments.size() <= 1) return null;
        return new TreePath(segments.subList(0, segments.size() - 1));
    }

    /**
     * Returns a new path that is this path extended by {@code child}.
     * {@code child} must be a direct child of this path's last segment.
     */
    @NonNull
    public TreePath pathByAddingChild(@NonNull TreeNode child) {
        List<TreeNode> newSegments = new ArrayList<>(segments);
        newSegments.add(child);
        return new TreePath(newSegments);
    }

    /**
     * Returns an ordered list of node IDs corresponding to each segment.
     * Useful for serializing the path without holding node references.
     */
    @NonNull
    public List<String> toIdList() {
        List<String> ids = new ArrayList<>(segments.size());
        for (TreeNode node : segments) {
            ids.add(node.getId());
        }
        return ids;
    }

    /**
     * Returns {@code true} if this path is an ancestor of {@code other} —
     * i.e. all of this path's segments appear at the start of {@code other}'s
     * segments in the same order.
     */
    public boolean isAncestorOf(@NonNull TreePath other) {
        if (getLength() >= other.getLength()) return false;
        for (int i = 0; i < segments.size(); i++) {
            if (!segments.get(i).getId().equals(other.segments.get(i).getId())) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns {@code true} if this path is a descendant of {@code other}.
     */
    public boolean isDescendantOf(@NonNull TreePath other) {
        return other.isAncestorOf(this);
    }

    // -------------------------------------------------------------------------
    // Object
    // -------------------------------------------------------------------------

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TreePath)) return false;
        TreePath other = (TreePath) o;
        if (segments.size() != other.segments.size()) return false;
        for (int i = 0; i < segments.size(); i++) {
            if (!segments.get(i).getId().equals(other.segments.get(i).getId())) {
                return false;
            }
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 1;
        for (TreeNode node : segments) {
            hash = 31 * hash + node.getId().hashCode();
        }
        return hash;
    }

    @NonNull
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("TreePath[");
        for (int i = 0; i < segments.size(); i++) {
            if (i > 0) sb.append(" > ");
            sb.append(segments.get(i).getName());
        }
        sb.append(']');
        return sb.toString();
    }
}
