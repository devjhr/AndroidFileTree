package ir.hanzodev1375.filetreelib.utils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ir.hanzodev1375.filetreelib.core.TreeNode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Authoritative flat list of nodes visible in the RecyclerView. Maintains an id-to-position index
 * for O(1) lookup.
 */
public final class VisibleNodeList {

  @NonNull private final List<TreeNode> items = new ArrayList<>();
  @NonNull private final Map<String, Integer> idToPos = new HashMap<>();

  public VisibleNodeList() {}

  @NonNull
  public List<TreeNode> getItems() {
    return Collections.unmodifiableList(items);
  }

  public int size() {
    return items.size();
  }

  @NonNull
  public TreeNode get(int pos) {
    return items.get(pos);
  }

  public int indexOf(@NonNull TreeNode node) {
    Integer p = idToPos.get(node.getId());
    return p != null ? p : -1;
  }

  public int indexOfId(@NonNull String id) {
    Integer p = idToPos.get(id);
    return p != null ? p : -1;
  }

  @Nullable
  public TreeNode getById(@NonNull String id) {
    Integer p = idToPos.get(id);
    return p != null ? items.get(p) : null;
  }

  public void insertAll(int position, @NonNull List<TreeNode> nodes) {
    if (nodes.isEmpty()) return;
    items.addAll(position, nodes);
    rebuildIndex();
  }

  public void removeRange(int from, int to) {
    if (from >= to) return;
    items.subList(from, to).clear();
    rebuildIndex();
  }

  public void removeAt(int position) {
    if (position < 0 || position >= items.size()) return;
    items.remove(position);
    rebuildIndex();
  }

  public void reset(@NonNull List<TreeNode> newItems) {
    items.clear();
    items.addAll(newItems);
    rebuildIndex();
  }

  public void add(@NonNull TreeNode node) {
    idToPos.put(node.getId(), items.size());
    items.add(node);
  }

  public void clear() {
    items.clear();
    idToPos.clear();
  }

  @NonNull
  public List<TreeNode> snapshot() {
    return new ArrayList<>(items);
  }

  private void rebuildIndex() {
    idToPos.clear();
    for (int i = 0; i < items.size(); i++) {
      idToPos.put(items.get(i).getId(), i);
    }
  }
}
