package cn.pandora.common.security;

/**
 * 登录用户线程上下文 —— 基于 ThreadLocal 的零侵入设计
 */
public final class LoginUserHolder {

    private static final ThreadLocal<LoginUser> CONTEXT = new ThreadLocal<>();

    private LoginUserHolder() {}

    public static void set(LoginUser user) {
        CONTEXT.set(user);
    }

    public static LoginUser get() {
        return CONTEXT.get();
    }

    public static LoginUser require() {
        LoginUser user = CONTEXT.get();
        if (user == null) {
            throw new IllegalStateException("当前线程无登录用户上下文");
        }
        return user;
    }

    public static void clear() {
        CONTEXT.remove();
    }
}
