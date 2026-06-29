package ir.hanzodev1375.filetreelib.model;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Date;

/**
 * Payload attached to a {@link com.treeview.core.TreeNode} when the node represents a real
 * filesystem entry. Carries metadata such as absolute path, MIME type, last-modified time, file
 * size, and Git status so that the UI layer never needs to hit the disk for display information.
 *
 * <p>This class is {@link Parcelable} so it survives process death and configuration changes when
 * packed inside a {@link com.treeview.core.TreeState}.
 */
public final class FilePayload implements Parcelable {

  // -------------------------------------------------------------------------
  // Git status constants (bitmask)
  // -------------------------------------------------------------------------

  /** File is untracked by Git. */
  public static final int GIT_UNTRACKED = 0;

  /** File has been modified since last commit. */
  public static final int GIT_MODIFIED = 1;

  /** File has been staged (index). */
  public static final int GIT_STAGED = 2;

  /** File was added to the index. */
  public static final int GIT_ADDED = 4;

  /** File has been deleted. */
  public static final int GIT_DELETED = 8;

  /** File has a merge conflict. */
  public static final int GIT_CONFLICTED = 16;

  /** File is ignored via .gitignore. */
  public static final int GIT_IGNORED = 32;

  /** File is clean (no changes). */
  public static final int GIT_CLEAN = 64;

  // -------------------------------------------------------------------------
  // Fields
  // -------------------------------------------------------------------------

  /** Absolute path on the device's filesystem. */
  @NonNull private final String absolutePath;

  /** MIME type, e.g. "text/x-java-source". May be null if unknown. */
  @Nullable private String mimeType;

  /** File size in bytes. -1 for directories. */
  private long size;

  /** Last-modified timestamp in milliseconds since epoch. */
  private long lastModified;

  /** Whether this entry is a directory. */
  private final boolean isDirectory;

  /** Whether this entry is a symbolic link. */
  private boolean isSymlink;

  /** Bitmask of GIT_* constants. */
  private int gitStatus;

  /** Number of errors/warnings from a linter or build system. */
  private int errorCount;

  /** Number of warnings. */
  private int warningCount;

  /** Whether the user has bookmarked this node. */
  private boolean bookmarked;

  /**
   * Arbitrary tag string for language-server badges or other extensions. For example: "⚑ 3 errors".
   */
  @Nullable private String badge;

  // -------------------------------------------------------------------------
  // Constructor
  // -------------------------------------------------------------------------

  private FilePayload(Builder builder) {
    this.absolutePath = builder.absolutePath;
    this.mimeType = builder.mimeType;
    this.size = builder.size;
    this.lastModified = builder.lastModified;
    this.isDirectory = builder.isDirectory;
    this.isSymlink = builder.isSymlink;
    this.gitStatus = builder.gitStatus;
    this.errorCount = builder.errorCount;
    this.warningCount = builder.warningCount;
    this.bookmarked = builder.bookmarked;
    this.badge = builder.badge;
  }

  // -------------------------------------------------------------------------
  // Parcelable
  // -------------------------------------------------------------------------

  protected FilePayload(@NonNull Parcel in) {
    absolutePath = in.readString();
    mimeType = in.readString();
    size = in.readLong();
    lastModified = in.readLong();
    isDirectory = in.readByte() != 0;
    isSymlink = in.readByte() != 0;
    gitStatus = in.readInt();
    errorCount = in.readInt();
    warningCount = in.readInt();
    bookmarked = in.readByte() != 0;
    badge = in.readString();
  }

  @Override
  public void writeToParcel(@NonNull Parcel dest, int flags) {
    dest.writeString(absolutePath);
    dest.writeString(mimeType);
    dest.writeLong(size);
    dest.writeLong(lastModified);
    dest.writeByte((byte) (isDirectory ? 1 : 0));
    dest.writeByte((byte) (isSymlink ? 1 : 0));
    dest.writeInt(gitStatus);
    dest.writeInt(errorCount);
    dest.writeInt(warningCount);
    dest.writeByte((byte) (bookmarked ? 1 : 0));
    dest.writeString(badge);
  }

