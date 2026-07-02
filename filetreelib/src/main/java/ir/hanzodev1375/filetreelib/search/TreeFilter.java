package ir.hanzodev1375.filetreelib.search;

import androidx.annotation.NonNull;
import ir.hanzodev1375.filetreelib.core.TreeNode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Filters the visible node list to show only nodes matching a query, plus their ancestor folders
 * (to preserve tree structure context).
 */
public class TreeFilter {

  /**
   * Given the full flat list of all nodes and a set of matching node IDs, returns a filtered list
   * containing matching nodes + their ancestors.
   */
  @NonNull
  public List<TreeNode> filter(@NonNull TreeNode root, @NonNull Set<String> matchingIds) {

    // Collect all nodes that should be visible: matches + ancestors
    Set<String> visibleIds = new HashSet<>(matchingIds);
    for (String id : matchingIds) {
      TreeNode node = findById(root, id);
      if (node == null) continue;
      TreeNode ancestor = node.getParent();
      while (ancestor != null && !ancestor.getId().equals("__root__")) {
        visibleIds.add(ancestor.getId());
        ancestor = ancestor.getParent();
      }
    }

    // Build ordered flat list (pre-order) containing only visible nodes
    List<TreeNode> result = new ArrayList<>();
    collectVisible(root, visibleIds, result);
    return result;
  }

  private void collectVisible(
      @NonNull TreeNode node, @NonNull Set<String> visibleIds, @NonNull List<TreeNode> out) {
    for (TreeNode child : node.getChildren()) {
      if (visibleIds.contains(child.getId())) {
        out.add(child);
        collectVisible(child, visibleIds, out);
      }
    }
  }

  private TreeNode findById(@NonNull TreeNode root, @NonNull String id) {
    if (root.getId().equals(id)) return root;
    for (TreeNode child : root.getChildren()) {
      TreeNode found = findById(child, id);
      if (found != null) return found;
    }
    return null;
  }
}
