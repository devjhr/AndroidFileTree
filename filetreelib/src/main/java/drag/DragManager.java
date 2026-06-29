package ir.hanzodev1375.filetreelib.drag;

import android.os.Handler;
import android.os.Looper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;
import ir.hanzodev1375.filetreelib.adapter.TreeAdapter;
import ir.hanzodev1375.filetreelib.core.TreeNode;
import ir.hanzodev1375.filetreelib.provider.TreeDataProvider;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class DragManager {

  public interface DragListener {
    void onNodeMoved(@NonNull TreeNode node, @NonNull TreeNode newParent, int newIndex);

    boolean canDrop(@NonNull TreeNode dragged, @NonNull TreeNode target);
  }

  @NonNull private final TreeDataProvider provider;
  @Nullable private DragListener dragListener;
  @NonNull private final ExecutorService executor = Executors.newSingleThreadExecutor();
  @NonNull private final Handler mainHandler = new Handler(Looper.getMainLooper());

  @Nullable private TreeNode draggedNode = null;

  public DragManager(@NonNull TreeDataProvider provider) {
    this.provider = provider;
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
            if (fromPos == RecyclerView.NO_ID || toPos == RecyclerView.NO_ID) return false;

            TreeNode draggedNode = adapter.getNode(fromPos);
            TreeNode targetNode = adapter.getNode(toPos);
            if (draggedNode == null || targetNode == null) return false;

            // جلوگیری از drop روی خودش یا فرزندانش
            if (draggedNode.getId().equals(targetNode.getId())) return false;
            if (targetNode.isDescendantOf(draggedNode)) return false;

            DragManager.this.draggedNode = draggedNode;
            drop(targetNode, null);
            return true;
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
  }

  public void drop(@NonNull TreeNode targetNode, @Nullable Runnable onSuccess) {

    if (draggedNode == null) return;
    final TreeNode dragged = draggedNode;
    draggedNode = null;

    if (dragged.getId().equals(targetNode.getId())) return;
    if (targetNode.isDescendantOf(dragged)) return;

    TreeNode destination = targetNode.isFolder() ? targetNode : targetNode.getParent();
    if (destination == null) return;

    if (dragListener != null && !dragListener.canDrop(dragged, destination)) return;

    int newIndex = targetNode.isFolder() ? 0 : destination.indexOfChild(targetNode);
    final TreeNode finalDestination = destination;
    final int finalIndex = newIndex;

    executor.submit(
        () -> {
          try {
            List<TreeNode> nodeList = new ArrayList<>();
            nodeList.add(dragged);
            provider.moveNodes(nodeList, finalDestination);
            mainHandler.post(
                () -> {
                  if (dragListener != null)
                    dragListener.onNodeMoved(dragged, finalDestination, finalIndex);
                  if (onSuccess != null) onSuccess.run();
                });
          } catch (Exception ignored) {
          }
        });
  }

  public void cancel() {
    draggedNode = null;
  }

  @Nullable
  public TreeNode getDraggedNode() {
    return draggedNode;
  }

  public boolean isDragging() {
    return draggedNode != null;
  }
}
