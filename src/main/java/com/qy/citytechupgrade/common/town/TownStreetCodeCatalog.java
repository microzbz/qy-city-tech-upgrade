package com.qy.citytechupgrade.common.town;

import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class TownStreetCodeCatalog {
    private static final List<TownStreetCode> CODES = List.of(
        new TownStreetCode("01", "滨海湾"),
        new TownStreetCode("02", "常平镇"),
        new TownStreetCode("03", "大朗镇"),
        new TownStreetCode("04", "大岭山镇"),
        new TownStreetCode("05", "东城街道"),
        new TownStreetCode("06", "东坑镇"),
        new TownStreetCode("07", "洪梅镇"),
        new TownStreetCode("08", "厚街镇"),
        new TownStreetCode("09", "虎门镇"),
        new TownStreetCode("10", "寮步镇"),
        new TownStreetCode("11", "麻涌镇"),
        new TownStreetCode("12", "南城街道"),
        new TownStreetCode("13", "沙田镇"),
        new TownStreetCode("14", "松山湖"),
        new TownStreetCode("15", "望牛墩镇"),
        new TownStreetCode("16", "长安镇"),
        new TownStreetCode("17", "中堂镇"),
        new TownStreetCode("18", "茶山镇"),
        new TownStreetCode("19", "道滘镇"),
        new TownStreetCode("20", "凤岗镇"),
        new TownStreetCode("21", "高埗镇"),
        new TownStreetCode("22", "莞城街道"),
        new TownStreetCode("23", "横沥镇"),
        new TownStreetCode("24", "黄江镇"),
        new TownStreetCode("25", "企石镇"),
        new TownStreetCode("26", "桥头镇"),
        new TownStreetCode("27", "清溪镇"),
        new TownStreetCode("28", "石碣镇"),
        new TownStreetCode("29", "石龙镇"),
        new TownStreetCode("30", "石排镇"),
        new TownStreetCode("31", "塘厦镇"),
        new TownStreetCode("32", "万江街道"),
        new TownStreetCode("33", "谢岗镇"),
        new TownStreetCode("34", "樟木头镇")
    );

    private TownStreetCodeCatalog() {
    }

    public static List<TownStreetCode> list() {
        return CODES;
    }

    public static Optional<TownStreetCode> findByCode(String code) {
        String normalized = normalizeCode(code);
        if (!StringUtils.hasText(normalized)) {
            return Optional.empty();
        }
        return CODES.stream()
            .filter(item -> item.code().equals(normalized))
            .findFirst();
    }

    public static boolean isValidCode(String code) {
        return findByCode(code).isPresent();
    }

    public static String normalizeCode(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (normalized.matches("\\d")) {
            return "0" + normalized;
        }
        return normalized;
    }
}
