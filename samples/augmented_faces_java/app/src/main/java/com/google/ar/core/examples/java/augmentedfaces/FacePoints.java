package com.google.ar.core.examples.java.augmentedfaces;

public class FacePoints {
    public enum Point {
        UTMOST_LEFT_EYEBROW(225),
        UTMOST_RIGHT_EYEBROW(445),
        UTMOST_RIGHT_APPLE(448),
        UTMOST_LEFT_APPLE(228),
        UTMOST_LEFT_FOREHEAD(251),
        UTMOST_RIGHT_FOREHEAD(21),

        NOSE_GLASSES_SUPPORT(6),

        ;

        private int index;

        Point(int index) {
            this.index = index;
        }

        public int getIndex() {
            return index;
        }
    }
}