package ir.hanzodev1375.filetreelib.clipboard;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ir.hanzodev1375.filetreelib.core.TreeNode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Manages copy/cut/paste operations for tree nodes. */
public final class ClipboardManager {

  public enum Operation {
    COPY,
    CUT
  }

  @NonNull private List<TreeNode> clipboard = new ArrayList<>();
  @Nullable private Operation operation = null;

  public void copy(@NonNull List<TreeNode> nodes) {
    clipboard = new ArrayList<>(nodes);
    operation = Operation.COPY;
  }

  public void cut(@NonNull List<TreeNode> nodes) {
    clipboard = new ArrayList<>(nodes);
    operation = Operation.CUT;
  }

  public void clear() {
    clipboard.clear();
    operation = null;
  }

  public boolean hasClipboard() {
    return !clipboard.isEmpty();
  }

  public boolean isCut() {
    return operation == Operation.CUT;
  }

  public boolean isCopy() {
    return operation == Operation.COPY;
  }

  @Nullable
  public Operation getOperation() {
    return operation;
  }

  @NonNull
  public List<TreeNode> getClipboard() {
    return Collections.unmodifiableList(clipboard);
  }

  /** Returns true if node is in the clipboard (for visual cut-dimming). */
  public boolean isInClipboard(@NonNull String nodeId) {
    for (TreeNode n : clipboard) if (n.getId().equals(nodeId)) return true;
    return false;
  }
}
