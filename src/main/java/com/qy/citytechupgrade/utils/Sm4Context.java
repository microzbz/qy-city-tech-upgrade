package com.qy.citytechupgrade.utils;

/**
 * sm4加解密上下文
 */
public class Sm4Context {
    public int mode;
    public int[] sk;
    public boolean isPadding;

    public Sm4Context() {
        this.mode = 1;
        this.isPadding = true;
        this.sk = new int[32];
    }
}
