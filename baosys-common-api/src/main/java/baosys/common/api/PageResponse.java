package baosys.common.api;

import java.util.List;

/**
 * 通用分页查询结果，封装当前页的数据及符合查询条件的分页统计信息。
 *
 * <p>对外接口使用从 0 开始的页码；调用从 1 开始计数的分页组件时，由业务实现负责
 * 转换。调用方负责查询总记录数，推荐通过 {@link #of(List, long, int, int)}
 * 创建响应，由该方法校验分页参数并计算总页数，不自动修正越界页码。</p>
 *
 * @param <T> 单条数据的响应类型，例如用户信息或登录会话信息
 * @param items 当前页的数据列表，不包含其他页的数据
 * @param total 符合当前查询条件的总记录数，不是当前页返回的条数
 * @param page 当前请求的页码，从 0 开始，0 表示第一页
 * @param size 每页允许返回的最大条数，即请求的分页大小
 * @param totalPages 按总记录数和分页大小计算的总页数，无记录时为 0
 */
public record PageResponse<T>(
        /**
         * 当前页的数据，顺序由查询的排序规则决定。
         *
         * <p>实际条数通常不超过 {@code size}，最后一页可能不足一页；无匹配数据时
         * 使用空列表。构造时会创建不可修改的列表快照，因此不能通过返回的列表增删
         * 或替换元素。此复制是浅复制，不保证元素对象本身不可修改。
         * 列表及其中的元素均不能为 {@code null}。</p>
         */
        List<T> items,

        /**
         * 符合本次筛选条件的总记录数，包含所有页的数据，应为非负数。
         *
         * <p>用于展示总条数和计算总页数，不等同于 {@code items.size()}。
         * 例如共有 45 条匹配数据，每页 20 条，则每一页对应的总记录数均为 45
         * （在查询期间数据未发生变化的情况下）。</p>
         */
        long total,

        /**
         * 当前请求的页码，从 0 开始，应为非负数：0 为第一页，1 为第二页。
         *
         * <p>有数据时，最后一页的索引为 {@code totalPages - 1}。本类型不将越界
         * 页码自动调整为最后一页；越界请求的处理由接口或查询实现决定。</p>
         */
        int page,

        /**
         * 请求指定的每页容量，应为正整数，不代表本次实际返回的数据条数。
         *
         * <p>例如请求每页 20 条，即使最后一页仅返回 5 条，该字段仍为 20。
         * 默认值和最大允许值由具体接口约定，本类型不设置统一上限。</p>
         */
        int size,

        /**
         * 总页数，应为非负数；在 {@code size > 0} 时等于
         * {@code ceil(total / size)}，其中除法按数学实数除法理解并向上取整。
         *
         * <p>例如总记录数为 45、每页容量为 20 时，总页数为 3，有效页码为
         * 0、1、2；总记录数为 0 时，总页数为 0。使用 {@code of} 工厂方法时
         * 自动计算；直接调用五参数构造器时，由调用方保证该值正确。</p>
         */
        int totalPages) {

    /**
     * 保存当前页数据的不可修改快照，避免调用方随后修改原列表影响响应内容。
     *
     * @throws NullPointerException {@code items} 为 {@code null} 或包含 {@code null} 元素
     */
    public PageResponse {
        items = List.copyOf(items);
    }

    /**
     * 根据当前页数据和分页统计创建响应，自动向上取整计算总页数。
     *
     * <p>先计算商和余数，避免 {@code total + size - 1} 在总记录数较大时溢出。
     * 保留调用方传入的页码，不将越界页码调整为最后一页。</p>
     *
     * @param <T> 单条数据的响应类型
     * @param items 当前页数据，列表及元素均不能为 {@code null}
     * @param total 符合查询条件的总记录数，必须大于或等于 0
     * @param page 从 0 开始的请求页码，必须大于或等于 0
     * @param size 请求的每页容量，必须大于 0
     * @return 包含自动计算的总页数及不可修改数据列表的分页响应
     * @throws IllegalArgumentException 总记录数或页码为负数，或者每页容量不为正数
     * @throws ArithmeticException 总页数超出 {@code int} 的表示范围
     * @throws NullPointerException 数据列表为 {@code null} 或包含 {@code null} 元素
     */
    public static <T> PageResponse<T> of(List<T> items, long total, int page, int size) {
        if (total < 0) {
            throw new IllegalArgumentException("total must be nonnegative");
        }
        if (page < 0) {
            throw new IllegalArgumentException("page must be nonnegative");
        }
        if (size <= 0) {
            throw new IllegalArgumentException("size must be positive");
        }
        long totalPages = total / size + (total % size == 0 ? 0 : 1);
        return new PageResponse<>(items, total, page, size, Math.toIntExact(totalPages));
    }
}
