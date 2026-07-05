package ir.hanzodev1375.filetreelib.adapter;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;
import ir.hanzodev1375.filetreelib.core.TreeNode;
import ir.hanzodev1375.filetreelib.model.FilePayload;
import java.util.List;

public final class TreeDiffCallback extends DiffUtil.Callback {

    @NonNull private final List<TreeNode> oldList;
    @NonNull private final List<TreeNode> newList;

    public TreeDiffCallback(@NonNull List<TreeNode> oldList, @NonNull List<TreeNode> newList) {
        this.oldList = oldList;
        this.newList = newList;
    }

    @Override public int getOldListSize() { return oldList.size(); }
    @Override public int getNewListSize() { return newList.size(); }

    @Override
    public boolean areItemsTheSame(int oldPos, int newPos) {
        return oldList.get(oldPos).getId().equals(newList.get(newPos).getId());
    }

    @Override
    public boolean areContentsTheSame(int oldPos, int newPos) {
        TreeNode o = oldList.get(oldPos);
        TreeNode n = newList.get(newPos);
        if (!o.getName().equals(n.getName()))         return false;
        if (o.isExpanded()  != n.isExpanded())        return false;
        if (o.isSelected()  != n.isSelected())        return false;
        if (o.isHighlighted() != n.isHighlighted())    return false;
        if (o.isBookmarked()!= n.isBookmarked())      return false;
        if (o.isLazyLoadPending() != n.isLazyLoadPending()) return false;
        FilePayload op = o.getPayload(FilePayload.class);
        FilePayload np = n.getPayload(FilePayload.class);
        if (op != null && np != null) {
            if (op.getGitStatus()   != np.getGitStatus())   return false;
            if (op.getErrorCount()  != np.getErrorCount())  return false;
            if (op.getLastModified()!= np.getLastModified())return false;
        }
        return true;
    }

    @Nullable
    @Override
    public Object getChangePayload(int oldPos, int newPos) {
        // Return non-null so RecyclerView calls partial bind instead of full rebind
        return Boolean.TRUE;
    }
}
