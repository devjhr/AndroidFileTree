package ir.hanzodev1375.filetreelib.core;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import ir.hanzodev1375.filetreelib.model.FilePayload;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Builds the Android Studio-style "Android" project view tree for {@code
 * FileTreeView#setAndroidMod} — Gradle module discovery/flattening, a single module's
 * manifests/java/res/assets restructuring, and the mipmap/values-by-filename resource grouping.
 * Pure {@link File} I/O plus building freestanding {@link TreeNode}/{@link FilePayload} objects;
 * nothing here touches a {@code View}, {@code TreeController}, or any other live-tree state —
 * that orchestration (background thread, loading spinner, swapping the result into the live
 * tree) stays in {@code FileTreeView} and just calls into the handful of {@code public} entry
 * points below.
 *
 * <p>Extracted out of {@code FileTreeView} once that file grew large enough that this project-
 * structure logic (which has nothing to do with rendering a tree row) was hard to find among the
 * view-level code around it.
 */
public final class AndroidModTreeBuilder {

  private AndroidModTreeBuilder() {}

  /**
   * A Flutter project keeps its Gradle/Android module inside an {@code android/} subfolder
   * instead of at the project root — {@code settings.gradle}/{@code build.gradle} live in {@code
   * android/}, not next to {@code pubspec.yaml}, and the rest of the tree ({@code lib/}, {@code
   * ios/}, {@code test/}, ...) is Dart source with nothing Gradle-related in it. Detected by
   * {@code pubspec.yaml} at the root plus an {@code android/} folder that is itself a Gradle
   * project; anything else is treated as a normal (non-Flutter) Android project rooted at {@code
   * projectRoot} directly. {@link #discoverAndroidModTree} starts from whatever this returns, so
   * for a Flutter project {@code lib/ios/test/pubspec.yaml} are never even visited — not filtered
   * out after the fact, just never walked into.
   */
  @NonNull
  public static File resolveGradleRoot(@NonNull File projectRoot) {
    File androidDir = new File(projectRoot, "android");
    boolean looksLikeFlutter =
        new File(projectRoot, "pubspec.yaml").isFile() && androidDir.isDirectory();
    if (looksLikeFlutter
        && firstExisting(androidDir, "settings.gradle.kts", "settings.gradle") != null) {
      return androidDir;
    }
    return projectRoot;
  }

  /**
   * Recursively walks {@code dir}, flattening every Gradle module found (at any depth) into
   * {@code outModules} and collecting each module's own {@code build.gradle}(.kts) into {@code
   * outScripts}. A folder that isn't itself a module but contains one somewhere below it is walked
   * through without adding a node for the folder itself — it never appears in the tree, only the
   * modules inside it do. A folder that is neither a module nor contains one anywhere below it is
   * skipped entirely. Ported from the reference project's {@code buildHierarchicalTreeLocal} +
   * {@code localContainsModules}.
   */
  public static void discoverAndroidModTree(
      @NonNull File dir,
      @NonNull List<TreeNode> outModules,
      @NonNull List<TreeNode> outScripts,
      @NonNull Map<String, Boolean> containsModuleCache) {
    File[] subDirs = dir.listFiles(File::isDirectory);
    if (subDirs == null) return;
    Arrays.sort(subDirs, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));

    for (File sub : subDirs) {
      if (isAndroidModSkipDir(sub.getName())) continue;

      ModuleType type = ModuleUtils.getModuleType(sub);
      if (type != ModuleType.NOT_A_MODULE) {
        String typeLabel = moduleTypeLabel(type);
        File moduleBuild = firstExisting(sub, "build.gradle.kts", "build.gradle");
        if (moduleBuild != null) {
          outScripts.add(
              gradleFileNode(moduleBuild, "(Module :" + sub.getName() + " · " + typeLabel + ")"));
        }
        outModules.add(buildModuleNode(sub, type, typeLabel));
        // A module can itself contain nested modules — keep looking, still flattened into the
        // same outModules list (mirrors the reference always recursing against rootNode).
        discoverAndroidModTree(sub, outModules, outScripts, containsModuleCache);
      } else if (containsModuleSomewhereBelow(sub, containsModuleCache)) {
        discoverAndroidModTree(sub, outModules, outScripts, containsModuleCache);
      }
      // else: not a module, and nothing under it is either → not shown at all.
    }
  }

  /**
   * Builds the "Gradle Scripts" virtual group node for {@code scripts} — the root-level
   * counterpart to each module's own entry already folded in by {@link #discoverAndroidModTree}.
   * Returns {@code null} if {@code scripts} is empty (nothing to show).
   */
  @Nullable
  public static TreeNode buildGradleScriptsGroup(
      @NonNull String rootId, @NonNull List<TreeNode> scripts) {
    if (scripts.isEmpty()) return null;
    TreeNode scriptsGroup =
        new TreeNode.Builder("Gradle Scripts")
            .setId(rootId + "::virtual::Gradle Scripts")
            .setType(TreeNode.TYPE_VIRTUAL)
            .setHasChildren(true)
            .build();
    for (TreeNode script : scripts) scriptsGroup.addChild(script);
    return scriptsGroup;
  }

  private static boolean containsModuleSomewhereBelow(
      @NonNull File dir, @NonNull Map<String, Boolean> cache) {
    String key = dir.getAbsolutePath();
    Boolean cached = cache.get(key);
    if (cached != null) return cached;

    boolean result = false;
    File[] subDirs = dir.listFiles(File::isDirectory);
    if (subDirs != null) {
      for (File sub : subDirs) {
        if (isAndroidModSkipDir(sub.getName())) continue;
        if (ModuleUtils.getModuleType(sub) != ModuleType.NOT_A_MODULE
            || containsModuleSomewhereBelow(sub, cache)) {
          result = true;
          break;
        }
      }
    }
    cache.put(key, result);
    return result;
  }

  /**
   * Folders never worth descending into while looking for more modules. {@code src} is the
   * important one for multi-module projects: a module's own source tree can contain thousands of
   * package folders, and none of them can ever be a real nested Gradle module (that's simply not
   * where Gradle looks), so walking into it was pure wasted work — on a large multi-module
   * project this was slow enough to look like a hang/crash. Anything starting with "." (
   * {@code .git}, {@code .gradle}, {@code .idea}, {@code .kotlin}, {@code .cxx},
   * {@code .androidpe}, IDE/tooling caches in general) is skipped the same way.
   */
  private static boolean isAndroidModSkipDir(@NonNull String name) {
    if (name.startsWith(".")) return true;
    return name.equals("build") || name.equals("src");
  }

  /** Builds one flattened module node — badge/description set on it directly, then restructured. */
  @NonNull
  private static TreeNode buildModuleNode(
      @NonNull File moduleDir, @NonNull ModuleType type, @NonNull String typeLabel) {
    FilePayload payload =
        new FilePayload.Builder(moduleDir.getAbsolutePath(), true)
            .badgeColor(moduleBadgeColor(type))
            .description(typeLabel)
            .build();
    TreeNode moduleNode =
        new TreeNode.Builder(moduleDir.getName())
            .setId(moduleDir.getAbsolutePath())
            .setType(TreeNode.TYPE_FOLDER)
            .setPayload(payload)
            .build();
    restructureModuleNode(moduleNode, moduleDir);
    return moduleNode;
  }

  /**
   * Rebuilds {@code moduleNode}'s children to mirror Android Studio's "Android" project view for
   * a single module: a {@code manifests} group holding {@code src/main/AndroidManifest.xml}, the
   * {@code java}/{@code kotlin} source roots promoted to direct children, generated sources/res
   * from {@code build/generated}, a {@code res} group with same-name resources merged across
   * qualifiers (see {@link #buildResGroup}), and {@code assets} — ported from the reference
   * project's {@code addLocalModuleNode}/{@code buildLocalResTree}.
   *
   * <p>Anything in the module folder that doesn't match one of those slots (custom top-level
   * files, non-standard folders) is intentionally left out, same as the reference — this view is
   * meant to look exactly like Android Studio's, not like a raw file browser.
   *
   * <p>Called from {@code FileTreeView}'s background thread, along with the rest of module
   * discovery — this is where most of that work's cost comes from on a module with a large {@code
   * res} tree, since every qualifier folder gets walked up front.
   *
   * @param moduleNode the already-loaded module folder node (real, non-virtual)
   * @param moduleDir the same folder as a {@link File}
   */
  private static void restructureModuleNode(@NonNull TreeNode moduleNode, @NonNull File moduleDir) {
    List<TreeNode> children = new ArrayList<>();

    File manifest = new File(moduleDir, "src/main/AndroidManifest.xml");
    if (manifest.isFile()) {
      TreeNode manifestsGroup =
          virtualGroupNode(moduleDir.getAbsolutePath() + "::virtual::manifests", "manifests");
      manifestsGroup.addChild(realFileNode(manifest));
      manifestsGroup.setHasChildren(true);
      children.add(manifestsGroup);
    }

    File javaDir = new File(moduleDir, "src/main/java");
    if (javaDir.isDirectory()) children.add(realFolderNode(javaDir));

    File kotlinDir = new File(moduleDir, "src/main/kotlin");
    if (kotlinDir.isDirectory()) children.add(realFolderNode(kotlinDir));

    File generatedJava = new File(moduleDir, "build/generated/source");
    if (generatedJava.isDirectory()) children.add(realFolderNode(generatedJava, "Generated"));

    File generatedRes = new File(moduleDir, "build/generated/res");
    if (generatedRes.isDirectory()) children.add(realFolderNode(generatedRes, "Generated"));

    File resDir = new File(moduleDir, "src/main/res");
    if (resDir.isDirectory()) {
      TreeNode resGroup = virtualGroupNode(resDir.getAbsolutePath() + "::virtual::res", "res");
      buildResGroup(resDir, resGroup);
      resGroup.setHasChildren(resGroup.getChildCount() > 0);
      children.add(resGroup);
    }

    File assetsDir = new File(moduleDir, "src/main/assets");
    if (assetsDir.isDirectory()) children.add(realFolderNode(assetsDir));

    if (!children.isEmpty()) {
      moduleNode.setChildren(children);
      moduleNode.setLazyLoadPending(false);
    }
  }

  /**
   * Groups {@code resDir}'s immediate subfolders the way Android Studio's Android view does:
   * {@code drawable}/{@code layout}/{@code mipmap}/{@code values} (and their qualified variants
   * like {@code drawable-night}, {@code layout-land}) are merged by base name into one virtual
   * group, and within it every file sharing a name across qualifiers (e.g. {@code activity_main.xml}
   * from both {@code layout} and {@code layout-land}) collapses into one expandable row. Folder
   * types outside that set ({@code menu}, {@code anim}, {@code raw}, {@code font}, ...) are added
   * as plain, still-lazily-loadable real folders. Ported from the reference project's
   * {@code buildLocalResTree}.
   */
  private static void buildResGroup(@NonNull File resDir, @NonNull TreeNode resGroup) {
    File[] resFolders = resDir.listFiles(File::isDirectory);
    if (resFolders == null) return;

    // "drawable" and "layout" are intentionally NOT merged by filename — both spread across many
    // qualifiers/states with mostly-distinct names, so merging would mostly just add an extra
    // layer over browsing the qualifier folders directly. "values" almost always benefits from
    // the merge (a handful of well-known files — colors.xml, strings.xml, themes.xml — shared
    // across a couple of qualifiers like values-night). "mipmap" is merged only for launcher-icon
    // names (see isLauncherIconName) — that's the one thing actually meant to live there; anything
    // else a project drops in mipmap (rare) is shown ungrouped instead of forcing it into the
    // same by-name merge.
    Set<String> virtualTypes = new HashSet<>(Arrays.asList("mipmap", "values"));
    Map<String, List<File>> grouped = new LinkedHashMap<>();
    for (File f : resFolders) {
      String name = f.getName();
      int dash = name.indexOf('-');
      String base = dash > 0 ? name.substring(0, dash) : name;
      grouped.computeIfAbsent(base, k -> new ArrayList<>()).add(f);
    }

    for (Map.Entry<String, List<File>> entry : grouped.entrySet()) {
      String base = entry.getKey();
      List<File> variants = entry.getValue();

      if (!virtualTypes.contains(base)) {
        for (File variant : variants) resGroup.addChild(realFolderNode(variant));
        continue;
      }

      boolean isMipmap = base.equals("mipmap");
      TreeNode typeGroup =
          virtualGroupNode(resDir.getAbsolutePath() + "::virtual::res::" + base, base);
      Map<String, TreeNode> byFileName = new LinkedHashMap<>();
      for (File variant : variants) {
        File[] files = variant.listFiles();
        if (files == null) continue;
        Arrays.sort(files, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
        for (File file : files) {
          String fileName = file.getName();
          if (fileName.startsWith(".")) continue;
          int dot = fileName.lastIndexOf('.');
          String nameNoExt = dot > 0 ? fileName.substring(0, dot) : fileName;

          if (isMipmap && !isLauncherIconName(nameNoExt)) {
            // Rare: some projects keep a non-launcher icon in mipmap too. There's no reason to
            // merge it by name across qualifiers like the launcher icon — just list it plainly.
            typeGroup.addChild(realFileNode(file, qualifierLabel(base, variant)));
            continue;
          }

          TreeNode fileGroup = byFileName.get(nameNoExt);
          if (fileGroup == null) {
            fileGroup =
                virtualGroupNode(
                    resDir.getAbsolutePath() + "::virtual::res::" + base + "::" + nameNoExt,
                    nameNoExt);
            typeGroup.addChild(fileGroup);
            byFileName.put(nameNoExt, fileGroup);
          }
          fileGroup.addChild(realFileNode(file, qualifierLabel(base, variant)));
          fileGroup.setHasChildren(true);
        }
      }
      typeGroup.setHasChildren(typeGroup.getChildCount() > 0);
      resGroup.addChild(typeGroup);
    }
  }

  /** {@code ic_launcher}, {@code ic_launcher_round}, {@code ic_launcher_foreground}, etc. — the
   * only names in {@code mipmap} that get merged by name across density qualifiers. */
  private static boolean isLauncherIconName(@NonNull String nameNoExt) {
    return nameNoExt.startsWith("ic_launcher");
  }

  /**
   * Readable qualifier for a file grouped under {@link #buildResGroup} — e.g. {@code "hdpi"} for
   * a file from {@code mipmap-hdpi}, {@code "night"} for one from {@code values-night}, or {@code
   * "default"} for the unqualified {@code values}/{@code mipmap} folder itself. Shown via {@link
   * FilePayload#getDescription()} next to the file name, so when several same-named files
   * collapse into one group it's still clear which physical file (which density, which mode)
   * each row actually is.
   */
  @NonNull
  private static String qualifierLabel(@NonNull String base, @NonNull File variant) {
    String name = variant.getName();
    return name.equals(base) ? "default" : name.substring(base.length() + 1);
  }

  /** Bare, not-yet-attached {@link TreeNode#TYPE_VIRTUAL} group — callers add children then attach it. */
  @NonNull
  private static TreeNode virtualGroupNode(@NonNull String id, @NonNull String name) {
    return new TreeNode.Builder(name).setId(id).setType(TreeNode.TYPE_VIRTUAL).build();
  }

  /** Real, still-lazily-loadable folder node pointing at {@code dir} (expand triggers the normal provider). */
  @NonNull
  private static TreeNode realFolderNode(@NonNull File dir) {
    return realFolderNode(dir, null);
  }

  /**
   * Same as {@link #realFolderNode(File)}, with a description shown next to the folder name —
   * used to mark {@code build/generated/source} and {@code build/generated/res} as generated,
   * the same way Android Studio's own "Android" project view labels them, distinct from the
   * hand-written {@code java}/{@code kotlin}/{@code res} source roots.
   */
  @NonNull
  private static TreeNode realFolderNode(@NonNull File dir, @Nullable String description) {
    String[] entries = dir.list();
    FilePayload.Builder pb = new FilePayload.Builder(dir.getAbsolutePath(), true);
    if (description != null) pb.description(description);
    return new TreeNode.Builder(dir.getName())
        .setId(dir.getAbsolutePath())
        .setType(TreeNode.TYPE_FOLDER)
        .setHasChildren(entries != null && entries.length > 0)
        .setPayload(pb.build())
        .build();
  }

  /** Real leaf file node pointing at {@code f} (tapping it opens the real file, same as anywhere else). */
  @NonNull
  private static TreeNode realFileNode(@NonNull File f) {
    return realFileNode(f, null);
  }

  /** Same as {@link #realFileNode(File)}, with a description shown next to the file name — used
   * by {@link #buildResGroup} to label which qualifier (density/mode) a grouped file came from. */
  @NonNull
  private static TreeNode realFileNode(@NonNull File f, @Nullable String description) {
    FilePayload.Builder pb = new FilePayload.Builder(f.getAbsolutePath(), false);
    if (description != null) pb.description(description);
    return new TreeNode.Builder(f.getName())
        .setId(f.getAbsolutePath())
        .setType(TreeNode.TYPE_FILE)
        .setPayload(pb.build())
        .build();
  }

  /**
   * Module-dot color by {@link ModuleType}, so an app module reads differently from a library at
   * a glance — the same way Android Studio's own project view distinguishes them. {@code
   * NOT_A_MODULE} is intentionally absent: those folders never reach {@link #moduleBadgeColor}.
   */
  private static int moduleBadgeColor(@NonNull ModuleType type) {
    switch (type) {
      case ANDROID_APP:
        return 0xFF8A63FF; // purple
      case ANDROID_LIBRARY:
        return 0xFF42A5F5; // blue
      case JAVA_LIBRARY:
        return 0xFFFFA726; // orange
      case KOTLIN_JVM:
        return 0xFF26A69A; // teal
      case GENERIC_MODULE:
      default:
        return 0xFF9E9E9E; // grey
    }
  }

  /** Short human label by {@link ModuleType}, appended to a module folder's description. */
  private static String moduleTypeLabel(@NonNull ModuleType type) {
    switch (type) {
      case ANDROID_APP:
        return "Android Application";
      case ANDROID_LIBRARY:
        return "Android Library";
      case JAVA_LIBRARY:
        return "Java Library";
      case KOTLIN_JVM:
        return "Kotlin";
      case GENERIC_MODULE:
      default:
        return "Module";
    }
  }

  public static void addGradleFileIfExists(
      @NonNull List<TreeNode> out,
      @NonNull String dirPath,
      @NonNull String name1,
      @Nullable String name2,
      @NonNull String description) {
    File f = firstExisting(new File(dirPath), name1, name2);
    if (f != null) out.add(gradleFileNode(f, description));
  }

  @Nullable
  public static File firstExisting(
      @NonNull File dir, @Nullable String name1, @Nullable String name2) {
    if (name1 != null) {
      File f = new File(dir, name1);
      if (f.exists()) return f;
    }
    if (name2 != null) {
      File f = new File(dir, name2);
      if (f.exists()) return f;
    }
    return null;
  }

  @NonNull
  private static TreeNode gradleFileNode(@NonNull File f, @NonNull String description) {
    String path = f.getAbsolutePath();
    return new TreeNode.Builder(f.getName())
        .setId(path)
        .setType(TreeNode.TYPE_FILE)
        .setPayload(new FilePayload.Builder(path, false).description(description).build())
        .build();
  }
}
