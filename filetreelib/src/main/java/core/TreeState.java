package ir.hanzodev1375.filetreelib.core;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import ir.hanzodev1375.filetreelib.model.TreeNodeState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A fully serializable snapshot of the entire tree's UI state.
 *
 * <p>{@code TreeState} captures:
 * <ul>
 *   <li>The set of expanded node IDs.
 *   <li>The set of selected node IDs.
 *   <li>The set of bookmarked node IDs.
 *   <li>The adapter scroll position and offset.
 *   <li>Individual per-node states (for nodes with custom scroll offsets, etc.).
 * </ul>
 *
 * <p>Usage:
 * <pre>{@code
 * // Save
 * TreeState state = treeController.saveState();
 * outState.putParcelable("tree_state", state);
 *
 * // Restore
 * TreeState state = savedInstanceState.getParcelable("tree_state");
 * treeController.restoreState(state);
 * }</pre>
 *
 * <p>This class is {@link Parcelable} and can be written to a {@link android.os.Bundle}
 * directly for Activity/Fragment state restoration.
 */
public final class TreeState implements Parcelable {

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    /** IDs of all nodes that were expanded when this snapshot was taken. */
    @NonNull
    private final Set<String> expandedIds;

    /** IDs of all nodes that were selected when this snapshot was taken. */
    @NonNull
    private final Set<String> selectedIds;

    /** IDs of all nodes that were bookmarked when this snapshot was taken. */
    @NonNull
    private final Set<String> bookmarkedIds;

    /**
     * Adapter position of the first fully visible item at the time of the snapshot.
     * {@code -1} if unknown.
     */
    private final int scrollPosition;

    /**
     * Pixel offset of the first visible item from the top of its view.
     * Used together with {@link #scrollPosition} for exact scroll restoration.
     */
    private final int scrollOffset;

    /**
     * Flat map of per-node states for nodes that carry extra metadata beyond the
     * simple expanded/selected/bookmarked flags.
     */
    @NonNull
    private final Map<String, TreeNodeState> nodeStates;

    /**
     * ID of the node that currently has keyboard focus, or {@code null} if no node
     * is focused.
     */
    @Nullable
    private final String focusedNodeId;

    // -------------------------------------------------------------------------
    // Constructor (private — use Builder)
    // -------------------------------------------------------------------------

    private TreeState(Builder builder) {
        this.expandedIds   = Collections.unmodifiableSet(new HashSet<>(builder.expandedIds));
        this.selectedIds   = Collections.unmodifiableSet(new HashSet<>(builder.selectedIds));
        this.bookmarkedIds = Collections.unmodifiableSet(new HashSet<>(builder.bookmarkedIds));
        this.scrollPosition = builder.scrollPosition;
        this.scrollOffset   = builder.scrollOffset;
        this.nodeStates     = Collections.unmodifiableMap(new HashMap<>(builder.nodeStates));
        this.focusedNodeId  = builder.focusedNodeId;
    }

    // -------------------------------------------------------------------------
    // Parcelable
    // -------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    protected TreeState(@NonNull Parcel in) {
        // expandedIds
        List<String> expanded = new ArrayList<>();
        in.readStringList(expanded);
        expandedIds = Collections.unmodifiableSet(new HashSet<>(expanded));

        // selectedIds
        List<String> selected = new ArrayList<>();
        in.readStringList(selected);
        selectedIds = Collections.unmodifiableSet(new HashSet<>(selected));

        // bookmarkedIds
        List<String> bookmarked = new ArrayList<>();
        in.readStringList(bookmarked);
        bookmarkedIds = Collections.unmodifiableSet(new HashSet<>(bookmarked));

        scrollPosition = in.readInt();
        scrollOffset   = in.readInt();
        focusedNodeId  = in.readString();

        // nodeStates map
        int mapSize = in.readInt();
        Map<String, TreeNodeState> statesMap = new HashMap<>(mapSize);
        for (int i = 0; i < mapSize; i++) {
            String key              = in.readString();
            TreeNodeState nodeState = in.readParcelable(TreeNodeState.class.getClassLoader());
            if (key != null && nodeState != null) {
                statesMap.put(key, nodeState);
            }
        }
        nodeStates = Collections.unmodifiableMap(statesMap);
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeStringList(new ArrayList<>(expandedIds));
        dest.writeStringList(new ArrayList<>(selectedIds));
        dest.writeStringList(new ArrayList<>(bookmarkedIds));
        dest.writeInt(scrollPosition);
        dest.writeInt(scrollOffset);
        dest.writeString(focusedNodeId);

        dest.writeInt(nodeStates.size());
        for (Map.Entry<String, TreeNodeState> entry : nodeStates.entrySet()) {
            dest.writeString(entry.getKey());
            dest.writeParcelable(entry.getValue(), flags);
        }
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<TreeState> CREATOR = new Creator<TreeState>() {
        @Override
        public TreeState createFromParcel(Parcel in) {
            return new TreeState(in);
        }

        @Override
        public TreeState[] newArray(int size) {
            return new TreeState[size];
        }
    };

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    /**
     * Returns the unmodifiable set of node IDs that should be in the expanded state
     * when this snapshot is restored.
     */
    @NonNull
    public Set<String> getExpandedIds() {
        return expandedIds;
    }

