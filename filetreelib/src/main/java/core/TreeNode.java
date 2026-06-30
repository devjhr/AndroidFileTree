package ir.hanzodev1375.filetreelib.core;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import ir.hanzodev1375.filetreelib.model.FilePayload;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The fundamental unit of the tree structure.
 *
 * <p>A {@code TreeNode} holds:
 *
 * <ul>
 *   <li>A stable, unique {@link #id} (by default a UUID, but callers can provide path-based IDs for
 *       filesystem trees).
 *   <li>A human-readable {@link #name} displayed in the row.
 *   <li>A {@link #type} constant distinguishing files, folders, and virtual nodes.
 *   <li>References to its parent and children.
 *   <li>UI-state flags: {@link #expanded}, {@link #selected}, {@link #bookmarked}.
 *   <li>An optional opaque {@link #payload} (e.g. {@link com.treeview.model.FilePayload}).
 * </ul>
 *
 * <p><strong>Thread safety:</strong> {@code TreeNode} is not thread-safe. All mutations must happen
 * on the main thread (or be marshalled through {@link TreeController}).
 *
 * <p><strong>Virtual Tree contract:</strong> children are only populated when the node is expanded
 * and the data provider has loaded them. A node with {@link #isLazyLoadPending()} returning {@code
 * true} shows a spinner row instead of its real children until the provider delivers them.
 */
public final class TreeNode {

  // -------------------------------------------------------------------------
  // Type constants
  // -------------------------------------------------------------------------

  /** This node has no children and represents a leaf (file). */
  public static final int TYPE_FILE = 0;

  /** This node can have children and represents a folder/directory. */
  public static final int TYPE_FOLDER = 1;

  /**
   * A synthetic grouping node injected by the provider (e.g. "node_modules [4,218 files]") that
   * uses a different visual treatment.
   */
  public static final int TYPE_VIRTUAL = 2;

  /** A transient placeholder shown while a lazy-load is in progress. */
  public static final int TYPE_LOADING = 3;

  // -------------------------------------------------------------------------
  // Fields
  // -------------------------------------------------------------------------

  /** Stable identifier — by default never changes, but may be updated when the underlying path changes (e.g. rename). */
  @NonNull private String id;

  /** Human-readable display name (filename, folder name, virtual label). */
  @NonNull private String name;

  /** One of the {@code TYPE_*} constants above. */
  private final int type;

  /**
   * Direct parent reference. {@code null} only for the invisible root node created by {@link
   * #root()}.
   */
  @Nullable private TreeNode parent;

  /**
   * Ordered list of direct children. This list is authoritative — the {@link
   * com.treeview.utils.VisibleNodeList} derives the flat adapter list from it by recursively
   * including only expanded subtrees.
   */
  @NonNull private final List<TreeNode> children = new ArrayList<>();

  /**
   * Depth in the tree (0 = root's direct children). Calculated lazily when the node is attached to
   * a parent via {@link #addChild(TreeNode)}.
   */
  private int depth;

  /** Whether this node's children are currently visible in the RecyclerView. */
  private boolean expanded;

  /** Whether this node is part of the current selection set. */
  private boolean selected;

  /** Whether the user has bookmarked this node. */
  private boolean bookmarked;

  /**
   * {@code true} when this is a folder whose children have not yet been loaded by the data
   * provider. Causes a {@link #TYPE_LOADING} placeholder row to be shown inside the expanded
   * subtree.
   */
  private final AtomicBoolean lazyLoadPending = new AtomicBoolean(false);

  /**
   * {@code true} when the node has at least one child (even if not yet loaded). Drives whether to
   * render a collapse/expand arrow.
   */
  private boolean hasChildren;

  /**
   * Arbitrary payload attached by the caller — typically a {@link com.treeview.model.FilePayload}
   * but can be any object.
   */
  @Nullable private Object payload;

  /**
   * Optional tag for in-memory extensions (e.g. sort order, custom badge text). Not serialized;
   * transient per session.
   */
  @Nullable private Object tag;

  // -------------------------------------------------------------------------
  // Factory methods
  // -------------------------------------------------------------------------

  /**
   * Creates the invisible root node. The root is never rendered — it is only a container for the
   * top-level nodes.
   */
  @NonNull
  public static TreeNode root() {
    TreeNode root = new TreeNode("__root__", "", TYPE_FOLDER);
    root.depth = -1; // root's children start at depth 0
    return root;
  }

  // -------------------------------------------------------------------------
  // Constructor (use Builder for public construction)
  // -------------------------------------------------------------------------

  private TreeNode(@NonNull String id, @NonNull String name, int type) {
    this.id = id;
    this.name = name;
    this.type = type;
  }

  // -------------------------------------------------------------------------
  // Child management
  // -------------------------------------------------------------------------

  /**
   * Appends {@code child} as the last child of this node.
   *
   * <p>Sets the child's {@link #parent} and recalculates its {@link #depth} (and recursively its
   * descendants' depths).
   *
   * @param child the node to add; must not already have a parent
   */
  public void addChild(@NonNull TreeNode child) {
    if (child.parent != null) {
      throw new IllegalStateException(
          "Node '"
              + child.name
              + "' already has a parent. "
              + "Detach it first with removeChild().");
    }
    child.parent = this;
    child.depth = this.depth + 1;
    recalculateDescendantDepths(child);
    children.add(child);
    hasChildren = true;
  }

  /**
   * Inserts {@code child} at the given index in this node's children list.
   *
   * @param index the position to insert at (0 = first)
   * @param child the node to insert; must not already have a parent
   */
  public void addChildAt(int index, @NonNull TreeNode child) {
    if (child.parent != null) {
      throw new IllegalStateException("Node '" + child.name + "' already has a parent.");
    }
    child.parent = this;
    child.depth = this.depth + 1;
    recalculateDescendantDepths(child);
    children.add(index, child);
    hasChildren = true;
  }

  /**
   * Removes the specified child from this node.
   *
   * @param child the child to remove (must be a direct child)
   * @return {@code true} if the child was found and removed
   */
  public boolean removeChild(@NonNull TreeNode child) {
    boolean removed = children.remove(child);
    if (removed) {
      child.parent = null;
      if (children.isEmpty()) {
        hasChildren = false;
      }
    }
    return removed;
  }

  /**
   * Removes the child at the given index.
   *
   * @param index index of the child to remove
   * @return the removed child
   */
  @NonNull
  public TreeNode removeChildAt(int index) {
    TreeNode removed = children.remove(index);
    removed.parent = null;
    if (children.isEmpty()) hasChildren = false;
    return removed;
  }

  /** Removes all children from this node. */
  public void clearChildren() {
    for (TreeNode child : children) {
      child.parent = null;
    }
    children.clear();
    hasChildren = false;
    lazyLoadPending.set(false);
  }

  /**
   * Replaces this node's entire child list in one shot. Used by the lazy-load mechanism once the
   * data provider delivers the loaded children.
   *
   * @param newChildren the new children list (will be defensive-copied)
   */
  public void setChildren(@NonNull List<TreeNode> newChildren) {
    clearChildren();
    for (TreeNode child : newChildren) {
      addChild(child);
    }
  }

  /**
   * Returns an unmodifiable view of the direct children of this node. Mutations must go through
   * {@link #addChild}, {@link #removeChild}, etc.
   */
  @NonNull
  public List<TreeNode> getChildren() {
    return Collections.unmodifiableList(children);
  }

  /** Returns the number of direct children. */
  public int getChildCount() {
    return children.size();
  }

  /** Returns the child at the given index. */
  @NonNull
  public TreeNode getChildAt(int index) {
    return children.get(index);
  }

  /**
   * Returns the index of {@code child} within this node's children list, or {@code -1} if not
   * found.
   */
  public int indexOfChild(@NonNull TreeNode child) {
    return children.indexOf(child);
  }

  // -------------------------------------------------------------------------
  // Depth recalculation
  // -------------------------------------------------------------------------

  /**
   * Recursively recalculates the {@link #depth} of all descendants of {@code node}, anchored to
   * {@code node}'s already-set depth.
   */
  private void recalculateDescendantDepths(@NonNull TreeNode node) {
    for (TreeNode child : node.children) {
      child.depth = node.depth + 1;
      recalculateDescendantDepths(child);
    }
  }

  // -------------------------------------------------------------------------
  // Tree navigation
  // -------------------------------------------------------------------------

  /** Returns this node's parent, or {@code null} if this is the root. */
  @Nullable
  public TreeNode getParent() {
    return parent;
  }

  /** Returns {@code true} if this node has no parent (i.e. it is the root). */
  public boolean isRoot() {
    return parent == null;
  }

  /**
   * Returns the root node by following parent references. For well-formed trees this runs in
   * O(depth) time.
   */
  @NonNull
  public TreeNode getRoot() {
    TreeNode current = this;
    while (current.parent != null) {
      current = current.parent;
    }
    return current;
  }

  /** Returns the {@link TreePath} from the root to this node. */
  @NonNull
  public TreePath getPath() {
    List<TreeNode> segments = new ArrayList<>();
    TreeNode current = this;
    while (current != null && !current.id.equals("__root__")) {
      segments.add(0, current);
      current = current.parent;
    }
    return new TreePath(segments);
  }

  /**
   * Returns {@code true} if {@code possibleAncestor} is a strict ancestor of this node (i.e. this
   * node is a descendant of {@code possibleAncestor}).
   */
  public boolean isDescendantOf(@NonNull TreeNode possibleAncestor) {
    TreeNode current = this.parent;
    while (current != null) {
      if (current == possibleAncestor) return true;
      current = current.parent;
    }
    return false;
  }

  /**
   * Finds the first child whose {@link #id} equals {@code childId}, or {@code null} if not found.
   */
  @Nullable
  public TreeNode findChildById(@NonNull String childId) {
    for (TreeNode child : children) {
      if (child.id.equals(childId)) return child;
    }
    return null;
  }

  // -------------------------------------------------------------------------
  // Accessors — identity
  // -------------------------------------------------------------------------

  /** Returns the stable, unique node ID. */
  @NonNull
  public String getId() {
    return id;
  }

  /**
   * Updates the node ID, e.g. after a rename changes a path-based ID. Caller is responsible for
   * invalidating any caches keyed on the old ID (via {@link #getId()}) *before* calling this, and
   * for re-inserting this node into any {@code HashMap}/{@code HashSet} keyed by ID, since
   * {@link #equals} and {@link #hashCode} are based on {@link #id} and mutating it while the node
   * sits in a hash-based collection corrupts that collection's bucket placement.
   */
  public void setId(@NonNull String id) {
    if (id.isEmpty()) throw new IllegalArgumentException("id must not be empty");
    this.id = id;
  }

  /** Returns the display name of this node. */
  @NonNull
  public String getName() {
    return name;
  }

  /** Updates the display name (e.g. after a rename operation). */
  public void setName(@NonNull String name) {
    if (name.isEmpty()) throw new IllegalArgumentException("name must not be empty");
    this.name = name;
  }

  /** Returns the node type: one of {@link #TYPE_FILE}, {@link #TYPE_FOLDER}, etc. */
  public int getType() {
    return type;
  }

  /** Convenience: returns {@code true} if {@link #type} is {@link #TYPE_FOLDER}. */
  public boolean isFolder() {
    return type == TYPE_FOLDER;
  }

  /** Convenience: returns {@code true} if {@link #type} is {@link #TYPE_FILE}. */
  public boolean isFile() {
    return type == TYPE_FILE;
  }

  /** Convenience: returns {@code true} if {@link #type} is {@link #TYPE_VIRTUAL}. */
  public boolean isVirtual() {
    return type == TYPE_VIRTUAL;
  }

  /** Convenience: returns {@code true} if {@link #type} is {@link #TYPE_LOADING}. */
  public boolean isLoadingPlaceholder() {
    return type == TYPE_LOADING;
  }

  // -------------------------------------------------------------------------
  // Accessors — depth and hierarchy
  // -------------------------------------------------------------------------

  /**
   * Returns the depth of this node in the tree. The root node's depth is {@code -1}; its direct
   * children have depth {@code 0}.
   */
  public int getDepth() {
    return depth;
  }

  // -------------------------------------------------------------------------
  // Accessors — UI state
  // -------------------------------------------------------------------------

  /** Returns {@code true} if this node's children are currently expanded. */
  public boolean isExpanded() {
    return expanded;
  }

  /**
   * Sets the expanded state. This flag is read by {@link com.treeview.utils.VisibleNodeList} to
   * decide which children to include in the flat adapter list.
   */
  public void setExpanded(boolean expanded) {
    this.expanded = expanded;
  }

  /** Returns {@code true} if this node is in the current selection set. */
  public boolean isSelected() {
    return selected;
  }

  /** Updates the selection flag. */
  public void setSelected(boolean selected) {
    this.selected = selected;
  }

  /** Returns {@code true} if the user has bookmarked this node. */
  public boolean isBookmarked() {
    return bookmarked;
  }

  /** Toggles the bookmarked flag. */
  public void setBookmarked(boolean bookmarked) {
    this.bookmarked = bookmarked;
  }

  // -------------------------------------------------------------------------
  // Accessors — lazy loading
  // -------------------------------------------------------------------------

  /**
   * Returns {@code true} when this is a folder that has been expanded but whose children have not
   * yet been loaded from the provider. The adapter inserts a {@link #TYPE_LOADING} placeholder row
   * to communicate this.
   */
  public boolean isLazyLoadPending() {
    return lazyLoadPending.get();
  }

  /**
   * Marks this node as awaiting a lazy load. Called by {@link com.treeview.core.ExpandManager} when
   * a folder is first expanded.
   */
  public void setLazyLoadPending(boolean pending) {
    lazyLoadPending.set(pending);
  }

  /**
   * Returns {@code true} if this node is known to have children (even if they are not yet loaded).
   * Determines whether to show an expand/collapse arrow.
   */
  public boolean hasChildren() {
    return hasChildren || !children.isEmpty();
  }

  /**
   * Explicitly declares whether this node has children. Used by the provider when it knows a
   * directory is non-empty but has not yet loaded the entries.
   */
  public void setHasChildren(boolean hasChildren) {
    this.hasChildren = hasChildren;
  }

  // -------------------------------------------------------------------------
  // Accessors — payload and tag
  // -------------------------------------------------------------------------

  /** Returns the opaque payload attached by the caller, or {@code null}. */
  @Nullable
  public Object getPayload() {
    return payload;
  }

  /**
   * Convenience cast accessor. Returns the payload cast to {@code T}, or {@code null} if the
   * payload is {@code null} or of the wrong type.
   */
  @Nullable
  @SuppressWarnings("unchecked")
  public <T> T getPayload(@NonNull Class<T> type) {
    if (payload != null && type.isInstance(payload)) {
      return (T) payload;
    }
    return null;
  }

  /** Attaches an opaque payload. */
  public void setPayload(@Nullable Object payload) {
    this.payload = payload;
  }

  /** Returns the transient tag, or {@code null}. */
  @Nullable
  public Object getTag() {
    return tag;
  }

  /** Sets a transient tag (not serialized). */
  public void setTag(@Nullable Object tag) {
    this.tag = tag;
  }

  // -------------------------------------------------------------------------
  // Object
  // -------------------------------------------------------------------------

  /**
   * Equality is based solely on {@link #id} — two nodes with the same ID represent the same logical
   * entity even if their mutable state differs.
   */
  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof TreeNode)) return false;
    return id.equals(((TreeNode) o).id);
  }

  @Override
  public int hashCode() {
    return id.hashCode();
  }

  @NonNull
  @Override
  public String toString() {
    return "TreeNode{"
        + "id='"
        + id
        + '\''
        + ", name='"
        + name
        + '\''
        + ", type="
        + type
        + ", depth="
        + depth
        + ", children="
        + children.size()
        + ", expanded="
        + expanded
        + '}';
  }

  // -------------------------------------------------------------------------
  // Builder
  // -------------------------------------------------------------------------

  /**
   * Fluent builder for {@link TreeNode}.
   *
   * <pre>{@code
   * TreeNode node = new TreeNode.Builder("src")
   *     .setId("/project/src")
   *     .setType(TreeNode.TYPE_FOLDER)
   *     .setHasChildren(true)
   *     .setPayload(new FilePayload.Builder("/project/src", true).build())
   *     .build();
   * }</pre>
   */
  public static final class Builder {

    @NonNull private final String name;
    @NonNull private String id;
    private int type = TYPE_FILE;
    private boolean hasChildren = false;
    private boolean expanded = false;
    private boolean selected = false;
    private boolean bookmarked = false;
    @Nullable private Object payload;

    /**
     * Creates a builder for a node with the given display name. A random UUID is assigned as the ID
     * unless overridden via {@link #setId}.
     *
     * @param name the display name (must not be empty)
     */
    public Builder(@NonNull String name) {
      if (name.isEmpty()) throw new IllegalArgumentException("name must not be empty");
      this.name = name;
      this.id = UUID.randomUUID().toString();
    }

    /**
     * Overrides the node ID. Use a stable, unique string such as the absolute file path to enable
     * reliable state restoration.
     */
    public Builder setId(@NonNull String id) {
      if (id.isEmpty()) throw new IllegalArgumentException("id must not be empty");
      this.id = id;
      return this;
    }

    /** Sets the node type. Defaults to {@link #TYPE_FILE}. */
    public Builder setType(int type) {
      this.type = type;
      return this;
    }

    /** Declares whether this node has children (drives arrow visibility). */
    public Builder setHasChildren(boolean hasChildren) {
      this.hasChildren = hasChildren;
      return this;
    }

    /** Sets the initial expanded state. */
    public Builder setExpanded(boolean expanded) {
      this.expanded = expanded;
      return this;
    }

    /** Sets the initial selected state. */
    public Builder setSelected(boolean selected) {
      this.selected = selected;
      return this;
    }

    /** Sets the bookmarked state. */
    public Builder setBookmarked(boolean bookmarked) {
      this.bookmarked = bookmarked;
      return this;
    }

    /** Attaches an opaque payload. */
    public Builder setPayload(@Nullable Object payload) {
      this.payload = payload;
      return this;
    }

    /** Builds and returns the {@link TreeNode}. */
    @NonNull
    public TreeNode build() {
      TreeNode node = new TreeNode(id, name, type);
      node.hasChildren = hasChildren;
      node.expanded = expanded;
      node.selected = selected;
      node.bookmarked = bookmarked;
      node.payload = payload;
      return node;
    }
  }

  @Nullable
  public String getAbsolutePath() {
    FilePayload payload = getPayload(FilePayload.class);
    return payload != null ? payload.getAbsolutePath() : null;
  }
}
