package com.qy.citytechupgrade.common.util;

import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;

public final class SubmissionNoUtils {
    private static final long MOD = 10_000_000_000L;

    private SubmissionNoUtils() {
    }

    public static String buildNo(String enterpriseName, Long submissionId, int salt) {
        String baseName = enterpriseName == null ? "" : enterpriseName.trim();
        long idPart = submissionId == null ? 0L : submissionId;
        String source = baseName + "#" + idPart + "#" + salt;
        CRC32 crc32 = new CRC32();
        crc32.update(source.getBytes(StandardCharsets.UTF_8));
        long num = crc32.getValue() % MOD;
        return "TG" + String.format("%010d", num);
    }
}