    /**
     * Returns {@code true} if the node with {@code nodeId} was expanded when this
     * state was captured.
     */
    public boolean isExpanded(@NonNull String nodeId) {
        return expandedIds.contains(nodeId);
    }

    /**
     * Returns the unmodifiable set of node IDs that should be selected when this
     * snapshot is restored.
     */
    @NonNull
    public Set<String> getSelectedIds() {
        return selectedIds;
    }

    /**
     * Returns {@code true} if the node with {@code nodeId} was selected when this
     * state was captured.
     */
    public boolean isSelected(@NonNull String nodeId) {
        return selectedIds.contains(nodeId);
    }

    /**
     * Returns the unmodifiable set of node IDs that were bookmarked when this
     * state was captured.
     */
    @NonNull
    public Set<String> getBookmarkedIds() {
        return bookmarkedIds;
    }

    /**
     * Returns {@code true} if the node with {@code nodeId} was bookmarked when this
     * state was captured.
     */
    public boolean isBookmarked(@NonNull String nodeId) {
        return bookmarkedIds.contains(nodeId);
    }

    /**
     * Returns the saved adapter position of the first visible item.
     * {@code -1} if not captured.
     */
    public int getScrollPosition() {
        return scrollPosition;
    }

    /**
     * Returns the saved pixel offset of the first visible item from the top of
     * the RecyclerView.
     */
    public int getScrollOffset() {
        return scrollOffset;
    }

    /**
     * Returns the ID of the node that had keyboard focus, or {@code null}.
     */
    @Nullable
    public String getFocusedNodeId() {
        return focusedNodeId;
    }

    /**
     * Returns the per-node state for the given node ID, or {@code null} if no
     * extra state was recorded for that node.
     */
    @Nullable
    public TreeNodeState getNodeState(@NonNull String nodeId) {
        return nodeStates.get(nodeId);
    }

    /**
     * Returns the full map of per-node states (unmodifiable).
     */
    @NonNull
    public Map<String, TreeNodeState> getNodeStates() {
        return nodeStates;
    }

    /**
     * Returns {@code true} if this state represents an entirely empty snapshot
     * (nothing expanded, selected, or bookmarked).
     */
    public boolean isEmpty() {
        return expandedIds.isEmpty()
                && selectedIds.isEmpty()
                && bookmarkedIds.isEmpty()
                && nodeStates.isEmpty();
    }

    // -------------------------------------------------------------------------
    // Object
    // -------------------------------------------------------------------------

    @NonNull
    @Override
    public String toString() {
        return "TreeState{"
                + "expanded=" + expandedIds.size()
                + ", selected=" + selectedIds.size()
                + ", bookmarked=" + bookmarkedIds.size()
                + ", scroll=(" + scrollPosition + "+" + scrollOffset + ")"
                + '}';
    }

    // -------------------------------------------------------------------------
    // Builder
    // -------------------------------------------------------------------------

    /** Fluent builder for {@link TreeState}. */
    public static final class Builder {

        @NonNull private final Set<String> expandedIds   = new HashSet<>();
        @NonNull private final Set<String> selectedIds   = new HashSet<>();
        @NonNull private final Set<String> bookmarkedIds = new HashSet<>();
        @NonNull private final Map<String, TreeNodeState> nodeStates = new HashMap<>();
        private int scrollPosition  = -1;
        private int scrollOffset    = 0;
        @Nullable private String focusedNodeId;

        public Builder() { /* empty */ }

        /** Marks a node ID as expanded. */
        public Builder addExpandedId(@NonNull String nodeId) {
            expandedIds.add(nodeId);
            return this;
        }

        /** Marks a set of node IDs as expanded. */
        public Builder addAllExpandedIds(@NonNull Set<String> ids) {
            expandedIds.addAll(ids);
            return this;
        }

        /** Marks a node ID as selected. */
        public Builder addSelectedId(@NonNull String nodeId) {
            selectedIds.add(nodeId);
            return this;
        }

        /** Marks a set of node IDs as selected. */
        public Builder addAllSelectedIds(@NonNull Set<String> ids) {
            selectedIds.addAll(ids);
            return this;
        }

        /** Marks a node ID as bookmarked. */
        public Builder addBookmarkedId(@NonNull String nodeId) {
            bookmarkedIds.add(nodeId);
            return this;
        }

        /** Marks a set of node IDs as bookmarked. */
        public Builder addAllBookmarkedIds(@NonNull Set<String> ids) {
            bookmarkedIds.addAll(ids);
            return this;
        }

        /** Records the first visible item's adapter position. */
        public Builder scrollPosition(int position) {
            this.scrollPosition = position;
            return this;
        }

        /** Records the first visible item's pixel offset. */
        public Builder scrollOffset(int offset) {
            this.scrollOffset = offset;
            return this;
        }

        /** Records the focused node ID. */
        public Builder focusedNodeId(@Nullable String focusedNodeId) {
            this.focusedNodeId = focusedNodeId;
            return this;
        }

        /** Attaches extra state for a specific node. */
        public Builder putNodeState(@NonNull String nodeId, @NonNull TreeNodeState state) {
            nodeStates.put(nodeId, state);
            return this;
        }

        /** Builds and returns the {@link TreeState}. */
        @NonNull
        public TreeState build() {
            return new TreeState(this);
        }
    }
}
