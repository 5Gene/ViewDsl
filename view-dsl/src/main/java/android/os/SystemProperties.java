package android.os;

import androidx.annotation.NonNull;

/***********************************************************
 ** Android 系统开发中经常需要通过属性在各个进程间传递信息，通过一个进程 set_property，另一个进程 get_property 达到进程间通信的需求。
 **
 ** 属性获取没有限制，但是如果需要进程可以进行设置属性操作，则需要做一些处理。因为在 init 进程属性设置处理过程中会进行 selinux 权限的检查，如果不通过的话，设置属性的请求会被拒绝。
 **
 ** System.setProperty("te", "lala")
 ** println(System.getProperty("te", "aa")) => lala
 **
 **
 ****************************************************************/
public class SystemProperties {

    public static String get(@NonNull String key) {
        return null;
    }

    public static String get(@NonNull String key, @NonNull String def) {
        return null;
    }

    public static boolean getBoolean(@NonNull String key, boolean def) {
        return false;
    }

    public static long getLong(@NonNull String key, long def) {
        return def;
    }

    public static int getInt(@NonNull String key, int def) {
        return def;
    }
}