# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# Extremely aggressive, strict release-ready obfuscation configurations:

# Repackage all classes into a single flat package with a short name 'o'
-repackageclasses 'o'

# Overload class and member names aggressively using overload names
-useuniqueclassmembernames
-allowaccessmodification

# Flatten the target package structure entirely to a single random namespace
-flattenpackagehierarchy 'o'

# Completely rename and obliterate class pointers to source files and line numbers
-renamesourcefileattribute 'SourceFile'
-keepattributes !SourceFile,!LineNumberTable,!LocalVariableTable,!LocalVariableTypeTable

# Keep only the mandatory components for operating Android entry-points 
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider
-keep public class * extends android.app.backup.BackupAgentHelper
-keep public class * extends android.preference.Preference

# Keep custom PreferenceFragment classes but permit obfuscation inside
-keep public class * extends androidx.preference.PreferenceFragmentCompat {
    public <init>();
}

# Keep database entities/ Room queries
-keep class * extends androidx.room.RoomDatabase
-keep class * implements androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-keep class * extends androidx.room.RoomDatabase {
    <protected-methods>;
}

# Retain Annotations metadata for Android framework/lifecycle
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Permit deep method name renames and member structure obfuscation inside custom models/controllers
-keepclassmembers class * {
    *** get*();
    *** set*(***);
}

