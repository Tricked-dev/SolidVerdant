-dontoptimize

# Some methods are only called from tests, so make sure the shrinker keeps them.
-keep class com.example.android.architecture.blueprints.** { *; }

-keep class androidx.drawerlayout.widget.DrawerLayout { *; }
-keep class androidx.test.espresso.**
# keep the class and specified members from being removed or renamed
-keep class androidx.test.espresso.IdlingRegistry { *; }
-keep class androidx.test.espresso.IdlingResource { *; }

-keep class com.google.common.base.Preconditions { *; }

-keep class androidx.room.RoomDataBase { *; }
-keep class androidx.room.Room { *; }
-keep class android.arch.** { *; }

# Proguard rules that are applied to your test apk/code.
-ignorewarnings

-keepattributes *Annotation*

-dontnote junit.framework.**
-dontnote junit.runner.**

-dontwarn androidx.test.**
-dontwarn org.junit.**
-dontwarn org.hamcrest.**
-dontwarn com.squareup.javawriter.JavaWriter
# Uncomment this if you use Mockito
-dontwarn org.mockito.**

# Keep all classes in the data model package
-keep class dev.tricked.solidverdant.data.model.** { *; }

# Keep all classes that are annotated with @Serializable
-keep @kotlinx.serialization.Serializable class * { *; }
-keep class dev.tricked.solidverdant.data.remote.TypeReference { *; }

# ===== Retrofit and OkHttp =====
# Retrofit does reflection on generic parameters. InnerClasses is required to use Signature and
# EnclosingMethod is required to use InnerClasses.
-keepattributes Signature, InnerClasses, EnclosingMethod

# Retrofit does reflection on method and parameter annotations.
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations

# Keep annotation default values (e.g., retrofit2.http.Field.encoded).
-keepattributes AnnotationDefault

# Retain service method parameters when optimizing.
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# Ignore annotation used for build tooling.
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement

# Ignore JSR 305 annotations for embedding nullability information.
-dontwarn javax.annotation.**

# Guarded by a NoClassDefFoundError try/catch and only used when on the classpath.
-dontwarn kotlin.Unit

# Top-level functions that can only be used by Kotlin.
-dontwarn retrofit2.KotlinExtensions
-dontwarn retrofit2.KotlinExtensions$*

# With R8 full mode, it sees no subtypes of Retrofit interfaces since they are created with a Proxy
# and replaces all potential values with null. Explicitly keeping the interfaces prevents this.
-if interface * { @retrofit2.http.* <methods>; }
-keep,allowobfuscation interface <1>

# Keep inherited services.
-if interface * { @retrofit2.http.* <methods>; }
-keep,allowobfuscation interface * extends <1>

# With R8 full mode generic signatures are stripped for classes that are not
# kept. Suspend functions are wrapped in continuations where the type argument
# is used.
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

# R8 full mode strips generic signatures from return types if not kept.
-if interface * { @retrofit2.http.* public *** *(...); }
-keep,allowoptimization,allowshrinking,allowobfuscation class <3>

# Keep generic signature of Call, Response (R8 full mode strips signatures from non-kept items).
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response

# With R8 full mode generic signatures are stripped for classes that are not kept.
-keep,allowobfuscation,allowshrinking class retrofit2.Response

# ===== Kotlin Serialization =====
# Keep `Companion` object fields of serializable classes.
# This avoids serializer lookup through `getDeclaredClasses` as done for named companion objects.
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}

# Keep `serializer()` on companion objects (both default and named) of serializable classes.
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep `INSTANCE.serializer()` of serializable objects.
-if @kotlinx.serialization.Serializable class ** {
    public static ** INSTANCE;
}
-keepclassmembers class <1> {
    public static <1> INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}

# @Serializable and @Polymorphic are used at runtime for polymorphic serialization.
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault

# ===== OkHttp Platform used only on JVM and when Conscrypt and other security providers are available. =====
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# ===== Android Components - Services, Receivers, Activities =====
# Keep all services (especially TileService and ForegroundService implementations)
-keep public class * extends android.app.Service {
    public <init>(...);
    public <methods>;
}

# Keep TileService and its methods
-keep class * extends android.service.quicksettings.TileService {
    *;
}

# Keep BroadcastReceivers and their inner classes
-keep public class * extends android.content.BroadcastReceiver {
    public <init>(...);
    public void onReceive(android.content.Context, android.content.Intent);
}

# Keep all inner BroadcastReceivers (like the one in TimeTrackingTileService)
-keepclassmembers class * {
    ** *BroadcastReceiver;
}

# Keep Activities
-keep public class * extends android.app.Activity {
    public <init>(...);
}

# Keep all companion object constants (used for intent actions and extras)
-keepclassmembers class ** {
    public static final ** Companion;
}
-keepclassmembers class **$Companion {
    public static final java.lang.String ACTION_*;
    public static final java.lang.String EXTRA_*;
    public static ** INSTANCE;
    <fields>;
}

# ===== Hilt Support =====
# Keep classes annotated with Hilt annotations
-keep @dagger.hilt.android.AndroidEntryPoint class * {
    *;
}

# Keep Hilt generated classes
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }

# ===== App-specific Services and Receivers =====
# Explicitly keep our time tracking components
-keep class dev.tricked.solidverdant.service.TimeTrackingTileService { *; }
-keep class dev.tricked.solidverdant.service.TimeTrackingNotificationService { *; }
-keep class dev.tricked.solidverdant.receiver.TimeTrackingBroadcastReceiver { *; }
-keep class dev.tricked.solidverdant.receiver.BootReceiver { *; }
-keep class dev.tricked.solidverdant.ui.tile.ProjectSelectionActivity { *; }

# Keep all action and extra string constants in our services
-keepclassmembers class dev.tricked.solidverdant.service.** {
    public static final java.lang.String ACTION_*;
    public static final java.lang.String EXTRA_*;
    public static ** Companion;
}

# ===== Widgets =====
# Keep AppWidgetProvider implementations
-keep public class * extends android.appwidget.AppWidgetProvider {
    public <init>(...);
    public void onUpdate(android.content.Context, android.appwidget.AppWidgetManager, int[]);
    public void onReceive(android.content.Context, android.content.Intent);
}

# Explicitly keep our widget
-keep class dev.tricked.solidverdant.widget.TimeTrackingWidget { *; }

# ===== Utilities and Managers =====
# Keep ShortcutManager and its methods (used via object singleton)
-keep class dev.tricked.solidverdant.util.ShortcutManager { *; }
-keepclassmembers class dev.tricked.solidverdant.util.ShortcutManager {
    public static final ** INSTANCE;
    public static *** EXTRA_*;
    <methods>;
}
