package com.alphaxk.keymapper;

interface ITouchService {
    void injectMultiTouch(in int[] actions, in int[] ids, in int[] xs, in int[] ys);
    void injectSwipe(float x1, float y1, float x2, float y2, int duration);
}
