package com.nbys.escape.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * 仓库格子放置规则。领域对象不依赖 Spring/JDBC，便于独立测试和后续替换布局策略。
 */
public final class GridPacking {
    private GridPacking() {}

    public static final class Rect {
        public final long id;
        public final int x;
        public final int y;
        public final int width;
        public final int height;

        public Rect(long id, int x, int y, int width, int height) {
            this.id = id;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }
    }

    public static final class Position {
        public final int x;
        public final int y;

        public Position(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }

    public static Position firstFit(int gridWidth, int gridHeight, int itemWidth, int itemHeight,
                                    List<Rect> occupied) {
        validateDimensions(gridWidth, gridHeight, itemWidth, itemHeight);
        for (int y = 0; y <= gridHeight - itemHeight; y++) {
            for (int x = 0; x <= gridWidth - itemWidth; x++) {
                if (isFree(x, y, itemWidth, itemHeight, occupied)) return new Position(x, y);
            }
        }
        return null;
    }

    public static boolean fitsAt(int gridWidth, int gridHeight, int x, int y, int itemWidth, int itemHeight,
                                 List<Rect> occupied) {
        validateDimensions(gridWidth, gridHeight, itemWidth, itemHeight);
        if (x < 0 || y < 0 || x + itemWidth > gridWidth || y + itemHeight > gridHeight) return false;
        return isFree(x, y, itemWidth, itemHeight, occupied);
    }

    public static List<Rect> copy(List<Rect> source) {
        return source == null ? new ArrayList<Rect>() : new ArrayList<Rect>(source);
    }

    private static boolean isFree(int x, int y, int width, int height, List<Rect> occupied) {
        if (occupied == null) return true;
        for (Rect other : occupied) {
            boolean separated = x + width <= other.x || other.x + other.width <= x
                    || y + height <= other.y || other.y + other.height <= y;
            if (!separated) return false;
        }
        return true;
    }

    private static void validateDimensions(int gridWidth, int gridHeight, int itemWidth, int itemHeight) {
        if (gridWidth <= 0 || gridHeight <= 0 || itemWidth <= 0 || itemHeight <= 0) {
            throw new IllegalArgumentException("仓库与物品尺寸必须大于0");
        }
    }
}
