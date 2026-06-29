package ir.hanzodev1375.filetreelib.utils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ir.hanzodev1375.filetreelib.core.TreeNode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Stateless utility methods for tree traversal, sorting and string helpers. */
public final class TreeUtils {

  private TreeUtils() {}

  // --- Traversal -----------------------------------------------------------

  /** Depth-first pre-order traversal; visits every node once. */
  @NonNull
  public static List<TreeNode> flatten(@NonNull TreeNode root) {
    List<TreeNode> result = new ArrayList<>();
    flattenRecursive(root, result);
    return result;
  }

  private static void flattenRecursive(@NonNull TreeNode node, @NonNull List<TreeNode> out) {
    out.add(node);
    for (TreeNode child : node.getChildren()) flattenRecursive(child, out);
  }

  /** Returns all leaf nodes (TYPE_FILE) under root. */
  @NonNull
  public static List<TreeNode> collectLeaves(@NonNull TreeNode root) {
    List<TreeNode> leaves = new ArrayList<>();
    collectLeavesRecursive(root, leaves);
    return leaves;
  }

  private static void collectLeavesRecursive(@NonNull TreeNode node, @NonNull List<TreeNode> out) {
    if (node.isFile()) {
      out.add(node);
      return;
    }
    for (TreeNode child : node.getChildren()) collectLeavesRecursive(child, out);
  }

  /** Counts all nodes in the subtree rooted at node (node itself + descendants). */
  public static int countNodes(@NonNull TreeNode node) {
    int c = 1;
    for (TreeNode child : node.getChildren()) c += countNodes(child);
    return c;
  }

  // --- String helpers ------------------------------------------------------

  /** Returns the file extension (without dot) for the given filename, or "". */
  @NonNull
  public static String getExtension(@NonNull String filename) {
    int dot = filename.lastIndexOf('.');
    if (dot < 0 || dot == filename.length() - 1) return "";
    return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
  }

  /** Returns the filename portion of a path. */
  @NonNull
  public static String getFileName(@NonNull String path) {
    int slash = path.lastIndexOf('/');
    return slash < 0 ? path : path.substring(slash + 1);
  }

  /** Returns the parent directory path, or "/" for root. */
  @NonNull
  public static String getParentPath(@NonNull String path) {
    int slash = path.lastIndexOf('/');
    if (slash <= 0) return "/";
    return path.substring(0, slash);
  }

  /** Case-insensitive fuzzy match: returns true if query chars appear in order in target. */
  public static boolean fuzzyMatch(@NonNull String target, @NonNull String query) {
    if (query.isEmpty()) return true;
    String t = target.toLowerCase(Locale.ROOT);
    String q = query.toLowerCase(Locale.ROOT);
    int ti = 0, qi = 0;
    while (ti < t.length() && qi < q.length()) {
      if (t.charAt(ti) == q.charAt(qi)) qi++;
      ti++;
    }
    return qi == q.length();
  }

  /** Computes a fuzzy match score. Higher = better. Returns -1 if no match. */
  public static int fuzzyScore(@NonNull String target, @NonNull String query) {
    if (query.isEmpty()) return 0;
    String t = target.toLowerCase(Locale.ROOT);
    String q = query.toLowerCase(Locale.ROOT);
    // Exact prefix match is best
    if (t.startsWith(q)) return 1000 + (1000 - target.length());
    // Contains match
    if (t.contains(q)) return 500 + (500 - target.length());
    // Fuzzy
    if (fuzzyMatch(t, q)) return 100;
    return -1;
  }

  // --- Sorting -------------------------------------------------------------

  /** Sorts a node list: folders first, then files, each group alphabetically. */
  @NonNull
  public static List<TreeNode> sortFoldersFirst(@NonNull List<TreeNode> nodes) {
    List<TreeNode> sorted = new ArrayList<>(nodes);
    Collections.sort(
        sorted,
        (a, b) -> {
          if (a.isFolder() && !b.isFolder()) return -1;
          if (!a.isFolder() && b.isFolder()) return 1;
          return a.getName().compareToIgnoreCase(b.getName());
        });
    return sorted;
  }

  /** Sorts alphabetically ignoring case. */
  @NonNull
  public static List<TreeNode> sortAlpha(@NonNull List<TreeNode> nodes) {
    List<TreeNode> sorted = new ArrayList<>(nodes);
    Collections.sort(sorted, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
    return sorted;
  }

  // --- Ancestor helpers ----------------------------------------------------

  /**
   * Returns true if ancestorId is an ancestor of descendantId in the given id-based path comparison
   * (path segments separated by '/').
   */
  public static boolean isAncestorPath(@NonNull String ancestorId, @NonNull String descendantId) {
    return descendantId.startsWith(ancestorId + "/");
  }

  /** Finds the lowest common ancestor of two nodes. */
  @Nullable
  public static TreeNode lowestCommonAncestor(@NonNull TreeNode a, @NonNull TreeNode b) {
    List<TreeNode> pathA = ancestorList(a);
    List<TreeNode> pathB = ancestorList(b);
    TreeNode lca = null;
    int minLen = Math.min(pathA.size(), pathB.size());
    for (int i = 0; i < minLen; i++) {
      if (pathA.get(i).getId().equals(pathB.get(i).getId())) lca = pathA.get(i);
      else break;
    }
    return lca;
  }

  private static List<TreeNode> ancestorList(@NonNull TreeNode node) {
    List<TreeNode> list = new ArrayList<>();
    TreeNode cur = node;
    while (cur != null) {
      list.add(0, cur);
      cur = cur.getParent();
    }
    return list;
  }
}
