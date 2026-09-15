# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
-renamesourcefileattribute SourceFile

# Archive engines reach into these libraries reflectively (7z codec lookup,
# RAR volume handling). They are tiny; keeping them whole costs a few KB and
# guarantees zip/7z/tar/xz/rar extraction never breaks under R8.
#
# org.apache.commons.compress's sevenz package MUST be kept verbatim:
# SevenZFile instantiates its coder/filter classes (LZMA2, BCJ, AES...) from
# names embedded in the archive header via Class.forName. Obfuscating them
# makes every non-trivial 7z fail on minified builds even though plain ZIP
# still works. The rest of these libraries is used through direct APIs, which
# R8 keeps automatically, so only the reflective surface is pinned.
-keep class org.apache.commons.compress.archivers.sevenz.** { *; }
-dontwarn com.github.junrar.**
-dontwarn org.apache.commons.compress.**

# Coil's video decoder touches MediaCodec internals via documented APIs only;
# bundled consumer rules cover it, this silences optional-metadata warnings.
-dontwarn org.checkerframework.**
