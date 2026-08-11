package verify;


import ta.TA;

import java.util.ArrayList;

public class GlobalSetting {
    public static boolean SHOW_TARGET = false;
    public static boolean SHOW_MAIN_TURN = false;
    public static boolean SHOW_REFINE_TURN = false;
    public static boolean SHOW_PREPARED_TABLE = false;
    public static boolean SHOW_HYPOTHESIS = false;
    public static boolean SHOW_SINK_TRANSITIONS = false;
    public static boolean SHOW_TABLE_CHANGE = false;
    public static boolean DBM_SATISFIABLE_CHECK = false;
    public static boolean SHOW_COUNTER_EXAMPLES = false;

    public static boolean IF_NORMALIZATION = true;
    public static double NORMALIZATION_FRAC = 0.1;
    public static String DEFAULT_HYPOTHESIS_NAME = "H";
    public static String WORD_TA_NAME = "rlw";
    public static int DBM_NAME_WIDTH = 8;
    public static int DBM_ELEMENT_WIDTH = 12;

    public static boolean ALL_ACCEPT = true;

    public static boolean SHOW_TABLE_MODEL = false;
    public static boolean SAVE_HYPOTHESIS = false;
    public static boolean SHOW_SIZE_CHANGE = false;
    public static boolean SHOW_ROLLBACK_POINT = false;

    public static boolean USE_STRICT_EQUAL = true;
    public static TA[] TARGETS;

    public static boolean FIX_CTX = false;
}
