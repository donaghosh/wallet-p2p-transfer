package com.paytm.wallet.util;

/**
 * Holds the authenticated user id for the current request thread. Set by the auth filter
 * and cleared in its {@code finally} block so no value leaks across pooled threads.
 */
public final class CurrentUserContext {

    private static final ThreadLocal<String> USER_ID = new ThreadLocal<>();

    private CurrentUserContext() {
    }

    public static void set(String userId) {
        USER_ID.set(userId);
    }

    public static String get() {
        return USER_ID.get();
    }

    public static void clear() {
        USER_ID.remove();
    }
}
