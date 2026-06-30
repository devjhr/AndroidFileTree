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

  @NonNull private final Context context;
  @NonNull private final TreeController controller;
  @NonNull private final VisibleNodeList visibleList;
  @NonNull private final ThemeManager theme;
  @NonNull private IconProvider iconProvider;
  @Nullable private ClipboardManager clipboardManager;
  @Nullable private TreeViewHolder holder;

  @NonNull private List<TreeNode> currentList = new ArrayList<>();
  @NonNull private final Map<String, SearchResult> searchResults = new HashMap<>();

  // آخرین وضعیت selection برای diff دقیق
  @NonNull private final Map<String, Boolean> lastSelectionState = new HashMap<>();

  @Nullable private OnNodeClickListener clickListener;
  @Nullable private OnNodeLongClickListener longClickListener;
  @Nullable private OnSelectionModeChangeListener selectionModeListener;

  private boolean selectionMode = false;

  private final ExecutorService diffExecutor =
      Executors.newSingleThreadExecutor(
          r -> {
            Thread t = new Thread(r, "TreeDiff");
            t.setDaemon(true);
            return t;
          });
  private final Handler mainHandler = new Handler(Looper.getMainLooper());

  public TreeAdapter(
      @NonNull Context context, @NonNull TreeController controller, @NonNull ThemeManager theme) {
    this.context = context;
    this.controller = controller;
    this.visibleList = controller.getVisibleList();
    this.theme = theme;
    this.iconProvider = new DefaultIconProvider();
    setHasStableIds(true);

    controller
        .getExpandManager()
        .addExpandListener(
            new ExpandManager.ExpandListener() {
              @Override
              public void onNodesExpanded(
                  @NonNull TreeNode parent, @NonNull List<TreeNode> inserted, int insertPos) {
                int parentPos = currentList.indexOf(parent);
                submitNewList(visibleList.snapshot());
                if (parentPos >= 0) {
                  mainHandler.post(() -> notifyItemChanged(parentPos, Boolean.TRUE));
                }
              }

              @Override
              public void onNodesCollapsed(
                  @NonNull TreeNode parent, @NonNull List<TreeNode> removed, int removePos) {
                int parentPos = currentList.indexOf(parent);
                submitNewList(visibleList.snapshot());
                if (parentPos >= 0) {
                  mainHandler.post(() -> notifyItemChanged(parentPos, Boolean.TRUE));
                }
              }
            });

    controller
        .getSelectionManager()
        .addListener(
            ids -> {
              // فقط آیتم‌هایی که وضعیت‌شون عوض شده رو notify کن
              notifySelectionChanged(ids);

              if (selectionModeListener != null) {
                selectionModeListener.onSelectionCountChanged(ids.size());
              }
              if (ids.isEmpty() && selectionMode) {
                selectionMode = false;
                notifyItemRangeChanged(0, currentList.size(), Boolean.TRUE);
                if (selectionModeListener != null) selectionModeListener.onSelectionModeExited();
              }
            });
  }

  /** فقط آیتم‌هایی که selected/deselected شدن رو notify می‌کنه — O(changed) نه O(n) */
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
    // آپدیت snapshot
    lastSelectionState.clear();
    for (String id : newIds) {
      lastSelectionState.put(id, Boolean.TRUE);
    }
  }

  @NonNull
  @Override
  public TreeViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
    View v = LayoutInflater.from(context).inflate(R.layout.item_tree_node, parent, false);
    return new TreeViewHolder(v);
  }

  @Override
  public void onBindViewHolder(@NonNull TreeViewHolder holder, int position) {
    this.holder = holder;
    TreeNode node = currentList.get(position);
    SearchResult sr = searchResults.get(node.getId());
    boolean isCut =
        clipboardManager != null
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
        selectionMode);
  }

  @Override
  public void onBindViewHolder(
      @NonNull TreeViewHolder holder, int position, @NonNull List<Object> payloads) {
    if (!payloads.isEmpty()) {
      TreeNode node = currentList.get(position);
      holder.updateSelection(node.isSelected(), theme.getSelectedBg(), selectionMode);
      holder.updateArrow(node.isExpanded());
      // Also reload icon so rename (extension change) reflects immediately
      holder.updateIcon(context, node, iconProvider);
    } else {
      onBindViewHolder(holder, position);
    }
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
    if (selectionModeListener != null) selectionModeListener.onSelectionModeEntered();
  }

  public void exitSelectionMode() {
    if (!selectionMode) return;
    selectionMode = false;
    controller.clearSelection();
    lastSelectionState.clear();
    notifyItemRangeChanged(0, currentList.size(), Boolean.TRUE);
    if (selectionModeListener != null) selectionModeListener.onSelectionModeExited();
  }

  public boolean isInSelectionMode() {
    return selectionMode;
  }

  public void submitNewList(@NonNull final List<TreeNode> newList) {
    final List<TreeNode> oldList = new ArrayList<>(currentList);
    diffExecutor.submit(
        () -> {
          DiffUtil.DiffResult result =
              DiffUtil.calculateDiff(new TreeDiffCallback(oldList, newList), true);
          mainHandler.post(
              () -> {
                currentList = new ArrayList<>(newList);
                result.dispatchUpdatesTo(this);
              });
        });
  }

  public void resetList(@NonNull List<TreeNode> newList) {
    currentList = new ArrayList<>(newList);
    lastSelectionState.clear();
    notifyDataSetChanged();
  }

  public void setSearchResults(@Nullable List<SearchResult> results) {
    searchResults.clear();
    if (results != null) {
      for (SearchResult r : results) searchResults.put(r.getNodeId(), r);
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
    holder.setShowIconFolderAndFile(bool);
  }
}
