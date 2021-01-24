package model;

public class Cell {
    public int x = 0;
    public int y = 0;
    public int dist = 0;

    public Cell() {
    }

    public Cell(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public Cell(int x, int y, int dist) {
        this(x, y);
        this.dist = dist;
    }
}
