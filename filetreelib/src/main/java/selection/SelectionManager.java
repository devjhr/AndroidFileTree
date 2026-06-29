package ir.hanzodev1375.filetreelib.selection;

import androidx.annotation.*;
import ir.hanzodev1375.filetreelib.core.TreeNode;
import java.util.*;

public final class SelectionManager {
  public static final int MODE_SINGLE = 0;
  public static final int MODE_MULTI = 1;
  public static final int MODE_RANGE = 2;

  public interface SelectionListener {
    void onSelectionChanged(@NonNull Set<String> selectedIds);
  }

  @NonNull private final LinkedHashMap<String, TreeNode> selected = new LinkedHashMap<>();
  @NonNull private final List<SelectionListener> listeners = new ArrayList<>();
  @NonNull private final List<TreeNode> orderedVisible = new ArrayList<>();
  @Nullable private TreeNode rangeAnchor = null;

  public void setVisibleNodes(@NonNull List<TreeNode> nodes) {
    orderedVisible.clear();
    orderedVisible.addAll(nodes);
  }

  public void select(@NonNull TreeNode node, int mode) {
    switch (mode) {
      case MODE_SINGLE:
        for (TreeNode n : selected.values()) n.setSelected(false);
        selected.clear();
        node.setSelected(true);
        selected.put(node.getId(), node);
        rangeAnchor = node;
        break;
      case MODE_MULTI:
        if (node.isSelected()) {
          node.setSelected(false);
          selected.remove(node.getId());
        } else {
          node.setSelected(true);
          selected.put(node.getId(), node);
          rangeAnchor = node;
        }
        break;
      case MODE_RANGE:
        if (rangeAnchor == null) {
          select(node, MODE_SINGLE);
          return;
        }
        int anchorIdx = indexIn(rangeAnchor);
        int nodeIdx = indexIn(node);
        if (anchorIdx < 0 || nodeIdx < 0) {
          select(node, MODE_SINGLE);
          return;
        }
        int from = Math.min(anchorIdx, nodeIdx);
        int to = Math.max(anchorIdx, nodeIdx);
        for (TreeNode n : selected.values()) n.setSelected(false);
        selected.clear();
        for (int i = from; i <= to && i < orderedVisible.size(); i++) {
          TreeNode n = orderedVisible.get(i);
          n.setSelected(true);
          selected.put(n.getId(), n);
        }
        break;
    }
    notifyListeners();
  }

  public void deselect(@NonNull TreeNode node) {
    node.setSelected(false);
    selected.remove(node.getId());
    notifyListeners();
  }

  public void clearSelection() {
    for (TreeNode n : selected.values()) n.setSelected(false);
    selected.clear();
    rangeAnchor = null;
    notifyListeners();
  }

  public void selectAll() {
    for (TreeNode n : orderedVisible) {
      n.setSelected(true);
      selected.put(n.getId(), n);
    }
    notifyListeners();
  }

  @NonNull
  public List<TreeNode> getSelectedNodes() {
    return new ArrayList<>(selected.values());
  }

  @NonNull
  public Set<String> getSelectedIds() {
    return new LinkedHashSet<>(selected.keySet());
  }

  public boolean isSelected(@NonNull String id) {
    return selected.containsKey(id);
  }

  public int getSelectionCount() {
    return selected.size();
  }

  public boolean hasSelection() {
    return !selected.isEmpty();
  }

  public void addListener(@NonNull SelectionListener l) {
    if (!listeners.contains(l)) listeners.add(l);
  }

  public void removeListener(@NonNull SelectionListener l) {
    listeners.remove(l);
  }

  private int indexIn(@NonNull TreeNode node) {
    for (int i = 0; i < orderedVisible.size(); i++)
      if (orderedVisible.get(i).getId().equals(node.getId())) return i;
    return -1;
  }

  private void notifyListeners() {
    Set<String> ids = getSelectedIds();
    for (SelectionListener l : listeners) l.onSelectionChanged(ids);
  }
}
