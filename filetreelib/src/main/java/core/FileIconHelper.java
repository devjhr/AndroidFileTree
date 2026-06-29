package ir.hanzodev1375.filetreelib.core;

import android.widget.ImageView;
import ir.hanzodev1375.filetreelib.R;
import ir.hanzodev1375.filetreelib.core.environment.FileEnvironmentHelper;

public class FileIconHelper {

  private String filePath;
  private String mimeType;

  private int fileIconRes;

  private boolean isDynamicFolderEnabled;
  private boolean isEnvironmentEnabled;

  private FileHelper fileHelper;
  private FileEnvironmentHelper fileEnvHelper;

  public FileIconHelper(String filePath) {
    this.filePath = filePath;
    this.mimeType = "";
    check();
  }

  public FileIconHelper(String filePath, String mimeType) {
    this.filePath = filePath;
    this.mimeType = mimeType;
    check();
  }

  public void setFilePath(String filePath) {
    this.filePath = filePath;
  }

  public String getFilePath() {
    return filePath;
  }

  public void setMimeType(String mimeType) {
    this.mimeType = mimeType;
  }

  public String getMimeType() {
    return mimeType;
  }

  public int getFileIcon() {
    return fileIconRes;
  }

  public void setDynamicFolderEnabled(boolean isDynamicFolderEnabled) {
    this.isDynamicFolderEnabled = isDynamicFolderEnabled;
    check();
  }

  public boolean isDynamicFolderEnabled() {
    return isDynamicFolderEnabled;
  }

  public void setEnvironmentEnabled(boolean isEnvironmentEnabled) {
    this.isEnvironmentEnabled = isEnvironmentEnabled;
    check();
  }

  public boolean isEnvironmentEnabled() {
    return isEnvironmentEnabled;
  }

  public void bindIcon(ImageView imageView) {
    imageView.setImageResource(fileIconRes);
  }

  private void check() {
    fileHelper = new FileHelper(filePath);
    fileEnvHelper = new FileEnvironmentHelper(filePath);

    if (mimeType == null) mimeType = "";

    if (FileUtil.isDirectory(filePath)) setupFolderIcons();
    else setupFileIcons();
  }

  private void setupFolderIcons() {
    if (filePath.equals("")) fileIconRes = R.drawable.ic_filetree_folder;

    if (isDynamicFolderEnabled) {
      if (isEnvironmentEnabled) {
        if (fileEnvHelper.angularjs().isAngularJsDirectory())
          fileIconRes = R.drawable.ic_filetree_folder_angular;
        else if (fileEnvHelper.vuejs().isVueJsDirectory())
          fileIconRes = R.drawable.ic_filetree_folder_vue;
        else if (fileEnvHelper.nodejs().isNodeJsDirectory())
          fileIconRes = R.drawable.ic_filetree_folder_node;
        else if (fileEnvHelper.react().isReactDirectory())
          fileIconRes = R.drawable.ic_filetree_folder_react_component;
        else if (fileEnvHelper.android().isAndroidDevDirectory())
          fileIconRes = R.drawable.ic_filetree_folder_android;
        else if (fileEnvHelper.git().isGitDirectory())
          fileIconRes = R.drawable.ic_filetree_folder_git;
      } else {
        if (fileEnvHelper.isJavaDirectory()) fileIconRes = R.drawable.ic_filetree_folder_java;
        else if (fileEnvHelper.isJavascriptDirectory())
          fileIconRes = R.drawable.ic_filetree_folder_javascript;
        else if (fileEnvHelper.isPhpDirectory()) fileIconRes = R.drawable.ic_filetree_folder_php;
        else if (fileEnvHelper.isCssDirectory()) fileIconRes = R.drawable.ic_filetree_folder_css;
        else if (fileEnvHelper.isMarkdownDirectory())
          fileIconRes = R.drawable.ic_filetree_folder_markdown;
        else if (fileEnvHelper.isLogDirectory()) fileIconRes = R.drawable.ic_filetree_folder_log;
        else if (fileEnvHelper.isJsonDirectory()) fileIconRes = R.drawable.ic_filetree_folder_json;
        else if (fileEnvHelper.isPythonDirectory())
          fileIconRes = R.drawable.ic_filetree_folder_python;
        else if (fileEnvHelper.isDownloadDirectory())
          fileIconRes = R.drawable.ic_filetree_folder_download;
        else if (fileEnvHelper.isDCIMDirectory() || fileEnvHelper.isPicturesDirectory())
          fileIconRes = R.drawable.ic_filetree_folder_images;
        else if (fileEnvHelper.isMusicDirectory() || fileEnvHelper.isNotificationsDirectory())
          fileIconRes = R.drawable.ic_filetree_folder_audio;
        else if (fileEnvHelper.isMoviesDirectory())
          fileIconRes = R.drawable.ic_filetree_folder_video;
        else if (fileEnvHelper.isSrcDirectory()) fileIconRes = R.drawable.ic_filetree_folder_src;
        else if (fileEnvHelper.isPublicDirectory())
          fileIconRes = R.drawable.ic_filetree_folder_public;
        else if (fileEnvHelper.isAppDirectory()) fileIconRes = R.drawable.ic_filetree_folder_app;
        else if (fileEnvHelper.isIntelliJDirectory())
          fileIconRes = R.drawable.ic_filetree_folder_intellij;
        else if (fileEnvHelper.isGradleJDirectory())
          fileIconRes = R.drawable.ic_filetree_folder_gradle;
      }
    } else {
      if (FileUtil.isFileHidden(filePath)) fileIconRes = R.drawable.ic_filetree_folder_secure;
      else fileIconRes = R.drawable.ic_filetree_folder;
    }
  }

