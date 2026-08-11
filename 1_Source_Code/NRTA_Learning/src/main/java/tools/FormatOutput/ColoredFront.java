package tools.FormatOutput;

import learning.LearningSetting;

public class ColoredFront {
    public static void coloredPrint(String content, Color color, Type type){
        System.out.println(ColoredFront.dealtString(content, color, type));
    }

    public static String dealtString(String content, Color color, Type type){
        String color_id = colorCode(color);
        int type_id;
        switch (type){
            case Bold -> {
                type_id = 1;
            }
            case Italic -> {
                type_id = 3;
            }
            case Underline ->{
                type_id = 4;
            }
            default -> {
                type_id = 0;
            }
        }
        return dealtString(content,color_id,type_id);
    }

    private static String colorCode(Color color) {
        if (LearningSetting.CONSOLE_COLOR_THEME == LearningSetting.ConsoleColorTheme.LIGHT) {
            return switch (color) {
                case Yellow -> "38;5;166";
                case Red -> "38;5;124";
                case Green -> "38;5;22";
                case Orange -> "38;5;130";
                case Blue -> "38;5;19";
                case Purple -> "38;5;54";
                case Cyan -> "38;5;30";
            };
        }
        return switch (color) {
            case Yellow -> "93";
            case Red -> "31";
            case Green -> "32";
            case Orange -> "33";
            case Blue -> "34";
            case Purple -> "35";
            case Cyan -> "36";
        };
    }

    public static String dealtString(String content, String color, int type) {
        boolean hasType = type == 1 || type == 3 || type == 4;
        if (hasType) {
            return String.format("\033[%d;%sm%s\033[0m", type, color, content);
        } else {
            return String.format("\033[%sm%s\033[0m", color, content);
        }
    }

    public static String dealtString(String content, int color, int type) {
        boolean hasType = type != 1 && type != 3 && type != 4;
        if (hasType) {
            return String.format("\033[%dm%s\033[0m", color, content);
        } else {
            return String.format("\033[%d;%dm%s\033[0m", color, type, content);
        }
    }

    public static void showMainTurn(int turn){
        String content = String.format("[ TURN %04d ]", turn);
        System.out.println(ColoredFront.dealtString(content, Color.Green, Type.Common));
    }

}
