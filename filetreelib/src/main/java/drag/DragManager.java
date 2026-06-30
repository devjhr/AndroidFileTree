package ir.hanzodev1375.filetreelib.drag;

import android.os.Handler;
import android.os.Looper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;
import ir.hanzodev1375.filetreelib.adapter.TreeAdapter;
import ir.hanzodev1375.filetreelib.core.TreeNode;
import ir.hanzodev1375.filetreelib.core.TreeController;
import ir.hanzodev1375.filetreelib.provider.TreeDataProvider;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class DragManager {

  public interface DragListener {
    void onNodeMoved(@NonNull TreeNode node, @NonNull TreeNode newParent, int newIndex);

    boolean canDrop(@NonNull TreeNode dragged, @NonNull TreeNode target);
  }

  @NonNull private final TreeDataProvider provider;
  @NonNull private final TreeController controller;
  @Nullable private DragListener dragListener;
  @NonNull private final ExecutorService executor = Executors.newSingleThreadExecutor();
  @NonNull private final Handler mainHandler = new Handler(Looper.getMainLooper());

  @Nullable private TreeNode draggedNode = null;
  @NonNull private List<TreeNode> draggedNodes = new ArrayList<>();
  @Nullable private TreeNode pendingDropTarget = null;

  public DragManager(@NonNull TreeController controller) {
    this.controller = controller;
    this.provider = controller.getDataProvider();
  }

  public void setDragListener(@Nullable DragListener listener) {
    this.dragListener = listener;
  }

  /**
   * ItemTouchHelper callback که به RecyclerView وصل میشه. از طریق TreeView.attachDragManager() صدا
   * زده میشه.
   */
  public ItemTouchHelper buildItemTouchHelper(@NonNull TreeAdapter adapter) {
    return new ItemTouchHelper(
        new ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {

          @Override
          public boolean onMove(
              @NonNull RecyclerView recyclerView,
              @NonNull RecyclerView.ViewHolder dragged,
              @NonNull RecyclerView.ViewHolder target) {

            int fromPos = dragged.getAdapterPosition();
            int toPos = target.getAdapterPosition();
            if (fromPos == RecyclerView.NO_POSITION || toPos == RecyclerView.NO_POSITION)
              return false;

            TreeNode draggedNode = adapter.getNode(fromPos);
            TreeNode targetNode = adapter.getNode(toPos);
            if (draggedNode == null || targetNode == null) return false;

            // جلوگیری از drop روی خودش یا فرزندانش
            if (draggedNode.getId().equals(targetNode.getId())) return false;
            if (targetNode.isDescendantOf(draggedNode)) return false;

            // onMove fires repeatedly while the finger is still moving over each row passed —
            // it is NOT a drop event. Only remember the current hover target here; the actual
            // file-system move runs once, in clearView(), when the drag gesture actually ends.
            startDrag(draggedNode);
            pendingDropTarget = targetNode;
            return false;
          }

          @Override
          public void clearView(
              @NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
            super.clearView(recyclerView, viewHolder);
            TreeNode target = pendingDropTarget;
            pendingDropTarget = null;
            if (target != null) drop(target, null);
          }

          @Override
          public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
            // swipe غیرفعاله
          }

          @Override
          public boolean isLongPressDragEnabled() {
            return true;
          }
        });
  }

  public void startDrag(@NonNull TreeNode node) {
    this.draggedNode = node;
    List<TreeNode> selected = controller.getSelectionManager().getSelectedNodes();
    this.draggedNodes =
        (node.isSelected() && selected.size() > 1)
            ? new ArrayList<>(selected)
            : Collections.singletonList(node);
  }

  public void drop(@NonNull TreeNode targetNode, @Nullable Runnable onSuccess) {

    if (draggedNode == null || draggedNodes.isEmpty()) return;
    // Drop any selected node that is a descendant of another selected node — moving the
    // parent already carries it along, so processing it again would operate on a path that
    // no longer exists once the parent has moved.
    final List<TreeNode> dragged =
        ir.hanzodev1375.filetreelib.utils.TreeUtils.filterTopLevel(draggedNodes);
    draggedNode = null;
    draggedNodes = new ArrayList<>();

    TreeNode destination = targetNode.isFolder() ? targetNode : targetNode.getParent();
    if (destination == null) return;

    // Skip any dragged node that the destination is invalid for: dropping onto itself, or
    // into one of its own subfolders, or rejected by the host app's canDrop().
    final List<TreeNode> validDragged = new ArrayList<>();
    for (TreeNode n : dragged) {
      if (n.getId().equals(destination.getId())) continue;
      if (destination.isDescendantOf(n)) continue;
      if (dragListener != null && !dragListener.canDrop(n, destination)) continue;
      validDragged.add(n);
    }
    if (validDragged.isEmpty()) return;

    final TreeNode finalDestination = destination;

    executor.submit(
        () -> {
          try {
            provider.moveNodes(validDragged, finalDestination);
            mainHandler.post(
                () -> {
                  for (TreeNode n : validDragged) {
                    controller.applyMovedNode(n, finalDestination);
                    if (dragListener != null)
                      dragListener.onNodeMoved(
                          n, finalDestination, finalDestination.indexOfChild(n));
                  }
                  if (onSuccess != null) onSuccess.run();
                });
          } catch (Exception e) {
            mainHandler.post(
                () -> {
                  if (dragListener != null) {
                    for (TreeNode n : validDragged) {
                      dragListener.onNodeMoved(n, finalDestination, -1);
                    }
                  }
                });
          }
        });
  }

  public void cancel() {
    draggedNode = null;
    draggedNodes = new ArrayList<>();
    pendingDropTarget = null;
  }

  @Nullable
  public TreeNode getDraggedNode() {
    return draggedNode;
  }

  public boolean isDragging() {
    return draggedNode != null;
  }
}
