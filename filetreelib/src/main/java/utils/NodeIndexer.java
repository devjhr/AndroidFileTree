package ir.hanzodev1375.filetreelib.utils;

import androidx.annotation.NonNull;
import ir.hanzodev1375.filetreelib.core.TreeNode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Provides O(1) adapter-position lookup for any TreeNode by maintaining a node-id to position map
 * that mirrors the VisibleNodeList.
 */
public final class NodeIndexer {

  @NonNull private final Map<String, Integer> index = new HashMap<>();

  /** Rebuilds the entire index from the provided visible list. O(n). */
  public void rebuild(@NonNull List<TreeNode> visibleNodes) {
    index.clear();
    for (int i = 0; i < visibleNodes.size(); i++) {
      index.put(visibleNodes.get(i).getId(), i);
    }
  }

  /** Returns the adapter position of the node with the given ID, or -1. */
  public int positionOf(@NonNull String nodeId) {
    Integer p = index.get(nodeId);
    return p != null ? p : -1;
  }

  /** Returns the adapter position of the given node, or -1. */
  public int positionOf(@NonNull TreeNode node) {
    return positionOf(node.getId());
  }

  /** Shifts all positions >= insertAt by +count. Called after an insert to avoid a full rebuild. */
  public void shiftRight(int insertAt, int count) {
    for (Map.Entry<String, Integer> e : index.entrySet()) {
      if (e.getValue() >= insertAt) {
        e.setValue(e.getValue() + count);
      }
    }
  }

  /** Shifts all positions >= removeAt by -count. Called after a removal. */
  public void shiftLeft(int removeAt, int count) {
    for (Map.Entry<String, Integer> e : index.entrySet()) {
      if (e.getValue() >= removeAt) {
        e.setValue(e.getValue() - count);
      }
    }
  }

  /** Removes the entry for a specific node ID. */
  public void remove(@NonNull String nodeId) {
    index.remove(nodeId);
  }

  /** Puts a direct position entry. */
  public void put(@NonNull String nodeId, int position) {
    index.put(nodeId, position);
  }

  /** Clears the entire index. */
  public void clear() {
    index.clear();
  }

  public int size() {
    return index.size();
  }
}
