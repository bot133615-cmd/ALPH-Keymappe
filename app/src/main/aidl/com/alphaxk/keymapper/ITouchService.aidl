package com.alphaxk.keymapper;

interface ITouchService {
    void injectTouch(int action, int pointerId, float x, float y);
    void injectMultiTouch(in int[] pointerIds, in float[] xs, in float[] ys);
}
