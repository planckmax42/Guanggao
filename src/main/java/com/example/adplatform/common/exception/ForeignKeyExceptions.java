package com.example.adplatform.common.exception;

import java.sql.SQLException;

/** 识别 MySQL 外键冲突；业务错误码和提示由 Service 决定。 */
public final class ForeignKeyExceptions {

    private static final int ROW_IS_REFERENCED = 1451;
    private static final int NO_REFERENCED_ROW = 1452;

    private ForeignKeyExceptions() {
    }

    public static boolean isReferenced(Throwable exception) {
        return hasSqlErrorCode(exception, ROW_IS_REFERENCED);
    }

    public static boolean isMissingReference(Throwable exception) {
        return hasSqlErrorCode(exception, NO_REFERENCED_ROW);
    }

    private static boolean hasSqlErrorCode(Throwable exception, int errorCode) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sqlException && sqlException.getErrorCode() == errorCode) {
                return true;
            }
        }
        return false;
    }
}
