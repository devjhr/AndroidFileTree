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
import ir.hanzodev1375.filetreelib.theme.ThemeManager;
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
  private final ThemeManager theme;
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
  @NonNull
  private final Map<Integer, TreeViewHolder> viewHolderCache = new HashMap<>();
  private int customIconArrowRes = 0;
  private boolean selectionMode = false;
  private int diffRequestId = 0;

  // Staggered reveal: children are inserted one real row at a time via a genuine
  // notifyItemRangeInserted(pos, 1) per step (not a synthetic animation-only delay),
  // so RecyclerView always computes each affected sibling's position from an actually
  // correct, current layout — this is what avoids position-drift/overlap.
  //
  // NOTE: this is a SEPARATE knob from app:tv_animateDuration / TreeAnimator's
  // expandDuration. expandDuration controls how long *one row's* fade/scale animation
  // takes; staggerStepDelay controls how quickly consecutive rows *start* appearing.
  // Changing one does not change the other.
  private static final int STAGGER_THRESHOLD = 4;
  private long staggerStepDelay = 4L;

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

  public TreeAdapter(@NonNull Context context, @NonNull TreeController controller, @NonNull ThemeManager theme) {
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
                if (inserted.size() > STAGGER_THRESHOLD) {
                    staggerInsert(parent, inserted, insertPos, parentPos);
                } else {
                    currentList.addAll(insertPos, inserted);
                    notifyItemRangeInserted(insertPos, inserted.size());
                    notifyItemChanged(parentPos, Boolean.TRUE);
                }
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
    private void staggerInsert(@NonNull TreeNode parent, @NonNull List<TreeNode> inserted,
                               int insertPos, int parentPos) {
        int totalSize = inserted.size();

        currentList.add(insertPos, inserted.get(0));
        notifyItemRangeInserted(insertPos, 1);
        notifyItemChanged(parentPos, Boolean.TRUE);

        if (totalSize <= 1) return;

        final String parentId = parent.getId();
        final int[] nextIndex = {1};

        Runnable job = new Runnable() {
            @Override
            public void run() {
                if (!pendingStaggerJobs.containsKey(parentId)) return;

                int pos = insertPos + nextIndex[0];
                currentList.add(pos, inserted.get(nextIndex[0]));
                notifyItemRangeInserted(pos, 1);
                nextIndex[0]++;

                if (nextIndex[0] < totalSize) {
                    mainHandler.postDelayed(this, staggerStepDelay);
                } else {
                    pendingStaggerJobs.remove(parentId);
                }
            }
        };

        pendingStaggerJobs.put(parentId, job);
        mainHandler.postDelayed(job, staggerStepDelay);
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
    }

    @Override
    public void onBindViewHolder(@NonNull TreeViewHolder holder, int position, @NonNull List<Object> payloads) {
        if (!payloads.isEmpty()) {
            TreeNode node = currentList.get(position);
            holder.updateSelection(node.isSelected(), theme.getSelectedBg(), selectionMode);
            holder.updateArrow(node, selectionMode);
            holder.updateIcon(context, node, iconProvider);
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

    public void setOnNodeLongClickListener(@Nullable OnNodeLongClickListener l) {
        this.longClickListener = l;
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