package com.spt.sptpicturebackend.utils;

import java.awt.Color;

/**
 * 颜色相似度计算工具类
 *
 * @author zhiyuan
 */
public class ColorSimilarityCalculator {

    /**
     * 使用欧几里得距离计算两个Color对象的相似度
     *
     * @param color1 第一个颜色
     * @param color2 第二个颜色
     * @return 相似度值，范围0-1，1表示完全相同，0表示完全不同
     */
    public static double calculateSimilarity(Color color1, Color color2) {
        // 获取RGB分量
        int r1 = color1.getRed();
        int g1 = color1.getGreen();
        int b1 = color1.getBlue();

        int r2 = color2.getRed();
        int g2 = color2.getGreen();
        int b2 = color2.getBlue();

        // 计算欧几里得距离
        double distance = Math.sqrt(Math.pow(r1 - r2, 2) + Math.pow(g1 - g2, 2) + Math.pow(b1 - b2, 2));

        // 转换为相似度 (最大距离约为441.67，即sqrt(3*255^2))
        double maxDistance = Math.sqrt(3 * Math.pow(255, 2));
        return 1 - (distance / maxDistance);
    }

    /**
     * 使用欧几里得距离计算两个十六进制颜色值的相似度
     *
     * @param hex1 第一个十六进制颜色值 (#RRGGBB格式)
     * @param hex2 第二个十六进制颜色值 (#RRGGBB格式)
     * @return 相似度值，范围0-1，1表示完全相同，0表示完全不同
     */
    public static double calculateSimilarity(String hex1, String hex2) {
        // 使用Color.decode()解析十六进制颜色值为Color对象
        Color color1 = Color.decode(hex1);
        Color color2 = Color.decode(hex2);

        // 复用已有的Color对象计算方法
        return calculateSimilarity(color1, color2);
    }

    /**
     * 主函数，用于测试
     */
    public static void main(String[] args) {
        // 示例1: 测试相同的颜色
        Color red1 = Color.RED;
        Color red2 = new Color(255, 0, 0);
        System.out.println("相同颜色测试:");
        System.out.println("Color.RED vs new Color(255, 0, 0): " + calculateSimilarity(red1, red2));
        System.out.println();

        // 示例2: 测试不同的Color对象
        Color blue = Color.BLUE;
        Color green = Color.GREEN;
        System.out.println("不同颜色测试:");
        System.out.println("Color.BLUE vs Color.GREEN: " + calculateSimilarity(blue, green));
        System.out.println();

        // 示例3: 测试十六进制颜色字符串
        System.out.println("十六进制颜色测试:");
        System.out.println("#FF0000 vs #FF0000: " + calculateSimilarity("#FF0000", "#FF0000"));
        System.out.println("#FF0000 vs #00FF00: " + calculateSimilarity("#FF0000", "#00FF00"));
        System.out.println("#FFFFFF vs #000000: " + calculateSimilarity("#FFFFFF", "#000000"));
        System.out.println();

        // 示例4: 混合测试 - Color对象与十六进制字符串
        Color yellow = Color.YELLOW;
        System.out.println("混合测试:");
        System.out.println("Color.YELLOW vs #FFFF00: " + calculateSimilarity(yellow, Color.decode("#FFFF00")));
        System.out.println("Color.YELLOW vs #FF0000: " + calculateSimilarity(yellow, Color.decode("#FF0000")));
        System.out.println();

        // 示例5: 相近颜色测试
        Color lightRed1 = new Color(255, 100, 100);
        Color lightRed2 = new Color(255, 110, 110);
        System.out.println("相近颜色测试:");
        System.out.println("rgb(255,100,100) vs rgb(255,110,110): " + calculateSimilarity(lightRed1, lightRed2));
    }

}