  @Override
  public int describeContents() {
    return 0;
  }

  public static final Creator<FilePayload> CREATOR =
      new Creator<FilePayload>() {
        @Override
        public FilePayload createFromParcel(Parcel in) {
          return new FilePayload(in);
        }

        @Override
        public FilePayload[] newArray(int size) {
          return new FilePayload[size];
        }
      };

  // -------------------------------------------------------------------------
  // Accessors
  // -------------------------------------------------------------------------

  /** Returns the absolute filesystem path of this entry. */
  @NonNull
  public String getAbsolutePath() {
    return absolutePath;
  }

  /** Returns the MIME type or {@code null} if unknown. */
  @Nullable
  public String getMimeType() {
    return mimeType;
  }

  /** Sets the MIME type (mutable because it may be resolved lazily). */
  public void setMimeType(@Nullable String mimeType) {
    this.mimeType = mimeType;
  }

  /** Returns the file size in bytes, or {@code -1} for directories. */
  public long getSize() {
    return size;
  }

  /** Sets the file size (updated after a file change event). */
  public void setSize(long size) {
    this.size = size;
  }

  /** Returns the last-modified time in milliseconds since epoch. */
  public long getLastModified() {
    return lastModified;
  }

  /** Sets the last-modified time. */
  public void setLastModified(long lastModified) {
    this.lastModified = lastModified;
  }

  /** Returns {@code true} if this payload represents a directory. */
  public boolean isDirectory() {
    return isDirectory;
  }

  /** Returns {@code true} if this entry is a symbolic link. */
  public boolean isSymlink() {
    return isSymlink;
  }

  /** Sets the symbolic-link flag. */
  public void setSymlink(boolean symlink) {
    this.isSymlink = symlink;
  }

  /**
   * Returns the Git status bitmask. Test against the {@code GIT_*} constants:
   *
   * <pre>
   *   if ((payload.getGitStatus() & FilePayload.GIT_MODIFIED) != 0) { ... }
   * </pre>
   */
  public int getGitStatus() {
    return gitStatus;
  }

  /** Updates the Git status bitmask. */
  public void setGitStatus(int gitStatus) {
    this.gitStatus = gitStatus;
  }

  /** Returns {@code true} if the file has been modified and not yet committed. */
  public boolean isGitModified() {
    return (gitStatus & GIT_MODIFIED) != 0;
  }

  /** Returns {@code true} if the file is staged for commit. */
  public boolean isGitStaged() {
    return (gitStatus & GIT_STAGED) != 0;
  }

  /** Returns {@code true} if the file has a merge conflict. */
  public boolean isGitConflicted() {
    return (gitStatus & GIT_CONFLICTED) != 0;
  }

  /** Returns the number of build/lint errors associated with this file. */
  public int getErrorCount() {
    return errorCount;
  }

  /** Sets the error count. */
  public void setErrorCount(int errorCount) {
    this.errorCount = errorCount;
  }

  /** Returns the number of build/lint warnings associated with this file. */
  public int getWarningCount() {
    return warningCount;
  }

  /** Sets the warning count. */
  public void setWarningCount(int warningCount) {
    this.warningCount = warningCount;
  }

  /** Returns {@code true} if the user has bookmarked this node. */
  public boolean isBookmarked() {
    return bookmarked;
  }

  /** Toggles the bookmark state. */
  public void setBookmarked(boolean bookmarked) {
    this.bookmarked = bookmarked;
  }

  /**
   * Returns an optional short badge string displayed next to the node name, e.g. a symbol count
   * like "42 symbols". May be {@code null}.
   */
  @Nullable
  public String getBadge() {
    return badge;
  }

  /** Sets the badge string. */
  public void setBadge(@Nullable String badge) {
    this.badge = badge;
  }

