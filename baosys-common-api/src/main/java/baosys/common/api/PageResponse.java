package baosys.common.api;

import java.util.List;

public record PageResponse<T>(
        List<T> items,
        long total,
        int page,
        int size,
        int totalPages) {

    public PageResponse {
        items = List.copyOf(items);
    }
}
