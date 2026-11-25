package baosys.manager.infra.database.mapper;

/**
 * 转义 SQL LIKE 通配符，确保搜索词按字面子串匹配。
 */
final class LikePatterns {

    private LikePatterns() {}

    /**
     * 将搜索词中的反斜杠、百分号和下划线转义后拼接为包含匹配模式，需配合 {@code ESCAPE '\'} 使用。
     *
     * @param value 非空搜索词
     * @return 可直接作为 LIKE 参数的包含匹配模式
     */
    static String contains(String value) {
        String escaped = value
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_");
        return "%" + escaped + "%";
    }
}
