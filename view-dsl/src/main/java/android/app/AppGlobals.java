package android.app;

/**
 * @author yun.
 * @date 2021/7/27
 * @des [http://androidxref.com/9.0.0_r3/xref/frameworks/base/core/java/android/app/AppGlobals.java#31]
 * 这里需要注意，我们在本地定义的两个ActivityThread和AppGlobals要以android.app包名来命名，这样就可以欺骗编辑器，
 * 然后根据类加载器的委托机制，他会直接加载系统的ActivityThread和AppGlobals因此就可以直接获取application了
 * @since [https://github.com/ZuYun]
 * <p><a href="https://github.com/ZuYun">github</a>
 */
public class AppGlobals {

    public static Application getInitialApplication() {
        return null;
    }
}