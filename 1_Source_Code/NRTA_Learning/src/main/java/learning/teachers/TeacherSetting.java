package learning.teachers;

import run.state.ZoneState;
import run.zone.AggressiveCompressedDBM;
import run.zone.DBM;
import run.zone.IntDBM;
import run.zone.SimplifiedDBM;
import run.zone.SimplifiedIntDBM;
import run.zone.Zone;
import ta.Clock;
import verify.StateHistory.ExactStateHistory;
import verify.StateHistory.StateHistory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

public final class TeacherSetting {
    private TeacherSetting() {}

    public static boolean VERIFY_DBM_SATISFIABLE_CHECK = false;
    public static boolean VERIFY_ALL_ACCEPT = true;
    public static boolean VERIFY_STORE_TRACE = true;
    public static boolean VERIFY_SHOW_UNSAT_TRACE_DETAIL = false;
    public static boolean VERIFY_CANONICAL_TABLE_SOLVE = true;
    public static double VERIFY_SIMPLIFIED_DBM_SPARSE_THRESHOLD = 0.25;
    public static String VERIFY_WORD_TA_NAME = "rlw";

    public enum ZoneImplementationType {
        EXACT_DBM,
        SIMPLIFIED_DBM,
        INT_DBM,
        SIMPLIFIED_INT_DBM,
        AGGRESSIVE_COMPRESSED_DBM
    }

    public static ZoneImplementationType VERIFY_ZONE_IMPLEMENTATION_TYPE = ZoneImplementationType.EXACT_DBM;

    public enum ExtrapolationType {
        NONE,
        EXTRA_M,
        EXTRA_LU
    }

    public static ExtrapolationType VERIFY_EXTRAPOLATION_TYPE = ExtrapolationType.EXTRA_LU;

    private static List<Clock> DBM_CLOCK_LIST = null;
    private static Map<Clock, Integer> DBM_CLOCK_INDEX_MAP = null;

    public static synchronized void setDBMClockIndex(Collection<Clock> clocks) {
        List<Clock> ordered = new ArrayList<>(new LinkedHashSet<>(clocks));
        ordered.removeIf(Clock::isZeroClock);
        Collections.sort(ordered);

        List<Clock> clockList = new ArrayList<>(ordered.size() + 1);
        Map<Clock, Integer> indexMap = new HashMap<>();
        clockList.add(Clock.getZeroClock());
        indexMap.put(Clock.getZeroClock(), 0);

        int idx = 1;
        for (Clock clock : ordered) {
            clockList.add(clock);
            indexMap.put(clock, idx);
            idx++;
        }

        DBM_CLOCK_LIST = Collections.unmodifiableList(clockList);
        DBM_CLOCK_INDEX_MAP = Collections.unmodifiableMap(indexMap);
    }

    public static synchronized boolean hasDBMClockIndexConfigured() {
        return DBM_CLOCK_LIST != null && DBM_CLOCK_INDEX_MAP != null;
    }

    public static synchronized List<Clock> getDBMClockList() {
        if (!hasDBMClockIndexConfigured()) {
            throw new IllegalStateException("Global DBM clock index is not configured");
        }
        return DBM_CLOCK_LIST;
    }

    public static synchronized Map<Clock, Integer> getDBMClockIndexMap() {
        if (!hasDBMClockIndexConfigured()) {
            throw new IllegalStateException("Global DBM clock index is not configured");
        }
        return DBM_CLOCK_INDEX_MAP;
    }

    public static synchronized int getDBMClockSize() {
        return getDBMClockList().size();
    }

    public static StateHistory<ZoneState> createStateHistory() {
        return new ExactStateHistory();
    }

    public static Zone createZone() {
        return switch (VERIFY_ZONE_IMPLEMENTATION_TYPE) {
            case EXACT_DBM -> new DBM();
            case SIMPLIFIED_DBM -> new SimplifiedDBM();
            case INT_DBM -> new IntDBM();
            case SIMPLIFIED_INT_DBM -> new SimplifiedIntDBM();
            case AGGRESSIVE_COMPRESSED_DBM -> new AggressiveCompressedDBM();
        };
    }
}