  private void setupFileIcons() {
    fileIconRes = R.drawable.ic_filetree_document;

    if (fileHelper.isCompressFiles()) fileIconRes = R.drawable.ic_filetree_zip;
    else if (fileHelper.isBitmapFiles()) fileIconRes = R.drawable.ic_filetree_image;
    else if (fileHelper.isVectorFiles()) fileIconRes = R.drawable.ic_filetree_svg;
    else if (fileHelper.isVideoFiles()) fileIconRes = R.drawable.ic_filetree_video;
    else if (fileHelper.isAudioFiles()) fileIconRes = R.drawable.ic_filetree_audio;
    else if (fileHelper.isFontFiles()) fileIconRes = R.drawable.ic_filetree_font;
    else if (fileHelper.isMicrosoftWordFiles()) fileIconRes = R.drawable.ic_filetree_word;
    else if (fileHelper.isGradleFiles()) fileIconRes = R.drawable.ic_filetree_gradle;
    else if (fileHelper.isYarnFiles()) fileIconRes = R.drawable.ic_filetree_yarn;
    else if (fileHelper.isTestJsFiles()) fileIconRes = R.drawable.ic_filetree_test_js;
    else if (fileHelper.isMinecraftRelatedFiles()) fileIconRes = R.drawable.ic_filetree_minecraft;
    else if (is("apk")) fileIconRes = R.drawable.ic_filetree_android;
    else if (is("pdf")) fileIconRes = R.drawable.ic_filetree_pdf;
    else if (is("ppt")) fileIconRes = R.drawable.ic_filetree_powerpoint;
    else if (is("as")) fileIconRes = R.drawable.ic_filetree_actionscript;
    else if (is("bat")) fileIconRes = R.drawable.ic_filetree_console;
    else if (is("c")) fileIconRes = R.drawable.ic_filetree_c;
    else if (is("cpp")) fileIconRes = R.drawable.ic_filetree_cpp;
    else if (is("csharp")) fileIconRes = R.drawable.ic_filetree_csharp;
    else if (is("class")) fileIconRes = R.drawable.ic_filetree_javaclass;
    else if (is("css")) fileIconRes = R.drawable.ic_filetree_css;
    else if (is("dart")) fileIconRes = R.drawable.ic_filetree_dart;
    else if (is("diff")) fileIconRes = R.drawable.ic_filetree_diff;
    else if (is("go")) fileIconRes = R.drawable.ic_filetree_go;
    else if (is("groovy") || is("gvy") || is("gy") || is("gsh"))
      fileIconRes = R.drawable.ic_filetree_groovy;
    else if (is("htm") || is("html")) {
      if (isEnvironmentEnabled) {
        if (fileEnvHelper.angularjs().isAngularJsFile())
          fileIconRes = R.drawable.ic_filetree_angular;
        else fileIconRes = R.drawable.ic_filetree_html;
      } else fileIconRes = R.drawable.ic_filetree_html;
    } else if (is("jar")) fileIconRes = R.drawable.ic_filetree_jar;
    else if (is("java")) fileIconRes = R.drawable.ic_filetree_java;
    else if (is("js")) {
      if (isEnvironmentEnabled) {
        if (fileEnvHelper.nodejs().isNodeJsFile()) fileIconRes = R.drawable.ic_filetree_nodejs;
        else if (fileEnvHelper.react().isReactFile()) fileIconRes = R.drawable.ic_filetree_react;
        else fileIconRes = R.drawable.ic_filetree_javascript;
      } else fileIconRes = R.drawable.ic_filetree_javascript;
    } else if (is("json")) {
      if (isEnvironmentEnabled) {
        if (fileEnvHelper.isNpmPackageJson()) fileIconRes = R.drawable.ic_filetree_npm;
        else fileIconRes = R.drawable.ic_filetree_json;
      } else fileIconRes = R.drawable.ic_filetree_json;
    } else if (is("kt")) fileIconRes = R.drawable.ic_filetree_kotlin;
    else if (is("less")) fileIconRes = R.drawable.ic_filetree_less;
    else if (is("log")) fileIconRes = R.drawable.ic_filetree_log;
    else if (is("lua")) fileIconRes = R.drawable.ic_filetree_lua;
    else if (is("md")) fileIconRes = R.drawable.ic_filetree_markdown;
    else if (is("mdx")) fileIconRes = R.drawable.ic_filetree_mdx;
    else if (is("pas")) fileIconRes = R.drawable.ic_filetree_pascal;
    else if (is("php")) fileIconRes = R.drawable.ic_filetree_php;
    else if (is("py")) fileIconRes = R.drawable.ic_filetree_python;
    else if (is("pug")) fileIconRes = R.drawable.ic_filetree_pug;
    else if (is("properties")) fileIconRes = R.drawable.ic_filetree_settings;
    else if (is("sass") || is("scss")) fileIconRes = R.drawable.ic_filetree_sass;
    else if (is("sql")) fileIconRes = R.drawable.ic_filetree_database;
    else if (is("stylus")) fileIconRes = R.drawable.ic_filetree_stylus;
    else if (is("swift")) fileIconRes = R.drawable.ic_filetree_swift;
    else if (is("ts")) {
      if (isEnvironmentEnabled) {
        if (fileEnvHelper.react().isReactFile()) fileIconRes = R.drawable.ic_filetree_react_ts;
        else fileIconRes = R.drawable.ic_filetree_typescript;
      } else fileIconRes = R.drawable.ic_filetree_typescript;
    } else if (is("vue")) fileIconRes = R.drawable.ic_filetree_vue;
    else if (is("xml") || is("xsl")) fileIconRes = R.drawable.ic_filetree_xml;
    else if (is("yml") || is("yaml")) fileIconRes = R.drawable.ic_filetree_yaml;

    if (fileEnvHelper.readme().isReadmeFile()) fileIconRes = R.drawable.ic_filetree_readme;
    else if (fileEnvHelper.git().isGitIgnoreFile()) fileIconRes = R.drawable.ic_filetree_git;
    else if (fileEnvHelper.isLicenseFile()) fileIconRes = R.drawable.ic_filetree_certificate;
    else if (FileUtil.isFileHidden(filePath)) fileIconRes = R.drawable.ic_filetree_lock;
  }

  private boolean is(String str) {
    return fileHelper.equals(str);
  }
}
