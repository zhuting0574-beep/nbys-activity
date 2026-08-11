package com.nbys.escape.domain;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GridPackingTest {
    @Test
    void firstFitSkipsOccupiedCellsAndUsesStableTopLeftOrder() {
        List<GridPacking.Rect> occupied = Arrays.asList(
                new GridPacking.Rect(1, 0, 0, 2, 2),
                new GridPacking.Rect(2, 2, 0, 1, 1));

        GridPacking.Position position = GridPacking.firstFit(4, 3, 1, 1, occupied);

        assertNotNull(position);
        assertEquals(3, position.x);
        assertEquals(0, position.y);
    }

    @Test
    void firstFitReturnsNullWhenNoRectangleCanFit() {
        List<GridPacking.Rect> occupied = Arrays.asList(
                new GridPacking.Rect(1, 0, 0, 2, 2),
                new GridPacking.Rect(2, 2, 0, 2, 2));

        assertNull(GridPacking.firstFit(4, 2, 1, 1, occupied));
    }

    @Test
    void fitsAtRejectsOverlapAndOutOfBounds() {
        List<GridPacking.Rect> occupied = Arrays.asList(new GridPacking.Rect(1, 1, 1, 2, 2));

        assertFalse(GridPacking.fitsAt(5, 5, 0, 0, 2, 2, occupied));
        assertTrue(GridPacking.fitsAt(5, 5, 3, 3, 2, 2, occupied));
        assertFalse(GridPacking.fitsAt(5, 5, 4, 4, 2, 2, occupied));
    }

    @Test
    void validatesDimensions() {
        assertThrows(IllegalArgumentException.class,
                () -> GridPacking.firstFit(0, 5, 1, 1, new ArrayList<GridPacking.Rect>()));
        assertThrows(IllegalArgumentException.class,
                () -> GridPacking.firstFit(5, 5, -1, 1, new ArrayList<GridPacking.Rect>()));
    }
}
