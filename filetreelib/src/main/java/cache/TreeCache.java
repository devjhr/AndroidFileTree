package ir.hanzodev1375.filetreelib.cache;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ir.hanzodev1375.filetreelib.core.TreeNode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Two-level LRU cache for loaded child lists. Avoids re-hitting the filesystem on collapse→expand
 * cycles.
 */
public final class TreeCache {

  private static final int DEFAULT_MAX_ENTRIES = 256;

  private final int maxEntries;
  private final LinkedHashMap<String, List<TreeNode>> cache;

  public TreeCache() {
    this(DEFAULT_MAX_ENTRIES);
  }

  public TreeCache(int maxEntries) {
    this.maxEntries = maxEntries;
    this.cache =
        new LinkedHashMap<String, List<TreeNode>>(maxEntries, 0.75f, true) {
          @Override
          protected boolean removeEldestEntry(Map.Entry<String, List<TreeNode>> eldest) {
            return size() > TreeCache.this.maxEntries;
          }
        };
  }

  /** Returns cached children for the given parent ID, or null on miss. */
  @Nullable
  public synchronized List<TreeNode> getChildren(@NonNull String parentId) {
    return cache.get(parentId);
  }

  /** Stores children for the given parent ID. */
  public synchronized void putChildren(@NonNull String parentId, @NonNull List<TreeNode> children) {
    cache.put(parentId, children);
  }

  /** Removes the cache entry for a specific node. */
  public synchronized void invalidate(@NonNull String nodeId) {
    cache.remove(nodeId);
  }

  /** Clears the entire cache. */
  public synchronized void clear() {
    cache.clear();
  }

  public synchronized int size() {
    return cache.size();
  }
}
