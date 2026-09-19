package com.catpaw.chesshelper.model;

import android.graphics.Rect;

/**
 * 棋子模型
 */
public class ChessPiece {

    public static final int JU = 1;
    public static final int MA = 2;
    public static final int XIANG = 3;
    public static final int SHI = 4;
    public static final int JIANG = 5;
    public static final int PAO = 6;
    public static final int BING = 7;

    private int type;       // 棋子类型
    private boolean isRed;  // 是否红方
    private int row;        // 棋盘行
    private int col;        // 棋盘列
    private Rect bounds;    // 在屏幕上的边界

    public ChessPiece(int type, boolean isRed) {
        this.type = type;
        this.isRed = isRed;
    }

    public ChessPiece(int type, boolean isRed, int row, int col) {
        this.type = type;
        this.isRed = isRed;
        this.row = row;
        this.col = col;
    }

    // Getters & Setters
    public int getType() { return type; }
    public void setType(int type) { this.type = type; }

    public boolean isRed() { return isRed; }
    public void setRed(boolean red) { isRed = red; }

    public int getRow() { return row; }
    public void setRow(int row) { this.row = row; }

    public int getCol() { return col; }
    public void setCol(int col) { this.col = col; }

    public Rect getBounds() { return bounds; }
    public void setBounds(Rect bounds) { this.bounds = bounds; }

    /**
     * 获取内部棋子编码（用于90格棋盘）
     */
    public int getInternalCode() {
        switch (type) {
            case JU:    return isRed ? 5 : 12;
            case MA:    return isRed ? 4 : 11;
            case XIANG: return isRed ? 3 : 10;
            case SHI:   return isRed ? 2 : 9;
            case JIANG: return isRed ? 1 : 8;
            case PAO:   return isRed ? 6 : 13;
            case BING:  return isRed ? 7 : 14;
            default:    return 0;
        }
    }

    /**
     * 棋子中文名称
     */
    public String getChineseName() {
        String colorPrefix = isRed ? "红" : "黑";
        String pieceName;
        switch (type) {
            case JU:    pieceName = "车"; break;
            case MA:    pieceName = "马"; break;
            case XIANG: pieceName = isRed ? "相" : "象"; break;
            case SHI:   pieceName = isRed ? "仕" : "士"; break;
            case JIANG: pieceName = isRed ? "帅" : "将"; break;
            case PAO:   pieceName = "炮"; break;
            case BING:  pieceName = isRed ? "兵" : "卒"; break;
            default:    pieceName = "?";
        }
        return colorPrefix + pieceName;
    }

    @Override
    public String toString() {
        return "ChessPiece{" + getChineseName() + " at (" + row + "," + col + ")}";
    }
}