  /**
   * Returns a human-readable file size string (e.g. "4.2 KB", "1.3 MB"). Returns an empty string
   * for directories.
   */
  @NonNull
  public String getHumanReadableSize() {
    if (isDirectory || size < 0) return "";
    if (size < 1024L) return size + " B";
    double kb = size / 1024.0;
    if (kb < 1024.0) return String.format("%.1f KB", kb);
    double mb = kb / 1024.0;
    if (mb < 1024.0) return String.format("%.1f MB", mb);
    double gb = mb / 1024.0;
    return String.format("%.1f GB", gb);
  }

  /** Returns the filename (last path segment of {@link #getAbsolutePath()}). */
  @NonNull
  public String getFileName() {
    int idx = absolutePath.lastIndexOf('/');
    if (idx < 0 || idx == absolutePath.length() - 1) return absolutePath;
    return absolutePath.substring(idx + 1);
  }

  /** Returns the file extension (without the dot), or an empty string. */
  @NonNull
  public String getExtension() {
    String name = getFileName();
    int dot = name.lastIndexOf('.');
    if (dot < 0 || dot == name.length() - 1) return "";
    return name.substring(dot + 1).toLowerCase();
  }

  /** Returns the parent directory path, or an empty string for root. */
  @NonNull
  public String getParentPath() {
    int idx = absolutePath.lastIndexOf('/');
    if (idx <= 0) return "/";
    return absolutePath.substring(0, idx);
  }

  @NonNull
  @Override
  public String toString() {
    return "FilePayload{"
        + "path='"
        + absolutePath
        + '\''
        + ", size="
        + size
        + ", gitStatus="
        + gitStatus
        + ", errors="
        + errorCount
        + '}';
  }

  // -------------------------------------------------------------------------
  // Builder
  // -------------------------------------------------------------------------

  /** Fluent builder for {@link FilePayload}. */
  public static final class Builder {

    @NonNull private final String absolutePath;
    private final boolean isDirectory;

    @Nullable private String mimeType;
    private long size = -1;
    private long lastModified = System.currentTimeMillis();
    private boolean isSymlink = false;
    private int gitStatus = GIT_UNTRACKED;
    private int errorCount = 0;
    private int warningCount = 0;
    private boolean bookmarked = false;
    @Nullable private String badge;

    /**
     * Creates a builder for the given absolute path.
     *
     * @param absolutePath absolute filesystem path (must not be null or empty)
     * @param isDirectory {@code true} if this entry is a directory
     */
    public Builder(@NonNull String absolutePath, boolean isDirectory) {
      if (absolutePath.isEmpty()) {
        throw new IllegalArgumentException("absolutePath must not be empty");
      }
      this.absolutePath = absolutePath;
      this.isDirectory = isDirectory;
    }

    /** Convenience factory for a file entry. */
    @NonNull
    public static Builder file(@NonNull String absolutePath) {
      return new Builder(absolutePath, false);
    }

    /** Convenience factory for a directory entry. */
    @NonNull
    public static Builder directory(@NonNull String absolutePath) {
      return new Builder(absolutePath, true);
    }

    public Builder mimeType(@Nullable String mimeType) {
      this.mimeType = mimeType;
      return this;
    }

    public Builder size(long size) {
      this.size = size;
      return this;
    }

    public Builder lastModified(long lastModified) {
      this.lastModified = lastModified;
      return this;
    }

    public Builder lastModified(@NonNull Date date) {
      this.lastModified = date.getTime();
      return this;
    }

    public Builder symlink(boolean symlink) {
      this.isSymlink = symlink;
      return this;
    }

    public Builder gitStatus(int gitStatus) {
      this.gitStatus = gitStatus;
      return this;
    }

    public Builder errorCount(int errorCount) {
      this.errorCount = errorCount;
      return this;
    }

    public Builder warningCount(int warningCount) {
      this.warningCount = warningCount;
      return this;
    }

    public Builder bookmarked(boolean bookmarked) {
      this.bookmarked = bookmarked;
      return this;
    }

    public Builder badge(@Nullable String badge) {
      this.badge = badge;
      return this;
    }

    /** Builds and returns the immutable {@link FilePayload}. */
    @NonNull
    public FilePayload build() {
      return new FilePayload(this);
    }
  }
}
