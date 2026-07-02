package ir.hanzodev1375.filetreelib.filesystem;

import android.os.FileObserver;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ir.hanzodev1375.filetreelib.core.TreeNode;
import ir.hanzodev1375.filetreelib.model.FilePayload;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Watches filesystem directories using Android's FileObserver. Posts change events back to the main
 * thread.
 */
public final class FileWatcher {

  public interface FileChangeListener {
    void onFileCreated(@NonNull String path);

    void onFileDeleted(@NonNull String path);

    void onFileModified(@NonNull String path);

    void onFileRenamed(@NonNull String oldPath, @NonNull String newPath);
  }

  private final Map<String, FileObserver> observers = new HashMap<>();
  private final List<FileChangeListener> listeners = new ArrayList<>();
  private final Handler mainHandler = new Handler(Looper.getMainLooper());

  /** Start watching the directory associated with node. */
  public void watch(@NonNull TreeNode node) {
    Object payload = node.getPayload();
    if (!(payload instanceof FilePayload)) return;
    String path = ((FilePayload) payload).getAbsolutePath();
    watch(path);
  }

  public void watch(@NonNull String dirPath) {
    if (observers.containsKey(dirPath)) return;
    FileObserver observer =
        new FileObserver(
            dirPath,
            FileObserver.CREATE
                | FileObserver.DELETE
                | FileObserver.MODIFY
                | FileObserver.MOVED_FROM
                | FileObserver.MOVED_TO) {
          @Nullable private String pendingMovedFrom = null;

          @Override
          public void onEvent(int event, @Nullable String name) {
            if (name == null) return;
            final String fullPath = dirPath + "/" + name;
            final int masked = event & FileObserver.ALL_EVENTS;
            mainHandler.post(
                () -> {
                  for (FileChangeListener l : listeners) {
                    switch (masked) {
                      case FileObserver.CREATE:
                        l.onFileCreated(fullPath);
                        break;
                      case FileObserver.DELETE:
                        l.onFileDeleted(fullPath);
                        break;
                      case FileObserver.MODIFY:
                        l.onFileModified(fullPath);
                        break;
                      case FileObserver.MOVED_FROM:
                        pendingMovedFrom = fullPath;
                        break;
                      case FileObserver.MOVED_TO:
                        if (pendingMovedFrom != null) {
                          l.onFileRenamed(pendingMovedFrom, fullPath);
                          pendingMovedFrom = null;
                        } else {
                          l.onFileCreated(fullPath);
                        }
                        break;
                    }
                  }
                });
          }
        };
    observer.startWatching();
    observers.put(dirPath, observer);
  }

  public void unwatch(@NonNull String dirPath) {
    FileObserver obs = observers.remove(dirPath);
    if (obs != null) obs.stopWatching();
  }

  public void unwatchAll() {
    for (FileObserver obs : observers.values()) obs.stopWatching();
    observers.clear();
  }

  public void addListener(@NonNull FileChangeListener l) {
    if (!listeners.contains(l)) listeners.add(l);
  }

  public void removeListener(@NonNull FileChangeListener l) {
    listeners.remove(l);
  }
}
