package ir.hanzodev1375.filetreelib.search;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ir.hanzodev1375.filetreelib.core.TreeNode;
import ir.hanzodev1375.filetreelib.model.SearchResult;
import ir.hanzodev1375.filetreelib.utils.TreeUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Runs fuzzy/exact search over the full tree (not just visible nodes). Should be called on a
 * background thread.
 */
public class TreeSearchEngine {

  public interface SearchCallback {
    void onResults(@NonNull List<SearchResult> results);
  }

  private volatile String currentQuery = "";

  /**
   * Searches all nodes under root for query.
   *
   * @param root invisible root
   * @param query search string
   * @return sorted list of SearchResult
   */
  @NonNull
  public List<SearchResult> search(@NonNull TreeNode root, @NonNull String query) {
    currentQuery = query;
    if (query.trim().isEmpty()) return Collections.emptyList();

    List<SearchResult> results = new ArrayList<>();
    String lowerQuery = query.toLowerCase(Locale.ROOT);
    searchRecursive(root, lowerQuery, results);

    // Sort: score desc, then depth asc
    Collections.sort(
        results,
        (a, b) -> {
          int scoreDiff = b.getScore() - a.getScore();
          if (scoreDiff != 0) return scoreDiff;
          return a.getDepth() - b.getDepth();
        });
    return results;
  }

  private void searchRecursive(
      @NonNull TreeNode node, @NonNull String lowerQuery, @NonNull List<SearchResult> out) {

    if (!node.getId().equals("__root__")) {
      int score = TreeUtils.fuzzyScore(node.getName(), lowerQuery);
      if (score >= 0) {
        SearchResult.Builder b =
            new SearchResult.Builder(node.getId(), node.getName())
                .score(score)
                .depth(node.getDepth());
        // Find match ranges (simple substring for now)
        String lowerName = node.getName().toLowerCase(Locale.ROOT);
        int idx = 0;
        while ((idx = lowerName.indexOf(lowerQuery, idx)) != -1) {
          b.addRange(idx, idx + lowerQuery.length());
          idx += lowerQuery.length();
        }
        out.add(b.build());
      }
    }
    for (TreeNode child : node.getChildren()) {
      searchRecursive(child, lowerQuery, out);
    }
  }

  public String getCurrentQuery() {
    return currentQuery;
  }
}
