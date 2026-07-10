package ir.hanzodev1375.filetreelib.adapter;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;
import ir.hanzodev1375.filetreelib.R;
import ir.hanzodev1375.filetreelib.clipboard.ClipboardManager;
import ir.hanzodev1375.filetreelib.core.ExpandManager;
import ir.hanzodev1375.filetreelib.core.TreeController;
import ir.hanzodev1375.filetreelib.core.TreeNode;
import ir.hanzodev1375.filetreelib.icons.DefaultIconProvider;
import ir.hanzodev1375.filetreelib.icons.IconProvider;
import ir.hanzodev1375.filetreelib.model.SearchResult;
import ir.hanzodev1375.filetreelib.selection.SelectionManager;
import ir.hanzodev1375.filetreelib.theme.FTThemeManager;
import ir.hanzodev1375.filetreelib.utils.VisibleNodeList;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class TreeAdapter extends RecyclerView.Adapter<TreeViewHolder> {

  public interface OnNodeClickListener {
    void onNodeClick(@NonNull TreeNode node, @NonNull View view);
  }

  public interface OnNodeLongClickListener {
    boolean onNodeLongClick(@NonNull TreeNode node, @NonNull View view);
  }

  public interface OnSelectionModeChangeListener {
    void onSelectionModeEntered();
    void onSelectionModeExited();
    void onSelectionCountChanged(int count);
  }

  @NonNull
  private final Context context;
  @NonNull
  private final TreeController controller;
  @NonNull
  private final VisibleNodeList visibleList;
  @NonNull
  private final FTThemeManager theme;
  @NonNull
  private IconProvider iconProvider;
  @Nullable
  private ClipboardManager clipboardManager;
  @Nullable
  private TreeViewHolder holder;
  @NonNull
  private List<TreeNode> currentList = new ArrayList<>();
  @NonNull
  private final Map<String, SearchResult> searchResults = new HashMap<>();
  @NonNull
  private final Map<String, Boolean> lastSelectionState = new HashMap<>();
  @Nullable
  private OnNodeClickListener clickListener;
  @Nullable
  private OnNodeLongClickListener longClickListener;
  @Nullable
  private OnSelectionModeChangeListener selectionModeListener;
  private boolean selectionModeEnabled = true;
  @NonNull
  private final Map<Integer, TreeViewHolder> viewHolderCache = new HashMap<>();
  private int customIconArrowRes = 0;
  private boolean selectionMode = false;
  private int diffRequestId = 0;

  // Previously, children were inserted one real row at a time across multiple posted Handler
  // callbacks (a "staggered reveal"), on the theory that each step recomputing from a fresh layout
  // would avoid position drift. In practice this created a real multi-tick window during which
  // `currentList` was only partially updated for a given expand — and if a SECOND, unrelated
  // expand happened during that window (e.g. expandToPath() walking through several folders in a
  // row, each with more than one child), its own notifyExpanded() would find its parent missing
  // from `currentList` (since it hadn't been staggered in yet), fall back to the async
  // submitNewList() diff path, and that diff's later wholesale currentList replacement would race
  // with the first expand's still-in-flight per-row inserts — corrupting RecyclerView's item-count
  // bookkeeping and crashing with "Inconsistency detected. Invalid view holder adapter position".
  // This wasn't rare: any real folder with 2+ children hit it. Batching the whole insert into one
  // notifyItemRangeInserted() call removes the multi-tick window entirely, so there's nothing left
  // for a second expand to race with.
  //
  // setStaggerStepDelay() is kept as a no-op for source compatibility; staggering itself is gone.
  private long staggerStepDelay = 0L;

  /**
   * @deprecated no longer has any effect — staggered (one-row-at-a-time) inserts were removed
   *     because they were the root cause of a class of RecyclerView "Inconsistency detected"
   *     crashes when two expansions overlapped (e.g. during {@link
   *     ir.hanzodev1375.filetreelib.widget.FileTreeView#expandToPath}). Inserts are now always a
   *     single atomic {@code notifyItemRangeInserted} call.
   */
  @Deprecated
  public void setStaggerStepDelay(long ms) {
    this.staggerStepDelay = Math.max(0L, ms);
  }

  @NonNull
  private final Map<String, Runnable> pendingStaggerJobs = new HashMap<>();
  private final ExecutorService diffExecutor = Executors.newSingleThreadExecutor(
        r -> {
            Thread t = new Thread(r, "TreeDiff");
            t.setDaemon(true);
            return t;
        }
  );
  private final Handler mainHandler = new Handler(Looper.getMainLooper());
  @Nullable private String highlightedNodeId;
  @Nullable private Runnable pendingHighlightClear;
  private static final long DEFAULT_HIGHLIGHT_DURATION_MS = 5000L;

  public TreeAdapter(@NonNull Context context, @NonNull TreeController controller, @NonNull FTThemeManager theme) {
    this.context = context;
    this.controller = controller;
    this.visibleList = controller.getVisibleList();
    this.theme = theme;
    this.iconProvider = new DefaultIconProvider();
    setHasStableIds(true);

    controller.getExpandManager().addExpandListener(new ExpandManager.ExpandListener() {
        @Override
        public void onNodesExpanded(@NonNull TreeNode parent, @NonNull List<TreeNode> inserted, int insertPos) {
            cancelPendingStagger(parent.getId());

            int parentPos = currentList.indexOf(parent);
            if (parentPos >= 0 && insertPos == parentPos + 1 && !inserted.isEmpty()) {
                staggerInsert(parent, inserted, insertPos, parentPos);
            } else {
                submitNewList(visibleList.snapshot());
                if (parentPos >= 0) {
                    mainHandler.post(() -> notifyItemChanged(parentPos, Boolean.TRUE));
                }
            }
        }

        @Override
        public void onNodesCollapsed(@NonNull TreeNode parent, @NonNull List<TreeNode> removed, int removePos) {
            boolean wasStaggering = cancelPendingStagger(parent.getId());
            int parentPos = currentList.indexOf(parent);
            boolean canFastPath = !wasStaggering
                    && parentPos >= 0
                    && removePos == parentPos + 1
                    && !removed.isEmpty()
                    && removePos + removed.size() <= currentList.size();

            if (canFastPath) {
                // Same reasoning as in staggerInsert(): invalidate any pending async
                // diff so it can't later re-notify RecyclerView about rows this sync
                // fast-path removal already accounted for.
                ++diffRequestId;
                for (int i = 0; i < removed.size(); i++) {
                    currentList.remove(removePos);
                }
                notifyItemRangeRemoved(removePos, removed.size());
                notifyItemChanged(parentPos, Boolean.TRUE);
            } else {
                submitNewList(visibleList.snapshot());
                if (parentPos >= 0) {
                    mainHandler.post(() -> notifyItemChanged(parentPos, Boolean.TRUE));
                }
            }
        }

        @Override
        public void onLazyLoadStateChanged(@NonNull TreeNode node) {
            int pos = currentList.indexOf(node);
            if (pos >= 0) {
                mainHandler.post(() -> notifyItemChanged(pos, Boolean.TRUE));
            }
        }
    });

    controller.getSelectionManager().addListener(ids -> {
        notifySelectionChanged(ids);
        if (selectionModeListener != null) {
            selectionModeListener.onSelectionCountChanged(ids.size());
        }
        if (ids.isEmpty() && selectionMode) {
            selectionMode = false;
            notifyItemRangeChanged(0, currentList.size(), Boolean.TRUE);
            if (selectionModeListener != null) {
                selectionModeListener.onSelectionModeExited();
            }
        }
    });
  }

    private void notifySelectionChanged(@NonNull Set<String> newIds) {
        for (int i = 0; i < currentList.size(); i++) {
            TreeNode node = currentList.get(i);
            String id = node.getId();
            boolean wasSelected = Boolean.TRUE.equals(lastSelectionState.get(id));
            boolean isSelected = newIds.contains(id);
            if (wasSelected != isSelected) {
                notifyItemChanged(i, Boolean.TRUE);
            }
        }
        lastSelectionState.clear();
        for (String id : newIds) {
            lastSelectionState.put(id, Boolean.TRUE);
        }
    }

    /**
     * Reveals a large batch of newly-expanded children one real row at a time. The
     * first row is inserted synchronously (instant response); each following row is
     * inserted (as a genuine structural change) after a short delay, so every affected
     * position — including the sibling below the folder — is always recomputed by
     * RecyclerView from the actual current layout.
     */
    /**
     * Inserts a newly-expanded batch of children as one atomic structural change.
     *
     * <p>Previously this staggered the insert across several posted Handler callbacks, one row at
     * a time. That created a multi-tick window where {@code currentList} was only partially
     * updated — long enough for a second, unrelated expand happening in that window (e.g. {@link
     * ir.hanzodev1375.filetreelib.widget.FileTreeView#expandToPath} walking through several
     * multi-child folders back to back) to see its own parent missing from {@code currentList},
     * fall back to the async {@link #submitNewList} diff path, and race its later wholesale list
     * replacement against this method's still-in-flight per-row inserts. Doing the whole insert in
     * one shot removes that window entirely.
     */
    private void staggerInsert(@NonNull TreeNode parent, @NonNull List<TreeNode> inserted,
                               int insertPos, int parentPos) {
        // Invalidate any in-flight async submitNewList() diff. If one is still
        // computing on the background thread when it later posts to the main
        // thread, its captured oldList/newList snapshot predates this synchronous
        // mutation — applying it would re-notify RecyclerView about rows we are
        // about to insert right now via the fast path below, double-counting them
        // and corrupting RecyclerView's internal item-count bookkeeping.
        ++diffRequestId;

        currentList.addAll(insertPos, inserted);
        notifyItemRangeInserted(insertPos, inserted.size());
        notifyItemChanged(parentPos, Boolean.TRUE);
    }

    private boolean cancelPendingStagger(@NonNull String nodeId) {
        Runnable job = pendingStaggerJobs.remove(nodeId);
        if (job != null) {
            mainHandler.removeCallbacks(job);
            return true;
        }
        return false;
    }

    private void cancelAllPendingStaggers() {
        for (Runnable job : pendingStaggerJobs.values()) {
            mainHandler.removeCallbacks(job);
        }
        pendingStaggerJobs.clear();
    }

    @NonNull
    /**
     * Temporarily highlights {@code nodeId}'s row (a brief background flash) then automatically
     * clears it after {@code durationMs} — meant to draw the user's eye to a row right after it's
     * been found and scrolled to programmatically, e.g. via {@link
     * ir.hanzodev1375.filetreelib.widget.FileTreeView#expandToPath}.
     *
     * <p>Calling this again before a previous highlight's timer fires cancels that timer and
     * clears the old row immediately first — only one row is ever highlighted at a time, and a
     * highlight can never get stuck lit up.
     *
     * @param nodeId the {@link TreeNode#getId()} of the row to flash
     * @param durationMs how long the highlight stays visible before auto-clearing
     */
    public void highlightNode(@NonNull String nodeId, long durationMs) {
        if (pendingHighlightClear != null) {
            mainHandler.removeCallbacks(pendingHighlightClear);
            pendingHighlightClear = null;
            String previous = highlightedNodeId;
            highlightedNodeId = null;
            refreshNodeRow(previous);
        }

        highlightedNodeId = nodeId;
        refreshNodeRow(nodeId);

        pendingHighlightClear = () -> {
            String id = highlightedNodeId;
            highlightedNodeId = null;
            pendingHighlightClear = null;
            refreshNodeRow(id);
        };
        mainHandler.postDelayed(pendingHighlightClear, durationMs);
    }

    /** Same as {@link #highlightNode(String, long)} using a sensible default duration. */
    public void highlightNode(@NonNull String nodeId) {
        highlightNode(nodeId, DEFAULT_HIGHLIGHT_DURATION_MS);
    }

    /** Cancels any in-progress temporary highlight immediately, if one is active. */
    public void clearHighlight() {
        if (pendingHighlightClear != null) {
            mainHandler.removeCallbacks(pendingHighlightClear);
            pendingHighlightClear = null;
        }
        if (highlightedNodeId != null) {
            String id = highlightedNodeId;
            highlightedNodeId = null;
            refreshNodeRow(id);
        }
    }

    private void refreshNodeRow(@Nullable String nodeId) {
        if (nodeId == null) return;
        for (int i = 0; i < currentList.size(); i++) {
            if (nodeId.equals(currentList.get(i).getId())) {
                notifyItemChanged(i, Boolean.TRUE);
                return;
            }
        }
    }

    @Override
    public TreeViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(context).inflate(R.layout.item_tree_node, parent, false);
        return new TreeViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull TreeViewHolder holder, int position) {
        viewHolderCache.put(position, holder);
        this.holder = holder;
        if (customIconArrowRes != 0) {
            holder.setIconArrow(customIconArrowRes);
        }

        TreeNode node = currentList.get(position);
        SearchResult sr = searchResults.get(node.getId());
        boolean isCut = clipboardManager != null
                && clipboardManager.isCut()
                && clipboardManager.isInClipboard(node.getId());

        holder.bind(
                node,
                theme,
                iconProvider,
                sr,
                v -> {
                    if (selectionMode) {
                        controller.getSelectionManager().setVisibleNodes(currentList);
                        controller.selectNode(node, SelectionManager.MODE_MULTI);
                    } else {
                        if (clickListener != null) clickListener.onNodeClick(node, v);
                    }
                },
                v -> {
                    if (!selectionMode) {
                        // Give the host first refusal: if it handles the long-press itself (its
                        // own dialog, custom action, etc.) and returns true, don't also enter the
                        // built-in selection mode on top of it.
                        if (longClickListener != null && longClickListener.onNodeLongClick(node, v)) {
                            return true;
                        }
                        if (!selectionModeEnabled) return false;
                        enterSelectionMode();
                        controller.getSelectionManager().setVisibleNodes(currentList);
                        controller.selectNode(node, SelectionManager.MODE_MULTI);
                        return true;
                    }
                    return false;
                },
                v -> {
                    if (!selectionMode) controller.toggleNode(node);
                },
                isCut,
                selectionMode
        );
        holder.setRevealHighlighted(
                node.getId().equals(highlightedNodeId),
                theme.getRevealHighlightColor(),
                node.isSelected(),
                theme.getSelectedBg());
    }

    @Override
    public void onBindViewHolder(@NonNull TreeViewHolder holder, int position, @NonNull List<Object> payloads) {
        if (!payloads.isEmpty()) {
            TreeNode node = currentList.get(position);
            holder.updateSelection(node.isSelected(), theme.getSelectedBg(), selectionMode);
            holder.updateArrow(node, selectionMode);
            holder.updateIcon(context, node, iconProvider);
            holder.setRevealHighlighted(
                    node.getId().equals(highlightedNodeId),
                    theme.getRevealHighlightColor(),
                    node.isSelected(),
                    theme.getSelectedBg());
        } else {
            onBindViewHolder(holder, position);
        }
        if (customIconArrowRes != 0) {
            holder.setIconArrow(customIconArrowRes);
        }
    }

    @Override
    public void onViewRecycled(@NonNull TreeViewHolder holder) {
        super.onViewRecycled(holder);
        int position = holder.getAdapterPosition();
        if (position != RecyclerView.NO_POSITION) {
            viewHolderCache.remove(position);
        }
        if (this.holder == holder) {
            this.holder = null;
        }
    }

    @Override
    public void onDetachedFromRecyclerView(@NonNull RecyclerView recyclerView) {
        super.onDetachedFromRecyclerView(recyclerView);
        viewHolderCache.clear();
        this.holder = null;
        cancelAllPendingStaggers();
    }

    @Override
    public int getItemCount() {
        return currentList.size();
    }

    @Override
    public long getItemId(int position) {
        return currentList.get(position).getId().hashCode();
    }

    public void enterSelectionMode() {
        if (selectionMode) return;
        selectionMode = true;
        notifyItemRangeChanged(0, currentList.size(), Boolean.TRUE);
        if (selectionModeListener != null) {
            selectionModeListener.onSelectionModeEntered();
        }
    }

    public void exitSelectionMode() {
        if (!selectionMode) return;
        selectionMode = false;
        controller.clearSelection();
        lastSelectionState.clear();
        notifyItemRangeChanged(0, currentList.size(), Boolean.TRUE);
        if (selectionModeListener != null) {
            selectionModeListener.onSelectionModeExited();
        }
    }

    public boolean isInSelectionMode() {
        return selectionMode;
    }

    public void submitNewList(@NonNull final List<TreeNode> newList) {
        final List<TreeNode> oldList = new ArrayList<>(currentList);
        final int requestId = ++diffRequestId;
        diffExecutor.submit(() -> {
            DiffUtil.DiffResult result = DiffUtil.calculateDiff(
                    new TreeDiffCallback(oldList, newList), true
            );
            mainHandler.post(() -> {
                if (requestId != diffRequestId) return;
                cancelAllPendingStaggers();
                currentList = new ArrayList<>(newList);
                result.dispatchUpdatesTo(this);
            });
        });
    }

    public void resetList(@NonNull List<TreeNode> newList) {
        cancelAllPendingStaggers();
        currentList = new ArrayList<>(newList);
        lastSelectionState.clear();
        notifyDataSetChanged();
    }

    public void setSearchResults(@Nullable List<SearchResult> results) {
        searchResults.clear();
        if (results != null) {
            for (SearchResult r : results) {
                searchResults.put(r.getNodeId(), r);
            }
        }
        notifyItemRangeChanged(0, currentList.size(), Boolean.TRUE);
    }

    public void clearSearch() {
        searchResults.clear();
        notifyItemRangeChanged(0, currentList.size(), Boolean.TRUE);
    }

    @Nullable
    public TreeNode getNode(int position) {
        if (position < 0 || position >= currentList.size()) return null;
        return currentList.get(position);
    }

    public void refreshNode(@NonNull String nodeId) {
        for (int i = 0; i < currentList.size(); i++) {
            if (currentList.get(i).getId().equals(nodeId)) {
                notifyItemChanged(i);
                return;
            }
        }
    }

    public void setIconProvider(@NonNull IconProvider provider) {
        this.iconProvider = provider;
    }

    public void setClipboardManager(@Nullable ClipboardManager manager) {
        this.clipboardManager = manager;
    }

    public void setOnNodeClickListener(@Nullable OnNodeClickListener l) {
        this.clickListener = l;
    }

    /**
     * Sets a listener consulted on every long-press before the built-in "enter selection mode"
     * behavior runs. Returning {@code true} means the host fully handled the long-press (its own
     * dialog, custom action, etc.) — the built-in selection mode is skipped for that press.
     * Returning {@code false}, or not setting a listener at all, falls through to the built-in
     * behavior (or does nothing, if {@link #setSelectionModeEnabled} is {@code false}).
     */
    public void setOnNodeLongClickListener(@Nullable OnNodeLongClickListener l) {
        this.longClickListener = l;
    }

    /**
     * Enables or disables the built-in "long-press enters selection mode, shows the selection
     * action panel" behavior. Default {@code true}. Set to {@code false} if the host wants to
     * handle long-press/selection/drag entirely itself — e.g. via {@link
     * #setOnNodeLongClickListener} and its own UI — instead of the library's built-in flow.
     * Doesn't prevent a host from calling {@link #enterSelectionMode()} directly; it only turns
     * off the automatic long-press trigger.
     */
    public void setSelectionModeEnabled(boolean enabled) {
        this.selectionModeEnabled = enabled;
    }

    public boolean isSelectionModeEnabled() {
        return selectionModeEnabled;
    }

    public void setOnSelectionModeChangeListener(@Nullable OnSelectionModeChangeListener l) {
        this.selectionModeListener = l;
    }

    @NonNull
    public List<TreeNode> getCurrentList() {
        return new ArrayList<>(currentList);
    }

    public void setShowIconFolderAndFile(boolean bool) {
        if (holder != null) {
            holder.setShowIconFolderAndFile(bool);
        }
        for (TreeViewHolder vh : viewHolderCache.values()) {
            vh.setShowIconFolderAndFile(bool);
        }
    }

    public void setIconArrow(int icon) {
        this.customIconArrowRes = icon;
        for (TreeViewHolder vh : viewHolderCache.values()) {
            vh.setIconArrow(icon);
            vh.getIvArrow().setImageResource(icon);
        }
        if (holder != null) {
            holder.setIconArrow(icon);
            holder.getIvArrow().setImageResource(icon);
        }
        notifyItemRangeChanged(0, currentList.size());
    }

    @Nullable
    public TreeViewHolder getViewHolderSafe(int position) {
        TreeViewHolder cached = viewHolderCache.get(position);
        if (cached != null && cached.getAdapterPosition() != RecyclerView.NO_POSITION) {
            return cached;
        }
        return null;
    }

    @Nullable
    public TreeViewHolder getFirstViewHolder() {
        return viewHolderCache.isEmpty() ? null : viewHolderCache.values().iterator().next();
    }

    @Nullable
    public TreeViewHolder getViewHolderIfValid(int position) {
        if (position < 0 || position >= currentList.size()) {
            return null;
        }
        TreeViewHolder vh = getViewHolderSafe(position);
        if (vh == null) {
            RecyclerView recyclerView = (RecyclerView) viewHolderCache.values()
                    .iterator().next().itemView.getParent();
            if (recyclerView != null) {
                RecyclerView.ViewHolder found = recyclerView.findViewHolderForAdapterPosition(position);
                if (found instanceof TreeViewHolder) {
                    vh = (TreeViewHolder) found;
                    viewHolderCache.put(position, vh);
                }
            }
        }
        return vh;
    }

    @Nullable
    public TreeViewHolder getCurrentHolder() {
        return holder;
    }

    @NonNull
    public Map<Integer, TreeViewHolder> getViewHolderCache() {
        return new HashMap<>(viewHolderCache);
    }

    private static class TreeDiffCallback extends DiffUtil.Callback {
        private final List<TreeNode> oldList;
        private final List<TreeNode> newList;

        TreeDiffCallback(List<TreeNode> oldList, List<TreeNode> newList) {
            this.oldList = oldList;
            this.newList = newList;
        }

        @Override
        public int getOldListSize() {
            return oldList.size();
        }

        @Override
        public int getNewListSize() {
            return newList.size();
        }

        @Override
        public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
            return oldList.get(oldItemPosition).getId()
                    .equals(newList.get(newItemPosition).getId());
        }

        @Override
        public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
            TreeNode oldNode = oldList.get(oldItemPosition);
            TreeNode newNode = newList.get(newItemPosition);
            
            return oldNode.getName().equals(newNode.getName())
                    && oldNode.isFolder() == newNode.isFolder()
                    && oldNode.isExpanded() == newNode.isExpanded()
                    && oldNode.isSelected() == newNode.isSelected();
        }
    }
}