<div align="center">

[![](https://jitpack.io/v/HanzoDev1375/AndroidFileTree.svg)](https://jitpack.io/#HanzoDev1375/AndroidFileTree)
[![License](https://img.shields.io/github/license/devjhr/AndroidFileTree)](./LICENSE)

# AndroidFileTree

A production-ready Android TreeView library for building modern file explorers similar to Android Studio, Visual Studio Code, and desktop file managers.

</div>

---

# Note

- If implementation is difficult for you, use `FileTreeView` for help.


---

## Features

- Lazy loading folders
- Expand / Collapse nodes
- Recursive search
- Multi-selection
- Copy / Cut / Paste
- Rename
- Delete
- Create files and folders
- Drag & Drop
- File watcher
- Theme support
- Tree state persistence
- Clipboard manager
- Node cache
- Android Studio-style project view (Gradle module detection, Flutter support)
- Expand-to-path with highlight
- Auto-expand single-child chains
- Fully customizable
- AndroidX compatible
- Java 8+

---

# Installation

### add `settings.gradle`

```gradle
dependencyResolutionManagement {
		repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
		repositories {
			mavenCentral()
			maven { url 'https://jitpack.io' }
		}
	}

```


```gradle
	dependencies {
	        implementation 'com.github.HanzoDev1375.AndroidFileTree:filetreelib:1.1.1'
	}
```

## how to load vector xml and svg?
### adding dependencies 

```gradle
	dependencies {
	        implementation 'com.github.HanzoDev1375:AndroidFileTree:1.1.1'
	        implementation 'com.github.HanzoDev1375.AndroidFileTree:filetreeglide:1.1.1'
	}
```
for `FileTreeView`

init FileTreeView

```java
var view = new FileTreeView(this);
    view.setNodePath("/storage/emulated/0/");
    view.loadTree();

```
Tap handling

```java
view.setClickNode((node, itemView) -> {
    // called for files; folders/virtual groups toggle expand/collapse automatically
});
```

custom IconProvider
```java

FileTreeView#setIconProvider(#IconProvider);

```
or call GlideIconProvider

```java
   
FileTreeView#setIconProvider(new FileIconGlide());

```
Custom Theme

```java
   var theme = FileTreeView#getTheme();
   theme.setTextColor(Color.CYAN);
   theme.setTreeLineColor(Color.parseColor("#fff888"));
   theme.setSelectedBg(Color.parseColor("#ff4107"));
```
Zoom Mod 


```java
FileTreeView#setZoomMod(true);
FileTreeView#setZoomScale(50,300);

```

Reset zoom / read current scale

```java
view.resetZoom();                 // back to 100%, doesn't change whether pinch-zoom is enabled
int[] range = view.getZoomScale();       // [min, max]
int current = view.getCurrentZoomScale();
view.setCurrentZoomScale(150);           // jump straight to 150%
```

---

# Android Studio-style Project View

Flattens the tree into what Android Studio's own "Android" project view shows: every Gradle
module (however deeply nested — `:app`, `:feature:settings`, all flattened to the same level),
each one badged and labeled by type (Android Application/Library, Java Library, Kotlin), with a
"Gradle Scripts" group at the top and, inside each module, `manifests`/`java`/`kotlin`/`res`/
`assets` instead of the raw folder layout. Everything else (non-module folders, stray root files)
is hidden — this is a project-structure view, not a filtered file browser.

```java
view.setAndroidMod(true);
view.isAndroidMod(); // true
```

Works the same for Flutter projects — detected automatically (a `pubspec.yaml` at the root plus
an `android/` folder that's itself a Gradle project) and the view is built from `android/` instead
of the project root, so `lib/`, `ios/`, `test/` are never shown.

Discovery runs on a background thread; the existing folder-loading spinner shows on the root row
while it's working. Turning the mode off (`setAndroidMod(false)`) restores the real on-disk
structure.

Virtual grouping nodes ("Gradle Scripts", "res", per-file-name groups) can never be renamed,
deleted, or dragged — they have no single real file to act on. Real files/folders shown inside the
view (module folders, `java`, individual resources) behave exactly as anywhere else in the tree.

---

# Selection Mode / Long-Press

Long-press enters the built-in multi-select mode and shows the selection action panel by default.
To handle long-press yourself instead (a custom dialog, menu, etc.) with no built-in panel at all:

```java
view.setSelectionModeEnabled(false);
view.setOnNodeLongClickListener((node, itemView) -> {
    // show your own UI here
    return true; // consumed
});
```

Returning `false` (or not setting a listener) falls through to the built-in behavior if
`setSelectionModeEnabled` is still `true`.

---

# Drag Enable / Disable

```java
view.setDragEnabled(false); // real files/folders become undraggable
view.isDragEnabled();
```

Virtual grouping nodes (see Android Studio-style Project View above) are always undraggable
regardless of this setting.

---

# Auto-Expand Single-Child Chains

Android Studio's "compact middle packages" behavior, for any folder shape: expanding a folder
that turns out to contain exactly one item auto-expands that item too, and so on down the chain,
stopping the moment a folder has 2+ items (or the chain ends at a file).

```java
view.setAutoExpandSingleChildChains(true);
view.isAutoExpandSingleChildChains();
```

Works the same whether Android Studio-style Project View is on or off.

---

# Permissions

## Android 11+

```xml
<uses-permission android:name="android.permission.MANAGE_EXTERNAL_STORAGE"/>
```

## Android 10 and below

```xml
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE"/>
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"/>
```

---

# Layout

```xml
<ir.hanzodev1375.filetreelib.widget.TreeView
    android:id="@+id/tree_view"
    android:layout_width="match_parent"
    android:layout_height="match_parent"/>
```

---

# Initialize

```java
TreeView treeView = findViewById(R.id.tree_view);

ThemeManager theme = new ThemeManager(this);

File rootDir = new File("/storage/emulated/0");

TreeNode root = TreeNode.root();

TreeNode storage =
    new TreeNode.Builder(rootDir.getName())
        .setId(rootDir.getAbsolutePath())
        .setType(TreeNode.TYPE_FOLDER)
        .setHasChildren(true)
        .setPayload(new FilePayload.Builder(rootDir.getAbsolutePath(), true).build())
        .build();

root.addChild(storage);

TreeModel model = new TreeModel(root);

FileTreeProvider provider = new FileTreeProvider();

TreeController controller =
    new TreeController.Builder(provider)
        .model(model)
        .cache(new TreeCache(512))
        .build();

treeView.setup(controller, theme);

TreeAdapter adapter = treeView.getTreeAdapter();
```

---

# Expand / Collapse

```java
adapter.setOnNodeClickListener((node, view) -> {

    if (node.isFolder()) {
        controller.toggleNode(node);
    }

});
```

---

# Search

```java
TreeSearchEngine engine = new TreeSearchEngine();

List<SearchResult> results =
    engine.search(controller.getModel().getRoot(), query);

adapter.setSearchResults(results);
```

Clear search

```java
adapter.clearSearch();
```

---

# Selection

```java
controller.getSelectionManager()
    .setVisibleNodes(adapter.getCurrentList());

controller.getSelectionManager()
    .selectAll();
```

Selected items

```java
List<TreeNode> selected =
    controller.getSelectedNodes();
```

Exit selection mode

```java
adapter.exitSelectionMode();
```

---

# Clipboard

Copy

```java
clipboard.copy(selected);
```

Cut

```java
clipboard.cut(selected);
```

Paste

```java
controller.getDataProvider()
    .copyNodes(nodes, destination);

controller.getDataProvider()
    .moveNodes(nodes, destination);
```

---

# Create Folder

```java
controller.createFolder(parent, "New Folder", callback);
```

Create File

```java
controller.createFile(parent, "New File.txt", callback);
```

---

# Rename

```java
controller.renameNode(node, "New Name", callback);
```

---

# Delete

```java
controller.deleteNode(node, callback);
```

---

# Drag & Drop

```java
DragManager dragManager = new DragManager(provider);

treeView.attachDragManager(dragManager);
```

---

# File Watcher

```java
FileWatcher watcher = new FileWatcher();

watcher.addListener(listener);

watcher.watch("/storage/emulated/0");
```

Stop watching

```java
watcher.unwatchAll();
```

---

# Save Tree State

```java
outState.putParcelable(
    "tree_state",
    controller.saveState()
);
```

Restore

```java
TreeState state =
    savedInstanceState.getParcelable("tree_state");

controller.restoreState(state);
```

---

# Destroy

```java
@Override
protected void onDestroy() {
    super.onDestroy();

    fileWatcher.unwatchAll();
    searchExecutor.shutdownNow();
    controller.destroy();
}
```

---

# Demo

The sample application demonstrates:

- Runtime permissions
- Tree initialization
- Lazy loading
- Search
- Multi-selection
- Copy
- Cut
- Paste
- Rename
- Delete
- Create files
- Create folders
- Drag & Drop
- File watcher
- Tree state restore
- Clipboard integration

The `app` module is a complete integration example and can be used as a reference when adding AndroidFileTree to your own project.

---

# Requirements

- Android API 21+
- AndroidX
- Java 8+

---

# License

MIT License
