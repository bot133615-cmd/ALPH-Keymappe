package com.alphaxk.keymapper;

interface ITouchService {
    void injectTap(int x, int y);
    void injectSwipe(int x1, int y1, int x2, int y2, int duration);
    void injectMultiTouch(int[] xs, int[] ys, int[] actions);
}
