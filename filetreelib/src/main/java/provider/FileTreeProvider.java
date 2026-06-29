package ir.hanzodev1375.filetreelib.provider;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;
import ir.hanzodev1375.filetreelib.core.TreeNode;
import ir.hanzodev1375.filetreelib.model.FilePayload;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** A TreeDataProvider backed by the real Android filesystem using java.io.File. */
public class FileTreeProvider implements TreeDataProvider {

  @WorkerThread
  @NonNull
  @Override
  public List<TreeNode> loadChildren(@NonNull TreeNode parent) throws Exception {
    FilePayload payload = parent.getPayload(FilePayload.class);
    if (payload == null) return Collections.emptyList();
    File dir = new File(payload.getAbsolutePath());
    if (!dir.isDirectory()) return Collections.emptyList();
    File[] entries = dir.listFiles();
    if (entries == null) return Collections.emptyList();

    // Sort: folders first, then files, each alphabetically
    Arrays.sort(
        entries,
        (a, b) -> {
          if (a.isDirectory() && !b.isDirectory()) return -1;
          if (!a.isDirectory() && b.isDirectory()) return 1;
          return a.getName().compareToIgnoreCase(b.getName());
        });

    List<TreeNode> nodes = new ArrayList<>(entries.length);
    for (File entry : entries) {
      if (entry.getName().startsWith(".")) continue; // skip hidden
      boolean isDir = entry.isDirectory();
      FilePayload ep =
          new FilePayload.Builder(entry.getAbsolutePath(), isDir)
              .size(isDir ? -1 : entry.length())
              .lastModified(entry.lastModified())
              .build();
      TreeNode node =
          new TreeNode.Builder(entry.getName())
              .setId(entry.getAbsolutePath())
              .setType(isDir ? TreeNode.TYPE_FOLDER : TreeNode.TYPE_FILE)
              .setHasChildren(isDir && hasAnyChild(entry))
              .setPayload(ep)
              .build();
      nodes.add(node);
    }
    return nodes;
  }

  @WorkerThread
  @Override
  public boolean hasChildren(@NonNull TreeNode node) throws Exception {
    FilePayload p = node.getPayload(FilePayload.class);
    if (p == null) return false;
    File dir = new File(p.getAbsolutePath());
    if (!dir.isDirectory()) return false;
    return hasAnyChild(dir);
  }

  @WorkerThread
  @Override
  public void renameNode(@NonNull TreeNode node, @NonNull String newName) throws Exception {
    FilePayload p = node.getPayload(FilePayload.class);
    if (p == null) throw new IllegalStateException("No FilePayload on node");
    File src = new File(p.getAbsolutePath());
    File dst = new File(src.getParent(), newName);
    if (!src.renameTo(dst)) throw new Exception("Rename failed: " + src.getPath());
    p.setMimeType(null);
  }

  @WorkerThread
  @Override
  public void deleteNodes(@NonNull List<TreeNode> nodes) throws Exception {
    for (TreeNode node : nodes) {
      FilePayload p = node.getPayload(FilePayload.class);
      if (p == null) continue;
      deleteRecursive(new File(p.getAbsolutePath()));
    }
  }

  @WorkerThread
  @NonNull
  @Override
  public TreeNode createNode(@NonNull TreeNode parent, @NonNull String name, int type)
      throws Exception {
    FilePayload pp = parent.getPayload(FilePayload.class);
    if (pp == null) throw new IllegalStateException("No FilePayload on parent");
    File newFile = new File(pp.getAbsolutePath(), name);
    if (type == TreeNode.TYPE_FOLDER) {
      if (!newFile.mkdirs()) throw new Exception("Could not create directory: " + newFile);
    } else {
      if (!newFile.createNewFile()) throw new Exception("Could not create file: " + newFile);
    }
    boolean isDir = newFile.isDirectory();
    FilePayload ep =
        new FilePayload.Builder(newFile.getAbsolutePath(), isDir)
            .size(isDir ? -1 : 0)
            .lastModified(System.currentTimeMillis())
            .build();
    return new TreeNode.Builder(name)
        .setId(newFile.getAbsolutePath())
        .setType(type)
        .setHasChildren(false)
        .setPayload(ep)
        .build();
  }

  @WorkerThread
  @Override
  public void copyNodes(@NonNull List<TreeNode> nodes, @NonNull TreeNode destination)
      throws Exception {
    FilePayload dp = destination.getPayload(FilePayload.class);
    if (dp == null) throw new IllegalStateException("No FilePayload on destination");
    for (TreeNode node : nodes) {
      FilePayload sp = node.getPayload(FilePayload.class);
      if (sp == null) continue;
      File src = new File(sp.getAbsolutePath());
      File dst = new File(dp.getAbsolutePath(), src.getName());
      copyRecursive(src, dst);
    }
  }

  @WorkerThread
  @Override
  public void moveNodes(@NonNull List<TreeNode> nodes, @NonNull TreeNode destination)
      throws Exception {
    FilePayload dp = destination.getPayload(FilePayload.class);
    if (dp == null) throw new IllegalStateException("No FilePayload on destination");
    for (TreeNode node : nodes) {
      FilePayload sp = node.getPayload(FilePayload.class);
      if (sp == null) continue;
      File src = new File(sp.getAbsolutePath());
      File dst = new File(dp.getAbsolutePath(), src.getName());
      if (!src.renameTo(dst)) {
        copyRecursive(src, dst);
        deleteRecursive(src);
      }
    }
  }

  // -------------------------------------------------------------------------
  private boolean hasAnyChild(@NonNull File dir) {
    String[] list = dir.list();
    return list != null && list.length > 0;
  }

  private void deleteRecursive(@NonNull File file) throws Exception {
    if (file.isDirectory()) {
      File[] children = file.listFiles();
      if (children != null) for (File c : children) deleteRecursive(c);
    }
    if (!file.delete()) throw new Exception("Could not delete: " + file);
  }

  private void copyRecursive(@NonNull File src, @NonNull File dst) throws Exception {
    if (src.isDirectory()) {
      if (!dst.mkdirs()) throw new Exception("Could not create dir: " + dst);
      File[] children = src.listFiles();
      if (children != null) for (File c : children) copyRecursive(c, new File(dst, c.getName()));
    } else {
      java.io.FileInputStream in = new java.io.FileInputStream(src);
      java.io.FileOutputStream out = new java.io.FileOutputStream(dst);
      byte[] buf = new byte[8192];
      int n;
      try {
        while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
      } finally {
        in.close();
        out.close();
      }
    }
  }
}
