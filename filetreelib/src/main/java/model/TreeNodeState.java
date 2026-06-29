package ir.hanzodev1375.filetreelib.model;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

/**
 * Serializable snapshot of the UI-relevant state of a single tree node. Used by {@link
 * com.treeview.core.TreeState} to persist and restore the visual appearance of every node across
 * configuration changes or process death.
 *
 * <p>Only data that affects the rendered row is stored here — the structural tree topology
 * (parent/child relationships) is reconstructed by the {@link
 * com.treeview.provider.TreeDataProvider}.
 */
public final class TreeNodeState implements Parcelable {

  /** Unique, stable identifier for the node (e.g. absolute file path). */
  @NonNull private final String nodeId;

  /** Whether this node's children are currently expanded in the UI. */
  private boolean expanded;

  /** Whether this node is currently selected. */
  private boolean selected;

  /** Whether the user has bookmarked this node. */
  private boolean bookmarked;

  /**
   * Scroll position within a lazy-loaded virtual subtree. Not used by the default implementation
   * but available for custom providers.
   */
  private int scrollOffset;

  // -------------------------------------------------------------------------
  // Constructor
  // -------------------------------------------------------------------------

  /**
   * Constructs a node state with explicit values.
   *
   * @param nodeId unique, stable identifier
   * @param expanded whether children are visible
   * @param selected whether the node is selected
   * @param bookmarked whether the node is bookmarked
   */
  public TreeNodeState(
      @NonNull String nodeId, boolean expanded, boolean selected, boolean bookmarked) {
    if (nodeId.isEmpty()) {
      throw new IllegalArgumentException("nodeId must not be empty");
    }
    this.nodeId = nodeId;
    this.expanded = expanded;
    this.selected = selected;
    this.bookmarked = bookmarked;
  }

  // -------------------------------------------------------------------------
  // Parcelable
  // -------------------------------------------------------------------------

  protected TreeNodeState(@NonNull Parcel in) {
    nodeId = in.readString();
    expanded = in.readByte() != 0;
    selected = in.readByte() != 0;
    bookmarked = in.readByte() != 0;
    scrollOffset = in.readInt();
  }

  @Override
  public void writeToParcel(@NonNull Parcel dest, int flags) {
    dest.writeString(nodeId);
    dest.writeByte((byte) (expanded ? 1 : 0));
    dest.writeByte((byte) (selected ? 1 : 0));
    dest.writeByte((byte) (bookmarked ? 1 : 0));
    dest.writeInt(scrollOffset);
  }

  @Override
  public int describeContents() {
    return 0;
  }

  public static final Creator<TreeNodeState> CREATOR =
      new Creator<TreeNodeState>() {
        @Override
        public TreeNodeState createFromParcel(Parcel in) {
          return new TreeNodeState(in);
        }

        @Override
        public TreeNodeState[] newArray(int size) {
          return new TreeNodeState[size];
        }
      };

  // -------------------------------------------------------------------------
  // Accessors
  // -------------------------------------------------------------------------

  /** Returns the stable node identifier. */
  @NonNull
  public String getNodeId() {
    return nodeId;
  }

  /** Returns {@code true} if this node's children are expanded. */
  public boolean isExpanded() {
    return expanded;
  }

  /** Sets the expanded flag. */
  public void setExpanded(boolean expanded) {
    this.expanded = expanded;
  }

  /** Returns {@code true} if this node is currently selected. */
  public boolean isSelected() {
    return selected;
  }

  /** Sets the selected flag. */
  public void setSelected(boolean selected) {
    this.selected = selected;
  }

  /** Returns {@code true} if this node is bookmarked. */
  public boolean isBookmarked() {
    return bookmarked;
  }

  /** Sets the bookmarked flag. */
  public void setBookmarked(boolean bookmarked) {
    this.bookmarked = bookmarked;
  }

  /** Returns an optional scroll offset for virtual subtrees. */
  public int getScrollOffset() {
    return scrollOffset;
  }

  /** Sets the scroll offset. */
  public void setScrollOffset(int scrollOffset) {
    this.scrollOffset = scrollOffset;
  }

  // -------------------------------------------------------------------------
  // Object
  // -------------------------------------------------------------------------

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof TreeNodeState)) return false;
    TreeNodeState other = (TreeNodeState) o;
    return expanded == other.expanded
        && selected == other.selected
        && bookmarked == other.bookmarked
        && nodeId.equals(other.nodeId);
  }

  @Override
  public int hashCode() {
    int result = nodeId.hashCode();
    result = 31 * result + (expanded ? 1 : 0);
    result = 31 * result + (selected ? 1 : 0);
    result = 31 * result + (bookmarked ? 1 : 0);
    return result;
  }

  @NonNull
  @Override
  public String toString() {
    return "TreeNodeState{"
        + "nodeId='"
        + nodeId
        + '\''
        + ", expanded="
        + expanded
        + ", selected="
        + selected
        + ", bookmarked="
        + bookmarked
        + '}';
  }
}
