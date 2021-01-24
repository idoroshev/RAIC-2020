package model;

public class MyVec2Int extends Vec2Int {

    public MyVec2Int(int x, int y) {
        super(x, y);
    }

    public MyVec2Int(Vec2Int vec) {
        super(vec.getX(), vec.getY());
    }

    public MyVec2Int add(Vec2Int vec) {
        this.setX(this.getX() + vec.getX());
        this.setY(this.getY() + vec.getY());
        return this;
    }

    public MyVec2Int sub(Vec2Int vec) {
        this.setX(this.getX() - vec.getX());
        this.setY(this.getY() - vec.getY());
        return this;
    }

    public boolean isEqual(Vec2Int vec) {
        return this.getX() == vec.getX() && this.getY() == vec.getY();
    }

    public static MyVec2Int add(Vec2Int vec1, Vec2Int vec2) {
        return new MyVec2Int(vec1.getX() + vec2.getX(), vec1.getY() + vec2.getY());
    }

    public static MyVec2Int sub(Vec2Int vec1, Vec2Int vec2) {
        return new MyVec2Int(vec1.getX() - vec2.getX(), vec1.getY() - vec2.getY());
    }

    public static int calcDist(Vec2Int vec1, Vec2Int vec2) {
        return Math.abs(vec1.getX() - vec2.getX()) + Math.abs(vec1.getY() - vec2.getY());
    }

    public static int calcDirectDist(Vec2Int vec1, Vec2Int vec2) {
        return (int) Math.sqrt(Math.pow(vec1.getX() - vec2.getX(), 2) + Math.pow(vec1.getY() - vec2.getY(), 2));
    }
}
